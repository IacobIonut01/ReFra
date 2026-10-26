/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.metadata.MetadataCategory
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.model.LocationData
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaDateCaption
import com.dot.gallery.domain.util.canMakeActions
import com.dot.gallery.domain.util.fileExtension
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isRaw
import com.dot.gallery.domain.util.isTrashed
import com.dot.gallery.domain.util.readUriOnly
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.cloud_media
import com.dot.gallery.feature.viewer.generated.resources.edit
import com.dot.gallery.feature.viewer.generated.resources.encrypted
import com.dot.gallery.feature.viewer.generated.resources.metadata
import com.dot.gallery.feature.viewer.generated.resources.remove_metadata
import com.dot.gallery.feature.viewer.generated.resources.s_items
import com.dot.gallery.feature.viewer.generated.resources.view_all_metadata


/**
 * The viewer's bottom info sheet: date/location card, info rows, action grid.
 *
 * Everything app-coupled is slotted: the caller resolves [dateCaption],
 * [mediaInfoList] and [locationData], renders the metadata/capture-date/move
 * sheets through slots, hosts the action grid via [actionsContent], and can
 * inject extra lazy items (motion-photo section, cloud backup card, people card)
 * via [preInfoItems]/[postInfoItems] using [MediaInfoSheetCard] for the chrome.
 */
