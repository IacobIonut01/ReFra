/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.SheetSummaryTile
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionLayoutStyle
import com.dot.gallery.core.presentation.components.OptionSheet
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.albums.ui.AlbumThumbnailActions
import com.dot.gallery.feature.albums.ui.util.formatSize
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.action_delete_permanently
import com.dot.gallery.feature.albums.generated.resources.action_move_to_trash
import com.dot.gallery.feature.albums.generated.resources.add_to_group
import com.dot.gallery.feature.albums.generated.resources.add_to_ignored
import com.dot.gallery.feature.albums.generated.resources.album_delete_confirm_summary
import com.dot.gallery.feature.albums.generated.resources.album_delete_confirm_title
import com.dot.gallery.feature.albums.generated.resources.album_shared_badge
import com.dot.gallery.feature.albums.generated.resources.album_trash_confirm_summary
import com.dot.gallery.feature.albums.generated.resources.album_trash_confirm_title
import com.dot.gallery.feature.albums.generated.resources.browse_subfolders
import com.dot.gallery.feature.albums.generated.resources.change_thumbnail
import com.dot.gallery.feature.albums.generated.resources.change_to_default
import com.dot.gallery.feature.albums.generated.resources.delete_album_permanently
import com.dot.gallery.feature.albums.generated.resources.delete_thumbnail
import com.dot.gallery.feature.albums.generated.resources.item_count
import com.dot.gallery.feature.albums.generated.resources.lock_album
import com.dot.gallery.feature.albums.generated.resources.locked
import com.dot.gallery.feature.albums.generated.resources.merge_parent_subfolders
import com.dot.gallery.feature.albums.generated.resources.merge_subfolders
import com.dot.gallery.feature.albums.generated.resources.move_album_to_trash
import com.dot.gallery.feature.albums.generated.resources.move_to_section
import com.dot.gallery.feature.albums.generated.resources.pin
import com.dot.gallery.feature.albums.generated.resources.remove_album_from_group
import com.dot.gallery.feature.albums.generated.resources.s_items
import com.dot.gallery.feature.albums.generated.resources.select_new_thumbnail
import com.dot.gallery.feature.albums.generated.resources.show_combined_timeline
import com.dot.gallery.feature.albums.generated.resources.unlock_album
import com.dot.gallery.feature.albums.generated.resources.unmerge_subfolders
import com.dot.gallery.feature.albums.generated.resources.unpin

/**
 * Renders a provider brand icon for a cloud album. App passes its descriptor
 * system (painter- or vector-based); other platforms fall back to a plain
 * cloud glyph in [CloudProviderBadge]/[CloudAlbumSectionHeader].
 */
