package com.dot.gallery.feature_node.presentation.mediaview.components.media

import android.graphics.Bitmap
import android.view.SurfaceView
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.MotionPhotoInfo
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewViewModel
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberSurfaceCapture
import dev.chrisbanes.haze.hazeSource

private val FILMSTRIP_HEIGHT = 36.dp
private val SCRUBBER_WIDTH = 3.dp
private val FAVOURITE_DOT_SIZE = 6.dp

// ======================== State ========================

/**
 * Holds all motion-photo state: detection, extraction, playback, and thumbnails.
 * Created via [rememberMotionPhotoState] at the screen level so the app bar pill
 * and bottom-sheet filmstrip can share the same state.
 */
@Stable
class MotionPhotoState(
    private val viewModel: MediaViewViewModel
) {
    var motionInfo by mutableStateOf<MotionPhotoInfo?>(null)
    var compositeFilmstrip by mutableStateOf<Bitmap?>(null)
    var player by mutableStateOf<ExoPlayer?>(null)
    var isPlaying by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var videoReady by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    val isDetected: Boolean get() = motionInfo != null

    fun togglePlayback() = viewModel.toggleMotionPlayback()
    fun startPlayback() = viewModel.startMotionPlayback()
    fun stopPlayback() = viewModel.stopMotionPlayback()
    fun seekTo(ms: Long) = viewModel.seekMotionTo(ms)
    fun seekAndPause(ms: Long) = viewModel.seekMotionAndPause(ms)
}

/**
 * Creates and remembers a [MotionPhotoState] for the given [media].
 * Extraction and playback are managed by [viewModel] (matching VideoPlayerViewModel pattern).
 * This composable observes the ViewModel's StateFlows and syncs into compose-reactive fields.
 */
@Composable
fun <T : Media> rememberMotionPhotoState(
    media: T?,
    viewModel: MediaViewViewModel
): MotionPhotoState {
    val state = remember(media?.id) { MotionPhotoState(viewModel) }

    // Trigger extraction in ViewModel
    LaunchedEffect(media?.id) {
        viewModel.prepareMotionPhoto(media)
    }

    // Observe extraction results
    val extraction by viewModel.motionPhotoExtraction.collectAsStateWithLifecycle()
    LaunchedEffect(extraction) {
        state.motionInfo = extraction.info
        state.compositeFilmstrip = extraction.compositeFilmstrip
        if (extraction.durationMs > 0L && state.durationMs == 0L) {
            state.durationMs = extraction.durationMs
        }
    }

    // Observe playback state from ViewModel
    val playback by viewModel.motionPlayback.collectAsStateWithLifecycle()
    LaunchedEffect(playback) {
        state.player = viewModel.motionPlayer
        state.isPlaying = playback.isPlaying
        state.isLoading = playback.isLoading
        state.videoReady = playback.videoReady
        state.positionMs = playback.positionMs
        if (playback.durationMs > 0) {
            state.durationMs = playback.durationMs
        }
    }

    return state
}

// ======================== Video Surface ========================

/**
 * Renders the ExoPlayer video surface and a loading spinner as overlays
 * inside a [BoxScope]. Place this on top of [ZoomablePagerImage].
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.MotionPhotoSurface(state: MotionPhotoState) {
    // Video surface – keep always composed when player exists so that
    // pause→resume doesn't recreate the surface (which causes cropping).
    val videoAlpha by animateFloatAsState(
        targetValue = if (state.isPlaying && state.videoReady) 1f else 0f,
        animationSpec = tween(250),
        label = "motionVideoAlpha"
    )
    val density = LocalDensity.current
    val allowBlur by rememberAllowBlur()
    val hazeState = LocalHazeState.current
    var surfaceViewRef by remember { mutableStateOf<View?>(null) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }
    val videoCapture by rememberSurfaceCapture(
        view = surfaceViewRef,
        enabled = allowBlur && state.isPlaying
    )
    if (state.player != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .graphicsLayer { alpha = videoAlpha }
        ) {
            if (videoSize != IntSize.Zero) {
                videoCapture?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(
                                with(density) { videoSize.width.toDp() },
                                with(density) { videoSize.height.toDp() }
                            )
                            .hazeSource(hazeState)
                    )
                }
            }
            state.player?.let { exo ->
                val presentationState = rememberPresentationState(
                    player = exo,
                    keepContentOnReset = true
                )
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { sv ->
                            surfaceViewRef = sv
                            exo.setVideoSurfaceView(sv)
                        }
                    },
                    update = { sv ->
                        exo.setVideoSurfaceView(sv)
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .resizeWithContentScale(
                            contentScale = ContentScale.Fit,
                            sourceSizeDp = presentationState.videoSizeDp
                        )
                        .onGloballyPositioned { coordinates ->
                            videoSize = coordinates.size
                        }
                )
            }
        }
    }

    // Loading spinner
    AnimatedVisibility(
        visible = state.isLoading,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.Center)
            .zIndex(5f)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Color.White
        )
    }
}

// ======================== Filmstrip ========================

/**
 * Filmstrip scrubber with frame thumbnails, a white scrub indicator, and
 * a favourite-shot dot. Supports tap-to-seek and horizontal-drag-to-scrub.
 * Place this inside the bottom sheet so it scrolls with it.
 */
