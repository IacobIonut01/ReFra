/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.media

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.IntOffset
import androidx.media3.exoplayer.ExoPlayer
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoPlayer
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

@Stable
@NonRestartableComposable
@Composable
fun <T: Media> MediaPreviewComponent(
    media: T?,
    modifier: Modifier = Modifier,
    containerModifier: Modifier = Modifier,
    uiEnabled: Boolean,
    playWhenReady: State<Boolean>,
    onItemClick: () -> Unit,
    onSwipeDown: () -> Unit,
    rotationDisabled: Boolean,
    onImageRotated: (newRotation: Int) -> Unit,
    offset: IntOffset,
    isPanorama: Boolean = false,
    isPhotosphere: Boolean = false,
    isMotionPhoto: Boolean = false,
    motionPhotoState: MotionPhotoState? = null,
    currentVault: Vault? = null,
    videoController: @Composable (ExoPlayer, MutableState<Boolean>, MutableLongState, Long, Int, Float) -> Unit,
) {
    AnimatedVisibility(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = LocalHazeState.current),
        visible = media != null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Non-translating blurred background
            if (!media!!.isVideo && !isPanorama && !isPhotosphere) {
                BlurredMediaBackground(
                    media = media,
                    uiEnabled = uiEnabled
                )
            }
            // Translating content
            Box(
                modifier = Modifier.fillMaxSize().then(containerModifier).offset { offset },
            ) {
            AnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                visible = media.isVideo,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                VideoPlayer(
                    modifier = modifier,
                    media = media,
                    playWhenReady = playWhenReady,
                    videoController = videoController,
                    onItemClick = onItemClick,
                    onSwipeDown = onSwipeDown
                )
            }

            AnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                visible = !media.isVideo && !isPanorama && !isPhotosphere,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ZoomablePagerImage(
                    modifier = modifier,
                    media = media,
                    uiEnabled = uiEnabled,
                    rotationDisabled = rotationDisabled,
                    onImageRotated = onImageRotated,
                    onItemClick = onItemClick,
                    onSwipeDown = onSwipeDown
                )
            }

            if (!media.isVideo && motionPhotoState != null) {
                MotionPhotoSurface(state = motionPhotoState)
            }

            AnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                visible = !media.isVideo && !isMotionPhoto && (isPanorama || isPhotosphere),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PanoramaImageViewer(
                    media = media,
                    isPhotosphere = isPhotosphere,
                    modifier = modifier,
                    onItemClick = onItemClick,
                    currentVault = currentVault
                )
            }
            }
        }
    }
}