/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature.albums.ui.collections

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.GalleryDetailAction
import com.dot.gallery.core.presentation.components.GalleryDetailCover
import com.dot.gallery.core.presentation.components.GalleryDetailHeader
import com.dot.gallery.core.presentation.components.GalleryDetailTopBar
import com.dot.gallery.core.presentation.components.galleryDetailTopBarHeight
import com.dot.gallery.core.presentation.components.mediaDateRangeLabel
import com.dot.gallery.core.presentation.components.rememberDetailHeaderCollapsed
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.albums.ui.components.AlbumSortPill
import com.dot.gallery.feature.albums.ui.components.AlbumSortTopBarAction
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.feature.albums.generated.resources.detail_collection
import com.dot.gallery.feature.albums.generated.resources.detail_slideshow
import com.dot.gallery.feature.albums.generated.resources.s_items
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.MediaGridView
import com.dot.gallery.core.presentation.components.MosaicMediaGrid
import com.dot.gallery.core.presentation.components.MosaicPinchZoomLayout
import com.dot.gallery.core.presentation.components.TimelineScroller
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.components.rememberMosaicMonthSegments
import com.dot.gallery.core.presentation.components.rememberMosaicPinchZoomState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.GROUP_MONTHLY
import com.dot.gallery.domain.settings.GROUP_NORMAL
import com.dot.gallery.domain.settings.GROUP_YEARLY
import com.dot.gallery.domain.settings.LAYOUT_MOSAIC
import com.dot.gallery.domain.settings.SettingsKeys
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.collection_empty
import com.dot.gallery.feature.albums.generated.resources.edit

/**
 * Pure-Compose port of the collection media grid.
 *
 * Platform seams:
 * - [metadataState] is provided app-side (`distributor.metadataFlow`).
 * - [gridSizeIndex]/[mosaicGridSizeIndex] are the persisted grid/mosaic column
 *   indexes (the app keeps the orientation-aware SharedPreferences state).
 * - [onMediaClick] receives the tapped media; the app owns navigation to
 *   `Screen.MediaViewScreen.idAndCollection`.
 * - [onSlideshow] lets the app show its slideshow options sheet and route.
 * - [selectionSheet] hosts the app's `SelectionSheet` overlay (move/copy/share
 *   actions stay app-side); it is placed in the root [Box] with
 *   `Alignment.BottomEnd` semantics left to the caller's modifier.
 */
