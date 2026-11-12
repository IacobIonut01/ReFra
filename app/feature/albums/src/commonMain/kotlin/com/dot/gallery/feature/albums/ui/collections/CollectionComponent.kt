/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature.albums.ui.collections

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.DragIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionSheet
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.feature.albums.ui.util.formatSize
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.delete_collection
import com.dot.gallery.feature.albums.generated.resources.n_items_in_collection
import com.dot.gallery.feature.albums.generated.resources.pin_collection
import com.dot.gallery.feature.albums.generated.resources.rearrange
import com.dot.gallery.feature.albums.generated.resources.rename_collection
import com.dot.gallery.feature.albums.generated.resources.select_albums_for_collection
import com.dot.gallery.feature.albums.generated.resources.unpin_collection

/**
 * Maps a collection's `thumbnailMediaId` to an image-loadable model. On Android
 * the app resolves it to the MediaStore files content URI; other platforms (or
 * previews) can keep the default which renders the placeholder icon.
 */
typealias CollectionThumbnailModel = (thumbnailMediaId: Long) -> Any?

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionComponent(
    modifier: Modifier = Modifier,
    collectionWithCount: CollectionWithCount,
    onItemClick: (CollectionWithCount) -> Unit,
    onRename: ((CollectionWithCount) -> Unit)? = null,
    onDelete: ((CollectionWithCount) -> Unit)? = null,
    onTogglePin: ((CollectionWithCount) -> Unit)? = null,
    onEditAlbums: ((CollectionWithCount) -> Unit)? = null,
    /** Opens the album arrange screen; null while the albums cannot be arranged. */
    onRearrange: (() -> Unit)? = null,
    collectionThumbnailModel: CollectionThumbnailModel = { null },
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    val collection = collectionWithCount.collection
    val feedbackManager = rememberFeedbackManager()

    val renameTitle = stringResource(Res.string.rename_collection)
    val deleteTitle = stringResource(Res.string.delete_collection)
    val addAlbumsTitle = stringResource(Res.string.select_albums_for_collection)
    val rearrangeTitle = stringResource(Res.string.rearrange)
    val pinTitle = stringResource(
        if (collection.isPinned) Res.string.unpin_collection else Res.string.pin_collection
    )
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val optionList = remember(collection.isPinned, onRename, onDelete, onTogglePin, onEditAlbums, onRearrange) {
        mutableListOf(
            OptionItem(
                icon = Icons.Outlined.LibraryAdd,
                text = addAlbumsTitle,
                containerColor = secondaryContainer,
                contentColor = onSecondaryContainer,
                enabled = onEditAlbums != null,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        onEditAlbums?.invoke(collectionWithCount)
                    }
                }
            ),
            OptionItem(
                icon = Icons.Outlined.PushPin,
                text = pinTitle,
                containerColor = secondaryContainer,
                contentColor = onSecondaryContainer,
                enabled = onTogglePin != null,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        onTogglePin?.invoke(collectionWithCount)
                    }
                }
            ),
            OptionItem(
                icon = Icons.Outlined.Edit,
                text = renameTitle,
                containerColor = tertiaryContainer,
                contentColor = onTertiaryContainer,
                enabled = onRename != null,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        onRename?.invoke(collectionWithCount)
                    }
                }
            ),
            OptionItem(
                icon = Icons.Outlined.Delete,
                text = deleteTitle,
                containerColor = primaryContainer,
                contentColor = onPrimaryContainer,
                enabled = onDelete != null,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        onDelete?.invoke(collectionWithCount)
                    }
                }
            )
        ).apply {
            if (onRearrange != null) add(
                OptionItem(
                    icon = Icons.Rounded.DragIndicator,
                    text = rearrangeTitle,
                    onClick = {
                        scope.launch {
                            appBottomSheetState.hide()
                            onRearrange()
                        }
                    }
                )
            )
        }.toMutableStateList()
    }

    OptionSheet(
        state = appBottomSheetState,
        optionList = arrayOf(optionList),
        headerContent = {
            Text(
                text = collection.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
    )

    Column(
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier.aspectRatio(1f)
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed = interactionSource.collectIsPressedAsState()
            val cornerRadius by animateDpAsState(
                targetValue = if (isPressed.value) 32.dp else 16.dp,
                label = "cornerRadius"
            )

            val thumbnailModel = remember(collectionWithCount.thumbnailMediaId) {
                collectionWithCount.thumbnailMediaId?.let(collectionThumbnailModel)
            }

            if (thumbnailModel != null) {
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
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = { onItemClick(collectionWithCount) },
                            onLongClick = {
                                feedbackManager.vibrate()
                                scope.launch { appBottomSheetState.show() }
                            }
                        ),
                    model = thumbnailModel,
                    contentDescription = collection.label,
                    contentScale = ContentScale.Crop,
                    signature = collectionWithCount
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = { onItemClick(collectionWithCount) },
                            onLongClick = {
                                feedbackManager.vibrate()
                                scope.launch { appBottomSheetState.show() }
                            }
                        )
                        .padding(48.dp)
                )
            }

            if (collection.isPinned) {
                Icon(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                        .align(Alignment.TopEnd),
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            modifier = Modifier
                .padding(top = 12.dp)
                .padding(horizontal = 16.dp),
            text = collection.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
        if (collectionWithCount.mediaCount > 0) {
            val sizeText = if (collectionWithCount.totalSize > 0) {
                " (${formatSize(collectionWithCount.totalSize)})"
            } else ""
            Text(
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                text = stringResource(
                    Res.string.n_items_in_collection,
                    collectionWithCount.mediaCount
                ) + sizeText,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionRowComponent(
    modifier: Modifier = Modifier,
    collectionWithCount: CollectionWithCount,
    onItemClick: (CollectionWithCount) -> Unit,
    onRename: ((CollectionWithCount) -> Unit)? = null,
    onDelete: ((CollectionWithCount) -> Unit)? = null,
    onTogglePin: ((CollectionWithCount) -> Unit)? = null,
    onEditAlbums: ((CollectionWithCount) -> Unit)? = null,
    /** Opens the album arrange screen; null while the albums cannot be arranged. */
    onRearrange: (() -> Unit)? = null,
    collectionThumbnailModel: CollectionThumbnailModel = { null },
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    val collection = collectionWithCount.collection
    val feedbackManager = rememberFeedbackManager()

    val renameTitle = stringResource(Res.string.rename_collection)
    val deleteTitle = stringResource(Res.string.delete_collection)
    val addAlbumsTitle = stringResource(Res.string.select_albums_for_collection)
    val rearrangeTitle = stringResource(Res.string.rearrange)
    val pinTitle = stringResource(
        if (collection.isPinned) Res.string.unpin_collection else Res.string.pin_collection
    )
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val optionList = remember(collection.isPinned, onRename, onDelete, onTogglePin, onEditAlbums, onRearrange) {
        mutableListOf(
            OptionItem(
                icon = Icons.Outlined.LibraryAdd,
                text = addAlbumsTitle,
                containerColor = secondaryContainer,
                contentColor = onSecondaryContainer,
                enabled = onEditAlbums != null,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        onEditAlbums?.invoke(collectionWithCount)
                    }
                }
            ),
            OptionItem(
                icon = Icons.Outlined.PushPin,
                text = pinTitle,
                containerColor = secondaryContainer,
                contentColor = onSecondaryContainer,
                enabled = onTogglePin != null,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        onTogglePin?.invoke(collectionWithCount)
                    }
                }
            ),
            OptionItem(
                icon = Icons.Outlined.Edit,
                text = renameTitle,
                containerColor = tertiaryContainer,
                contentColor = onTertiaryContainer,
                enabled = onRename != null,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        onRename?.invoke(collectionWithCount)
                    }
                }
            ),
            OptionItem(
                icon = Icons.Outlined.Delete,
                text = deleteTitle,
                containerColor = primaryContainer,
                contentColor = onPrimaryContainer,
                enabled = onDelete != null,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        onDelete?.invoke(collectionWithCount)
                    }
                }
            )
        ).apply {
            if (onRearrange != null) add(
                OptionItem(
                    icon = Icons.Rounded.DragIndicator,
                    text = rearrangeTitle,
                    onClick = {
                        scope.launch {
                            appBottomSheetState.hide()
                            onRearrange()
                        }
                    }
                )
            )
        }.toMutableStateList()
    }

    OptionSheet(
        state = appBottomSheetState,
        optionList = arrayOf(optionList),
        headerContent = {
            Text(
                text = collection.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
    )

    val interactionSource = remember { MutableInteractionSource() }

    val thumbnailModel = remember(collectionWithCount.thumbnailMediaId) {
        collectionWithCount.thumbnailMediaId?.let(collectionThumbnailModel)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onItemClick(collectionWithCount) },
                onLongClick = {
                    feedbackManager.vibrate()
                    scope.launch { appBottomSheetState.show() }
                }
            ),
    ) {
        Box(
            modifier = Modifier.aspectRatio(1f)
        ) {
            if (thumbnailModel != null) {
                val renderer = LocalMediaImageRenderer.current
                renderer.RenderImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    model = thumbnailModel,
                    contentDescription = collection.label,
                    contentScale = ContentScale.Crop,
                    signature = collectionWithCount
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .padding(12.dp)
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
                text = collection.label,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis
            )
            if (collectionWithCount.mediaCount > 0) {
                val sizeText = if (collectionWithCount.totalSize > 0) {
                    " (${formatSize(collectionWithCount.totalSize)})"
                } else ""
                Text(
                    text = stringResource(
                        Res.string.n_items_in_collection,
                        collectionWithCount.mediaCount
                    ) + sizeText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (collection.isPinned) {
            Icon(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterVertically),
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
