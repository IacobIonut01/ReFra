/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation
import com.dot.gallery.core.presentation.components.CheckBox
import com.dot.gallery.core.presentation.components.util.advancedShadow
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.getIcon
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isFavorite
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoDurationHeader
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.resize.Precision

@Composable
fun <T : Media> MediaImage(
    modifier: Modifier = Modifier,
    media: T,
    metadata: MediaMetadata? = null,
    selectionState: MutableState<Boolean>,
    selectedMedia: MutableState<Set<Long>>,
    canClick: Boolean,
    onMediaClick: (T) -> Unit,
    onItemSelect: (T) -> Unit,
) {
    val isSelected by rememberedDerivedState(selectionState.value, selectedMedia, media) {
        selectionState.value && selectedMedia.value.any { it == media.id }
    }
    val selectedSize by animateDpAsState(
        targetValue = if (isSelected) 12.dp else 0.dp,
        label = "selectedSize"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.5f else 1f,
        label = "scale"
    )
    val selectedShapeSize by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 0.dp,
        label = "selectedShapeSize"
    )
    val strokeSize by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        label = "strokeSize"
    )
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val strokeColor by animateColorAsState(
        targetValue = if (isSelected) primaryContainerColor else Color.Transparent,
        label = "strokeColor"
    )
    val roundedShape = remember(selectedShapeSize) {
        RoundedCornerShape(selectedShapeSize)
    }
    Box(
        modifier = Modifier
            .semantics {
                if (!selectionState.value) {
                    onLongClick("Select") {
                        onItemSelect(media)
                        true
                    }
                }
            }
            .clickable(
                enabled = canClick,
                onClick = {
                    if (selectionState.value) {
                        onItemSelect(media)
                    } else {
                        onMediaClick(media)
                    }
                }
            )
            .aspectRatio(1f)
            .then(modifier)
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .aspectRatio(1f)
                .padding(selectedSize)
                .clip(roundedShape)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = roundedShape
                )
                .border(
                    width = strokeSize,
                    shape = roundedShape,
                    color = strokeColor
                ),
            request = ComposableImageRequest(media.getUri().toString()) {
                precision(Precision.LESS_PIXELS)
                setExtra(
                    key = "mediaKey",
                    value = media.idLessKey,
                )
                setExtra(
                    key = "realMimeType",
                    value = media.mimeType,
                )
            },
            filterQuality = FilterQuality.None,
            contentDescription = media.label,
            contentScale = ContentScale.Crop,
        )

        AnimatedVisibility(
            visible = remember(media) { media.isVideo },
            enter = Animation.enterAnimation,
            exit = Animation.exitAnimation,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            VideoDurationHeader(
                modifier = Modifier
                    .padding(selectedSize / 1.5f)
                    .scale(scale),
                media = media
            )
        }

        AnimatedVisibility(
            visible = remember(media) {
                media.isFavorite
            },
            enter = Animation.enterAnimation,
            exit = Animation.exitAnimation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
        ) {
            Image(
                modifier = Modifier
                    .padding(selectedSize / 1.5f)
                    .scale(scale)
                    .padding(8.dp)
                    .size(16.dp),
                imageVector = Icons.Filled.Favorite,
                colorFilter = ColorFilter.tint(Color.Red),
                contentDescription = null
            )
        }

        AnimatedVisibility(
            visible = metadata != null && metadata.isRelevant,
            enter = Animation.enterAnimation,
            exit = Animation.exitAnimation,
            modifier = Modifier
                .align(Alignment.BottomStart)
        ) {
            Icon(
                modifier = Modifier
                    .padding(selectedSize / 1.5f)
                    .scale(scale)
                    .padding(8.dp)
                    .size(16.dp)
                    .advancedShadow(
                        cornersRadius = 8.dp,
                        shadowBlurRadius = 6.dp,
                        alpha = 0.3f
                    ),
                imageVector = metadata!!.getIcon()!!,
                tint = Color.White,
                contentDescription = null
            )
        }

        AnimatedVisibility(
            visible = selectionState.value,
            enter = Animation.enterAnimation,
            exit = Animation.exitAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                CheckBox(isChecked = isSelected)
            }
        }
    }
}
