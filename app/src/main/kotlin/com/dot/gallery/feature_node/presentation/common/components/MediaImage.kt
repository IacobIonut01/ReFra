/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.core.LocalMediaSelector
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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun <T : Media> MediaImage(
    modifier: Modifier = Modifier,
    media: T,
    metadata: () -> MediaMetadata? = { null },
    canClick: () -> Boolean,
    onMediaClick: (T) -> Unit,
    onItemSelect: (T) -> Unit,
) {
    val selector = LocalMediaSelector.current
    val selectionState by selector.isSelectionActive.collectAsStateWithLifecycle()
    val selectedMedia by selector.selectedMedia.collectAsStateWithLifecycle()
    val isSelected by rememberedDerivedState(selectionState, selectedMedia, media) {
        selectionState && selectedMedia.any { it == media.id }
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
/*    val request = ComposableImageRequest(media.getUri().toString()) {
        precision(Precision.LESS_PIXELS)
        colorType(Bitmap.Config.RGB_565)
        sizeMultiplier(0.8f)
        setExtra(
            key = "mediaKey",
            value = media.idLessKey,
        )
        setExtra(
            key = "realMimeType",
            value = media.mimeType,
        )
        memoryCacheKey(media.idLessKey)
        resultCacheKey(media.idLessKey)
    }*/
    Box(
        modifier = Modifier
            .combinedClickable(
                enabled = canClick(),
                onClick = {
                    if (selectionState) {
                        onItemSelect(media)
                    } else {
                        onMediaClick(media)
                    }
                },
                onLongClick = if (selectionState) {
                    null // No long click action when selection is active
                } else {
                    { onItemSelect(media) }
                }
            )
            .aspectRatio(1f)
            .then(modifier)
    ) {

        GlideImage(
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
            model = media.getUri(),
            contentDescription = media.label,
            contentScale = ContentScale.Crop,
            requestBuilderTransform = {
                val newRequest = it.centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                newRequest.thumbnail(newRequest.clone().sizeMultiplier(0.4f))
            }
        )

/*        AsyncImage(
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
            request = request,
            filterQuality = FilterQuality.None,
            contentDescription = media.label,
            contentScale = ContentScale.Crop,
        )*/

        if (media.isVideo) {
            VideoDurationHeader(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(selectedSize / 1.5f)
                    .scale(scale),
                media = media
            )
        }

        if (media.isFavorite) {
            Icon(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(selectedSize / 1.5f)
                    .scale(scale)
                    .padding(8.dp)
                    .size(16.dp),
                imageVector = Icons.Filled.Favorite,
                tint = Color.Red,
                contentDescription = null
            )
        }

        if (metadata() != null && metadata()!!.isRelevant) {
            Icon(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(selectedSize / 1.5f)
                    .scale(scale)
                    .padding(8.dp)
                    .size(16.dp)
                    .advancedShadow(
                        cornersRadius = 8.dp,
                        shadowBlurRadius = 6.dp,
                        alpha = 0.3f
                    ),
                imageVector = metadata()!!.getIcon()!!,
                tint = Color.White,
                contentDescription = null
            )
        }

        if (selectionState) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                val number by rememberedDerivedState {
                    if (isSelected) {
                        selectedMedia.indexOf(media.id) + 1
                    } else null
                }
                CheckBox(
                    isChecked = isSelected,
                    number = number
                )
            }
        }
    }
}
