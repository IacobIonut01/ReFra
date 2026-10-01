/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albums.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.dot.gallery.core.presentation.components.util.advancedShadow
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.Settings.Misc.rememberTrashEnabled
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumMergeResolver
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionLayoutStyle
import com.dot.gallery.feature_node.presentation.common.components.OptionSheet
import com.dot.gallery.feature_node.presentation.picker.PickerActivityContract
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.formatSize
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.dot.gallery.feature_node.presentation.vault.components.ConfirmationSheet
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AlbumComponent(
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    album: Album,
    isEnabled: Boolean = true,
    onItemClick: (Album) -> Unit,
    onMoveAlbumToTrash: ((Album) -> Unit)? = null,
    onTogglePinClick: ((Album) -> Unit)? = null,
    onToggleIgnoreClick: ((Album) -> Unit)? = null,
    onToggleLockClick: ((Album) -> Unit)? = null,
    onDeleteAlbumThumbnailClick: ((Album) -> Unit)? = null,
    onAddToGroup: ((Album) -> Unit)? = null,
    onRemoveFromGroup: ((Album) -> Unit)? = null,
    onMoveToSection: ((Album) -> Unit)? = null,
    onToggleMergeSubfolders: ((Album) -> Unit)? = null,
    onMergeParentSubfolders: ((Album) -> Unit)? = null,
    onToggleMergedSubfolderDisplayMode: ((Album) -> Unit)? = null,
    isMergedSubfolder: Boolean = false,
    isSubGallery: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    Column(
        modifier = modifier
            .alpha(if (isEnabled) 1f else 0.4f)
            .padding(horizontal = 8.dp),
    ) {
        AlbumOptionSheet(
            album = album,
            appBottomSheetState = appBottomSheetState,
            onMoveAlbumToTrash = onMoveAlbumToTrash,
            onTogglePinClick = onTogglePinClick,
            onToggleIgnoreClick = onToggleIgnoreClick,
            onToggleLockClick = onToggleLockClick,
            onDeleteAlbumThumbnailClick = onDeleteAlbumThumbnailClick,
            onAddToGroup = onAddToGroup,
            onRemoveFromGroup = onRemoveFromGroup,
            onMoveToSection = onMoveToSection,
            onToggleMergeSubfolders = onToggleMergeSubfolders,
            onMergeParentSubfolders = onMergeParentSubfolders,
            onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
            isMergedSubfolder = isMergedSubfolder,
            isSubGallery = isSubGallery
        )
        Box(
            modifier = Modifier
                .aspectRatio(1f)
        ) {
            AlbumImage(
                modifier = thumbnailModifier,
                album = album,
                isEnabled = isEnabled,
                onItemClick = onItemClick,
                onItemLongClick = if (onTogglePinClick != null) {
                    {
                        scope.launch {
                            appBottomSheetState.show()
                        }
                    }
                } else null
            )
            if (album.isOnSdcard) {
                Icon(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp)
                        .align(Alignment.BottomEnd),
                    imageVector = Icons.Outlined.SdCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (album.relativePath.startsWith("cloud/")) {
                CloudProviderBadge(
                    album = album,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }
        Text(
            modifier = Modifier
                .padding(top = 12.dp)
                .padding(horizontal = 16.dp),
            text = album.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
        if (album.count > 0) {
            Text(
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                text = pluralStringResource(
                    id = R.plurals.item_count,
                    count = album.count.toInt(),
                    album.count
                ) + if (album.size > 0) " (${formatSize(album.size)})" else "",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }

    }
}

/**
 * Small overlay badge shown on a cloud album's thumbnail, carrying the provider's
 * brand logo (falling back to a generic cloud glyph) over a subtle dark scrim.
 */
@Composable
private fun CloudProviderBadge(album: Album, modifier: Modifier = Modifier) {
    val providerType = remember(album.relativePath) {
        try {
            ProviderType.valueOf(album.relativePath.removePrefix("cloud/").substringBefore("/"))
        } catch (_: Exception) { null }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (providerType != null) {
                ProviderBrandIcon(
                    providerType = providerType,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Cloud,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (album.isCloudShared) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.People,
                    contentDescription = stringResource(R.string.album_shared_badge),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumRowComponent(
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    album: Album,
    isEnabled: Boolean = true,
    onItemClick: (Album) -> Unit,
    onMoveAlbumToTrash: ((Album) -> Unit)? = null,
    onTogglePinClick: ((Album) -> Unit)? = null,
    onToggleIgnoreClick: ((Album) -> Unit)? = null,
    onToggleLockClick: ((Album) -> Unit)? = null,
    onAddToGroup: ((Album) -> Unit)? = null,
    onMoveToSection: ((Album) -> Unit)? = null,
    onToggleMergeSubfolders: ((Album) -> Unit)? = null,
    onMergeParentSubfolders: ((Album) -> Unit)? = null,
    onToggleMergedSubfolderDisplayMode: ((Album) -> Unit)? = null,
    isMergedSubfolder: Boolean = false,
    isSubGallery: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.4f)
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .clip(Shapes.large)
            .combinedClickable(
                enabled = isEnabled,
                onClick = { onItemClick(album) },
                onLongClick = if (onTogglePinClick != null) {
                    {
                        scope.launch {
                            appBottomSheetState.show()
                        }
                    }
                } else null
            ),
    ) {
        AlbumOptionSheet(
            album = album,
            appBottomSheetState = appBottomSheetState,
            onMoveAlbumToTrash = onMoveAlbumToTrash,
            onTogglePinClick = onTogglePinClick,
            onToggleIgnoreClick = onToggleIgnoreClick,
            onToggleLockClick = onToggleLockClick,
            onAddToGroup = onAddToGroup,
            onMoveToSection = onMoveToSection,
            onToggleMergeSubfolders = onToggleMergeSubfolders,
            onMergeParentSubfolders = onMergeParentSubfolders,
            onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
            isMergedSubfolder = isMergedSubfolder,
            isSubGallery = isSubGallery
        )
        Box(
            modifier = Modifier
                .aspectRatio(1f)
        ) {
            AlbumImage(
                modifier = thumbnailModifier,
                album = album,
                isEnabled = isEnabled,
                onItemClick = onItemClick,
                onItemLongClick = if (onTogglePinClick != null) {
                    {
                        scope.launch {
                            appBottomSheetState.show()
                        }
                    }
                } else null
            )
            if (album.isOnSdcard) {
                Icon(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp)
                        .align(Alignment.BottomEnd),
                    imageVector = Icons.Outlined.SdCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (album.relativePath.startsWith("cloud/")) {
                CloudProviderBadge(
                    album = album,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = album.label,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = pluralStringResource(
                    id = R.plurals.item_count,
                    count = album.count.toInt(),
                    album.count
                ) + if (album.size > 0) " (${formatSize(album.size)})" else "",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AlbumOptionSheet(
    album: Album,
    appBottomSheetState: AppBottomSheetState,
    onMoveAlbumToTrash: ((Album) -> Unit)? = null,
    onTogglePinClick: ((Album) -> Unit)? = null,
    onToggleIgnoreClick: ((Album) -> Unit)? = null,
    onToggleLockClick: ((Album) -> Unit)? = null,
    onDeleteAlbumThumbnailClick: ((Album) -> Unit)? = null,
    onAddToGroup: ((Album) -> Unit)? = null,
    onRemoveFromGroup: ((Album) -> Unit)? = null,
    onMoveToSection: ((Album) -> Unit)? = null,
    onToggleMergeSubfolders: ((Album) -> Unit)? = null,
    onMergeParentSubfolders: ((Album) -> Unit)? = null,
    onToggleMergedSubfolderDisplayMode: ((Album) -> Unit)? = null,
    isMergedSubfolder: Boolean = false,
    isSubGallery: Boolean = false
) {
    val isCloudAlbum = remember(album) { album.relativePath.startsWith("cloud/") }
    val scope = rememberCoroutineScope()
    val trashEnabled by rememberTrashEnabled()
    val useRecoverableTrash = !isCloudAlbum && trashEnabled && SdkCompat.supportsTrash
    val trashTitle = stringResource(
        if (useRecoverableTrash) R.string.move_album_to_trash
        else R.string.delete_album_permanently
    )
    val removeAlbumState = rememberAppBottomSheetState()
    val pinTitle = stringResource(if (album.isPinned) R.string.unpin else R.string.pin)
    val changeThumbnailTitle = stringResource(R.string.change_thumbnail)
    val ignoredTitle = stringResource(id = R.string.add_to_ignored)
    val lockTitle = stringResource(if (album.isLocked) R.string.unlock_album else R.string.lock_album)
    val addToGroupTitle = stringResource(R.string.add_to_group)
    val removeFromGroupTitle = stringResource(R.string.remove_album_from_group)
    val moveToSectionTitle = stringResource(R.string.move_to_section)
    val mergeSubfoldersTitle = stringResource(
        if (isMergedSubfolder) R.string.unmerge_subfolders else R.string.merge_subfolders
    )
    val mergedLayoutTitle = stringResource(
        if (isSubGallery) R.string.show_combined_timeline else R.string.browse_subfolders
    )
    val mergeParentSubfoldersTitle = stringResource(R.string.merge_parent_subfolders)
    var isSelectingThumbnail by rememberSaveable { mutableStateOf(false) }
    val handler = LocalMediaHandler.current
    val hasThumbnail by handler.hasAlbumThumbnail(album.id)
        .collectAsStateWithLifecycle(initialValue = false)

    val pickerLauncher = rememberLauncherForActivityResult(
        PickerActivityContract(
            allowMultiple = false,
            mediaType = "image/*"
        )
    ) { uriList ->
        scope.launch {
            if (uriList.isNotEmpty()) {
                val newThumbnailUri = uriList.map { it.toUri() }.firstOrNull()
                if (newThumbnailUri != null) {
                    handler.updateAlbumThumbnail(album.id, newThumbnailUri)
                    delay(100)
                    appBottomSheetState.hide()
                } else {
                    printError("No thumbnail selected for album: ${album.label}")
                }
            }
        }
    }

    val selectThumbnailTitle = stringResource(R.string.select_new_thumbnail)
    val restoreThumbnailTitle = stringResource(R.string.change_to_default)
    val changeThumbnailOptions = remember(
        hasThumbnail,
        selectThumbnailTitle,
        restoreThumbnailTitle,
    ) {
        listOf(
            OptionItem(
                text = selectThumbnailTitle,
                icon = Icons.Outlined.FileOpen,
                onClick = { pickerLauncher.launch(Unit) }
            ),
            OptionItem(
                text = restoreThumbnailTitle,
                icon = Icons.Outlined.Restore,
                enabled = hasThumbnail,
                onClick = {
                    scope.launch {
                        handler.deleteAlbumThumbnail(album.id)
                        appBottomSheetState.hide()
                    }
                }
            )
        )
    }
    val optionList = remember(
        onMoveAlbumToTrash,
        onTogglePinClick,
        onToggleIgnoreClick,
        onToggleLockClick,
        onAddToGroup,
        onRemoveFromGroup,
        onMoveToSection,
        onToggleMergeSubfolders,
        onMergeParentSubfolders,
        onToggleMergedSubfolderDisplayMode,
        album.isPinned,
        album.isLocked,
        isCloudAlbum,
        isMergedSubfolder,
        mergeSubfoldersTitle,
        mergedLayoutTitle,
        mergeParentSubfoldersTitle,
        trashTitle
    ) {
        mutableListOf<OptionItem>().apply {
            add(
                OptionItem(
                    icon = Icons.Outlined.Delete,
                    text = trashTitle,
                    enabled = onMoveAlbumToTrash != null && !isCloudAlbum,
                    onClick = {
                        scope.launch {
                            appBottomSheetState.hide()
                            removeAlbumState.show()
                        }
                    }
                )
            )
            if (!isCloudAlbum) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.PushPin,
                        text = pinTitle,
                        enabled = onTogglePinClick != null,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onTogglePinClick?.invoke(album)
                            }
                        }
                    )
                )
                add(
                    OptionItem(
                        icon = Icons.Outlined.Wallpaper,
                        text = changeThumbnailTitle,
                        onClick = { isSelectingThumbnail = true }
                    )
                )
            }
            if (onToggleLockClick != null && !isCloudAlbum) {
                add(
                    OptionItem(
                        icon = if (album.isLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                        text = lockTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onToggleLockClick(album)
                            }
                        }
                    )
                )
            }
            if (onToggleIgnoreClick != null && !AlbumMergeResolver.isVirtualAlbumId(album.id)) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.VisibilityOff,
                        text = ignoredTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onToggleIgnoreClick(album)
                            }
                        }
                    )
                )
            }
            if (onAddToGroup != null && !isCloudAlbum) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.CreateNewFolder,
                        text = addToGroupTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onAddToGroup(album)
                            }
                        }
                    )
                )
            }
            if (onMoveToSection != null && !isCloudAlbum) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.Folder,
                        text = moveToSectionTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onMoveToSection(album)
                            }
                        }
                    )
                )
            }
            if (onRemoveFromGroup != null && !isCloudAlbum) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.RemoveCircleOutline,
                        text = removeFromGroupTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onRemoveFromGroup(album)
                            }
                        }
                    )
                )
            }
            if (onToggleMergeSubfolders != null && !isCloudAlbum) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.AccountTree,
                        text = mergeSubfoldersTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onToggleMergeSubfolders(album)
                            }
                        }
                    )
                )
            }
            if (
                !isMergedSubfolder &&
                album.relativePath.trim('/').contains('/') &&
                onMergeParentSubfolders != null
            ) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.AccountTree,
                        text = mergeParentSubfoldersTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onMergeParentSubfolders(album)
                            }
                        }
                    )
                )
            }
            if (isMergedSubfolder && onToggleMergedSubfolderDisplayMode != null) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.Folder,
                        text = mergedLayoutTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onToggleMergedSubfolderDisplayMode(album)
                            }
                        }
                    )
                )
            }
        }
    }
    val deleteThumbnailTitle = stringResource(R.string.delete_thumbnail)
    LaunchedEffect(onDeleteAlbumThumbnailClick) {
        if (onDeleteAlbumThumbnailClick != null) {
            optionList.add(
                OptionItem(
                    text = deleteThumbnailTitle,
                    onClick = {
                        scope.launch {
                            appBottomSheetState.hide()
                            onDeleteAlbumThumbnailClick(album)
                        }
                    }
                )
            )
        }
    }

    val options = remember(isSelectingThumbnail, optionList) {
        (if (isSelectingThumbnail) {
            changeThumbnailOptions
        } else {
            optionList
        }).toMutableStateList()
    }

    OptionSheet(
        state = appBottomSheetState,
        optionList = arrayOf(options),
        style = OptionLayoutStyle.Grid,
        headerContent = {
            BackHandler(isSelectingThumbnail) {
                isSelectingThumbnail = false
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (albumThumbnailPresentation(album.isLocked)) {
                    AlbumThumbnailPresentation.LOCKED_PLACEHOLDER -> LockedAlbumThumbnail(
                        modifier = Modifier.size(98.dp),
                        iconModifier = Modifier.size(36.dp)
                    )
                    AlbumThumbnailPresentation.MEDIA -> {
                        val renderer = LocalMediaImageRenderer.current
                        renderer.RenderImage(
                            modifier = Modifier
                                .size(98.dp)
                                .clip(Shapes.large),
                            contentScale = ContentScale.Crop,
                            model = album.uri,
                            contentDescription = album.label,
                            signature = album
                        )
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                letterSpacing = MaterialTheme.typography.titleLarge.letterSpacing
                            )
                        ) {
                            append(album.label)
                        }
                        append("\n")
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing
                            )
                        ) {
                            append(
                                stringResource(
                                    R.string.s_items,
                                    album.count
                                ) + " (${formatSize(album.size)})"
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }
        }
    )
    ConfirmationSheet(
        state = removeAlbumState,
        title = stringResource(
            if (useRecoverableTrash) R.string.album_trash_confirm_title
            else R.string.album_delete_confirm_title
        ),
        summary = stringResource(
            if (useRecoverableTrash) R.string.album_trash_confirm_summary
            else R.string.album_delete_confirm_summary
        ),
        confirmText = stringResource(
            if (useRecoverableTrash) R.string.action_move_to_trash
            else R.string.action_delete_permanently
        ),
        onConfirm = { onMoveAlbumToTrash?.invoke(album) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumImage(
    modifier: Modifier = Modifier,
    album: Album,
    isEnabled: Boolean,
    onItemClick: (Album) -> Unit,
    onItemLongClick: ((Album) -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed.value) 32.dp else 16.dp,
        label = "cornerRadius"
    )
    val feedbackManager = rememberFeedbackManager()
    if (albumThumbnailPresentation(album.isLocked) == AlbumThumbnailPresentation.LOCKED_PLACEHOLDER) {
        LockedAlbumThumbnail(
            modifier = modifier.fillMaxSize(),
            iconModifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            shape = RoundedCornerShape(cornerRadius),
            showBorder = true,
            contentModifier = Modifier.combinedClickable(
                enabled = isEnabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onItemClick(album) },
                onLongClick = {
                    onItemLongClick?.let {
                        feedbackManager.vibrate()
                        it(album)
                    }
                }
            )
        )
    } else if (album.id == -200L && album.count == 0L) {
        Icon(
            imageVector = Icons.Outlined.AddCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .alpha(0.8f)
                .clip(RoundedCornerShape(cornerRadius))
                .combinedClickable(
                    enabled = isEnabled,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { onItemClick(album) },
                    onLongClick = {
                        onItemLongClick?.let {
                            feedbackManager.vibrate()
                            it(album)
                        }
                    }
                )
                .padding(48.dp)
        )
    } else {
        val renderer = LocalMediaImageRenderer.current
        renderer.RenderImage(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .combinedClickable(
                    enabled = isEnabled,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { onItemClick(album) },
                    onLongClick = {
                        onItemLongClick?.let {
                            feedbackManager.vibrate()
                            it(album)
                        }
                    }
                ),
            model = album.uri,
            contentDescription = album.label,
            contentScale = ContentScale.Crop,
            signature = album
        )
    }
}

@Composable
internal fun LockedAlbumThumbnail(
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier.size(36.dp),
    shape: Shape = Shapes.large,
    showBorder: Boolean = false,
    contentModifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .then(contentModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = stringResource(R.string.locked),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = iconModifier
        )
    }
}