@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun CollectionViewContent(
    collectionId: Long,
    collectionName: String,
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    metadataState: State<MediaMetadataState>,
    gridSizeIndex: MutableState<Int>,
    mosaicGridSizeIndex: MutableState<Int>,
    onMediaClick: (UriMedia) -> Unit,
    onEditAlbums: (() -> Unit)? = null,
    /** Opens the slideshow options from this media id; null hides the Slideshow action. */
    onSlideshow: ((startMediaId: Long) -> Unit)? = null,
    selectionSheet: @Composable BoxScope.(mediaState: MediaState<UriMedia>, selectedMedia: SnapshotStateList<UriMedia>) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    /** Docs seam: opens the grid scrolled to this item (0 is the header). */
    initialFirstVisibleItemIndex: Int = 0,
) {
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by gridSizeIndex
    val distributor = LocalMediaDistributor.current
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsState()

    val collectionMediaFlow = remember(collectionId) {
        distributor.collectionMediaFlow(collectionId)
    }
    val mediaState = collectionMediaFlow.collectAsState()

    var albumMediaSort by rememberPreference(SettingsKeys.albumMediaSort)

    val cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 2f) }
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex,
        gridState = rememberLazyGridState(
            cacheWindow = cacheWindow,
            initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        )
    )
    var lastMosaicCellIndex by mosaicGridSizeIndex
    val mosaicPinchState = rememberMosaicPinchZoomState(
        initialColumnsIndex = lastMosaicCellIndex,
        gridState = rememberLazyGridState(
            cacheWindow = cacheWindow,
            initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        )
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.Default) {
            canScroll = !pinchState.isZooming
            lastCellIndex = cellsList.indexOf(pinchState.currentCells)
        }
    }
    LaunchedEffect(mosaicPinchState.isZooming) {
        lastMosaicCellIndex = mosaicPinchState.currentColumnsIndex
    }

    val hideTimelineOnAlbum by rememberPreference(SettingsKeys.hideTimelineOnAlbum)
    val albumsGroupMethod by rememberPreference(SettingsKeys.albumsGroupMethod)
    val timelineLayoutType by rememberPreference(SettingsKeys.timelineLayoutType)
    val isMosaicLayout = timelineLayoutType == LAYOUT_MOSAIC && !hideTimelineOnAlbum
    val hazeState = LocalHazeState.current
    val topBarHeight = galleryDetailTopBarHeight()
    val isSelectionActive by selector.isSelectionActive.collectAsState()
    val media = mediaState.value.media
    val headerCollapsed by rememberDetailHeaderCollapsed(
        if (isMosaicLayout) mosaicPinchState.gridState else pinchState.gridState
    )
    val itemsLabel = stringResource(Res.string.s_items, media.size.toString())
    val dateRange = remember(media) { mediaDateRangeLabel(media) }
    val collectionLabel = stringResource(Res.string.detail_collection)
    val editLabel = stringResource(Res.string.edit)
    val slideshowLabel = stringResource(Res.string.detail_slideshow)
    val startSlideshow: (() -> Unit)? = if (onSlideshow != null && media.isNotEmpty()) {
        { onSlideshow(mediaState.value.pagerMedia.firstOrNull()?.id ?: media.first().id) }
    } else null

    val headerContent: @Composable () -> Unit = {
        GalleryDetailHeader(
            title = collectionName,
            stats = listOfNotNull(collectionLabel, itemsLabel, dateRange).joinToString(" · "),
            cover = {
                val cover = media.firstOrNull()
                GalleryDetailCover(
                    model = cover?.getUri()?.toString(),
                    signature = cover,
                    fallbackIcon = Icons.Outlined.Collections,
                )
            },
            actions = {
                if (onEditAlbums != null) {
                    GalleryDetailAction(label = editLabel, icon = Icons.Outlined.Edit, onClick = onEditAlbums)
                }
                if (startSlideshow != null) {
                    GalleryDetailAction(label = slideshowLabel, icon = Icons.Outlined.Slideshow, onClick = startSlideshow)
                }
                AlbumSortPill(
                    currentSort = albumMediaSort,
                    onSortChange = { newSort -> albumMediaSort = newSort },
                )
            },
        )
    }
    val emptyContent: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            headerContent()
            EmptyMedia(title = stringResource(Res.string.collection_empty))
        }
    }

    Box(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
    ) {
        val gridPaddingValues = PaddingValues(
            top = topBarHeight,
            bottom = paddingValues.calculateBottomPadding() + 128.dp
        )
        if (isMosaicLayout) {
            val mosaicGridState = mosaicPinchState.gridState
            val mappedData by remember(mediaState, albumsGroupMethod) {
                derivedStateOf {
                    when (albumsGroupMethod) {
                        GROUP_MONTHLY -> mediaState.value.mappedMediaWithMonthly
                        GROUP_YEARLY -> mediaState.value.mappedMediaWithYearly
                        else -> mediaState.value.mappedMedia
                    }.toMutableStateList()
                }
            }
            val headers by remember(mediaState) {
                derivedStateOf {
                    mediaState.value.headers.toMutableStateList()
                }
            }
            MosaicPinchZoomLayout(
                state = mosaicPinchState,
                indicatorTopPadding = topBarHeight + 16.dp,
            ) { currentColumns ->
                TimelineScroller(
                    modifier = Modifier
                        .padding(gridPaddingValues)
                        .padding(top = 32.dp)
                        .padding(vertical = 32.dp),
                    segments = rememberMosaicMonthSegments(
                        mappedData = mappedData,
                        columns = currentColumns,
                        allowHeaders = true,
                        leadingItemCount = 1,
                    ),
                    headers = headers,
                    state = mosaicGridState,
                ) {
                    MosaicMediaGrid(
                        modifier = Modifier.hazeSource(hazeState),
                        gridState = mosaicGridState,
                        columns = currentColumns,
                        mediaState = mediaState,
                        metadataState = metadataState,
                        mappedData = mappedData,
                        paddingValues = gridPaddingValues,
                        allowSelection = true,
                        canScroll = !mosaicPinchState.isZooming,
                        allowHeaders = true,
                        aboveGridContent = headerContent,
                        isScrolling = isScrolling,
                        emptyContent = emptyContent,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMediaClick = onMediaClick,
                    )
                }
            }
        } else {
            GridPinchZoomLayout(
                state = pinchState,
                modifier = Modifier.hazeSource(hazeState),
                indicatorTopPadding = topBarHeight + 16.dp,
            ) {
                MediaGridView(
                    mediaState = mediaState,
                    metadataState = metadataState,
                    allowSelection = true,
                    showSearchBar = false,
                    enableStickyHeaders = false,
                    groupMethod = if (!hideTimelineOnAlbum) albumsGroupMethod else GROUP_NORMAL,
                    paddingValues = gridPaddingValues,
                    canScroll = canScroll,
                    allowHeaders = !hideTimelineOnAlbum,
                    aboveGridContent = headerContent,
                    isScrolling = isScrolling,
                    emptyContent = emptyContent,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onMediaClick = onMediaClick,
                )
            }
        }
        GalleryDetailTopBar(
            title = collectionName,
            count = media.size.toString(),
            collapsed = headerCollapsed,
            hazeState = hazeState,
            onBack = if (isSelectionActive) selector::clearSelection else null,
            navigationIcon = if (isSelectionActive) Icons.Outlined.Close else null,
            collapsedActions = buildList {
                if (onEditAlbums != null) {
                    add { GalleryPageTopBarAction(icon = Icons.Outlined.Edit, contentDescription = editLabel, onClick = onEditAlbums) }
                }
                if (startSlideshow != null) {
                    add { GalleryPageTopBarAction(icon = Icons.Outlined.Slideshow, contentDescription = slideshowLabel, onClick = startSlideshow) }
                }
                add {
                    AlbumSortTopBarAction(
                        currentSort = albumMediaSort,
                        onSortChange = { newSort -> albumMediaSort = newSort },
                    )
                }
            },
        )
        val selectedMediaList by selectedMedia(
            media = mediaState.value.media,
            selectedSet = selectedMedia
        )
        selectionSheet(mediaState.value, selectedMediaList)
    }
}