@Composable
fun <T : Media> MediaViewSheetDetails(
    metadata: MediaMetadata?,
    currentMedia: T?,
    dateCaption: MediaDateCaption,
    mediaInfoList: List<InfoRow>,
    locationData: LocationData?,
    // Whether any local album exists — gates the move sheet.
    hasAlbums: Boolean,
    // App-resolved sanitize capability gate (SdkCompat.supportsTrash on Android).
    canReplaceOriginal: Boolean,
    // Sheet states owned by the caller so label clicks / sheet content stay app-side.
    metadataSheetState: AppBottomSheetState,
    captureDateSheetState: AppBottomSheetState,
    moveSheetState: AppBottomSheetState,
    // Metadata sanitization — the phase is mapped from the app ViewModel state.
    sanitizationPhase: MetadataSanitizationUiPhase = MetadataSanitizationUiPhase.Idle,
    probeMetadataSanitization: (T) -> Unit = {},
    sanitizeMetadata: (T, Set<MetadataCategory>, MetadataSaveMode) -> Unit = { _, _, _ -> },
    // Fired when a sanitize completes for the viewed item — app toasts/launches the
    // trash request, then calls [onResetSanitization]; the sheet hides on Idle.
    onSanitizationComplete: (T) -> Unit = {},
    onResetSanitization: () -> Unit = {},
    // Navigation seams.
    onShowLocationInApp: (T) -> Unit = {},
    onViewAllMetadata: (T) -> Unit = {},
    onOpenCategory: (String) -> Unit = {},
    // Category row data, resolved app-side from the media handler.
    category: String? = null,
    categoryItemCount: Int = 0,
    categoryThumbnail: Any? = null,
    // Location map preview slot (StaticMapPreview is platform/app-side). Null disables maps.
    mapPreview: (@Composable (Modifier) -> Unit)? = null,
    onOpenMap: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    // ACCESS_MEDIA_LOCATION prompt — app-side (permissions). Rendered when location is
    // missing on a local image and this is non-null.
    locationAccessPrompt: (@Composable (Modifier) -> Unit)? = null,
    // Extra LazyColumn sections. Use [MediaInfoSheetCard] for the shared card chrome.
    motionPhotoContent: (@Composable (Modifier) -> Unit)? = null,
    preInfoItems: LazyListScope.() -> Unit = {},
    postInfoItems: LazyListScope.() -> Unit = {},
    // App-side sheets.
    metadataEditSheet: @Composable (AppBottomSheetState) -> Unit = {},
    captureDateSheet: @Composable (AppBottomSheetState) -> Unit = {},
    moveSheet: @Composable (AppBottomSheetState) -> Unit = {},
    // The action grid — caller renders MediaViewSheetActions with its app wiring and
    // receives the caller-owned [moveSheetState] back.
    actionsContent: @Composable (media: T, moveSheetState: AppBottomSheetState) -> Unit,
    logDebug: (String) -> Unit = {},
) {
    LaunchedEffect(metadata) {
        logDebug("Available metadata for ${currentMedia?.id}:\n${metadata.toString()}")
    }
    val isBlurEnabled = LocalMediaViewerVisualPolicy.current.allowBlur
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceColorVariant = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val sheetHazeStyle = HazeMaterials.thin(
        containerColor = surfaceColor
    )
    val iconBackgroundHazeStyle = HazeMaterials.thick(
        containerColor = surfaceContainerHigh
    )
    val sheetCardButtonHazeStyle = HazeMaterials.thick(
        containerColor = surfaceColorVariant
    )
    val iconBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            )
        } else {
            Modifier
        }
    }
    val buttonBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            )
        } else {
            Modifier
        }
    }
    val sheetBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = surfaceColor,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
        } else {
            Modifier
        }
    }
    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp
                )
            )
            .then(sheetBackgroundModifier)
            .hazeEffectScaled(
                state = LocalHazeState.current,
                style = sheetHazeStyle
            )
            .graphicsLayer {
                translationY = -1f
            }
    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DragHandle()
        }

        AnimatedVisibility(
            modifier = Modifier.fillMaxWidth(),
            visible = currentMedia != null && !currentMedia.isTrashed,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            if (currentMedia != null) {
                val scope = rememberCoroutineScope()
                val imageRenderer = LocalMediaImageRenderer.current

                // Metadata removal sheet — visibility follows the sanitization phase.
                val metadataRemovalSheetState = rememberAppBottomSheetState()
                var rememberedSanitizationCapability by remember(currentMedia.id) {
                    mutableStateOf<SanitizationCapability?>(null)
                }
                LaunchedEffect(sanitizationPhase, currentMedia.id) {
                    if (sanitizationPhase is MetadataSanitizationUiPhase.Ready &&
                        sanitizationPhase.mediaId == currentMedia.id
                    ) {
                        rememberedSanitizationCapability = sanitizationPhase.capability
                    }
                }
                val sanitizationCapability = when (sanitizationPhase) {
                    is MetadataSanitizationUiPhase.Ready ->
                        sanitizationPhase.takeIf { it.mediaId == currentMedia.id }?.capability
                            ?: rememberedSanitizationCapability
                    else -> rememberedSanitizationCapability
                }
                val metadataRemovalBusy =
                    sanitizationPhase is MetadataSanitizationUiPhase.Probing ||
                        sanitizationPhase is MetadataSanitizationUiPhase.Running
                LaunchedEffect(sanitizationPhase, currentMedia.id) {
                    val staleTerminalState = when (sanitizationPhase) {
                        is MetadataSanitizationUiPhase.Ready ->
                            sanitizationPhase.mediaId != currentMedia.id
                        is MetadataSanitizationUiPhase.Complete ->
                            sanitizationPhase.mediaId != currentMedia.id
                        else -> false
                    }
                    if (staleTerminalState) {
                        onResetSanitization()
                        return@LaunchedEffect
                    }
                    when (sanitizationPhase) {
                        is MetadataSanitizationUiPhase.Ready -> {
                            if (sanitizationPhase.mediaId == currentMedia.id) {
                                metadataRemovalSheetState.show()
                            }
                        }
                        is MetadataSanitizationUiPhase.Complete -> {
                            if (sanitizationPhase.mediaId == currentMedia.id) {
                                // App-side: result toasts and the REPLACE_ORIGINAL trash
                                // request; onResetSanitization() returns the phase to Idle.
                                onSanitizationComplete(currentMedia)
                            }
                        }
                        is MetadataSanitizationUiPhase.Idle -> {
                            if (metadataRemovalSheetState.isVisible) {
                                metadataRemovalSheetState.hide()
                            }
                        }
                        else -> Unit
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item(key = "date_location") {
                        MediaInfoSheetCard(innerPadding = Modifier.padding(16.dp)) {
                            DateHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = currentMedia.canMakeActions && currentMedia.isImage,
                                        indication = null,
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        }
                                    ) {
                                        scope.launch {
                                            captureDateSheetState.show()
                                        }
                                    },
                                mediaDateCaption = dateCaption
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(state = rememberScrollState())
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentMedia.isRaw) {
                                    MediaInfoChip(
                                        text = currentMedia.fileExtension.uppercase(),
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                if (currentMedia.isEncrypted) {
                                    MediaInfoChip(
                                        text = stringResource(Res.string.encrypted),
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                }
                                if (currentMedia.isCloud) {
                                    MediaInfoChip(
                                        text = stringResource(Res.string.cloud_media),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            LocationItem(
                                iconBackgroundModifier = Modifier
                                    .then(iconBackgroundModifier)
                                    .hazeEffectScaled(
                                        state = LocalHazeState.current,
                                        style = iconBackgroundHazeStyle
                                    ),
                                locationData = locationData,
                                mediaUri = currentMedia.getUri(),
                                mapPreview = mapPreview,
                                onOpenMap = onOpenMap,
                                onShowInApp = { onShowLocationInApp(currentMedia) }
                            )
                            if (locationData == null && currentMedia.isImage &&
                                !currentMedia.isCloud && !currentMedia.isEncrypted &&
                                !currentMedia.readUriOnly && locationAccessPrompt != null
                            ) {
                                locationAccessPrompt(
                                    Modifier
                                        .then(iconBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = iconBackgroundHazeStyle
                                        )
                                )
                            }
                            AnimatedVisibility(visible = currentMedia.canMakeActions) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(buttonBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = sheetCardButtonHazeStyle
                                        )
                                        .clickable(enabled = !metadataRemovalBusy) {
                                            probeMetadataSanitization(currentMedia)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocalFireDepartment,
                                        contentDescription = stringResource(Res.string.remove_metadata),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(Res.string.remove_metadata),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (motionPhotoContent != null) {
                        item(key = "motion_photo") {
                            motionPhotoContent(
                                Modifier
                                    .widthIn(max = 600.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                    }
                    // Cloud-backup / people cards etc.
                    preInfoItems()
                    item(key = "media_info") {
                        MediaInfoSheetCard(innerPadding = Modifier.padding(vertical = 16.dp)) {
                            mediaInfoList.forEach {
                                MediaInfoRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    label = it.label,
                                    content = it.content,
                                    icon = it.icon,
                                    iconBackgroundModifier = Modifier
                                        .then(iconBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = iconBackgroundHazeStyle
                                        ),
                                    trailingContent = {
                                        if (it.trailingIcon != null && currentMedia.canMakeActions) {
                                            MediaInfoChip(
                                                text = stringResource(Res.string.edit),
                                                contentColor = MaterialTheme.colorScheme.secondary,
                                                containerColor = MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.1f
                                                ),
                                                onClick = {
                                                    scope.launch {
                                                        metadataSheetState.show()
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    onClick = it.onClick
                                )
                            }
                            // Only offer "view all metadata" when there is metadata to show.
                            // metadata is null when nothing could be parsed for this item, so
                            // hiding the row avoids opening an empty metadata screen.
                            if (!currentMedia.isEncrypted && metadata != null) {
                                MediaInfoRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    label = stringResource(Res.string.view_all_metadata),
                                    content = stringResource(Res.string.metadata),
                                    icon = Icons.Outlined.Info,
                                    iconBackgroundModifier = Modifier
                                        .then(iconBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = iconBackgroundHazeStyle
                                        ),
                                    onClick = { onViewAllMetadata(currentMedia) }
                                )
                            }
                            if (category != null) {
                                MediaInfoRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    label = category,
                                    content = stringResource(
                                        Res.string.s_items,
                                        categoryItemCount
                                    ),
                                    iconBackgroundModifier = Modifier
                                        .then(iconBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = iconBackgroundHazeStyle
                                        ),
                                    trailingContent = {
                                        AnimatedVisibility(
                                            visible = categoryThumbnail != null,
                                            enter = enterAnimation,
                                            exit = exitAnimation
                                        ) {
                                            categoryThumbnail?.let { thumb ->
                                                imageRenderer.RenderImage(
                                                    model = thumb,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    signature = thumb,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onOpenCategory(category) }
                                )
                            }
                        }
                    }
                    postInfoItems()
                    item(key = "actions") {
                        actionsContent(currentMedia, moveSheetState)
                    }
                    item(key = "spacer") {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        )
                    }
                }

                if (metadataRemovalSheetState.isVisible) {
                    MetadataRemovalSheet(
                        state = metadataRemovalSheetState,
                        capability = sanitizationCapability,
                        isBusy = metadataRemovalBusy,
                        canReplaceOriginal = canReplaceOriginal,
                        onConfirm = { categories, saveMode ->
                            sanitizeMetadata(currentMedia, categories, saveMode)
                        },
                        onDismissed = onResetSanitization
                    )
                }

                if (metadataSheetState.isVisible) {
                    metadataEditSheet(metadataSheetState)
                }

                if (captureDateSheetState.isVisible) {
                    captureDateSheet(captureDateSheetState)
                }

            }
        }

        if (hasAlbums) {
            moveSheet(moveSheetState)
        }
    }
}
