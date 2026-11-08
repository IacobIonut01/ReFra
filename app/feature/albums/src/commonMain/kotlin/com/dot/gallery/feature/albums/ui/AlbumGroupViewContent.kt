/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryDetailAction
import com.dot.gallery.core.presentation.components.GalleryDetailCover
import com.dot.gallery.core.presentation.components.GalleryDetailHeader
import com.dot.gallery.core.presentation.components.GalleryDetailTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.galleryDetailTopBarHeight
import com.dot.gallery.core.presentation.components.rememberDetailHeaderCollapsed
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.mediaSharedElement
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.detail_album_group
import com.dot.gallery.feature.albums.generated.resources.detail_rename
import com.dot.gallery.feature.albums.generated.resources.group_add_albums
import com.dot.gallery.feature.albums.generated.resources.group_album_count
import com.dot.gallery.feature.albums.generated.resources.group_members_header
import com.dot.gallery.feature.albums.generated.resources.no_groups
import com.dot.gallery.feature.albums.generated.resources.s_items
import com.dot.gallery.feature.albums.ui.components.AlbumOptionSheet
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An album group: the detail header (cover, name, album and item counts, Rename and Add albums),
 * then its member albums as rows. A long press on a row opens the album's option sheet. The app
 * keeps the MediaStore delete/trash launcher seam via [onMoveAlbumToTrash].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumGroupViewContent(
    groupId: Long,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onMoveAlbumToTrash: (Album) -> Unit,
    onIgnoreAlbum: (Album) -> Unit,
    onRemoveFromGroup: (Album) -> Unit,
    onRenameGroup: (AlbumGroupWithAlbums) -> Unit,
    onAddAlbums: ((groupId: Long) -> Unit)? = null,
    supportsTrash: Boolean = true,
    thumbnailActions: AlbumThumbnailActions? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val distributor = LocalMediaDistributor.current
    val albumsState by distributor.albumsFlow.collectAsState()

    val group = albumsState.albumGroups.find { it.group.id == groupId }
    val groupAlbums = group?.albums ?: emptyList()
    val title = group?.group?.label.orEmpty()

    val hazeState = LocalHazeState.current
    val listState = rememberLazyListState()
    val collapsed by rememberDetailHeaderCollapsed(listState)
    val albumCount = pluralStringResource(Res.plurals.group_album_count, groupAlbums.size, groupAlbums.size)
    val itemCount = stringResource(Res.string.s_items, (group?.totalCount ?: 0L).toString())
    val groupLabel = stringResource(Res.string.detail_album_group)
    val renameLabel = stringResource(Res.string.detail_rename)
    val addLabel = stringResource(Res.string.group_add_albums)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(top = galleryDetailTopBarHeight(), bottom = 16.dp + 64.dp),
        ) {
            item(key = "header") {
                GalleryDetailHeader(
                    title = title,
                    stats = listOf(groupLabel, albumCount, itemCount).joinToString(" · "),
                    cover = {
                        val coverAlbum = groupAlbums.firstOrNull()
                        GalleryDetailCover(
                            model = coverAlbum?.uri?.toString(),
                            signature = coverAlbum,
                            fallbackIcon = Icons.Outlined.PhotoAlbum,
                        )
                    },
                    actions = if (group != null) {
                        {
                            GalleryDetailAction(
                                label = renameLabel,
                                icon = Icons.Outlined.DriveFileRenameOutline,
                                onClick = { onRenameGroup(group) },
                            )
                            if (onAddAlbums != null) {
                                GalleryDetailAction(
                                    label = addLabel,
                                    icon = Icons.Outlined.Add,
                                    onClick = { onAddAlbums(groupId) },
                                )
                            }
                        }
                    } else null,
                )
            }
            item(key = "membersHeader") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SettingsItem(item = SettingsEntity.Header(title = stringResource(Res.string.group_members_header)))
                }
            }
            itemsIndexed(items = groupAlbums, key = { _, album -> album.key }) { index, album ->
                GroupMemberRow(
                    album = album,
                    thumbnailModifier = with(sharedTransitionScope) {
                        Modifier.mediaSharedElement(album = album, animatedVisibilityScope = animatedContentScope)
                    },
                    position = when {
                        groupAlbums.size == 1 -> Position.Alone
                        index == 0 -> Position.Top
                        index == groupAlbums.lastIndex -> Position.Bottom
                        else -> Position.Middle
                    },
                    onClick = { onAlbumClick(album) },
                    onTogglePinClick = onAlbumLongClick,
                    onMoveAlbumToTrash = onMoveAlbumToTrash,
                    onIgnoreAlbum = onIgnoreAlbum,
                    onRemoveFromGroup = onRemoveFromGroup,
                    supportsTrash = supportsTrash,
                    thumbnailActions = thumbnailActions,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp),
                )
            }
            if (groupAlbums.isEmpty()) {
                item(key = "emptyGroup") {
                    Text(
                        text = stringResource(Res.string.no_groups),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
        GalleryDetailTopBar(
            title = title,
            count = albumCount,
            collapsed = collapsed,
            hazeState = hazeState,
            collapsedActions = buildList {
                if (group != null) {
                    add {
                        GalleryPageTopBarAction(
                            icon = Icons.Outlined.DriveFileRenameOutline,
                            contentDescription = renameLabel,
                            onClick = { onRenameGroup(group) },
                        )
                    }
                    if (onAddAlbums != null) {
                        add {
                            GalleryPageTopBarAction(
                                icon = Icons.Outlined.Add,
                                contentDescription = addLabel,
                                onClick = { onAddAlbums(groupId) },
                            )
                        }
                    }
                }
            },
        )
    }
}

/** A member album: its thumbnail, name, item count and newest month; long press for options. */
@Composable
private fun GroupMemberRow(
    album: Album,
    thumbnailModifier: Modifier,
    position: Position,
    onClick: () -> Unit,
    onTogglePinClick: (Album) -> Unit,
    onMoveAlbumToTrash: (Album) -> Unit,
    onIgnoreAlbum: (Album) -> Unit,
    onRemoveFromGroup: (Album) -> Unit,
    supportsTrash: Boolean,
    thumbnailActions: AlbumThumbnailActions?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val optionsSheetState = rememberAppBottomSheetState()
    AlbumOptionSheet(
        album = album,
        appBottomSheetState = optionsSheetState,
        onMoveAlbumToTrash = onMoveAlbumToTrash,
        onTogglePinClick = onTogglePinClick,
        onToggleIgnoreClick = onIgnoreAlbum,
        onRemoveFromGroup = onRemoveFromGroup,
        supportsTrash = supportsTrash,
        thumbnailActions = thumbnailActions,
    )
    val items = stringResource(Res.string.s_items, album.count.toString())
    val newest = albumMonthLabel(album.timestamp)
    SettingsItem(
        modifier = modifier.fillMaxWidth(),
        item = SettingsEntity.Preference(
            icon = Icons.Outlined.PhotoAlbum,
            title = album.label,
            summary = listOfNotNull(items, newest).joinToString(" · "),
            screenPosition = position,
            onClick = onClick,
            onLongClick = { scope.launch { optionsSheetState.show() } },
        ),
        customIcon = { _, _, _ ->
            LocalMediaImageRenderer.current.RenderImage(
                modifier = thumbnailModifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp)),
                model = album.uri.toString(),
                contentScale = ContentScale.Crop,
                contentDescription = null,
                signature = album,
            )
        },
        customTrailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** "Jul 2026" for an album's newest timestamp, in seconds or milliseconds; null when unset. */
private fun albumMonthLabel(timestamp: Long): String? {
    if (timestamp <= 0) return null
    val seconds = if (timestamp > 100_000_000_000L) timestamp / 1000 else timestamp
    return seconds.formatEpochSec("MMM yyyy")
}
