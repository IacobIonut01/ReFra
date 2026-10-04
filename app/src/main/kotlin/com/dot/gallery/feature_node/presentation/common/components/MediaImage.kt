/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.common.components

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberFavoriteIconPosition
import com.dot.gallery.core.presentation.components.CheckBox
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.util.advancedShadow
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.getIcon
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isFavorite
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoDurationHeader
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.sketch

/**
 * Per-grid hoist of the global flows every [MediaImage] cell would otherwise collect
 * individually (selection state, the favorite-icon preference, cloud sync states).
 *
 * Heavy grids (timeline, mosaic) collect these once at grid scope and provide them via
 * [LocalMediaCellState] so each cell becomes a pure reader — no per-cell
 * `collectAsStateWithLifecycle` coroutine/observer churn while cells recycle during a fast fling.
 */
@Immutable
data class MediaCellState(
    val selectionActive: Boolean,
    val selectedMedia: Set<Long>,
    val favoriteIconPosition: String,
    val cloudSyncStates: Map<Long, SyncState>,
    /** Local media ids that have at least one cloud backup (shows a small backup indicator). */
    val cloudBackedUpIds: Set<Long> = emptySet(),
)

/** Null by default: callers that don't provide it (search, picker) fall back to per-cell collection. */
val LocalMediaCellState = compositionLocalOf<MediaCellState?> { null }

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
    val roundedShape = remember(selectedShapeSize) {
        RoundedCornerShape(selectedShapeSize)
    }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .clip(roundedShape)
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
                            context.sketch.enqueue(
                                ImageRequest(context, media.getUri().toString()) {
                                    resize(width = 600, height = 600, precision = Precision.LESS_PIXELS)
                                    setExtra("realMimeType", media.mimeType)
                                    // Match the viewer's cache key so this prefetch stays valid
                                    // and busts when the file changes (#1004).
                                    setExtra(key = "mediaVersion", value = "${media.timestamp}:${media.size}")
                                    if (media.isEncrypted) {
                                        setExtra(key = "mediaKeyPreviewEnc", value = media.idLessKey)
                                    }
                                }
                            )
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

        val favIconPosition = cellState?.favoriteIconPosition ?: rememberFavoriteIconPosition().value
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
