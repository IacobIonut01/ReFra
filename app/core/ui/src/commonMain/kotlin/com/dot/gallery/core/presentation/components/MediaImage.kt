/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.domain.settings.FAV_ICON_BOTTOM_START
import com.dot.gallery.domain.settings.FAV_ICON_DISABLED
import com.dot.gallery.domain.settings.FAV_ICON_TOP_END
import com.dot.gallery.domain.settings.FAV_ICON_TOP_START
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.LocalMediaViewerOverlayController
import com.dot.gallery.core.presentation.components.util.advancedShadow
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isFavorite
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.domain.uri.path
import com.dot.gallery.ui.core.icons.Encrypted
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.data.entity.isRelevant
import com.dot.gallery.core.presentation.components.CheckBox
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer


@Composable
fun <T : Media> MediaImage(
    modifier: Modifier = Modifier,
    media: T,
    metadataState: State<MediaMetadataState>,
    stackCount: Int = 1,
    isCloudGroup: Boolean = false,
    aspectRatio: Float = 1f,
    canClick: () -> Boolean,
    onMediaClick: (T) -> Unit,
    onItemSelect: (T) -> Unit,
) {
    val selector = LocalMediaSelector.current
    val cellState = LocalMediaCellState.current
    val selectionState: Boolean
    val selectedMedia: Set<Long>
    if (cellState != null) {
        selectionState = cellState.selectionActive
        selectedMedia = cellState.selectedMedia
    } else {
        selectionState = selector.isSelectionActive.collectAsStateWithLifecycle().value
        selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle().value
    }
    val isSelected by rememberedDerivedState(selectionState, selectedMedia, media) {
        selectionState && media.id in selectedMedia
    }
    val viewer = LocalMediaViewerOverlayController.current
    val isPreviewed = LocalGalleryDetailPane.current &&
        viewer?.visible == true && viewer.currentMediaId == media.id
    val metadata by rememberedDerivedState(metadataState.value) {
        metadataState.value.metadataMap[media.id]
    }

    val selectedSize: Dp
    val scale: Float
    val selectedShapeSize: Dp
    val strokeSize: Dp
    val strokeColor: Color
    if (selectionState) {
        selectedSize = animateDpAsState(
            targetValue = if (isSelected) 12.dp else 0.dp,
            label = "selectedSize"
        ).value
        scale = animateFloatAsState(
            targetValue = if (isSelected) 0.5f else 1f,
            label = "scale"
        ).value
        selectedShapeSize = animateDpAsState(
            targetValue = if (isSelected) 16.dp else 0.dp,
            label = "selectedShapeSize"
        ).value
        strokeSize = animateDpAsState(
            targetValue = if (isSelected) 2.dp else 0.dp,
            label = "strokeSize"
        ).value
        val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
        strokeColor = animateColorAsState(
            targetValue = if (isSelected) primaryContainerColor else Color.Transparent,
            label = "strokeColor"
        ).value
    } else {
        selectedSize = 0.dp
        scale = 1f
        selectedShapeSize = 0.dp
        strokeSize = 0.dp
        strokeColor = Color.Transparent
    }
    val roundedShape = remember(selectedShapeSize, isPreviewed) {
        RoundedCornerShape(if (isPreviewed) 24.dp else selectedShapeSize)
    }
    val prefetcher = LocalMediaPrefetcher.current

    Box(
        modifier = Modifier
            .clip(roundedShape)
            .then(
                if (isPreviewed) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, roundedShape)
                else Modifier
            )
            .combinedClickable(
                enabled = canClick(),
                onClick = {
                    if (selectionState) {
                        onItemSelect(media)
                    } else {
                        // Encrypted videos are played by the vault player, not the image
                        // pipeline — prefetching would only pay a full decrypt for nothing
                        // it can use (#1282).
                        if (!(media.isEncrypted && media.isVideo)) {
                            prefetcher?.prefetch(media)
                        }
                        onMediaClick(media)
                    }
                },
                onLongClick = if (selectionState) {
                    null // No long click action when selection is active
                } else {
                    { onItemSelect(media) }
                }
            )
            .aspectRatio(aspectRatio)
            .then(modifier)
    ) {

        val renderer = LocalMediaImageRenderer.current
        // Stable thumbnail cache signature: invalidates only when the underlying bytes change
        // (definedTimestamp/size) and still carries filename/mime for animated-format detection.
        // Excludes volatile presentation fields (favorite/trashed/selection) so toggling them no
        // longer evicts the decoded thumbnail and forces a re-decode flash (#1076).
        val thumbnailSignature = remember(
            media.id,
            media.definedTimestamp,
            media.size,
            media.mimeType,
            media.label,
            media.path
        ) {
            "${media.id}:${media.definedTimestamp}:${media.size}:${media.mimeType}:${media.label}:${media.path}"
        }
        renderer.RenderImage(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .aspectRatio(aspectRatio)
                // Shrink the thumbnail visually (GPU scale) instead of padding its layout.
                // Padding changes the measured size, forcing the image loader to re-request a
                // smaller bitmap on selection (visible flash). Scaling keeps the layout size
                // constant so the already-decoded painter is simply resized on screen.
                .graphicsLayer {
                    val insetPx = selectedSize.toPx()
                    val min = size.minDimension
                    if (min > 0f && insetPx > 0f) {
                        val s = ((min - insetPx * 2f) / min).coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                    }
                }
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
            signature = thumbnailSignature
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

        AnimatedVisibility(
            visible = stackCount > 1
        ) {
            val badgeShape = RoundedCornerShape(6.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(selectedSize / 1.5f)
                    .scale(scale)
                    .padding(6.dp)
                    .clip(badgeShape)
                    .background(Color.Black.copy(alpha = 0.45f))
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
                    imageVector = if (isCloudGroup) Icons.Outlined.CloudSync else Icons.Outlined.BurstMode,
                    tint = Color.White,
                    contentDescription = null
                )
            }
        }

        val favIconPosition = cellState?.favoriteIconPosition ?: rememberPreference(SettingsKeys.favoriteIconPosition).value
        if (media.isFavorite && favIconPosition != FAV_ICON_DISABLED) {
            val favAlignment = when (favIconPosition) {
                FAV_ICON_BOTTOM_START -> Alignment.BottomStart
                FAV_ICON_TOP_END -> Alignment.TopEnd
                FAV_ICON_TOP_START -> Alignment.TopStart
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

        if (media.isCloud) {
            val syncStates = cellState?.cloudSyncStates
                ?: LocalMediaDistributor.current.cloudSyncStates.collectAsStateWithLifecycle().value
            val syncState = syncStates[media.id]
            val syncIcon = when (syncState) {
                SyncState.SYNCED -> Icons.Outlined.CloudDone
                SyncState.CONFLICT -> Icons.Outlined.CloudOff
                else -> Icons.Outlined.Cloud
            }
            val showProgress =
                syncState == SyncState.DOWNLOADING || syncState == SyncState.UPLOAD_PENDING
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(selectedSize / 1.5f)
                        .scale(scale)
                        .padding(6.dp)
                        .size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White
                )
            } else {
                Icon(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(selectedSize / 1.5f)
                        .scale(scale)
                        .padding(6.dp)
                        .size(10.dp)
                        .advancedShadow(
                            cornersRadius = 5.dp,
                            shadowBlurRadius = 4.dp,
                            alpha = 0.3f
                        ),
                    imageVector = syncIcon,
                    tint = Color.White.copy(alpha = 0.7f),
                    contentDescription = null
                )
            }
        } else if (cellState?.cloudBackedUpIds?.contains(media.id) == true) {
            // Local media that is backed up on at least one cloud provider.
            Icon(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(selectedSize / 1.5f)
                    .scale(scale)
                    .padding(6.dp)
                    .size(10.dp)
                    .advancedShadow(
                        cornersRadius = 5.dp,
                        shadowBlurRadius = 4.dp,
                        alpha = 0.3f
                    ),
                imageVector = Icons.Outlined.CloudDone,
                tint = Color.White.copy(alpha = 0.7f),
                contentDescription = null
            )
        }

        if (selectionState) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                val number by rememberedDerivedState(isSelected, selectedMedia) {
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
