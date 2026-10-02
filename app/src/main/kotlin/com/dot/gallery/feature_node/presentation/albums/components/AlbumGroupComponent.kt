/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albums.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionSheet
import com.dot.gallery.feature_node.presentation.util.formatSize
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun AlbumGroupComponent(
    modifier: Modifier = Modifier,
    groupWithAlbums: AlbumGroupWithAlbums,
    onGroupClick: (AlbumGroupWithAlbums) -> Unit,
    onRenameGroup: ((AlbumGroupWithAlbums) -> Unit)? = null,
    onDeleteGroup: ((AlbumGroupWithAlbums) -> Unit)? = null,
    onEditGroup: ((AlbumGroupWithAlbums) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    val feedbackManager = rememberFeedbackManager()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState()
    val cornerRadius = if (isPressed.value) 32.dp else 16.dp

    val renameTitle = stringResource(R.string.rename_group)
    val deleteTitle = stringResource(R.string.delete_group)
    val editTitle = stringResource(R.string.edit_group)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer

    val optionList = remember(onRenameGroup, onDeleteGroup, onEditGroup) {
        mutableListOf<OptionItem>().apply {
            if (onEditGroup != null) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.Collections,
                        text = editTitle,
                        containerColor = secondaryContainer,
                        contentColor = onSecondaryContainer,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onEditGroup(groupWithAlbums)
                            }
                        }
                    )
                )
            }
            if (onRenameGroup != null) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.Edit,
                        text = renameTitle,
                        containerColor = secondaryContainer,
                        contentColor = onSecondaryContainer,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onRenameGroup(groupWithAlbums)
                            }
                        }
                    )
                )
            }
            if (onDeleteGroup != null) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = deleteTitle,
                        containerColor = primaryContainer,
                        contentColor = onPrimaryContainer,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onDeleteGroup(groupWithAlbums)
                            }
                        }
                    )
                )
            }
        }.toMutableStateList()
    }

    OptionSheet(
        state = appBottomSheetState,
        optionList = arrayOf(optionList),
        headerContent = {
            val albums = groupWithAlbums.albums
            if (albums.isNotEmpty()) {
                val thumbRadius = 12.dp
                val gap = 2.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(thumbRadius))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            GroupThumbnailCell(
                                modifier = Modifier.weight(1f),
                                album = albums.getOrNull(0),
                                cornerShape = RoundedCornerShape(
                                    topStart = thumbRadius, topEnd = 2.dp,
                                    bottomStart = 2.dp, bottomEnd = 2.dp
                                )
                            )
                            GroupThumbnailCell(
                                modifier = Modifier.weight(1f),
                                album = albums.getOrNull(1),
                                cornerShape = RoundedCornerShape(
                                    topStart = 2.dp, topEnd = thumbRadius,
                                    bottomStart = 2.dp, bottomEnd = 2.dp
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            GroupThumbnailCell(
                                modifier = Modifier.weight(1f),
                                album = albums.getOrNull(2),
                                cornerShape = RoundedCornerShape(
                                    topStart = 2.dp, topEnd = 2.dp,
                                    bottomStart = thumbRadius, bottomEnd = 2.dp
                                )
                            )
                            GroupThumbnailCell(
                                modifier = Modifier.weight(1f),
                                album = albums.getOrNull(3),
                                cornerShape = RoundedCornerShape(
                                    topStart = 2.dp, topEnd = 2.dp,
                                    bottomStart = 2.dp, bottomEnd = thumbRadius
                                )
                            )
                        }
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Outlined.Collections,
                    contentDescription = null,
                    modifier = Modifier.height(64.dp).padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = groupWithAlbums.group.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = stringResource(R.string.n_albums, groupWithAlbums.albums.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    )

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(cornerRadius))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { onGroupClick(groupWithAlbums) },
                    onLongClick = {
                        if (onRenameGroup != null || onDeleteGroup != null) {
                            feedbackManager.vibrate()
                            scope.launch { appBottomSheetState.show() }
                        }
                    }
                )
        ) {
            val albums = groupWithAlbums.albums
            if (albums.isEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Collections,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.4f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                // 2x2 grid of album thumbnails
                val innerCornerRadius = (cornerRadius.value * 0.5f).dp
                val gap = 2.dp
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        GroupThumbnailCell(
                            modifier = Modifier.weight(1f),
                            album = albums.getOrNull(0),
                            cornerShape = RoundedCornerShape(
                                topStart = cornerRadius,
                                topEnd = innerCornerRadius,
                                bottomStart = innerCornerRadius,
                                bottomEnd = innerCornerRadius
                            )
                        )
                        GroupThumbnailCell(
                            modifier = Modifier.weight(1f),
                            album = albums.getOrNull(1),
                            cornerShape = RoundedCornerShape(
                                topStart = innerCornerRadius,
                                topEnd = cornerRadius,
                                bottomStart = innerCornerRadius,
                                bottomEnd = innerCornerRadius
                            )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        GroupThumbnailCell(
                            modifier = Modifier.weight(1f),
                            album = albums.getOrNull(2),
                            cornerShape = RoundedCornerShape(
                                topStart = innerCornerRadius,
                                topEnd = innerCornerRadius,
                                bottomStart = cornerRadius,
                                bottomEnd = innerCornerRadius
                            )
                        )
                        GroupThumbnailCell(
                            modifier = Modifier.weight(1f),
                            album = albums.getOrNull(3),
                            cornerShape = RoundedCornerShape(
                                topStart = innerCornerRadius,
                                topEnd = innerCornerRadius,
                                bottomStart = innerCornerRadius,
                                bottomEnd = cornerRadius
                            )
                        )
                    }
                }
            }
        }

        Text(
            modifier = Modifier
                .padding(top = 12.dp)
                .padding(horizontal = 16.dp),
            text = groupWithAlbums.group.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
        Text(
            modifier = Modifier
                .padding(top = 2.dp, bottom = 16.dp)
                .padding(horizontal = 16.dp),
            text = stringResource(
                R.string.n_albums,
                groupWithAlbums.albums.size
            ) + " \u2022 " + formatSize(groupWithAlbums.totalSize),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun AlbumGroupRowComponent(
    modifier: Modifier = Modifier,
    groupWithAlbums: AlbumGroupWithAlbums,
    onGroupClick: (AlbumGroupWithAlbums) -> Unit,
    onRenameGroup: ((AlbumGroupWithAlbums) -> Unit)? = null,
    onDeleteGroup: ((AlbumGroupWithAlbums) -> Unit)? = null,
    onEditGroup: ((AlbumGroupWithAlbums) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    val feedbackManager = rememberFeedbackManager()
    val interactionSource = remember { MutableInteractionSource() }

    val renameTitle = stringResource(R.string.rename_group)
    val deleteTitle = stringResource(R.string.delete_group)
    val editTitle = stringResource(R.string.edit_group)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer

    val optionList = remember(onRenameGroup, onDeleteGroup, onEditGroup) {
        mutableListOf<OptionItem>().apply {
            if (onEditGroup != null) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.Collections,
                        text = editTitle,
                        containerColor = secondaryContainer,
                        contentColor = onSecondaryContainer,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onEditGroup(groupWithAlbums)
                            }
                        }
                    )
                )
            }
            if (onRenameGroup != null) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.Edit,
                        text = renameTitle,
                        containerColor = secondaryContainer,
                        contentColor = onSecondaryContainer,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onRenameGroup(groupWithAlbums)
                            }
                        }
                    )
                )
            }
            if (onDeleteGroup != null) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = deleteTitle,
                        containerColor = primaryContainer,
                        contentColor = onPrimaryContainer,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onDeleteGroup(groupWithAlbums)
                            }
                        }
                    )
                )
            }
        }.toMutableStateList()
    }

    OptionSheet(
        state = appBottomSheetState,
        optionList = arrayOf(optionList),
        headerContent = {
            Text(
                text = groupWithAlbums.group.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = stringResource(R.string.n_albums, groupWithAlbums.albums.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onGroupClick(groupWithAlbums) },
                onLongClick = {
                    if (onRenameGroup != null || onDeleteGroup != null) {
                        feedbackManager.vibrate()
                        scope.launch { appBottomSheetState.show() }
                    }
                }
            ),
    ) {
        val albums = groupWithAlbums.albums
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (albums.isEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Collections,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.5f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                val gap = 1.dp
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        GroupThumbnailCell(
                            modifier = Modifier.weight(1f),
                            album = albums.getOrNull(0),
                            cornerShape = RoundedCornerShape(topStart = 12.dp)
                        )
                        GroupThumbnailCell(
                            modifier = Modifier.weight(1f),
                            album = albums.getOrNull(1),
                            cornerShape = RoundedCornerShape(topEnd = 12.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        GroupThumbnailCell(
                            modifier = Modifier.weight(1f),
                            album = albums.getOrNull(2),
                            cornerShape = RoundedCornerShape(bottomStart = 12.dp)
                        )
                        GroupThumbnailCell(
                            modifier = Modifier.weight(1f),
                            album = albums.getOrNull(3),
                            cornerShape = RoundedCornerShape(bottomEnd = 12.dp)
                        )
                    }
                }
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
                text = groupWithAlbums.group.label,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.n_albums,
                    groupWithAlbums.albums.size
                ) + " \u2022 " + formatSize(groupWithAlbums.totalSize),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun GroupThumbnailCell(
    modifier: Modifier = Modifier,
    album: com.dot.gallery.feature_node.domain.model.Album?,
    cornerShape: RoundedCornerShape
) {
    if (album != null) {
        GlideImage(
            modifier = modifier
                .fillMaxSize()
                .clip(cornerShape),
            model = album.uri,
            contentDescription = album.label,
            contentScale = ContentScale.Crop,
            requestBuilderTransform = {
                it.centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .thumbnail(it.clone().sizeMultiplier(0.4f))
            }
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(cornerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
