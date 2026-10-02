/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.collection.components

import android.content.ContentUris
import android.provider.MediaStore
import com.dot.gallery.feature_node.presentation.util.formatSize
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
import com.dot.gallery.feature_node.domain.model.CollectionWithCount
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionSheet
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionComponent(
    modifier: Modifier = Modifier,
    collectionWithCount: CollectionWithCount,
    onItemClick: (CollectionWithCount) -> Unit,
    onRename: ((CollectionWithCount) -> Unit)? = null,
    onDelete: ((CollectionWithCount) -> Unit)? = null,
    onTogglePin: ((CollectionWithCount) -> Unit)? = null,
    onEditAlbums: ((CollectionWithCount) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    val collection = collectionWithCount.collection
    val feedbackManager = rememberFeedbackManager()

    val renameTitle = stringResource(R.string.rename_collection)
    val deleteTitle = stringResource(R.string.delete_collection)
    val addAlbumsTitle = stringResource(R.string.select_albums_for_collection)
    val pinTitle = stringResource(
        if (collection.isPinned) R.string.unpin_collection else R.string.pin_collection
    )
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val optionList = remember(collection.isPinned, onRename, onDelete, onTogglePin, onEditAlbums) {
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
        ).toMutableStateList()
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

            val thumbnailUri = remember(collectionWithCount.thumbnailMediaId) {
                collectionWithCount.thumbnailMediaId?.let { id ->
                    ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        id
                    )
                }
            }

            if (thumbnailUri != null) {
                GlideImage(
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
                    model = thumbnailUri,
                    contentDescription = collection.label,
                    contentScale = ContentScale.Crop,
                    requestBuilderTransform = {
                        it.centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .thumbnail(it.clone().sizeMultiplier(0.4f))
                    }
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
                    R.string.n_items_in_collection,
                    collectionWithCount.mediaCount
                ) + sizeText,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionRowComponent(
    modifier: Modifier = Modifier,
    collectionWithCount: CollectionWithCount,
    onItemClick: (CollectionWithCount) -> Unit,
    onRename: ((CollectionWithCount) -> Unit)? = null,
    onDelete: ((CollectionWithCount) -> Unit)? = null,
    onTogglePin: ((CollectionWithCount) -> Unit)? = null,
    onEditAlbums: ((CollectionWithCount) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    val collection = collectionWithCount.collection
    val feedbackManager = rememberFeedbackManager()

    val renameTitle = stringResource(R.string.rename_collection)
    val deleteTitle = stringResource(R.string.delete_collection)
    val addAlbumsTitle = stringResource(R.string.select_albums_for_collection)
    val pinTitle = stringResource(
        if (collection.isPinned) R.string.unpin_collection else R.string.pin_collection
    )
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val optionList = remember(collection.isPinned, onRename, onDelete, onTogglePin, onEditAlbums) {
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
        ).toMutableStateList()
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

    val thumbnailUri = remember(collectionWithCount.thumbnailMediaId) {
        collectionWithCount.thumbnailMediaId?.let { id ->
            ContentUris.withAppendedId(
                MediaStore.Files.getContentUri("external"),
                id
            )
        }
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
            if (thumbnailUri != null) {
                GlideImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    model = thumbnailUri,
                    contentDescription = collection.label,
                    contentScale = ContentScale.Crop,
                    requestBuilderTransform = {
                        it.centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .thumbnail(it.clone().sizeMultiplier(0.4f))
                    }
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
                        R.string.n_items_in_collection,
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
