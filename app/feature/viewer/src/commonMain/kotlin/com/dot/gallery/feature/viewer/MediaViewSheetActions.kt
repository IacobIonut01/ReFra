/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionLayoutStyle
import com.dot.gallery.core.presentation.components.OptionSheet
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isVideo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.add_to_collection
import com.dot.gallery.feature.viewer.generated.resources.copy
import com.dot.gallery.feature.viewer.generated.resources.copy_to_clipboard
import com.dot.gallery.feature.viewer.generated.resources.download
import com.dot.gallery.feature.viewer.generated.resources.edit
import com.dot.gallery.feature.viewer.generated.resources.frame_picker_extract_frames
import com.dot.gallery.feature.viewer.generated.resources.hide
import com.dot.gallery.feature.viewer.generated.resources.more_options
import com.dot.gallery.feature.viewer.generated.resources.motion_photo_export_video
import com.dot.gallery.feature.viewer.generated.resources.move
import com.dot.gallery.feature.viewer.generated.resources.open_with
import com.dot.gallery.feature.viewer.generated.resources.restore
import com.dot.gallery.feature.viewer.generated.resources.share
import com.dot.gallery.feature.viewer.generated.resources.use_as
import com.dot.gallery.feature.viewer.generated.resources.use_as_album_cover
import com.dot.gallery.feature.viewer.generated.resources.vault_confirm_restore_summary
import com.dot.gallery.feature.viewer.generated.resources.vault_confirm_restore_title


/**
 * Two-column action grid shown inside the viewer's info sheet. Every action that
 * touches app services (share/copy intents, vault flows, media handler, external
 * editor) is a callback, and the complex sheets are rendered through slots.
 */