typealias ProviderIconSlot = @Composable (providerType: ProviderType, tint: Color, modifier: Modifier) -> Unit

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
    isSubGallery: Boolean = false,
    supportsTrash: Boolean = true,
    thumbnailActions: AlbumThumbnailActions? = null,
    providerIcon: ProviderIconSlot? = null,
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
            isSubGallery = isSubGallery,
            supportsTrash = supportsTrash,
            thumbnailActions = thumbnailActions,
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
                    providerIcon = providerIcon,
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
                    Res.plurals.item_count,
                    album.count.toInt(),
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
private fun CloudProviderBadge(
    album: Album,
    providerIcon: ProviderIconSlot?,
    modifier: Modifier = Modifier,
) {
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
            if (providerType != null && providerIcon != null) {
                providerIcon(providerType, Color.White, Modifier.size(16.dp))
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
                    contentDescription = stringResource(Res.string.album_shared_badge),
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
    isSubGallery: Boolean = false,
    supportsTrash: Boolean = true,
    thumbnailActions: AlbumThumbnailActions? = null,
    providerIcon: ProviderIconSlot? = null,
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
            isSubGallery = isSubGallery,
            supportsTrash = supportsTrash,
            thumbnailActions = thumbnailActions,
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
                    providerIcon = providerIcon,
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
                    Res.plurals.item_count,
                    album.count.toInt(),
                    album.count
                ) + if (album.size > 0) " (${formatSize(album.size)})" else "",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
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
    isSubGallery: Boolean = false,
    supportsTrash: Boolean = true,
    thumbnailActions: AlbumThumbnailActions? = null,
) {
    val isCloudAlbum = remember(album) { album.relativePath.startsWith("cloud/") }
    val scope = rememberCoroutineScope()
    val trashEnabled by rememberPreference(SettingsKeys.trashEnabled)
    val useRecoverableTrash = !isCloudAlbum && trashEnabled && supportsTrash
    val trashTitle = stringResource(
        if (useRecoverableTrash) Res.string.move_album_to_trash
        else Res.string.delete_album_permanently
    )
    val removeAlbumState = rememberAppBottomSheetState()
    val pinTitle = stringResource(if (album.isPinned) Res.string.unpin else Res.string.pin)
    val changeThumbnailTitle = stringResource(Res.string.change_thumbnail)
    val ignoredTitle = stringResource(Res.string.add_to_ignored)
    val lockTitle = stringResource(if (album.isLocked) Res.string.unlock_album else Res.string.lock_album)
    val addToGroupTitle = stringResource(Res.string.add_to_group)
    val removeFromGroupTitle = stringResource(Res.string.remove_album_from_group)
    val moveToSectionTitle = stringResource(Res.string.move_to_section)
    val mergeSubfoldersTitle = stringResource(
        if (isMergedSubfolder) Res.string.unmerge_subfolders else Res.string.merge_subfolders
    )
    val mergedLayoutTitle = stringResource(
        if (isSubGallery) Res.string.show_combined_timeline else Res.string.browse_subfolders
    )
    val mergeParentSubfoldersTitle = stringResource(Res.string.merge_parent_subfolders)
    var isSelectingThumbnail by rememberSaveable { mutableStateOf(false) }
    val hasThumbnail by (thumbnailActions?.hasThumbnail?.invoke(album.id)
        ?: flowOf(false))
        .collectAsState(initial = false)

    val selectThumbnailTitle = stringResource(Res.string.select_new_thumbnail)
    val restoreThumbnailTitle = stringResource(Res.string.change_to_default)
    val changeThumbnailOptions = remember(
        hasThumbnail,
        selectThumbnailTitle,
        restoreThumbnailTitle,
        thumbnailActions,
    ) {
        mutableListOf<OptionItem>().apply {
            if (thumbnailActions != null) {
                add(
                    OptionItem(
                        text = selectThumbnailTitle,
                        icon = Icons.Outlined.FileOpen,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                thumbnailActions.onPickThumbnail(album)
                            }
                        }
                    )
                )
                add(
                    OptionItem(
                        text = restoreThumbnailTitle,
                        icon = Icons.Outlined.Restore,
                        enabled = hasThumbnail,
                        onClick = {
                            scope.launch {
                                thumbnailActions.onDeleteThumbnail(album.id)
                                appBottomSheetState.hide()
                            }
                        }
                    )
                )
            }
        }
    }
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    val optionList = remember(
        errorContainer,
        onErrorContainer,
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
        isSubGallery,
        thumbnailActions,
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
                    containerColor = errorContainer,
                    contentColor = onErrorContainer,
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
                if (thumbnailActions != null) {
                    add(
                        OptionItem(
                            icon = Icons.Outlined.Wallpaper,
                            text = changeThumbnailTitle,
                            onClick = { isSelectingThumbnail = true }
                        )
                    )
                }
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
            if (onToggleIgnoreClick != null && !MergedSubfolderAlbum.isVirtualAlbumId(album.id)) {
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
    val deleteThumbnailTitle = stringResource(Res.string.delete_thumbnail)
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

    val albumSummary = stringResource(Res.string.s_items, album.count) +
        if (album.size > 0) " · ${formatSize(album.size)}" else ""
    OptionSheet(
        state = appBottomSheetState,
        optionList = arrayOf(options),
        style = OptionLayoutStyle.Grid,
        headerContent = {
            BackHandler(isSelectingThumbnail) {
                isSelectingThumbnail = false
            }
            AlbumOptionSheetHeader(
                album = album,
                summary = albumSummary,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
            )
        }
    )
    ConfirmationSheet(
        icon = Icons.Outlined.Delete,
        tone = SheetTone.Error,
        confirmIcon = Icons.Outlined.Delete,
        details = {
            SheetSummaryTile(
                title = album.label,
                summary = albumSummary,
                leading = { AlbumOptionThumbnail(album = album, size = 48.dp) },
            )
        },
        state = removeAlbumState,
        title = stringResource(
            if (useRecoverableTrash) Res.string.album_trash_confirm_title
            else Res.string.album_delete_confirm_title
        ),
        summary = stringResource(
            if (useRecoverableTrash) Res.string.album_trash_confirm_summary
            else Res.string.album_delete_confirm_summary
        ),
        confirmText = stringResource(
            if (useRecoverableTrash) Res.string.action_move_to_trash
            else Res.string.action_delete_permanently
        ),
        onConfirm = { onMoveAlbumToTrash?.invoke(album) }
    )
}

/** Cover, name and item count at the top of [AlbumOptionSheet]. */
@Composable
fun AlbumOptionSheetHeader(
    album: Album,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AlbumOptionThumbnail(album = album, size = 72.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = album.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Rounded album cover used by the option sheet header and its confirmation tile. */
@Composable
fun AlbumOptionThumbnail(album: Album, size: Dp) {
    val shape = RoundedCornerShape(if (size >= 64.dp) 20.dp else 12.dp)
    when (albumThumbnailPresentation(album.isLocked)) {
        AlbumThumbnailPresentation.LOCKED_PLACEHOLDER -> LockedAlbumThumbnail(
            modifier = Modifier.size(size),
            iconModifier = Modifier.size(size / 3),
            shape = shape,
        )
        AlbumThumbnailPresentation.MEDIA -> LocalMediaImageRenderer.current.RenderImage(
            modifier = Modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
            model = album.uri,
            contentDescription = album.label,
            signature = album
        )
    }
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
fun LockedAlbumThumbnail(
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
            contentDescription = stringResource(Res.string.locked),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = iconModifier
        )
    }
}
