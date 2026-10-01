@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.dot.gallery.feature_node.presentation.mediaview.components.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.dot.gallery.core.util.HdrCapabilities
import com.dot.gallery.feature_node.domain.model.SubtitleTrack
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import java.util.Locale
import com.dot.gallery.cloud.core.CloudAccountRuntimeSettings
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.media.CloudDataSource
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printInfo
import com.dot.gallery.feature_node.presentation.util.printWarn
import com.dot.gallery.feature_node.presentation.util.printWarning
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * A ViewModel that owns an ExoPlayer instance keyed by a media.id.
 * This survives configuration changes, reducing churn and preserving playback state.
 *
 * You still pass the current Media object from the Composable; if it changes (same id, new instance),
 * the ViewModel reuses the existing player unless the underlying uri actually changed.
 *
 * Persisted (process death) fields via SavedStateHandle:
 * - positionMs
 * - wasPlaying
 */
@UnstableApi
@HiltViewModel(assistedFactory = VideoPlayerViewModel.Factory::class)
class VideoPlayerViewModel @AssistedInject constructor(
    @param:ApplicationContext
    private val appContext: Context,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    @Assisted("media") private val media: Media,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("media") media: Media): VideoPlayerViewModel
    }

    data class PlaybackState(
        val isDecrypting: Boolean = false,
        val decryptFailed: Boolean = false,
        val playbackFailed: Boolean = false,
        val ready: Boolean = false,
        val durationMs: Long = 0L,
        val positionMs: Long = 0L,
        val bufferedPercent: Int = 0,
        val frameRate: Float = 60f,
        val isPlaying: Boolean = false,
        val subtitleTracks: List<SubtitleTrack> = emptyList(),
        val videoHdr: VideoHdrInfo = VideoHdrInfo.NONE
    )

    private val keychainHolder = KeychainHolder(appContext)

    private var decryptedFile: File? = null
    private var initialSeekApplied = false
    private var progressJob: Job? = null
    private var loadTimeoutJob: Job? = null
    private val _manualSubtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

    private val mediaUriString = media.getUri().toString()
    private var configuredRepeatMode = videoRepeatMode(
        isCloud = media.isCloud,
        mediaUri = mediaUriString,
        settingsByConfigId = CloudRuntimeSettings.settingsByConfigId.value,
    )
    private var slideshowActive = false

    // Public immutable flow
    private val _state =
        MutableStateFlow(PlaybackState(isDecrypting = media.isEncrypted))
    val state: StateFlow<PlaybackState> = _state

    // Owned player — exposed as StateFlow so Compose recomposes on player recreation
    private val _playerFlow = MutableStateFlow(createExoPlayer())
    val playerFlow: StateFlow<ExoPlayer> = _playerFlow
    var player: ExoPlayer
        get() = _playerFlow.value
        set(value) { _playerFlow.value = value }

    init {
        restoreFromSavedState()
        observeCloudLoopSetting()
        prepareMedia()
        startProgressLoop()
    }

    @UnstableApi
    private fun createExoPlayer(): ExoPlayer {
        val dataSourceFactory = CloudDataSource.Factory(appContext)
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
            setSeekParameters(SeekParameters.EXACT)
            repeatMode = effectiveRepeatMode()
            // Ensure text tracks are not disabled so embedded subtitles are available
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        markReady()
                    }
                    updateDuration(duration)
                }

                override fun onPlayerError(error: PlaybackException) {
                    printWarning("Video load failed for ${media.id}: ${error.message}")
                    loadTimeoutJob?.cancel()
                    _state.update {
                        it.copy(
                            ready = false,
                            isPlaying = false,
                            playbackFailed = true,
                        )
                    }
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    // Duration might update after dynamic metadata
                    updateDuration(player.duration)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    // Keep isPlaying consistent (ExoPlayer may report false until ready)
                    if (!playWhenReady) {
                        _state.update { it.copy(isPlaying = false) }
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    updateSubtitleTracks(tracks)
                    updateVideoHdrInfo(tracks)
                }
            })
            // Reports which decoder was actually selected for the video track — the field that
            // proves on a reporter's device whether a Dolby Vision decoder (c2.dolby.*/OMX.dolby.*)
            // or a base-layer fallback was picked.
            addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long
                ) {
                    if (_state.value.videoHdr.decoderName == decoderName) return
                    _state.update {
                        it.copy(videoHdr = it.videoHdr.copy(decoderName = decoderName))
                    }
                    val info = _state.value.videoHdr
                    printInfo(
                        "video.hdr",
                        "decoder=$decoderName type=${info.type} codecs=${info.codecs} " +
                            "dvPlayback=${dolbyVisionPlayback(
                                info,
                                HdrCapabilities.hasDolbyVisionDecoder(),
                                HdrCapabilities.hasDolbyVisionDisplay(appContext)
                            )}"
                    )
                }
            })
        }
    }

    private fun observeCloudLoopSetting() {
        if (!media.isCloud) return
        viewModelScope.launch {
            CloudRuntimeSettings.settingsByConfigId
                .map { videoRepeatMode(true, mediaUriString, it) }
                .distinctUntilChanged()
                .collect { repeatMode ->
                    configuredRepeatMode = repeatMode
                    applyRepeatMode()
                }
        }
    }

    private fun effectiveRepeatMode(): Int =
        effectiveVideoRepeatMode(configuredRepeatMode, slideshowActive)

    private fun applyRepeatMode() {
        if (!player.isReleased) player.repeatMode = effectiveRepeatMode()
    }

    fun setSlideshowActive(active: Boolean) {
        slideshowActive = active
        applyRepeatMode()
    }

    private fun restoreFromSavedState() {
        val positionRestore = savedStateHandle.get<Long>(KEY_POSITION) ?: 0L
        val wasPlayingRestore = savedStateHandle.get<Boolean>(KEY_PLAYING) ?: false
        if (positionRestore > 0) {
            // Seek will be applied after player becomes ready
            _state.update { it.copy(positionMs = positionRestore, isPlaying = wasPlayingRestore) }
        }
    }

    private fun prepareMedia() {
        if (media.isEncrypted) {
            decryptAndPrepare()
        } else {
            setAndPrepare(media.getUri(), media.mimeType)
            retrieveFrameRate(encrypted = false)
        }
    }

    private fun decryptAndPrepare() {
        viewModelScope.launch {
            _state.update { it.copy(isDecrypting = true, decryptFailed = false) }
            decryptedFile = withContext(Dispatchers.IO) {
                try {
                    createDecryptedVideoFile(keychainHolder, media)
                } catch (t: Throwable) {
                    printWarning("Decrypt failed: ${t.message}")
                    null
                }
            }
            if (decryptedFile == null) {
                _state.update { it.copy(isDecrypting = false, decryptFailed = true) }
                return@launch
            }
            _state.update { it.copy(isDecrypting = false, decryptFailed = false) }
            setAndPrepare(Uri.fromFile(decryptedFile!!), media.mimeType)
            retrieveFrameRate(encrypted = true)
        }
    }

    private fun setAndPrepare(uri: Uri, mime: String?) {
        val existingUri = player.currentMediaItem?.localConfiguration?.uri
        if (existingUri == uri && !_state.value.playbackFailed) return

        initialSeekApplied = false
        _state.update {
            it.copy(
                ready = false,
                playbackFailed = false,
                videoHdr = VideoHdrInfo.NONE
            )
        }
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mime)
            .build()
        player.setMediaItem(item)
        player.prepare()
        loadTimeoutJob?.cancel()
        loadTimeoutJob = viewModelScope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (!_state.value.ready) {
                player.pause()
                _state.update {
                    it.copy(
                        ready = false,
                        isPlaying = false,
                        playbackFailed = true,
                    )
                }
            }
        }
    }

    private fun markReady() {
        loadTimeoutJob?.cancel()
        if (!_state.value.ready) {
            _state.update { it.copy(ready = true, playbackFailed = false) }
            // Apply initial seek only once
            if (!initialSeekApplied && _state.value.positionMs > 0) {
                player.seekTo(_state.value.positionMs)
                if (_state.value.isPlaying) {
                    player.play()
                }
                initialSeekApplied = true
            }
        }
    }

    private fun updateDuration(duration: Long) {
        if (duration > 0 && duration != _state.value.durationMs) {
            _state.update { it.copy(durationMs = duration) }
        }
    }

    @UnstableApi
    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val p = player
                if (!p.isReleased) {
                    val pos = p.currentPosition
                    val buffered = p.bufferedPercentage
                    _state.update {
                        it.copy(
                            positionMs = pos,
                            bufferedPercent = buffered
                        )
                    }
                }
                delay(1.seconds / 30) // ~30fps updates
            }
        }
    }

    private fun retrieveFrameRate(encrypted: Boolean) {
        if (media.isCloud) {
            _state.update { it.copy(frameRate = 30f) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val fps = try {
                MediaMetadataRetriever().use { r ->
                    if (encrypted) {
                        decryptedFile?.inputStream()?.use { r.setDataSource(it.fd) }
                    } else {
                        r.setDataSource(appContext, media.getUri())
                    }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        ?.toFloat()
                        ?: 60f
                }
            } catch (_: Exception) {
                60f
            }
            _state.update { it.copy(frameRate = fps) }
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pause() {
        if (player.isReleased) return
        player.playWhenReady = false
        player.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun setUserPlayWhenReady(play: Boolean, canAutoPlay: Boolean) {
        // Apply user intended state respecting autoplay rules externally
        val targetPlay = if (!play && canAutoPlay) false else play
        player.playWhenReady = targetPlay
        if (targetPlay) player.play() else player.pause()
        _state.update { it.copy(isPlaying = targetPlay) }
    }

    fun retryDecryption() {
        if (!_state.value.decryptFailed) return
        decryptAndPrepare()
    }

    fun retryPlayback() {
        if (!_state.value.playbackFailed) return
        if (media.isEncrypted && decryptedFile != null) {
            setAndPrepare(Uri.fromFile(decryptedFile!!), media.mimeType)
        } else {
            setAndPrepare(media.getUri(), media.mimeType)
        }
    }

    @UnstableApi
    fun reattachFromComposition() {
        if (player.isReleased) {
            printDebug("Reattached to composition ${media.id}'s video")
            runCatching {
                _state.update { it.copy(ready = false) }
                initialSeekApplied = false
                player = createExoPlayer()
                restoreFromSavedState()
                prepareMedia()
                startProgressLoop()
                val isPlaying = savedStateHandle.get<Boolean>(KEY_PLAYING)
                player.playWhenReady = isPlaying ?: _state.value.isPlaying

                printDebug("Video after reattach: playWhenReady: ${player.playWhenReady}")
            }
        } else {
            printDebug("Skipped re-attaching to composition ${media.id}'s video. We are already there")
        }
    }

    @UnstableApi
    fun detachFromComposition() {
        printDebug("Cleared from composition ${media.id}'s video")
        progressJob?.cancel()
        progressJob = null
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        runCatching {
            if (!player.isReleased) {
                savedStateHandle[KEY_POSITION] = player.currentPosition
                savedStateHandle[KEY_PLAYING] = player.isPlaying
                player.release()
            }
        }
    }

    @UnstableApi
    override fun onCleared() {
        progressJob?.cancel()
        progressJob = null
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        try {
            if (!player.isReleased) {
                // Persist position & play state
                savedStateHandle[KEY_POSITION] = player.currentPosition
                savedStateHandle[KEY_PLAYING] = player.isPlaying
                player.release()
            }
        } catch (_: Throwable) {
        }
        decryptedFile?.delete()
        decryptedFile = null
        super.onCleared()
    }

    private fun updateSubtitleTracks(tracks: Tracks) {
        val subs = mutableListOf<SubtitleTrack>()
        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language
                val displayName = format.label
                    ?: lang?.let { Locale.forLanguageTag(it).displayLanguage }
                    ?: "Track ${subs.size + 1}"
                printDebug("Subtitle track found: groupIndex=$groupIndex, trackIndex=$i, label=$displayName, lang=$lang, selected=${group.isTrackSelected(i)}, supported=${group.isTrackSupported(i)}")
                subs.add(
                    SubtitleTrack(
                        groupIndex = groupIndex,
                        trackIndex = i,
                        label = displayName,
                        language = lang,
                        isSelected = group.isTrackSelected(i)
                    )
                )
            }
        }
        // Mark manually-added tracks (they appear after embedded ones)
        val manualCount = _manualSubtitleConfigs.size
        val embeddedCount = (subs.size - manualCount).coerceAtLeast(0)
        for (i in embeddedCount until subs.size) {
            val manualIdx = i - embeddedCount
            val uri = _manualSubtitleConfigs.getOrNull(manualIdx)?.uri
            val filename = uri?.lastPathSegment?.substringAfterLast('/') ?: subs[i].label
            subs[i] = subs[i].copy(
                label = filename,
                isManuallyAdded = true,
                manualIndex = manualIdx
            )
        }
        printDebug("Subtitle tracks total: ${subs.size} (embedded=$embeddedCount, manual=$manualCount)")
        _state.update { it.copy(subtitleTracks = subs) }
    }

    /**
     * Classify the selected video track's HDR format (Dolby Vision / HDR10 / HLG / SDR) from its
     * [androidx.media3.common.Format] into [PlaybackState.videoHdr]. The media viewer drives the
     * window's COLOR_MODE_HDR from this — without it the window stays in SDR and HDR output is
     * tone-mapped by the compositor even on capable devices (#1274).
     */
    @UnstableApi
    private fun updateVideoHdrInfo(tracks: Tracks) {
        val videoGroup = tracks.groups.firstOrNull { group ->
            group.type == C.TRACK_TYPE_VIDEO && group.isSelected
        } ?: tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        val format = videoGroup?.let { group ->
            (0 until group.length)
                .firstOrNull { group.isTrackSelected(it) }
                ?.let(group::getTrackFormat)
                ?: group.getTrackFormat(0)
        }
        val info = format?.let {
            classifyVideoHdr(it.sampleMimeType, it.codecs, it.colorInfo?.colorTransfer)
        } ?: VideoHdrInfo.NONE
        if (info.type == _state.value.videoHdr.type && info.codecs == _state.value.videoHdr.codecs) return
        // Preserve the decoder name reported by the analytics listener across track updates.
        _state.update { it.copy(videoHdr = info.copy(decoderName = it.videoHdr.decoderName)) }
        if (info.type != VideoHdrType.NONE) {
            val playback = dolbyVisionPlayback(
                info,
                HdrCapabilities.hasDolbyVisionDecoder(),
                HdrCapabilities.hasDolbyVisionDisplay(appContext)
            )
            val message = "HDR video track: type=${info.type} mime=${format?.sampleMimeType} " +
                "codecs=${info.codecs} colorTransfer=${format?.colorInfo?.colorTransfer} " +
                "dvProfile=${info.dolbyVisionProfile} dvPlayback=$playback"
            if (playback == DolbyVisionPlayback.UNSUPPORTED) {
                printWarn("video.hdr", "$message — no Dolby Vision decoder on this device")
            } else {
                printInfo("video.hdr", message)
            }
        }
    }

    fun selectSubtitleTrack(track: SubtitleTrack) {
        val tracks = player.currentTracks
        val groups = tracks.groups
        if (track.groupIndex !in groups.indices) return
        val trackGroup = groups[track.groupIndex].mediaTrackGroup

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(trackGroup, listOf(track.trackIndex))
            )
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        // Force refresh so the UI reflects the new selection immediately
        updateSubtitleTracks(player.currentTracks)
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        updateSubtitleTracks(player.currentTracks)
    }

    @UnstableApi
    fun addExternalSubtitle(uri: Uri) {
        val subtitleConfig = buildSubtitleConfig(uri)
        _manualSubtitleConfigs.add(subtitleConfig)
        rebuildMediaItemWithSubtitles()
    }

    @UnstableApi
    fun removeExternalSubtitle(track: SubtitleTrack) {
        if (!track.isManuallyAdded || track.manualIndex !in _manualSubtitleConfigs.indices) return
        _manualSubtitleConfigs.removeAt(track.manualIndex)
        rebuildMediaItemWithSubtitles()
    }

    private fun buildSubtitleConfig(uri: Uri): MediaItem.SubtitleConfiguration {
        val path = uri.path?.lowercase() ?: ""
        val subtitleMime = when {
            path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            path.endsWith(".vtt") || path.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP // fallback
        }
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(subtitleMime)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    @UnstableApi
    private fun rebuildMediaItemWithSubtitles() {
        val currentItem = player.currentMediaItem ?: return
        val currentPosition = player.currentPosition
        val wasPlaying = player.isPlaying

        val newItem = currentItem.buildUpon()
            .setSubtitleConfigurations(_manualSubtitleConfigs.toList())
            .build()

        player.setMediaItem(newItem, currentPosition)
        player.prepare()
        // Re-enable text tracks in case they were disabled
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        if (wasPlaying) player.play()
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val KEY_POSITION = "positionMs"
        private const val KEY_PLAYING = "wasPlaying"
    }
}

internal fun effectiveVideoRepeatMode(configuredRepeatMode: Int, slideshowActive: Boolean): Int =
    if (slideshowActive) Player.REPEAT_MODE_OFF else configuredRepeatMode

internal fun videoRepeatMode(
    isCloud: Boolean,
    mediaUri: String,
    settingsByConfigId: Map<Long, CloudAccountRuntimeSettings>,
): Int {
    if (!isCloud) return Player.REPEAT_MODE_ONE
    val loopVideos = CloudRuntimeSettings
        .settingsForCloudUri(mediaUri, settingsByConfigId)
        .loopVideos
    return if (loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
}