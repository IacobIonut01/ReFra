/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albums.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SdCard
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionSheet
import com.dot.gallery.feature_node.presentation.picker.PickerActivityContract
import com.dot.gallery.feature_node.presentation.util.formatSize
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlideComposeApi::class)
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
    onDeleteAlbumThumbnailClick: ((Album) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    Column(
        modifier = modifier
            .alpha(if (isEnabled) 1f else 0.4f)
            .padding(horizontal = 8.dp),
    ) {
        val trashTitle = stringResource(R.string.move_album_to_trash)
        val pinTitle = stringResource(R.string.pin)
        val changeThumbnailTitle = stringResource(R.string.change_thumbnail)
        val ignoredTitle = stringResource(id = R.string.add_to_ignored)
        val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
        val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
        val primaryContainer = MaterialTheme.colorScheme.primaryContainer
        val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
        val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
        val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer
        var isSelectingThumbnail by rememberSaveable { mutableStateOf(false) }
        val handler = LocalMediaHandler.current
        val hasThumbnail by handler.hasAlbumThumbnail(album.id).collectAsStateWithLifecycle(initialValue = false)

        val pickerLauncher = rememberLauncherForActivityResult(
            PickerActivityContract(
                allowMultiple = false,
                mediaType = "image/*"
            )
        ) { uriList ->
            scope.launch {
                if (uriList.isNotEmpty()) {
                    val newThumbnailUri = uriList.firstOrNull()
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

        val changeThumbnailOptions = remember(hasThumbnail) {
            listOf(
                OptionItem(
                    text = "Select a new thumbnail",
                    icon = Icons.Outlined.FileOpen,
                    onClick = { pickerLauncher.launch(Unit) }
                ),
                OptionItem(
                    text = "Change to default",
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
        val optionList = remember {
            mutableListOf(
                OptionItem(
                    icon = Icons.Outlined.Delete,
                    text = trashTitle,
                    containerColor = primaryContainer,
                    contentColor = onPrimaryContainer,
                    enabled = onMoveAlbumToTrash != null,
                    onClick = {
                        scope.launch {
                            appBottomSheetState.hide()
                            onMoveAlbumToTrash?.invoke(album)
                        }
                    }
                ),
                OptionItem(
                    icon = Icons.Outlined.PushPin,
                    text = pinTitle,
                    containerColor = secondaryContainer,
                    contentColor = onSecondaryContainer,
                    enabled = onTogglePinClick != null,
                    onClick = {
                        scope.launch {
                            appBottomSheetState.hide()
                            onTogglePinClick?.invoke(album)
                        }
                    }
                ),
                OptionItem(
                    icon = Icons.Outlined.Wallpaper,
                    text = changeThumbnailTitle,
                    containerColor = tertiaryContainer,
                    contentColor = onTertiaryContainer,
                    onClick = { isSelectingThumbnail = true }
                ),
            )
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
        LaunchedEffect(onToggleIgnoreClick) {
            if (onToggleIgnoreClick != null) {
                optionList.add(
                    OptionItem(
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
        }

        OptionSheet(
            state = appBottomSheetState,
            optionList = remember(isSelectingThumbnail) {
                arrayOf(
                    if (isSelectingThumbnail) {
                        changeThumbnailOptions
                    } else {
                        optionList
                    }
                )
            },
            headerContent = {
                BackHandler(isSelectingThumbnail) {
                    isSelectingThumbnail = false
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlideImage(
                        modifier = Modifier
                            .size(98.dp)
                            .clip(Shapes.large),
                        contentScale = ContentScale.Crop,
                        model = album.uri.toString(),
                        contentDescription = album.label
                    )
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
                ) + " (${formatSize(album.size)})",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }

    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AlbumRowComponent(
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    album: Album,
    isEnabled: Boolean = true,
    onItemClick: (Album) -> Unit,
    onMoveAlbumToTrash: ((Album) -> Unit)? = null,
    onTogglePinClick: ((Album) -> Unit)? = null,
    onToggleIgnoreClick: ((Album) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.4f)
            .height(64.dp)
            .padding(horizontal = 8.dp),
    ) {
        if (onTogglePinClick != null) {
            val trashTitle = stringResource(R.string.move_album_to_trash)
            val pinTitle = stringResource(R.string.pin)
            val ignoredTitle = stringResource(id = R.string.add_to_ignored)
            val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
            val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
            val primaryContainer = MaterialTheme.colorScheme.primaryContainer
            val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
            val optionList = remember {
                mutableListOf(
                    OptionItem(
                        text = trashTitle,
                        containerColor = primaryContainer,
                        contentColor = onPrimaryContainer,
                        enabled = onMoveAlbumToTrash != null,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onMoveAlbumToTrash?.invoke(album)
                            }
                        }
                    ),
                    OptionItem(
                        text = pinTitle,
                        containerColor = secondaryContainer,
                        contentColor = onSecondaryContainer,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                onTogglePinClick(album)
                            }
                        }
                    )
                )
            }
            LaunchedEffect(onToggleIgnoreClick) {
                if (onToggleIgnoreClick != null) {
                    optionList.add(
                        OptionItem(
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
            }

            OptionSheet(
                state = appBottomSheetState,
                optionList = arrayOf(optionList),
                headerContent = {
                    GlideImage(
                        modifier = Modifier
                            .size(98.dp)
                            .clip(Shapes.large),
                        contentScale = ContentScale.Crop,
                        model = album.uri.toString(),
                        contentDescription = album.label
                    )
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
                                append(stringResource(R.string.s_items, album.count) + " (${formatSize(album.size)})")
                            }
                        },
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    )
                }
            )
        }
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
                ) + " (${formatSize(album.size)})",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
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
    val cornerRadius by animateDpAsState(targetValue = if (isPressed.value) 32.dp else 16.dp, label = "cornerRadius")
    val feedbackManager = rememberFeedbackManager()
    if (album.id == -200L && album.count == 0L) {
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
            requestBuilderTransform = {
                val newRequest = it.centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                newRequest.thumbnail(newRequest.clone().sizeMultiplier(0.4f))
            }
        )
    }
}