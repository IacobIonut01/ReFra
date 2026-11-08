/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.dot.gallery.feature.albums.ui.util.bleedHorizontally
import com.dot.gallery.ui.theme.BlackScrim
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionSheet
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.domain.model.Album
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.browse_subfolders
import com.dot.gallery.feature.albums.generated.resources.item_count
import com.dot.gallery.feature.albums.generated.resources.merge_subfolders
import com.dot.gallery.feature.albums.generated.resources.s_items
import com.dot.gallery.feature.albums.generated.resources.show_combined_timeline
import com.dot.gallery.feature.albums.generated.resources.unmerge_subfolders
import com.dot.gallery.feature.albums.generated.resources.unpin

private val PinnedCardShape = RoundedCornerShape(28.dp)
private val PinnedLabelScrim = Brush.verticalGradient(listOf(Color.Transparent, BlackScrim))

@Composable
private fun PinnedAlbumCard(
    album: Album,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 164.dp, height = 208.dp)
            .clip(PinnedCardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        when (albumThumbnailPresentation(album.isLocked)) {
            AlbumThumbnailPresentation.LOCKED_PLACEHOLDER -> LockedAlbumThumbnail(
                modifier = Modifier.fillMaxSize(),
                iconModifier = Modifier.size(36.dp),
                shape = PinnedCardShape
            )
            AlbumThumbnailPresentation.MEDIA -> LocalMediaImageRenderer.current.RenderImage(
                modifier = Modifier.fillMaxSize(),
                model = album.uri,
                contentDescription = album.label,
                contentScale = ContentScale.Crop,
                signature = album
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(BlackScrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(PinnedLabelScrim)
                .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp),
        ) {
            Text(
                text = album.label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    Res.plurals.item_count,
                    album.count.toInt(),
                    album.count
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/**
 * Pinned albums as a row of portrait cards, sharing the Timeline story cards' look: a pin badge, and
 * the name over a scrim. A plain lazy row, since a masked carousel relayouts its items every frame.
 * [bleed] lets the row scroll to the screen edge from inside the page's side padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselPinnedAlbums(
    modifier: Modifier = Modifier,
    albumList: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    mergedSubfolderIds: Set<Long> = emptySet(),
    subGalleryIds: Set<Long> = emptySet(),
    onToggleMergeSubfolders: ((Album) -> Unit)? = null,
    onToggleMergedSubfolderDisplayMode: ((Album) -> Unit)? = null,
    bleed: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    val appBottomSheetState = rememberAppBottomSheetState()
    var currentAlbum: Album? by remember { mutableStateOf(null) }
    val selectedAlbum = currentAlbum?.let { selected ->
        albumList.firstOrNull { it.id == selected.id } ?: selected
    }
    val feedbackManager = rememberFeedbackManager()
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .bleedHorizontally(bleed),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = albumList, key = { it.id }) { album ->
            PinnedAlbumCard(
                album = album,
                onClick = { onAlbumClick(album) },
                onLongClick = {
                    feedbackManager.vibrate()
                    currentAlbum = album
                    scope.launch { appBottomSheetState.show() }
                },
            )
        }
    }

    val unpinTitle = stringResource(Res.string.unpin)
    val mergeTitle = stringResource(
        if (selectedAlbum?.id in mergedSubfolderIds) Res.string.unmerge_subfolders
        else Res.string.merge_subfolders
    )
    val layoutTitle = stringResource(
        if (selectedAlbum?.id in subGalleryIds) Res.string.show_combined_timeline
        else Res.string.browse_subfolders
    )
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val optionList = remember(
        selectedAlbum,
        mergedSubfolderIds,
        subGalleryIds,
        onToggleMergeSubfolders,
        onToggleMergedSubfolderDisplayMode,
        mergeTitle,
        layoutTitle
    ) {
        mutableListOf(
            OptionItem(
                text = unpinTitle,
                containerColor = tertiaryContainer,
                contentColor = onTertiaryContainer,
                onClick = {
                    scope.launch {
                        appBottomSheetState.hide()
                        currentAlbum?.let {
                            onAlbumLongClick(it)
                            currentAlbum = null
                        }
                    }
                }
            )
        ).apply {
            if (onToggleMergeSubfolders != null) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.AccountTree,
                        text = mergeTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                currentAlbum?.let(onToggleMergeSubfolders)
                                currentAlbum = null
                            }
                        }
                    )
                )
            }
            if (
                selectedAlbum?.id in mergedSubfolderIds &&
                onToggleMergedSubfolderDisplayMode != null
            ) {
                add(
                    OptionItem(
                        icon = Icons.Outlined.AccountTree,
                        text = layoutTitle,
                        onClick = {
                            scope.launch {
                                appBottomSheetState.hide()
                                currentAlbum?.let(onToggleMergedSubfolderDisplayMode)
                                currentAlbum = null
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
            selectedAlbum?.let { album ->
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
                            append(stringResource(Res.string.s_items, album.count))
                        }
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }
        }
    )
}
