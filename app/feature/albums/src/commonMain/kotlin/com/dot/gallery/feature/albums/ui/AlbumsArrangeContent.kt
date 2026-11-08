/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumViewType
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.arrange_albums_cancel
import com.dot.gallery.feature.albums.generated.resources.arrange_albums_done
import com.dot.gallery.feature.albums.generated.resources.arrange_albums_hint
import com.dot.gallery.feature.albums.generated.resources.arrange_albums_move_earlier
import com.dot.gallery.feature.albums.generated.resources.arrange_albums_move_later
import com.dot.gallery.feature.albums.generated.resources.arrange_albums_reset
import com.dot.gallery.feature.albums.generated.resources.arrange_albums_title
import com.dot.gallery.feature.albums.ui.components.AlbumComponent
import com.dot.gallery.feature.albums.ui.components.AlbumRowComponent
import com.dot.gallery.feature.albums.ui.components.ProviderIconSlot
import com.dot.gallery.feature.albums.ui.components.ReorderableLazyState
import com.dot.gallery.feature.albums.ui.components.blockTaps
import com.dot.gallery.feature.albums.ui.components.rememberReorderableGridState
import com.dot.gallery.feature.albums.ui.components.rememberReorderableListState
import com.dot.gallery.feature.albums.ui.components.reorderable
import com.dot.gallery.feature.albums.ui.util.move
import org.jetbrains.compose.resources.stringResource

private const val ARRANGE_KEY_PREFIX = "arrange_"
private const val ARRANGE_HEADER_KEY = "arrangeHeader"

