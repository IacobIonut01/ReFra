package com.dot.gallery.feature_node.presentation.mediaview.components.media

import android.graphics.Bitmap
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.MotionPhotoInfo
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewViewModel
import com.github.panpf.zoomimage.compose.zoom.Transform
import kotlin.math.roundToInt

private val FILMSTRIP_HEIGHT = 36.dp
private val SCRUBBER_WIDTH = 3.dp
private val FAVOURITE_DOT_SIZE = 6.dp

internal data class MotionPhotoVideoTransform(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val scaleOrigin: TransformOrigin,
) {
    companion object {
        val Identity = Transform.Origin.toMotionPhotoVideoTransform()
    }
}

internal fun Transform.toMotionPhotoVideoTransform() = MotionPhotoVideoTransform(
    scaleX = scaleX,
    scaleY = scaleY,
    offsetX = offsetX,
    offsetY = offsetY,
    scaleOrigin = scaleOrigin,
)

internal fun fitMotionPhotoFilmstrip(
    sourceWidth: Int,
    sourceHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
): IntSize {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) {
        return IntSize.Zero
    }
    val scale = minOf(
        maxWidth.toFloat() / sourceWidth,
        maxHeight.toFloat() / sourceHeight,
    )
    return IntSize(
        width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
        height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
    )
}

// ======================== State ========================

/**
 * Holds all motion-photo state: detection, extraction, playback, and thumbnails.
 * Created via [rememberMotionPhotoState] at the screen level so the app bar pill
 * and bottom-sheet filmstrip can share the same state.
 */
@Stable
class MotionPhotoState(
    private val onToggle: () -> Unit = {},
    private val onStart: () -> Unit = {},
    private val onStop: () -> Unit = {},
    private val onSeek: (Long) -> Unit = {},
    private val onSeekAndPause: (Long) -> Unit = {},
) {
    var motionInfo by mutableStateOf<MotionPhotoInfo?>(null)
    var compositeFilmstrip by mutableStateOf<Bitmap?>(null)
    var player by mutableStateOf<ExoPlayer?>(null)
    var isPlaying by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var videoReady by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    internal var videoTransform by mutableStateOf(MotionPhotoVideoTransform.Identity)

    val isDetected: Boolean get() = motionInfo != null

    fun togglePlayback() = onToggle()
    fun startPlayback() = onStart()
    fun stopPlayback() = onStop()
    fun seekTo(ms: Long) = onSeek(ms)
    fun seekAndPause(ms: Long) = onSeekAndPause(ms)
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
    val state = remember(media?.id) {
        MotionPhotoState(
            onToggle = viewModel::toggleMotionPlayback,
            onStart = viewModel::startMotionPlayback,
            onStop = viewModel::stopMotionPlayback,
            onSeek = viewModel::seekMotionTo,
            onSeekAndPause = viewModel::seekMotionAndPause,
        )
    }

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
    val player = state.player
    val presentationState = rememberPresentationState(
        player = player,
        keepContentOnReset = true
    )
    val videoAlpha by animateFloatAsState(
        targetValue = if (
            state.isPlaying && state.videoReady && !presentationState.coverSurface
        ) 1f else 0f,
        // "Animate media items" off — the video surface appears over the still instantly.
        animationSpec = if (LocalMediaViewerVisualPolicy.current.animationsEnabled) {
            tween(250)
        } else {
            snap()
        },
        label = "motionVideoAlpha"
    )
    var textureViewRef by remember(player) { mutableStateOf<TextureView?>(null) }
    if (player != null) {
        val transform = state.videoTransform
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .graphicsLayer { alpha = videoAlpha }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transform.scaleX
                        scaleY = transform.scaleY
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                        transformOrigin = transform.scaleOrigin
                    }
            ) {
                AndroidView(
                    factory = { TextureView(it) },
                    update = { textureViewRef = it },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .resizeWithContentScale(
                            contentScale = ContentScale.Fit,
                            sourceSizeDp = presentationState.videoSizeDp
                        )
                )
            }

            val textureView = textureViewRef
            DisposableEffect(player, textureView) {
                if (textureView == null) {
                    onDispose { }
                } else {
                    player.setVideoTextureView(textureView)
                    onDispose {
                        if (!player.isReleased) {
                            player.clearVideoTextureView(textureView)
                        }
                    }
                }
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
    val compositeFilmstrip = state.compositeFilmstrip ?: return
    val compositeImage = remember(compositeFilmstrip) {
        compositeFilmstrip.asImageBitmap()
    }

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

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stripSize = remember(compositeFilmstrip, maxWidth, density) {
            fitMotionPhotoFilmstrip(
                sourceWidth = compositeFilmstrip.width,
                sourceHeight = compositeFilmstrip.height,
                maxWidth = with(density) { maxWidth.toPx().roundToInt() },
                maxHeight = with(density) { FILMSTRIP_HEIGHT.toPx().roundToInt() },
            )
        }
        if (stripSize == IntSize.Zero) return@BoxWithConstraints
        val stripWidth = with(density) { stripSize.width.toDp() }
        val stripHeight = with(density) { stripSize.height.toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(stripWidth)
        ) {
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
                    .padding(top = FAVOURITE_DOT_SIZE + 4.dp)
                    .width(stripWidth)
                    .height(stripHeight)
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
                    modifier = Modifier.fillMaxSize()
                )

                // Scrub indicator (white vertical bar) — positioned via graphicsLayer
                // to avoid recomposition on every positionMs update (draw-phase only).
                if (state.positionMs > 0 && stripWidthPx > 0f) {
                    val scrubberHalfPx = with(density) { SCRUBBER_WIDTH.toPx() / 2f }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(SCRUBBER_WIDTH)
                            .height(stripHeight)
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
    onOpenFramePicker: () -> Unit = {},
) {
    // Hide entirely until composite filmstrip is loaded
    val compositeFilmstrip = state.compositeFilmstrip ?: return
    val compositeImage = remember(compositeFilmstrip) {
        compositeFilmstrip.asImageBitmap()
    }

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
            text = stringResource(R.string.frame_picker_shots_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stripSize = remember(compositeFilmstrip, maxWidth, density) {
                fitMotionPhotoFilmstrip(
                    sourceWidth = compositeFilmstrip.width,
                    sourceHeight = compositeFilmstrip.height,
                    maxWidth = with(density) { maxWidth.toPx().roundToInt() },
                    maxHeight = with(density) { FILMSTRIP_HEIGHT.toPx().roundToInt() },
                )
            }
            if (stripSize == IntSize.Zero) return@BoxWithConstraints
            val stripWidth = with(density) { stripSize.width.toDp() }
            val stripHeight = with(density) { stripSize.height.toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(stripWidth)
            ) {
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
                        .padding(top = FAVOURITE_DOT_SIZE + 4.dp)
                        .width(stripWidth)
                        .height(stripHeight)
                        .clip(filmstripShape)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.15f),
                            shape = filmstripShape
                        )
                        .onSizeChanged { size -> stripWidthPx = size.width.toFloat() }
                        .pointerInput(Unit) { detectTapGestures { onOpenFramePicker() } }
                )
            }
        }
    }
}
