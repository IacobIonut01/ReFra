/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.media3.exoplayer.ExoPlayer
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoControllerState
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoHdrInfo
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoPlayer
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

internal fun shouldRenderMotionPhotoSurface(
    isVideo: Boolean,
    isMotionPhoto: Boolean,
    isSelected: Boolean,
) = !isVideo && isMotionPhoto && isSelected

@Stable
@NonRestartableComposable
@Composable
fun <T : Media> MediaPreviewComponent(
    media: T?,
    modifier: Modifier = Modifier,
    containerModifier: Modifier = Modifier,
    uiEnabled: Boolean,
    playWhenReady: State<Boolean>,
    onItemClick: () -> Unit,
    onImageTap: (Offset) -> Unit = { onItemClick() },
    onImageImmediateTap: (Offset) -> Boolean = { false },
    onSwipeDown: () -> Unit,
    swipeToDismissEnabled: Boolean = true,
    rotationDisabled: Boolean,
    onImageRotated: (newRotation: Int) -> Unit,
    offset: IntOffset,
    isPanorama: Boolean = false,
    isPhotosphere: Boolean = false,
    isMotionPhoto: Boolean = false,
    motionPhotoState: MotionPhotoState? = null,
    currentVault: Vault? = null,
    onZoomChange: (Boolean) -> Unit = {},
    onImageZoomChange: (Boolean) -> Unit = {},
    onSubsamplingLoadingChange: (Boolean) -> Unit = {},
    onCutoutStateChanged: (Boolean) -> Unit = {},
    onCutoutController: (CutoutController?) -> Unit = {},
    isSelected: Boolean = true,
    // Slideshow mode: mutes video audio and, together with [onVideoEnded], lets the video play
    // through once (no looping) so the slideshow can advance when playback finishes.
    slideshowActive: Boolean = false,
    storyActive: Boolean = false,
    cutoutEnabled: Boolean = true,
    onLoadFailed: () -> Unit = {},
    onVideoEnded: () -> Unit = {},
    // Reports the detected HDR format even when playback fails (the controller lambda is
    // `ready`-gated and never composes for undecodable tracks) (#1274).
    onVideoHdrChanged: (VideoHdrInfo) -> Unit = {},
    // When false the (expensive, fullscreen `.blur(100.dp)`) blurred backdrop is skipped. Used to
    // keep it off the shared-element open/close transition's critical frames — it's imperceptible
    // during the animation but a heavy per-frame RenderEffect pass.
    renderBackground: Boolean = true,
    // Dismiss-drag fade for the blurred backdrop — tracks the gesture instantly.
    backdropGestureAlpha: Float = 1f,
    videoController: @Composable (ExoPlayer, MutableState<Boolean>, MutableLongState, Long, Int, Float, VideoControllerState) -> Unit,
) {
    // "Animate media items" off — media surfaces appear/disappear instantly.
    val animationsEnabled = LocalMediaViewerVisualPolicy.current.animationsEnabled
    val enter = if (animationsEnabled) fadeIn() else EnterTransition.None
    val exit = if (animationsEnabled) fadeOut() else ExitTransition.None
    AnimatedVisibility(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = LocalHazeState.current),
        visible = media != null,
        enter = enter,
        exit = exit,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Non-translating blurred background: a sibling of the offset content box, so it
            // stays pinned to the screen while the media rides the dismiss drag offset.
            if ((!media!!.isVideo || storyActive) && !isPanorama && !isPhotosphere && renderBackground) {
                BlurredMediaBackground(
                    media = media,
                    uiEnabled = uiEnabled,
                    gestureAlpha = backdropGestureAlpha,
                )
            }
            // Translating content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(modifier)
                    .then(containerModifier)
                    .offset { offset },
            ) {
                AnimatedVisibility(
                    modifier = Modifier.fillMaxSize(),
                    visible = media.isVideo,
                    enter = enter,
                    exit = exit
                ) {
                    VideoPlayer(
                        modifier = Modifier,
                        media = media,
                        playWhenReady = playWhenReady,
                        videoController = videoController,
                        onItemClick = onItemClick,
                        onSwipeDown = onSwipeDown,
                        swipeToDismissEnabled = swipeToDismissEnabled,
                        onZoomChange = onZoomChange,
                        captureBlur = uiEnabled,
                        slideshowActive = slideshowActive,
                        storyActive = storyActive,
                        compositedOutput = storyActive || !swipeToDismissEnabled,
                        onLoadFailed = onLoadFailed,
                        onVideoEnded = onVideoEnded,
                        onVideoHdrChanged = onVideoHdrChanged
                    )
                }

                AnimatedVisibility(
                    modifier = Modifier.fillMaxSize(),
                    visible = !media.isVideo && !isPanorama && !isPhotosphere,
                    enter = enter,
                    exit = exit
                ) {
                    ZoomablePagerImage(
                        media = media,
                        rotationDisabled = rotationDisabled,
                        onImageRotated = onImageRotated,
                        onItemClick = onImageTap,
                        onImmediateTap = onImageImmediateTap,
                        onSwipeDown = onSwipeDown,
                        swipeToDismissEnabled = swipeToDismissEnabled,
                        onSubsamplingLoadingChange = onSubsamplingLoadingChange,
                        onZoomTransformChange = {
                            if (isSelected) {
                                onImageZoomChange(it.scaleX > 1.01f || it.scaleY > 1.01f)
                                motionPhotoState?.videoTransform = it.toMotionPhotoVideoTransform()
                            }
                        },
                        onLoadFailed = onLoadFailed,
                        onCutoutStateChanged = onCutoutStateChanged,
                        onCutoutController = onCutoutController,
                        isSelected = isSelected,
                        uiVisible = uiEnabled,
                        cutoutEnabled = cutoutEnabled && !slideshowActive
                    )
                }

                if (shouldRenderMotionPhotoSurface(media.isVideo, isMotionPhoto, isSelected)) {
                    motionPhotoState?.let { MotionPhotoSurface(state = it) }
                }

                AnimatedVisibility(
                    modifier = Modifier.fillMaxSize(),
                    visible = !media.isVideo && !isMotionPhoto && (isPanorama || isPhotosphere),
                    enter = enter,
                    exit = exit
                ) {
                    PanoramaImageViewer(
                        media = media,
                        isPhotosphere = isPhotosphere,
                        modifier = Modifier,
                        onItemClick = onItemClick,
                        currentVault = currentVault,
                        captureBlur = uiEnabled
                    )
                }
            }
        }
    }
}