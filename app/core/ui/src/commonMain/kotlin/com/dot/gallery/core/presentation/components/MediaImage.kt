/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.GraphicsLayerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
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
    val rotationTracker = LocalMediaRotationTracker.current
    // Per-cell view of the shared tracker: only this cell recomposes when its entry changes.
    val rotationEntry by remember(rotationTracker, media.id) {
        derivedStateOf { rotationTracker?.entry(media.id) }
    }
    val pendingRotation = rotationEntry?.pending
    val rotationRevision = rotationEntry?.revision ?: 0

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
        // A completed in-app rotation bumps the revision so the rewritten bytes are re-decoded
        // even when the file kept its timestamp and size.
        val thumbnailSignature = remember(
            media.id,
            media.definedTimestamp,
            media.size,
            media.mimeType,
            media.label,
            media.path,
            rotationRevision
        ) {
            val base = "${media.id}:${media.definedTimestamp}:${media.size}:${media.mimeType}:${media.label}:${media.path}"
            if (rotationRevision > 0) "$base:rot$rotationRevision" else base
        }
        // Rotation animation state exists only while this cell has a rotation in flight, like
        // the selection animations above, so idle cells pay nothing for it.
        val rotation = if (pendingRotation != null) {
            rememberCellRotation(media.id, pendingRotation, thumbnailSignature, rotationTracker)
        } else null
        val rotationDone = pendingRotation?.phase == PendingMediaRotation.Phase.Done
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
                    val s = if (min > 0f && insetPx > 0f) ((min - insetPx * 2f) / min).coerceIn(0f, 1f) else 1f
                    if (rotation != null) {
                        // Once done the new bytes are already rotated, so the base layer stays upright.
                        val angle = if (rotationDone) 0f else rotation.turn.value
                        applyRotationTurn(angle, rotation.lift.value * s, rotation.cornerFraction() * RotationCorner.toPx())
                    } else if (s != 1f) {
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
        if (rotation != null && rotationDone) {
            renderer.RenderImage(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .aspectRatio(aspectRatio)
                    .graphicsLayer {
                        alpha = rotation.outgoingAlpha.value
                        applyRotationTurn(rotation.turn.value, 1f, 0f)
                    },
                model = media.getUri(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                signature = rotation.outgoingSignature
            )
        }
        if (rotation != null) {
            AnimatedVisibility(
                modifier = Modifier.align(Alignment.Center),
                visible = pendingRotation?.phase == PendingMediaRotation.Phase.Processing,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        }

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

private val RotationSpring = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)
private const val RotationLift = 0.86f
private val RotationCorner = 16.dp
// Long enough for the re-encoded thumbnail (new cache key, full decode) to land underneath.
private const val RotationOutgoingHoldMs = 900L
private const val RotationOutgoingFadeMs = 280

/**
 * Turns the cell content by [angle] degrees, scaled so the rotated bounds always fit inside the
 * cell (mosaic cells are not square), with rounded corners while the item is lifted.
 */
private fun GraphicsLayerScope.applyRotationTurn(angle: Float, lift: Float, cornerPx: Float) {
    if (angle == 0f && lift == 1f && cornerPx == 0f) return
    val w = size.width
    val h = size.height
    val radians = angle * (PI.toFloat() / 180f)
    val c = abs(cos(radians))
    val sn = abs(sin(radians))
    val fit = if (w > 0f && h > 0f) min(w / (w * c + h * sn), h / (w * sn + h * c)) else 1f
    rotationZ = angle
    scaleX = fit * lift
    scaleY = fit * lift
    if (cornerPx > 0f) {
        shape = RoundedCornerShape(cornerPx)
        clip = true
    }
}

/** Animation state of one cell's in-flight rotation; [outgoingSignature] is the pre-rotation thumbnail. */
@Stable
private class CellRotation(val outgoingSignature: String) {
    val turn = Animatable(0f)
    val lift = Animatable(1f)
    val outgoingAlpha = Animatable(1f)

    /** Corner rounding follows the lift, so the card rounds as it rises and squares as it lands. */
    fun cornerFraction(): Float = ((1f - lift.value) / (1f - RotationLift)).coerceIn(0f, 1f)
}

@Composable
private fun rememberCellRotation(
    mediaId: Long,
    pending: PendingMediaRotation,
    signature: String,
    tracker: MediaRotationTracker?,
): CellRotation {
    val rotation = remember(mediaId) { CellRotation(outgoingSignature = signature) }
    LaunchedEffect(rotation, pending.phase, pending.degrees) {
        val degrees = pending.degrees.toFloat()
        when (pending.phase) {
            PendingMediaRotation.Phase.Queued -> rotation.lift.animateTo(RotationLift, RotationSpring)
            PendingMediaRotation.Phase.Processing -> coroutineScope {
                launch { rotation.lift.animateTo(RotationLift, RotationSpring) }
                rotation.turn.animateTo(degrees, RotationSpring)
            }
            PendingMediaRotation.Phase.Done -> coroutineScope {
                // The rotated file loads underneath while the old thumbnail finishes its turn
                // on top and fades out, so there is no blank frame and no turn back.
                launch { rotation.lift.animateTo(1f, RotationSpring) }
                launch { rotation.turn.animateTo(degrees, RotationSpring) }
                delay(RotationOutgoingHoldMs)
                rotation.outgoingAlpha.animateTo(0f, tween(RotationOutgoingFadeMs))
                tracker?.settle(mediaId)
            }
            PendingMediaRotation.Phase.Failed -> coroutineScope {
                launch { rotation.lift.animateTo(1f, RotationSpring) }
                rotation.turn.animateTo(0f, RotationSpring)
                tracker?.settle(mediaId)
            }
        }
    }
    return rotation
}