@Composable
fun MotionPhotoFilmstrip(
    state: MotionPhotoState,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    val compositeImage = remember(state.compositeFilmstrip) {
        state.compositeFilmstrip?.asImageBitmap()
    } ?: return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var stripWidthPx by remember { mutableFloatStateOf(0f) }
    var didSnapToFavourite by remember { mutableStateOf(false) }

    val favouriteShotUs = state.motionInfo?.presentationTimestampUs ?: -1L
    val favouriteShotMs = if (favouriteShotUs > 0) favouriteShotUs / 1000L else -1L
    val favouriteFraction =
        if (favouriteShotMs in 0..state.durationMs && state.durationMs > 0) {
            (favouriteShotMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else -1f

    val filmstripShape = RoundedCornerShape(8.dp)

    Box(modifier = modifier.fillMaxWidth()) {
        // Favourite shot dot (above the filmstrip)
        if (favouriteFraction >= 0f && stripWidthPx > 0f) {
            val dotOffsetDp = with(density) { (favouriteFraction * stripWidthPx).toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(bottom = 4.dp)
                    .offset(x = dotOffsetDp - FAVOURITE_DOT_SIZE / 2)
                    .size(FAVOURITE_DOT_SIZE)
                    .background(Color.White, CircleShape)
            )
        }

        // Filmstrip + scrub indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = FAVOURITE_DOT_SIZE + 4.dp)
                .clip(filmstripShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = filmstripShape
                )
                .onSizeChanged { size -> stripWidthPx = size.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures { onTap() }
                }
                .pointerInput(state.durationMs, favouriteShotMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { didSnapToFavourite = false },
                        onDragEnd = { didSnapToFavourite = false },
                        onDragCancel = { didSnapToFavourite = false },
                    ) { change, _ ->
                        change.consume()
                        if (stripWidthPx > 0 && state.durationMs > 0) {
                            val x = change.position.x.coerceIn(0f, stripWidthPx)
                            var seekMs =
                                ((x / stripWidthPx) * state.durationMs).toLong()
                                    .coerceIn(0L, state.durationMs)

                            // Snap to favourite frame when within threshold
                            if (favouriteShotMs > 0) {
                                val snapThresholdMs = (state.durationMs * 0.03f).toLong()
                                val nearFavourite =
                                    (seekMs - favouriteShotMs) in -snapThresholdMs..snapThresholdMs
                                if (nearFavourite) {
                                    seekMs = favouriteShotMs
                                    if (!didSnapToFavourite) {
                                        didSnapToFavourite = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                } else {
                                    didSnapToFavourite = false
                                }
                            }

                            state.seekAndPause(seekMs)
                        }
                    }
                }
        ) {
            // Single composite filmstrip image (stitched on IO in ViewModel)
            Image(
                bitmap = compositeImage,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FILMSTRIP_HEIGHT)
            )

            // Scrub indicator (white vertical bar) — positioned via graphicsLayer
            // to avoid recomposition on every positionMs update (draw-phase only).
            if (state.positionMs > 0 && stripWidthPx > 0f) {
                val scrubberHalfPx = with(density) { SCRUBBER_WIDTH.toPx() / 2f }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(SCRUBBER_WIDTH)
                        .height(FILMSTRIP_HEIGHT)
                        .graphicsLayer {
                            val f = if (state.durationMs > 0) {
                                (state.positionMs.toFloat() / state.durationMs.toFloat())
                                    .coerceIn(0f, 1f)
                            } else 0f
                            translationX = f * stripWidthPx - scrubberHalfPx
                        }
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

// ======================== Static Shots Section ========================

/**
 * Static "Shots in this photo" section for the expanded sheet details.
 * Displays frame thumbnails with a heading and favourite-shot dot.
 * Non-interactive — just for display.
 */
@Composable
fun MotionPhotoShotsSection(
    state: MotionPhotoState,
    modifier: Modifier = Modifier,
) {
    // Hide entirely until composite filmstrip is loaded
    val compositeImage = remember(state.compositeFilmstrip) {
        state.compositeFilmstrip?.asImageBitmap()
    } ?: return

    val density = LocalDensity.current
    var stripWidthPx by remember { mutableFloatStateOf(0f) }

    val favouriteShotUs = state.motionInfo?.presentationTimestampUs ?: -1L
    val favouriteShotMs = if (favouriteShotUs > 0) favouriteShotUs / 1000L else -1L
    val favouriteFraction =
        if (favouriteShotMs in 0..state.durationMs && state.durationMs > 0) {
            (favouriteShotMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else -1f

    val filmstripShape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Shots in this photo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            // Favourite shot dot (above the filmstrip)
            if (favouriteFraction >= 0f && stripWidthPx > 0f) {
                val dotOffsetDp =
                    with(density) { (favouriteFraction * stripWidthPx).toDp() }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(bottom = 4.dp)
                        .offset(x = dotOffsetDp - FAVOURITE_DOT_SIZE / 2)
                        .size(FAVOURITE_DOT_SIZE)
                        .background(Color.White, CircleShape)
                )
            }

            // Single composite filmstrip image
            Image(
                bitmap = compositeImage,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FAVOURITE_DOT_SIZE + 4.dp)
                    .clip(filmstripShape)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = filmstripShape
                    )
                    .height(FILMSTRIP_HEIGHT)
                    .onSizeChanged { size -> stripWidthPx = size.width.toFloat() }
            )
        }
    }
}
