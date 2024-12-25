/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
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
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoPlayer

@Stable
@NonRestartableComposable
@Composable
fun <T: Media> MediaPreviewComponent(
    media: T?,
    modifier: Modifier = Modifier,
    uiEnabled: Boolean,
    playWhenReady: State<Boolean>,
    onItemClick: () -> Unit,
    onSwipeDown: () -> Unit,
    offset: IntOffset,
    videoController: @Composable (ExoPlayer, MutableState<Boolean>, MutableLongState, Long, Int, Float) -> Unit,
) {
    if (media != null) {
        Box(
            modifier = Modifier.fillMaxSize().offset { offset },
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
                visible = !media.isVideo,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ZoomablePagerImage(
                    modifier = modifier,
                    media = media,
                    uiEnabled = uiEnabled,
                    onItemClick = onItemClick,
                    onSwipeDown = onSwipeDown
                )
            }
        }
    }
}