@Composable
fun <T : Media> MediaViewSheetActions(
    media: T,
    // Whether any local album exists — gates the Copy/Move rows.
    hasAlbums: Boolean,
    // Whether any vault exists — a hide tap with none notifies [onHideNoVaults].
    hasVaults: Boolean,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?,
    moveSheetState: AppBottomSheetState,
    isMotionPhoto: Boolean = false,
    onOpenFramePicker: () -> Unit = {},
    // Capability inputs resolved app-side (provider settings, SDK gates).
    cloudReadOnly: Boolean = false,
    providerSupportsFavorite: Boolean = false,
    providerSupportsTrash: Boolean = false,
    platformSupportsFavorite: Boolean = false,
    sourceAllowsDelete: Boolean = true,
    // Action seams — the app launches intents/handlers.
    onShare: () -> Unit = {},
    onCopyToClipboard: () -> Unit = {},
    onHideNoVaults: () -> Unit = {},
    onOpenAs: () -> Unit = {},
    onSetAlbumCover: () -> Unit = {},
    onExportMotionVideo: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDownload: () -> Unit = {},
    logViewAction: (String) -> Unit = {},
    // Sheet slots rendered by the app.
    vaultHideSheets: @Composable (state: AppBottomSheetState) -> Unit = {},
    copySheet: @Composable (state: AppBottomSheetState) -> Unit = {},
    collectionSheet: @Composable (visible: Boolean, onDismiss: () -> Unit) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()

    // Sheet states for complex actions
    val hideSheetState = rememberAppBottomSheetState()
    val restoreConfirmState = rememberAppBottomSheetState()
    val copySheetState = rememberAppBottomSheetState()
    val useAsSheetState = rememberAppBottomSheetState()
    var showCollectionSheet by rememberSaveable { mutableStateOf(false) }

    // Resolve strings
    val shareText = stringResource(Res.string.share)
    val copyToClipboardText = stringResource(Res.string.copy_to_clipboard)
    val hideText = stringResource(Res.string.hide)
    val restoreText = stringResource(Res.string.restore)
    val openWithText = stringResource(Res.string.open_with)
    val useAsText = stringResource(Res.string.use_as)
    val useAsAlbumCoverText = stringResource(Res.string.use_as_album_cover)
    val moreOptionsText = stringResource(Res.string.more_options)
    val copyText = stringResource(Res.string.copy)
    val moveText = stringResource(Res.string.move)
    val editText = stringResource(Res.string.edit)
    val addToCollectionText = stringResource(Res.string.add_to_collection)
    val downloadText = stringResource(Res.string.download)
    val exportVideoText = stringResource(Res.string.motion_photo_export_video)
    val extractFramesText = stringResource(Res.string.frame_picker_extract_frames)

    val capabilities = media.viewerActionCapabilities(
        cloudReadOnly = cloudReadOnly,
        providerSupportsFavorite = providerSupportsFavorite,
        providerSupportsTrash = providerSupportsTrash,
        platformSupportsFavorite = platformSupportsFavorite,
        sourceAllowsDelete = sourceAllowsDelete,
        vaultRestoreAvailable = currentVault != null && restoreMedia != null,
    )

    // "Use as album cover" is offered for local, non-encrypted media that live in a real album.
    val canSetAlbumCover = remember(media, capabilities) {
        capabilities.setAlbumCover && media.albumID > 0
    }
    // Options shown when tapping "Use as" for an album-cover-capable item.
    val useAsOptions = remember(
        media,
        useAsAlbumCoverText,
        moreOptionsText,
    ) {
        listOf(
            OptionItem(
                icon = Icons.Outlined.Wallpaper,
                text = useAsAlbumCoverText,
                onClick = {
                    logViewAction("album_cover")
                    scope.launch {
                        onSetAlbumCover()
                        useAsSheetState.hide()
                    }
                }
            ),
            OptionItem(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                text = moreOptionsText,
                onClick = {
                    logViewAction("open_externally")
                    scope.launch {
                        useAsSheetState.hide()
                        onOpenAs()
                    }
                }
            )
        ).toMutableStateList()
    }

    // Build action list
    val actions = remember(
        media,
        hasAlbums,
        hasVaults,
        currentVault,
        isMotionPhoto,
        capabilities,
        canSetAlbumCover,
        shareText,
        copyToClipboardText,
        hideText,
        restoreText,
        openWithText,
        useAsText,
        copyText,
        moveText,
        editText,
        addToCollectionText,
        downloadText,
        exportVideoText,
        extractFramesText,
        onOpenFramePicker,
    ) {
        buildList<ActionGridItem> {
            if (capabilities.share) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Share,
                    text = shareText,
                    onClick = {
                        logViewAction("share")
                        onShare()
                    }
                ))
            }
            if (capabilities.copyToClipboard) {
                add(ActionGridItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = copyToClipboardText,
                    onClick = {
                        logViewAction("copy_to_clipboard")
                        onCopyToClipboard()
                    }
                ))
            }
            if (capabilities.hideInVault) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Lock,
                    text = hideText,
                    onClick = {
                        logViewAction("vault_hide")
                        if (!hasVaults) {
                            onHideNoVaults()
                        } else {
                            scope.launch { hideSheetState.show() }
                        }
                    }
                ))
            }
            // Restore
            if (capabilities.restoreFromVault && restoreMedia != null && currentVault != null) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Restore,
                    text = restoreText,
                    onClick = {
                        logViewAction("vault_restore")
                        scope.launch { restoreConfirmState.show() }
                    }
                ))
            }
            // Open As / Use As — when the item is a local, non-encrypted media in a real album,
            // tapping opens a sub-sheet offering "Use as album cover" plus "More options" (the
            // system chooser). Otherwise it falls back to launching the system chooser directly.
            if (capabilities.openExternally) {
                add(ActionGridItem(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    text = if (media.isVideo) openWithText else useAsText,
                    onClick = {
                        logViewAction("open_externally")
                        if (canSetAlbumCover) {
                            scope.launch { useAsSheetState.show() }
                        } else {
                            onOpenAs()
                        }
                    }
                ))
            }
            if (capabilities.extractFrames && (media.isVideo || isMotionPhoto)) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Image,
                    text = extractFramesText,
                    onClick = {
                        logViewAction("extract_frames")
                        onOpenFramePicker()
                    },
                ))
            }
            // Save Motion Photo embedded video as a standalone file
            if (isMotionPhoto && capabilities.exportMotionVideo) {
                add(ActionGridItem(
                    icon = Icons.Outlined.MovieCreation,
                    text = exportVideoText,
                    onClick = {
                        logViewAction("export_motion_video")
                        onExportMotionVideo()
                    }
                ))
            }
            // Copy & Move
            if (hasAlbums && capabilities.copyOrMove) {
                add(ActionGridItem(
                    icon = Icons.Outlined.CopyAll,
                    text = copyText,
                    onClick = {
                        logViewAction("copy")
                        scope.launch { copySheetState.show() }
                    }
                ))
                add(ActionGridItem(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    text = moveText,
                    onClick = {
                        logViewAction("move")
                        scope.launch { moveSheetState.show() }
                    }
                ))
            }
            // Edit
            if (capabilities.edit) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Edit,
                    text = editText,
                    onClick = {
                        logViewAction("edit")
                        onEdit()
                    }
                ))
            }
            // Add to Collection
            if (capabilities.addToCollection) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Collections,
                    text = addToCollectionText,
                    onClick = {
                        logViewAction("collection_add")
                        showCollectionSheet = true
                    }
                ))
            }
            // Download (cloud only)
            if (capabilities.download) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Download,
                    text = downloadText,
                    onClick = {
                        logViewAction("download")
                        onDownload()
                    }
                ))
            }
        }
    }

    // Two-column grouped grid: only the grid's outer corners are rounded, cells of a row
    // share the tallest label's height, and a lone last action spans its row.
    val actionRows = actions.chunked(2)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ViewerSheetGroupGap)
    ) {
        actionRows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(ViewerSheetGroupGap)
            ) {
                rowItems.forEachIndexed { column, item ->
                    ActionGridCell(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = item.icon,
                        text = item.text,
                        enabled = item.enabled,
                        shape = actionGridCellCorners(
                            row = rowIndex,
                            rowCount = actionRows.size,
                            column = column,
                            spansRow = rowItems.size == 1,
                        ).toShape(),
                        onClick = item.onClick
                    )
                }
            }
        }
    }

    // --- Complex action sheets ---

    // "Use as" sub-sheet: album cover + system chooser
    if (canSetAlbumCover) {
        OptionSheet(
            state = useAsSheetState,
            optionList = arrayOf(useAsOptions),
            style = OptionLayoutStyle.Column,
        )
    }

    // Restore confirmation
    if (media.isEncrypted && restoreMedia != null && currentVault != null) {
        ConfirmationSheet(
            state = restoreConfirmState,
            title = stringResource(Res.string.vault_confirm_restore_title),
            summary = stringResource(Res.string.vault_confirm_restore_summary),
            onConfirm = {
                scope.launch { restoreMedia(currentVault, media) {} }
            }
        )
    }

    // Hide (vault selection + encrypt behavior) — app-side sheets keyed by the shared state.
    if (capabilities.hideInVault) {
        vaultHideSheets(hideSheetState)
    }

    // Copy sheet (move uses the caller-owned moveSheetState).
    if (hasAlbums && capabilities.copyOrMove) {
        copySheet(copySheetState)
    }

    // Add to Collection sheet
    if (capabilities.addToCollection) {
        collectionSheet(showCollectionSheet) { showCollectionSheet = false }
    }
}

private data class ActionGridItem(
    val icon: ImageVector,
    val text: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
private fun ActionGridCell(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ViewerSheetInnerCorner),
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(viewerSheetRowColor())
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
