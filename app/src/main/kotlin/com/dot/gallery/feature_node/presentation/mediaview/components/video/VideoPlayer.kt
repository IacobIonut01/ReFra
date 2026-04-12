package com.dot.gallery.feature_node.presentation.mediaview.components.video

import android.app.Activity
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberSurfaceCapture
import dev.chrisbanes.haze.hazeSource

@OptIn(UnstableApi::class)
@Composable
fun <T : Media> VideoPlayer(
    media: T,
    modifier: Modifier = Modifier,
    playWhenReady: State<Boolean>,
    videoController: @Composable (ExoPlayer, MutableState<Boolean>, MutableLongState, Long, Int, Float) -> Unit,
    onItemClick: () -> Unit,
    onSwipeDown: () -> Unit
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
        player = vm.player,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { updatedOnClick() }
            )
            .swipe(onSwipeDown = updatedOnSwipeDown)
            .onVisibilityChanged(
                minFractionVisible = 0.2f
            ) { isVisible ->
                iWasVisible = iAmVisible
                iAmVisible = isVisible
            }
            .then(modifier)
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
                    vm.player.setVideoSurfaceView(sv)
                }
            },
            update = { sv ->
                vm.player.setVideoSurfaceView(sv)
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
            vm.player,
            isPlayingState,
            positionState,
            playback.durationMs,
            playback.bufferedPercent,
            playback.frameRate
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