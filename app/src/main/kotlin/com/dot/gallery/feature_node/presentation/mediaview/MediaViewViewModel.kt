package com.dot.gallery.feature_node.presentation.mediaview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.metadata.MediaContainerFormat
import com.dot.gallery.core.metadata.MetadataRemovalMode
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.local.LocalPeopleProvider
import com.dot.gallery.core.workers.RotateMediaWorker
import com.dot.gallery.core.workers.rotateImage
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MotionPhotoHelper
import com.dot.gallery.feature_node.domain.util.MotionPhotoInfo
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

private const val NUM_FILMSTRIP_FRAMES = 12
private const val FILMSTRIP_THUMB_HEIGHT = 108 // px, ~36dp @ 3x

internal fun scaledFilmstripFrameWidth(
    sourceWidth: Int,
    sourceHeight: Int,
    targetHeight: Int,
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetHeight <= 0) return 1
    return (sourceWidth.toFloat() * targetHeight / sourceHeight).roundToInt().coerceAtLeast(1)
}

@HiltViewModel
class MediaViewViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val repository: MediaRepository,
    private val localPeopleProvider: LocalPeopleProvider,
    private val visualSearchLauncher: VisualSearchLauncher,
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<MediaViewEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<MediaViewEvent> = _uiEvents

    // Non-null while a rotation write is in flight; drives the busy state of the top-bar Rotate chip.
    private val _rotationState = MutableStateFlow<RotationUiState?>(null)
    val rotationState: StateFlow<RotationUiState?> = _rotationState.asStateFlow()

    // Non-Idle while a shareable image is being produced for the visual-search provider
    // (cloud download, vault decrypt, video frame extract, exotic-format transcode).
    private val _visualSearchState = MutableStateFlow<VisualSearchUiState>(VisualSearchUiState.Idle)
    val visualSearchState: StateFlow<VisualSearchUiState> = _visualSearchState.asStateFlow()

    private var visualSearchJob: Job? = null

    private var rotateWorkId: UUID? = null
    // Id of the media being rotated (so the viewer can hold the visual rotation for that page only).
    private var pendingRotationMediaId: Long? = null

    /** Live list of on-device persons whose faces were detected in [mediaId]. */
    fun peopleForMedia(mediaId: Long): Flow<List<PersonInfo>> =
        localPeopleProvider.getMediaPeople(mediaId)

    /** Un-assign [mediaId] from [personId] without deleting the media. */
    fun removeMediaFromPerson(personId: String, mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            localPeopleProvider.removeMediaFromPerson(personId, listOf(mediaId))
        }
    }

    // ======================== On-demand Metadata Fetching ========================

    sealed interface MetadataSanitizationUiState {
        data object Idle : MetadataSanitizationUiState
        data class Probing(val mediaId: Long) : MetadataSanitizationUiState
        data class Ready(val mediaId: Long, val capability: SanitizationCapability) : MetadataSanitizationUiState
        data class Running(
            val mediaId: Long,
            val mode: MetadataRemovalMode,
            val saveMode: MetadataSaveMode
        ) : MetadataSanitizationUiState
        data class Complete(val mediaId: Long, val result: SanitizationResult) : MetadataSanitizationUiState
    }

    private val _metadataSanitizationState = MutableStateFlow<MetadataSanitizationUiState>(
        MetadataSanitizationUiState.Idle
    )
    val metadataSanitizationState: StateFlow<MetadataSanitizationUiState> =
        _metadataSanitizationState.asStateFlow()

    private var lastMetadataFetchId: Long? = null
    private var metadataSanitizationJob: Job? = null

    fun probeMetadataSanitization(media: Media) {
        if (metadataSanitizationJob?.isActive == true) return
        _metadataSanitizationState.value = MetadataSanitizationUiState.Probing(media.id)
        metadataSanitizationJob = viewModelScope.launch(Dispatchers.IO) {
            val capability = try {
                repository.probeMetadataSanitization(media)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                SanitizationCapability(
                    MediaContainerFormat.UNKNOWN,
                    emptySet(),
                    limitation = "The media could not be inspected safely."
                )
            }
            _metadataSanitizationState.value = MetadataSanitizationUiState.Ready(media.id, capability)
        }
    }

    fun sanitizeMetadata(media: Media, mode: MetadataRemovalMode, saveMode: MetadataSaveMode) {
        if (metadataSanitizationJob?.isActive == true) return
        _metadataSanitizationState.value = MetadataSanitizationUiState.Running(media.id, mode, saveMode)
        metadataSanitizationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                repository.sanitizeMediaMetadata(media, mode, saveMode)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SanitizationResult.CommitFailed(error.message ?: error.javaClass.simpleName, false)
            }
            _metadataSanitizationState.value = MetadataSanitizationUiState.Complete(media.id, result)
        }
    }

    fun resetMetadataSanitization() {
        _metadataSanitizationState.value = MetadataSanitizationUiState.Idle
    }

    fun ensureMetadataAvailable(media: Media?, metadataState: MediaMetadataState) {
        if (media == null) return
        if (media.id == lastMetadataFetchId) return
        val existing = metadataState.metadata.firstOrNull { it.mediaId == media.id }
        if (existing != null && (existing.imageWidth > 0 || existing.manufacturerName != null)) {
            return
        }
        lastMetadataFetchId = media.id
        viewModelScope.launch(Dispatchers.IO) {
            repository.collectMetadataFor(media)
        }
    }

    // ======================== Motion Photo Extraction ========================

    data class MotionPhotoExtraction(
        val info: MotionPhotoInfo? = null,
        val videoFile: File? = null,
        val durationMs: Long = 0L,
        val compositeFilmstrip: Bitmap? = null
    )

    private val _motionPhotoExtraction = MutableStateFlow(MotionPhotoExtraction())
    val motionPhotoExtraction: StateFlow<MotionPhotoExtraction> = _motionPhotoExtraction.asStateFlow()

    private var currentMotionMediaId: Long? = null
    private var currentMotionMediaKey: String? = null
    private var extractionJob: Job? = null

    fun prepareMotionPhoto(media: Media?) {
        if (media == null || media.isVideo) {
            cleanupMotionPhoto()
            return
        }
        if (media.id == currentMotionMediaId && media.key == currentMotionMediaKey) return

        // Release any playing motion player before switching to a new media item,
        // otherwise the old player + progress loop keeps running and its surface
        // overlays the next page's image (black screen bug).
        releaseMotionPlayer()

        extractionJob?.cancel()
        val oldFile = _motionPhotoExtraction.value.videoFile

        currentMotionMediaId = media.id
        currentMotionMediaKey = media.key
        val expectedMediaKey = media.key
        _motionPhotoExtraction.value = MotionPhotoExtraction()

        extractionJob = viewModelScope.launch(Dispatchers.IO) {
            oldFile?.delete()

            val uri: Uri = media.getUri()
            val info = MotionPhotoHelper.parseInfo(context, uri) ?: return@launch
            if (currentMotionMediaKey != expectedMediaKey) return@launch
            _motionPhotoExtraction.value = MotionPhotoExtraction(info = info)

            val file = MotionPhotoHelper.extractVideo(context, uri, info) ?: return@launch
            if (currentMotionMediaKey != expectedMediaKey) {
                file.delete()
                return@launch
            }

            val duration = try {
                MediaMetadataRetriever().use { mmr ->
                    mmr.setDataSource(file.absolutePath)
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                }
            } catch (_: Exception) { 0L }

            if (currentMotionMediaKey != expectedMediaKey) {
                file.delete()
                return@launch
            }
            _motionPhotoExtraction.value = MotionPhotoExtraction(
                info = info, videoFile = file, durationMs = duration
            )

            val frames = MotionPhotoHelper.extractFrames(file, NUM_FILMSTRIP_FRAMES)
            val composite = stitchFrames(frames, FILMSTRIP_THUMB_HEIGHT)
            if (currentMotionMediaKey != expectedMediaKey) {
                composite?.recycle()
                file.delete()
                return@launch
            }
            _motionPhotoExtraction.value = MotionPhotoExtraction(
                info = info, videoFile = file, durationMs = duration,
                compositeFilmstrip = composite
            )
        }
    }

    private fun cleanupMotionPhoto() {
        extractionJob?.cancel()
        releaseMotionPlayer()
        _motionPhotoExtraction.value.videoFile?.delete()
        _motionPhotoExtraction.value = MotionPhotoExtraction()
        currentMotionMediaId = null
        currentMotionMediaKey = null
    }

    // ======================== Motion Photo Playback ========================

    data class MotionPlaybackState(
        val isPlaying: Boolean = false,
        val isLoading: Boolean = false,
        val videoReady: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L
    )

    private val _motionPlayback = MutableStateFlow(MotionPlaybackState())
    val motionPlayback: StateFlow<MotionPlaybackState> = _motionPlayback.asStateFlow()

    var motionPlayer: ExoPlayer? = null
        private set

    private var motionProgressJob: Job? = null

    fun startMotionPlayback() {
        if (_motionPlayback.value.isLoading) return
        val file = _motionPhotoExtraction.value.videoFile ?: return

        motionPlayer?.let { exo ->
            exo.play()
            _motionPlayback.update { it.copy(isPlaying = true) }
            startMotionProgressLoop()
            return
        }

        _motionPlayback.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val exo = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        .setMimeType("video/mp4")
                        .build()
                )
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val dur = duration.coerceAtLeast(0L)
                            _motionPlayback.update {
                                it.copy(videoReady = true, durationMs = dur)
                            }
                        }
                    }
                })
                prepare()
                playWhenReady = true
            }
            motionPlayer = exo
            _motionPlayback.update { it.copy(isPlaying = true, isLoading = false) }
            startMotionProgressLoop()
        }
    }

    fun stopMotionPlayback() {
        motionPlayer?.pause()
        _motionPlayback.update { it.copy(isPlaying = false) }
    }

    fun toggleMotionPlayback() {
        if (_motionPlayback.value.isPlaying) stopMotionPlayback() else startMotionPlayback()
    }

    fun seekMotionTo(ms: Long) {
        motionPlayer?.seekTo(ms)
        _motionPlayback.update { it.copy(positionMs = ms) }
    }

    fun seekMotionAndPause(ms: Long) {
        _motionPlayback.update { it.copy(positionMs = ms) }
        motionPlayer?.let { p ->
            p.seekTo(ms)
            p.pause()
            _motionPlayback.update { it.copy(isPlaying = true) }
        } ?: startMotionPlayback()
    }

    private fun startMotionProgressLoop() {
        motionProgressJob?.cancel()
        motionProgressJob = viewModelScope.launch {
            while (isActive) {
                motionPlayer?.let { p ->
                    _motionPlayback.update { it.copy(positionMs = p.currentPosition) }
                }
                delay(33)
            }
        }
    }

    fun releaseMotionPlayer() {
        motionProgressJob?.cancel()
        motionProgressJob = null
        try { motionPlayer?.release() } catch (_: Throwable) { }
        motionPlayer = null
        _motionPlayback.value = MotionPlaybackState(
            durationMs = _motionPhotoExtraction.value.durationMs
        )
    }

    /**
     * Scale frames to [thumbHeight] px and stitch into a single wide bitmap.
     * Runs on the calling (IO) thread.
     */
    private fun stitchFrames(frames: List<Bitmap>, thumbHeight: Int): Bitmap? {
        if (frames.isEmpty()) return null
        val scaled = frames.map { bmp ->
            val width = scaledFilmstripFrameWidth(bmp.width, bmp.height, thumbHeight)
            bmp.scale(width, thumbHeight)
        }
        val totalWidth = scaled.sumOf { it.width }
        val composite = createBitmap(totalWidth, thumbHeight)
        val canvas = Canvas(composite)
        var x = 0f
        for (i in scaled.indices) {
            canvas.drawBitmap(scaled[i], x, 0f, null)
            x += scaled[i].width
            if (scaled[i] !== frames[i]) scaled[i].recycle()
            frames[i].recycle()
        }
        return composite
    }

    // ======================== Image Rotation ========================

    enum class RotationStage { DECODING, ROTATING, SAVING, UPLOADING }
    enum class RotationMode { OVERWRITE, COPY, CLOUD }

    data class RotationUiState(
        val stage: RotationStage,
        val mode: RotationMode,
    )

    fun rotateImage(media: Media, degrees: Int, forceCopy: Boolean = false) {
        val isCloud = media.getUri().scheme == "cloud"
        val mode = when {
            isCloud -> RotationMode.CLOUD
            forceCopy -> RotationMode.COPY
            else -> RotationMode.OVERWRITE
        }
        pendingRotationMediaId = media.id
        _rotationState.value = RotationUiState(RotationStage.DECODING, mode)
        val id = workManager.rotateImage(media, degrees, forceCopy)
        rotateWorkId = id
        observeRotateWork(id, mode, media.id)
    }

    private fun stageForStatus(status: Int): RotationStage = when (status) {
        RotateMediaWorker.Status.ROTATING -> RotationStage.ROTATING
        RotateMediaWorker.Status.SAVING -> RotationStage.SAVING
        RotateMediaWorker.Status.UPLOADING -> RotationStage.UPLOADING
        else -> RotationStage.DECODING
    }

    private fun observeRotateWork(id: UUID, mode: RotationMode, mediaId: Long) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).filterNotNull().collect { info ->
                if (!info.state.isFinished) {
                    val status = info.progress.getInt(RotateMediaWorker.KEY_STATUS, -1)
                    if (status >= 0) {
                        _rotationState.update { it?.copy(stage = stageForStatus(status)) }
                    }
                    return@collect
                }
                if (info.state == WorkInfo.State.SUCCEEDED) {
                    delay(300) // wait for media store to be updated
                    when (mode) {
                        RotationMode.COPY -> {
                            val uri = info.outputData.getString(RotateMediaWorker.KEY_RESULT_URI)
                            if (uri != null) {
                                _uiEvents.emit(MediaViewEvent.NavigateToRotatedCopy(uri))
                            } else {
                                _uiEvents.emit(MediaViewEvent.ScrollToFirstPage)
                            }
                        }

                        RotationMode.OVERWRITE ->
                            _uiEvents.emit(MediaViewEvent.OverwriteApplied(mediaId))

                        RotationMode.CLOUD ->
                            _uiEvents.emit(MediaViewEvent.ScrollToFirstPage)
                    }
                } else {
                    val message = info.outputData.getString(RotateMediaWorker.KEY_MESSAGE)
                    _uiEvents.emit(MediaViewEvent.RotationFailed(message))
                }
                _rotationState.value = null
                pendingRotationMediaId = null
                rotateWorkId = null
            }
        }
    }

    sealed interface VisualSearchUiState {
        data object Idle : VisualSearchUiState
        data class Preparing(
            val mediaId: Long,
            val stage: VisualSearchStage,
            val progress: Int?,
        ) : VisualSearchUiState
    }

    fun launchVisualSearch(
        media: Media,
        metadata: MediaMetadata?,
        currentVault: Vault?,
        target: VisualSearchTarget,
        positionMs: Long?,
        convertTo: ImageReencoder.ImageWriteFormat?,
    ) {
        if (visualSearchJob?.isActive == true) return
        _visualSearchState.value = VisualSearchUiState.Preparing(
            mediaId = media.id,
            stage = initialVisualSearchStage(media, currentVault, convertTo),
            progress = null,
        )
        visualSearchJob = viewModelScope.launch {
            try {
                val prepared = visualSearchLauncher.prepare(
                    media = media,
                    metadata = metadata,
                    currentVault = currentVault,
                    positionMs = positionMs,
                    convertTo = convertTo,
                ) { stage, progress ->
                    _visualSearchState.value =
                        VisualSearchUiState.Preparing(media.id, stage, progress)
                }
                _visualSearchState.value = VisualSearchUiState.Idle
                _uiEvents.emit(
                    MediaViewEvent.LaunchVisualSearch(
                        uri = prepared.uri,
                        mimeType = prepared.mimeType,
                        target = target,
                    )
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _visualSearchState.value = VisualSearchUiState.Idle
                throw cancelled
            } catch (error: Exception) {
                _visualSearchState.value = VisualSearchUiState.Idle
                _uiEvents.emit(MediaViewEvent.VisualSearchFailed(error.message))
            }
        }
    }

    fun cancelVisualSearch() {
        visualSearchJob?.cancel()
        visualSearchJob = null
        _visualSearchState.value = VisualSearchUiState.Idle
    }

    private fun initialVisualSearchStage(
        media: Media,
        currentVault: Vault?,
        convertTo: ImageReencoder.ImageWriteFormat?,
    ): VisualSearchStage = when {
        media.isVideo -> when {
            media.isEncrypted || currentVault != null -> VisualSearchStage.DECRYPTING
            media.isCloud -> VisualSearchStage.DOWNLOADING
            else -> VisualSearchStage.PREPARING
        }
        media.isEncrypted -> VisualSearchStage.DECRYPTING
        media.isCloud -> VisualSearchStage.DOWNLOADING
        convertTo != null -> VisualSearchStage.CONVERTING
        else -> VisualSearchStage.PREPARING
    }

    override fun onCleared() {
        releaseMotionPlayer()
        _motionPhotoExtraction.value.videoFile?.delete()
        visualSearchJob?.cancel()
        super.onCleared()
    }

    sealed interface MediaViewEvent {
        data object ScrollToFirstPage : MediaViewEvent
        data class NavigateToRotatedCopy(val uri: String) : MediaViewEvent
        data class OverwriteApplied(val mediaId: Long) : MediaViewEvent
        data class RotationFailed(val message: String?) : MediaViewEvent
        data class LaunchVisualSearch(
            val uri: Uri,
            val mimeType: String,
            val target: VisualSearchTarget,
        ) : MediaViewEvent
        data class VisualSearchFailed(val message: String?) : MediaViewEvent
    }
}
