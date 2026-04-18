/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberAllowGifAnimation
import com.dot.gallery.core.Settings.Misc.rememberFavoriteIconPosition
import androidx.compose.ui.util.fastFirstOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.presentation.components.CheckBox
import com.dot.gallery.core.presentation.components.util.advancedShadow
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.getIcon
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isFavorite
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoDurationHeader
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun <T : Media> MediaImage(
    modifier: Modifier = Modifier,
    media: T,
    metadataState: State<MediaMetadataState>,
    stackCount: Int = 1,
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
    val metadata by rememberedDerivedState(metadataState.value) {
        metadataState.value.metadata.fastFirstOrNull { it.mediaId == media.id }
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
    val allowBlur by rememberAllowBlur()
    val allowGifAnimation by rememberAllowGifAnimation()
    val badgeHazeState = rememberHazeState(blurEnabled = allowBlur)

    Box(
        modifier = Modifier
            .clip(roundedShape)
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
                .hazeSource(badgeHazeState)
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
                var newRequest = it.centerCrop().diskCacheStrategy(DiskCacheStrategy.ALL)
                newRequest = newRequest.thumbnail(newRequest.clone().sizeMultiplier(0.4f))
                    .signature(GlideInvalidation.signature(media))
                if (allowGifAnimation && media.label.contains(".gif", ignoreCase = true)) {
                    newRequest = newRequest.decode(GifDrawable::class.java)
                }
                newRequest
            }
        )

        if (media.isVideo) {
            VideoDurationHeader(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(selectedSize / 1.5f)
                    .scale(scale),
                media = media
            )
        }

        if (stackCount > 1) {
            val badgeShape = RoundedCornerShape(6.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(selectedSize / 1.5f)
                    .scale(scale)
                    .padding(6.dp)
                    .clip(badgeShape)
                    .hazeEffect(
                        state = badgeHazeState,
                        style = HazeMaterials.ultraThin(
                            containerColor = Color.Black.copy(alpha = 0.35f)
                        )
                    )
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stackCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    modifier = Modifier.size(12.dp),
                    imageVector = Icons.Outlined.BurstMode,
                    tint = Color.White,
                    contentDescription = null
                )
            }
        }

        val favIconPosition by rememberFavoriteIconPosition()
        if (media.isFavorite && favIconPosition != Settings.Misc.FAV_ICON_DISABLED) {
            val favAlignment = when (favIconPosition) {
                Settings.Misc.FAV_ICON_BOTTOM_START -> Alignment.BottomStart
                Settings.Misc.FAV_ICON_TOP_END -> Alignment.TopEnd
                Settings.Misc.FAV_ICON_TOP_START -> Alignment.TopStart
                else -> Alignment.BottomEnd
            }
            Icon(
                modifier = Modifier
                    .align(favAlignment)
                    .padding(selectedSize / 1.5f)
                    .scale(scale)
                    .padding(8.dp)
                    .size(16.dp),
                imageVector = Icons.Filled.Favorite,
                tint = Color.Red,
                contentDescription = null
            )
        }

        if (metadata != null && metadata!!.isRelevant) {
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
                imageVector = metadata!!.getIcon()!!,
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
