package com.dot.gallery.feature_node.presentation.mediaview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.workers.rotateImage
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.MotionPhotoHelper
import com.dot.gallery.feature_node.domain.util.MotionPhotoInfo
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

private const val NUM_FILMSTRIP_FRAMES = 12
private const val FILMSTRIP_THUMB_HEIGHT = 108 // px, ~36dp @ 3x

@HiltViewModel
class MediaViewViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<MediaViewEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<MediaViewEvent> = _uiEvents

    private var rotateWorkId: UUID? = null

    // ======================== Motion Photo Extraction ========================

    data class MotionPhotoExtraction(
        val info: MotionPhotoInfo? = null,
        val videoFile: File? = null,
        val durationMs: Long = 0L,
        val thumbnails: List<Bitmap> = emptyList(),
        val compositeFilmstrip: Bitmap? = null
    )

    private val _motionPhotoExtraction = MutableStateFlow(MotionPhotoExtraction())
    val motionPhotoExtraction: StateFlow<MotionPhotoExtraction> = _motionPhotoExtraction.asStateFlow()

    private var currentMotionMediaId: Long? = null
    private var extractionJob: Job? = null

    fun prepareMotionPhoto(media: Media?) {
        if (media == null || media.isVideo) {
            cleanupMotionPhoto()
            return
        }
        if (media.id == currentMotionMediaId) return

        extractionJob?.cancel()
        val oldFile = _motionPhotoExtraction.value.videoFile

        currentMotionMediaId = media.id
        _motionPhotoExtraction.value = MotionPhotoExtraction()

        extractionJob = viewModelScope.launch(Dispatchers.IO) {
            oldFile?.delete()

            val uri: Uri = media.getUri()
            val info = MotionPhotoHelper.parseInfo(context, uri) ?: return@launch
            _motionPhotoExtraction.value = MotionPhotoExtraction(info = info)

            val file = MotionPhotoHelper.extractVideo(context, uri, info) ?: return@launch

            val duration = try {
                MediaMetadataRetriever().use { mmr ->
                    mmr.setDataSource(file.absolutePath)
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                }
            } catch (_: Exception) { 0L }

            _motionPhotoExtraction.value = MotionPhotoExtraction(
                info = info, videoFile = file, durationMs = duration
            )

            val frames = MotionPhotoHelper.extractFrames(file, NUM_FILMSTRIP_FRAMES)
            val composite = stitchFrames(frames, FILMSTRIP_THUMB_HEIGHT)
            _motionPhotoExtraction.value = MotionPhotoExtraction(
                info = info, videoFile = file, durationMs = duration,
                thumbnails = frames, compositeFilmstrip = composite
            )
        }
    }

    private fun cleanupMotionPhoto() {
        extractionJob?.cancel()
        releaseMotionPlayer()
        _motionPhotoExtraction.value.videoFile?.delete()
        _motionPhotoExtraction.value = MotionPhotoExtraction()
        currentMotionMediaId = null
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
            val scale = thumbHeight.toFloat() / bmp.height
            val w = (bmp.width * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bmp, w, thumbHeight, true)
        }
        val totalWidth = scaled.sumOf { it.width }
        val composite = Bitmap.createBitmap(totalWidth, thumbHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)
        var x = 0f
        for (i in scaled.indices) {
            canvas.drawBitmap(scaled[i], x, 0f, null)
            x += scaled[i].width
            if (scaled[i] !== frames[i]) scaled[i].recycle()
        }
        return composite
    }

    // ======================== Image Rotation ========================

    fun rotateImage(media: Media, degrees: Int) {
        val id = workManager.rotateImage(media, degrees)
        rotateWorkId = id
        observeRotateWork(id)
    }

    private fun observeRotateWork(id: UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).filterNotNull().collect { info ->
                if (info.state.isFinished) {
                    if (info.state == WorkInfo.State.SUCCEEDED) {
                        delay(300) // wait for media store to be updated
                        _uiEvents.emit(MediaViewEvent.ScrollToFirstPage)
                    }
                    rotateWorkId = null
                }
            }
        }
    }

    override fun onCleared() {
        releaseMotionPlayer()
        _motionPhotoExtraction.value.videoFile?.delete()
        super.onCleared()
    }

    sealed interface MediaViewEvent {
        data object ScrollToFirstPage : MediaViewEvent
    }
}
