package com.dot.gallery.feature_node.presentation.mediaview.components.video

import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.media3.common.Player
import androidx.media3.ui.SubtitleView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.Settings.Misc.rememberVideoSurfaceRebind
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.SubtitleTrack
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberSurfaceCapture
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

internal fun shouldPlayVideoOnce(slideshowActive: Boolean, storyActive: Boolean): Boolean =
    slideshowActive || storyActive

internal fun shouldUseTextureVideoOutput(
    storyActive: Boolean,
    compositedOutput: Boolean,
): Boolean = storyActive || compositedOutput

@OptIn(UnstableApi::class)
@Composable
fun <T : Media> VideoPlayer(
    media: T,
    modifier: Modifier = Modifier,
    playWhenReady: State<Boolean>,
    videoController: @Composable (ExoPlayer, MutableState<Boolean>, MutableLongState, Long, Int, Float, VideoControllerState) -> Unit,
    onItemClick: () -> Unit,
    onSwipeDown: () -> Unit,
    swipeToDismissEnabled: Boolean = true,
    onZoomChange: (Boolean) -> Unit = {},
    captureBlur: Boolean = true,
    slideshowActive: Boolean = false,
    storyActive: Boolean = false,
    compositedOutput: Boolean = storyActive,
    onLoadFailed: () -> Unit = {},
    onVideoEnded: () -> Unit = {}
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

    LaunchedEffect(playback.playbackFailed) {
        if (playback.playbackFailed) onLoadFailed()
    }

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

    // Pause playback when the app loses focus (Home, app switch, screen lock) so
    // audio does not keep playing in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                vm.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val presentationState = rememberPresentationState(
        player = currentPlayer,
        keepContentOnReset = true
    )

    // Slideshow mode: play the video through once (no looping) and mute audio, then notify the
    // host so it can advance to the next item when playback ends.
    val updatedOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val playOnce = shouldPlayVideoOnce(slideshowActive, storyActive)
    DisposableEffect(currentPlayer, playOnce, slideshowActive) {
        vm.setSlideshowActive(playOnce)
        if (!playOnce) {
            return@DisposableEffect onDispose { }
        }
        val previousVolume = currentPlayer.volume
        if (slideshowActive && !currentPlayer.isReleased) {
            currentPlayer.volume = 0f
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    updatedOnVideoEnded()
                }
            }
        }
        currentPlayer.addListener(listener)
        onDispose {
            vm.setSlideshowActive(false)
            if (!currentPlayer.isReleased) {
                currentPlayer.removeListener(listener)
                if (slideshowActive) currentPlayer.volume = previousVolume
            }
        }
    }

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
    // Key these to media.id: when switching between grouped members on the same page the
    // VideoPlayer node is reused (only `media` changes), so a stale surface/size would leave
    // the new video attached to the previous surface and stretched. Resetting per media forces
    // a fresh surface bound to the new player.
    var surfaceViewRef by remember(media.id) { mutableStateOf<View?>(null) }
    var videoSize by remember(media.id) { mutableStateOf(IntSize.Zero) }

    // Keep the screen awake while this video is playing. The keepScreenOn flag
    // must go on this page's own video view, not LocalView: LocalView is the
    // shared compose root for every pager page, so a pre-composed or disposing
    // neighbour writing keepScreenOn=false to it races with and can clobber
    // the playing page's request — the screen then times out mid-playback
    // (#1005, #1067). The framework ORs keepScreenOn across the view tree, so
    // scoping it to this page's surface view makes it truly per-page; it also
    // clears itself when the page's view detaches, no onDispose needed.
    LaunchedEffect(isPlayingState.value, surfaceViewRef) {
        surfaceViewRef?.keepScreenOn = isPlayingState.value
    }
    // Workaround for #967: on some devices (notably Samsung) the video SurfaceView
    // blacks out when the system bars are toggled while playing — the player keeps
    // decoding but its output surface stops presenting. When enabled we re-bind the
    // player to the surface on every system-bar visibility change. Read via
    // rememberUpdatedState so the long-lived inset listener always sees the latest
    // value and player without re-registering.
    val rebindOnInsetChange by rememberVideoSurfaceRebind()
    val rebindEnabledState = rememberUpdatedState(rebindOnInsetChange)
    val playerForRebind = rememberUpdatedState(currentPlayer)
    val allowBlur = LocalMediaViewerVisualPolicy.current.allowBlur
    val hazeState = LocalHazeState.current
    val videoCapture by rememberSurfaceCapture(
        view = surfaceViewRef,
        enabled = allowBlur && captureBlur
    )

    // Zoom state — mirrors the zoomimage image viewer feel: live pinch/pan is
    // applied instantly (snapTo, no animation lag) and anchored at the gesture
    // centroid, while double-tap and reset use smooth animations (animateTo).
    val scope = rememberCoroutineScope()
    val scaleAnim = remember(media.id) { Animatable(1f) }
    val offsetXAnim = remember(media.id) { Animatable(0f) }
    val offsetYAnim = remember(media.id) { Animatable(0f) }

    val scale = scaleAnim.value
    val offsetX = offsetXAnim.value
    val offsetY = offsetYAnim.value

    var isZoomed by remember(media.id) { mutableStateOf(false) }
    val updatedOnZoomChange by rememberUpdatedState(onZoomChange)
    LaunchedEffect(isZoomed) {
        updatedOnZoomChange(isZoomed)
    }

    // Maximum pan offset so the zoomed video edges stay within view
    fun maxOffsetFor(targetScale: Float): Offset {
        if (targetScale <= 1f) return Offset.Zero
        return Offset(
            x = (targetScale - 1f) * videoSize.width / 2f,
            y = (targetScale - 1f) * videoSize.height / 2f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { updatedOnClick() },
                    onDoubleTap = { tapOffset ->
                        if (scaleAnim.value > 1.01f) {
                            // Reset zoom
                            isZoomed = false
                            scope.launch { scaleAnim.animateTo(1f, tween(300)) }
                            scope.launch { offsetXAnim.animateTo(0f, tween(300)) }
                            scope.launch { offsetYAnim.animateTo(0f, tween(300)) }
                        } else {
                            // Zoom to 2.5x anchored on the tap position
                            val targetScale = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val focal = (center - tapOffset) * (targetScale - 1f)
                            val max = maxOffsetFor(targetScale)
                            isZoomed = true
                            scope.launch { scaleAnim.animateTo(targetScale, tween(300)) }
                            scope.launch {
                                offsetXAnim.animateTo(focal.x.coerceIn(-max.x, max.x), tween(300))
                            }
                            scope.launch {
                                offsetYAnim.animateTo(focal.y.coerceIn(-max.y, max.y), tween(300))
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        // Poll on the Initial pass so the pinch claims the gesture
                        // before the HorizontalPager / overlay can start a swipe,
                        // mirroring the timeline's prioritized pinch handling.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            val oldScale = scaleAnim.value
                            val newScale = (oldScale * zoomChange).coerceIn(1f, 5f)
                            val k = newScale / oldScale
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val r = centroid - center
                            // Focal-point zoom: keep the content under the fingers fixed
                            val max = maxOffsetFor(newScale)
                            val newX = (r.x * (1f - k) + k * offsetXAnim.value + panChange.x)
                                .coerceIn(-max.x, max.x)
                            val newY = (r.y * (1f - k) + k * offsetYAnim.value + panChange.y)
                                .coerceIn(-max.y, max.y)
                            scope.launch {
                                scaleAnim.snapTo(newScale)
                                offsetXAnim.snapTo(newX)
                                offsetYAnim.snapTo(newY)
                            }
                            isZoomed = newScale > 1.01f
                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1 && scaleAnim.value > 1.01f) {
                            val panChange = event.calculatePan()
                            val max = maxOffsetFor(scaleAnim.value)
                            val newX = (offsetXAnim.value + panChange.x).coerceIn(-max.x, max.x)
                            val newY = (offsetYAnim.value + panChange.y).coerceIn(-max.y, max.y)
                            scope.launch {
                                offsetXAnim.snapTo(newX)
                                offsetYAnim.snapTo(newY)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .swipe(enabled = !isZoomed && swipeToDismissEnabled, onSwipeDown = updatedOnSwipeDown)
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
            // Recreate the surface per media so a switched group member binds to its own player.
            key(media.id, storyActive, compositedOutput) {
                AndroidView(
                    factory = { ctx ->
                        if (shouldUseTextureVideoOutput(storyActive, compositedOutput)) {
                            TextureView(ctx).also {
                                it.isOpaque = false
                                surfaceViewRef = it
                            }
                        } else {
                            SurfaceView(ctx).also { sv ->
                                surfaceViewRef = sv
                                // #967 workaround: re-bind the player surface whenever the
                                // system bars are shown/hidden, so the video keeps presenting
                                // through immersive transitions on affected devices.
                                var lastBarsVisible: Boolean? = null
                                ViewCompat.setOnApplyWindowInsetsListener(sv) { v, insets ->
                                    if (rebindEnabledState.value) {
                                        val barsVisible =
                                            insets.isVisible(WindowInsetsCompat.Type.systemBars())
                                        if (lastBarsVisible != null && lastBarsVisible != barsVisible) {
                                            v.post {
                                                val p = playerForRebind.value
                                                if (!p.isReleased && v is SurfaceView) {
                                                    runCatching { p.setVideoSurfaceView(v) }
                                                }
                                                v.invalidate()
                                                v.requestLayout()
                                            }
                                        }
                                        lastBarsVisible = barsVisible
                                    }
                                    insets
                                }
                            }
                        }
                    },
                    update = { outputView ->
                        if (!currentPlayer.isReleased) {
                            when (outputView) {
                                is SurfaceView -> currentPlayer.setVideoSurfaceView(outputView)
                                is TextureView -> currentPlayer.setVideoTextureView(outputView)
                            }
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
            }

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
            // Match the media viewer's background while the SurfaceView has no
            // content yet (e.g. when swiping to the next video). The viewer forces
            // a black background when blur is enabled, so painting the cover with
            // MaterialTheme.colorScheme.background (white in a light theme) caused a
            // white flash between videos (#1021).
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (allowBlur) Color.Black else MaterialTheme.colorScheme.background)
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
    if (!playback.ready && !playback.decryptFailed && !playback.playbackFailed) {
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

    if (playback.decryptFailed || playback.playbackFailed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(
                    if (playback.decryptFailed) R.string.decrypt_failed_tap_to_retry
                    else R.string.media_load_failed_tap_to_retry
                ),
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (playback.decryptFailed) vm.retryDecryption()
                            else vm.retryPlayback()
                        }
                    )
            )
        }
    }
}