/**
 * Edit mode for the user's album arrangement. Only the albums the arrangement covers are shown,
 * as one draft list seeded with the arranged albums followed by Others. Nothing is saved until
 * [onDone]; back and close discard the draft.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AlbumsArrangeContent(
    albums: List<Album>,
    viewType: AlbumViewType,
    gridCells: GridCells,
    contentPadding: PaddingValues,
    hasArrangement: Boolean,
    mergedSubfolderIds: Set<Long>,
    subGalleryIds: Set<Long>,
    providerIcon: ProviderIconSlot?,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onDone: (draft: List<Album>, changed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = remember { mutableStateListOf<Album>().apply { addAll(albums) } }
    var changed by remember { mutableStateOf(false) }

    // Keep the draft in step with library changes without losing the user's ordering.
    LaunchedEffect(albums) {
        val incoming = albums.associateBy { it.id }
        draft.removeAll { it.id !in incoming }
        draft.indices.forEach { index -> incoming[draft[index].id]?.let { draft[index] = it } }
        val present = draft.mapTo(HashSet()) { it.id }
        albums.filter { it.id !in present }.forEach { draft += it }
    }

    BackHandler(onBack = onCancel)

    val canDrag: (Any) -> Boolean = { (it as? String)?.startsWith(ARRANGE_KEY_PREFIX) == true }
    val onMove: (Any, Any) -> Unit = { from, to ->
        val fromIndex = draft.indexOfFirst { arrangeKey(it) == from }
        val toIndex = draft.indexOfFirst { arrangeKey(it) == to }
        if (fromIndex >= 0 && toIndex >= 0) {
            draft.move(fromIndex, toIndex)
            changed = true
        }
    }
    val moveBy: (Album, Int) -> Boolean = { album, step ->
        val from = draft.indexOfFirst { it.id == album.id }
        val to = from + step
        if (from >= 0 && to in draft.indices) {
            draft.move(from, to)
            changed = true
            true
        } else false
    }

    val header: @Composable () -> Unit = {
        ArrangeHeader(
            canReset = hasArrangement || changed,
            onCancel = onCancel,
            onReset = onReset,
            onDone = { onDone(draft.toList(), changed) },
        )
    }

    when (viewType) {
        AlbumViewType.GRID -> {
            val gridState = rememberLazyGridState()
            val reorder = rememberReorderableGridState(gridState, canDrag, onMove)
            LazyVerticalGrid(
                state = gridState,
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxSize()
                    .reorderable(reorder),
                columns = gridCells,
                contentPadding = contentPadding,
                userScrollEnabled = !reorder.isDragging,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = ARRANGE_HEADER_KEY) { header() }
                items(items = draft, key = ::arrangeKey) { album ->
                    val key = arrangeKey(album)
                    Box(
                        modifier = arrangeItem(reorder, key, album, moveBy)
                            .blockTaps(),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        AlbumComponent(
                            album = album,
                            onItemClick = {},
                            isMergedSubfolder = album.id in mergedSubfolderIds,
                            isSubGallery = album.id in subGalleryIds,
                            providerIcon = providerIcon,
                        )
                        Icon(
                            imageVector = Icons.Outlined.DragIndicator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(16.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                .padding(4.dp)
                                .size(18.dp)
                        )
                    }
                }
            }
        }

        AlbumViewType.LIST -> {
            val listState = rememberLazyListState()
            val reorder = rememberReorderableListState(listState, canDrag, onMove)
            LazyColumn(
                state = listState,
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxSize()
                    .reorderable(reorder),
                contentPadding = contentPadding,
                userScrollEnabled = !reorder.isDragging,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = ARRANGE_HEADER_KEY) { header() }
                items(items = draft, key = ::arrangeKey) { album ->
                    val key = arrangeKey(album)
                    Row(
                        modifier = arrangeItem(reorder, key, album, moveBy)
                            .blockTaps(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumRowComponent(
                            modifier = Modifier.weight(1f),
                            album = album,
                            onItemClick = {},
                            isMergedSubfolder = album.id in mergedSubfolderIds,
                            isSubGallery = album.id in subGalleryIds,
                            providerIcon = providerIcon,
                        )
                        Icon(
                            imageVector = Icons.Outlined.DragHandle,
                            contentDescription = null,
                            tint = if (reorder.draggingKey == key) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun arrangeKey(album: Album): String = "$ARRANGE_KEY_PREFIX${album.id}"

@Composable
private fun ArrangeHeader(
    canReset: Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.arrange_albums_cancel),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                text = stringResource(Res.string.arrange_albums_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onReset, enabled = canReset) {
                Text(text = stringResource(Res.string.arrange_albums_reset))
            }
            Button(onClick = onDone) {
                Text(text = stringResource(Res.string.arrange_albums_done))
            }
        }
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = stringResource(Res.string.arrange_albums_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LazyGridItemScope.arrangeItem(
    state: ReorderableLazyState,
    key: Any,
    album: Album,
    moveBy: (Album, Int) -> Boolean,
): Modifier {
    val dragging = state.draggingKey == key
    return Modifier
        .then(if (dragging) Modifier else Modifier.animateItem())
        .liftWhileDragging(state, key, dragging)
        .moveActions(album, moveBy)
}

@Composable
private fun LazyItemScope.arrangeItem(
    state: ReorderableLazyState,
    key: Any,
    album: Album,
    moveBy: (Album, Int) -> Boolean,
): Modifier {
    val dragging = state.draggingKey == key
    return Modifier
        .then(if (dragging) Modifier else Modifier.animateItem())
        .liftWhileDragging(state, key, dragging)
        .moveActions(album, moveBy)
}

@Composable
private fun Modifier.liftWhileDragging(
    state: ReorderableLazyState,
    key: Any,
    dragging: Boolean,
): Modifier {
    val scale by animateFloatAsState(if (dragging) 1.05f else 1f, label = "arrangeLiftScale")
    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "arrangeLiftElevation")
    val shape = RoundedCornerShape(16.dp)
    val lifted = MaterialTheme.colorScheme.surfaceContainerHigh
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            val translation = state.translationOf(key)
            translationX = translation.x
            translationY = translation.y
            scaleX = scale
            scaleY = scale
            shadowElevation = elevation.toPx()
            this.shape = shape
            clip = false
        }
        .then(if (elevation > 0.dp) Modifier.background(lifted, shape) else Modifier)
}

@Composable
private fun Modifier.moveActions(album: Album, moveBy: (Album, Int) -> Boolean): Modifier {
    val earlier = stringResource(Res.string.arrange_albums_move_earlier)
    val later = stringResource(Res.string.arrange_albums_move_later)
    // Replaces the album's own click actions so screen readers cannot open it while arranging.
    return clearAndSetSemantics {
        contentDescription = album.label
        customActions = listOf(
            CustomAccessibilityAction(earlier) { moveBy(album, -1) },
            CustomAccessibilityAction(later) { moveBy(album, 1) },
        )
    }
}
