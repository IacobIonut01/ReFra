package com.dot.gallery.feature_node.presentation.mediaview.components.video

import android.app.Activity
import android.net.Uri
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.media3.common.Player
import androidx.media3.ui.SubtitleView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberAudioFocus
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.SubtitleTrack
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberSurfaceCapture
import dev.chrisbanes.haze.hazeSource

@OptIn(UnstableApi::class)
@Composable
fun <T : Media> VideoPlayer(
    media: T,
    modifier: Modifier = Modifier,
    playWhenReady: State<Boolean>,
    videoController: @Composable (ExoPlayer, MutableState<Boolean>, MutableLongState, Long, Int, Float, VideoControllerState) -> Unit,
    onItemClick: () -> Unit,
    onSwipeDown: () -> Unit,
    onZoomChange: (Boolean) -> Unit = {}
) {
    // Acquire or create the ViewModel for this media id
    val vm: VideoPlayerViewModel =
        hiltViewModel<VideoPlayerViewModel, VideoPlayerViewModel.Factory>(
            key = "video:${media.id}",
            creationCallback = { factory ->
                factory.create(media)
            }
        )

    val playback by vm.state.collectAsStateWithLifecycle()
    val currentPlayer by vm.playerFlow.collectAsStateWithLifecycle()

    // Adapter states to satisfy legacy videoController signature
    val isPlayingState = rememberSaveable(media.id) { mutableStateOf(playback.isPlaying) }
    val positionState = rememberSaveable(media.id) { mutableLongStateOf(playback.positionMs) }

    LaunchedEffect(playback.isPlaying) {
        isPlayingState.value = playback.isPlaying
    }
    LaunchedEffect(playback.positionMs) {
        positionState.longValue = playback.positionMs
    }

    // External autoplay preference + user initial intent
    val canAutoPlay by rememberVideoAutoplay()
    LaunchedEffect(playWhenReady.value, canAutoPlay) {
        vm.setUserPlayWhenReady(playWhenReady.value, canAutoPlay)
    }

    // Safety net: release player when leaving composition (e.g. fast scroll disposing page)
    DisposableEffect(vm) {
        onDispose {
            vm.detachFromComposition()
        }
    }

    // Audio focus preferences
    val audioFocus by rememberAudioFocus()
    LaunchedEffect(audioFocus) {
        vm.applyAudioFocusPreference(audioFocus)
    }

    // Keep screen awake while playing
    val context = LocalContext.current
    LaunchedEffect(isPlayingState.value) {
        (context as? Activity)?.let { act ->
            if (isPlayingState.value) {
                act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    val presentationState = rememberPresentationState(
        player = currentPlayer,
        keepContentOnReset = true
    )

    val updatedOnClick by rememberUpdatedState(onItemClick)
    val updatedOnSwipeDown by rememberUpdatedState(onSwipeDown)
    var iWasVisible by rememberSaveable { mutableStateOf(false) }
    var iAmVisible by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    var prevOrientation by rememberSaveable { mutableIntStateOf(configuration.orientation) }
    LaunchedEffect(configuration, iAmVisible) {
        if (prevOrientation != configuration.orientation) {
            // Orientation changed; do nothing
            prevOrientation = configuration.orientation
            return@LaunchedEffect
        }

        when {
            iAmVisible -> {
                vm.reattachFromComposition()
            }

            !iAmVisible && iWasVisible -> {
                vm.detachFromComposition()
            }
        }
    }

    val density = LocalDensity.current
    var surfaceViewRef by remember { mutableStateOf<View?>(null) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }
    val allowBlur by rememberAllowBlur()
    val hazeState = LocalHazeState.current
    val videoCapture by rememberSurfaceCapture(
        view = surfaceViewRef,
        enabled = allowBlur
    )

    // Zoom state
    var targetScale by rememberSaveable(media.id) { mutableFloatStateOf(1f) }
    var targetOffsetX by rememberSaveable(media.id) { mutableFloatStateOf(0f) }
    var targetOffsetY by rememberSaveable(media.id) { mutableFloatStateOf(0f) }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 200),
        label = "videoZoomScale"
    )
    val offsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = tween(durationMillis = 200),
        label = "videoZoomOffsetX"
    )
    val offsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = 200),
        label = "videoZoomOffsetY"
    )

    val isZoomed = targetScale > 1.01f
    val updatedOnZoomChange by rememberUpdatedState(onZoomChange)

    LaunchedEffect(isZoomed) {
        updatedOnZoomChange(isZoomed)
    }

    // Clamp offsets within the zoomed content bounds
    fun clampOffsets() {
        if (targetScale <= 1f) {
            targetOffsetX = 0f
            targetOffsetY = 0f
            return
        }
        val maxX = (targetScale - 1f) * videoSize.width / 2f
        val maxY = (targetScale - 1f) * videoSize.height / 2f
        targetOffsetX = targetOffsetX.coerceIn(-maxX, maxX)
        targetOffsetY = targetOffsetY.coerceIn(-maxY, maxY)
    }

    // Container size for offset clamping during gestures
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { updatedOnClick() },
                    onDoubleTap = { tapOffset ->
                        if (isZoomed) {
                            // Zoom out
                            targetScale = 1f
                            targetOffsetX = 0f
                            targetOffsetY = 0f
                        } else {
                            // Zoom to 2.5x centered on tap position
                            targetScale = 2.5f
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            targetOffsetX = (centerX - tapOffset.x) * (2.5f - 1f)
                            targetOffsetY = (centerY - tapOffset.y) * (2.5f - 1f)
                            clampOffsets()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (targetScale * zoomChange).coerceIn(1f, 5f)
                            targetScale = newScale
                            if (targetScale > 1f) {
                                targetOffsetX += panChange.x
                                targetOffsetY += panChange.y
                                clampOffsets()
                            } else {
                                targetOffsetX = 0f
                                targetOffsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1 && isZoomed) {
                            val panChange = event.calculatePan()
                            targetOffsetX += panChange.x
                            targetOffsetY += panChange.y
                            clampOffsets()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .swipe(enabled = !isZoomed, onSwipeDown = updatedOnSwipeDown)
            .onVisibilityChanged(
                minFractionVisible = 0.2f
            ) { isVisible ->
                iWasVisible = iAmVisible
                iAmVisible = isVisible
            }
            .then(modifier)
    ) {
        // Inner Box with zoom transform applied to video content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
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
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).also { sv ->
                        surfaceViewRef = sv
                    }
                },
                update = { sv ->
                    if (!currentPlayer.isReleased) {
                        currentPlayer.setVideoSurfaceView(sv)
                    }
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

            // Subtitle rendering overlay
            var subtitleViewRef by remember { mutableStateOf<SubtitleView?>(null) }
            AndroidView(
                factory = { ctx ->
                    SubtitleView(ctx).also { subtitleViewRef = it }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .resizeWithContentScale(
                        contentScale = ContentScale.Fit,
                        sourceSizeDp = presentationState.videoSizeDp
                    )
            )
            DisposableEffect(currentPlayer) {
                val listener = object : Player.Listener {
                    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                        subtitleViewRef?.setCues(cueGroup.cues)
                    }
                }
                currentPlayer.addListener(listener)
                onDispose {
                    if (!currentPlayer.isReleased) {
                        currentPlayer.removeListener(listener)
                    }
                }
            }
        }

        if (presentationState.coverSurface) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }

    AnimatedVisibility(
        modifier = Modifier.zIndex(10f),
        visible = playback.ready,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        videoController(
            currentPlayer,
            isPlayingState,
            positionState,
            playback.durationMs,
            playback.bufferedPercent,
            playback.frameRate,
            VideoControllerState(
                subtitleTracks = playback.subtitleTracks,
                onSelectSubtitle = vm::selectSubtitleTrack,
                onDisableSubtitles = vm::disableSubtitles,
                onAddExternalSubtitle = vm::addExternalSubtitle,
                onRemoveSubtitle = vm::removeExternalSubtitle
            )
        )
    }

    // Loading & decrypt states
    if (!playback.ready && !playback.decryptFailed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            if (playback.isDecrypting) {
                Text(
                    text = "…",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (playback.decryptFailed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.decrypt_failed_tap_to_retry),
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { vm.retryDecryption() }
                    )
            )
        }
    }
}