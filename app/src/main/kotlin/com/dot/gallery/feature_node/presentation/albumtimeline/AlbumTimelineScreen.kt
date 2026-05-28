/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albumtimeline

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings.Album.rememberAlbumMediaSort
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Album.rememberHideTimelineOnAlbum
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.NavigationButton
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.albumtimeline.components.AlbumSortDropdown
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.MosaicMediaGrid
import com.dot.gallery.feature_node.presentation.common.components.MosaicPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.TimelineScroller
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicPinchZoomState
import com.dot.gallery.feature_node.presentation.common.components.TwoLinedDateToolbarTitle
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun AlbumTimelineScreen(
    albumId: Long,
    albumName: String,
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    albumMediaState: State<MediaState<Media.UriMedia>>,
    metadataState: State<MediaMetadataState>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by rememberGridSize()
    val eventHandler = LocalEventHandler.current
    val distributor = LocalMediaDistributor.current
    val isRefreshing by distributor.isRefreshing.collectAsStateWithLifecycle()
    val refreshScope = rememberCoroutineScope()
    val albumsState by distributor.albumsFlow.collectAsStateWithLifecycle()
    val currentAlbum = remember(albumsState, albumId) {
        albumsState.albums.find { it.id == albumId }
    }
    val mergedAlbumIds = currentAlbum?.mergedAlbumIds ?: emptyList()
    val constituentAlbums = remember(albumsState, mergedAlbumIds) {
        if (mergedAlbumIds.size > 1) {
            val idSet = mergedAlbumIds.toSet()
            albumsState.albumsWithBlacklisted.filter { it.id in idSet }
        } else emptyList()
    }
    var showMergedBanner by rememberSaveable { mutableStateOf(true) }
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    // Sort preferences - saved to DataStore, read by MediaDistributor
    var albumMediaSort by rememberAlbumMediaSort()

    // Media state is already sorted by MediaDistributor based on albumMediaSort
    val mediaState = albumMediaState

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { canScroll },
        flingAnimationSpec = null
    )

    val dpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex,
        gridState = rememberLazyGridState(
            cacheWindow = dpCacheWindow
        )
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.IO) {
            canScroll = !pinchState.isZooming
            lastCellIndex = cellsList.indexOf(pinchState.currentCells)
        }
    }
    Box(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    modifier = Modifier.hazeEffect(
                        state = LocalHazeState.current,
                        style = LocalHazeStyle.current
                    ),
                    title = {
                        TwoLinedDateToolbarTitle(
                            albumName = albumName,
                            dateHeader = mediaState.value.dateHeader
                        )
                    },
                    navigationIcon = {
                        NavigationButton(
                            albumId = albumId,
                            target = null,
                            alwaysGoBack = true,
                        )
                    },
                    actions = {
                        AlbumSortDropdown(
                            currentSort = albumMediaSort,
                            onSortChange = { newSort ->
                                albumMediaSort = newSort
                            }
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        ) { it ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshScope.launch { distributor.invalidate() } },
            ) {
            val hideTimelineOnAlbum by rememberHideTimelineOnAlbum()
            val timelineLayoutType by rememberTimelineLayoutType()
            val isMosaicLayout = timelineLayoutType == Settings.Misc.LAYOUT_MOSAIC && !hideTimelineOnAlbum
            if (isMosaicLayout) {
                var lastMosaicCellIndex by rememberMosaicGridSize()
                val mosaicPinchState = rememberMosaicPinchZoomState(
                    initialColumnsIndex = lastMosaicCellIndex,
                    gridState = rememberLazyGridState(
                        cacheWindow = dpCacheWindow
                    )
                )
                val mosaicGridState = mosaicPinchState.gridState

                LaunchedEffect(mosaicPinchState.isZooming) {
                    lastMosaicCellIndex = mosaicPinchState.currentColumnsIndex
                }

                val mappedData by remember(mediaState) {
                    derivedStateOf {
                        mediaState.value.mappedMedia.toMutableStateList()
                    }
                }
                val headers by remember(mediaState) {
                    derivedStateOf {
                        mediaState.value.headers.toMutableStateList()
                    }
                }
                val mosaicPaddingValues = PaddingValues(
                    top = it.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 128.dp
                )
                MosaicPinchZoomLayout(
                    state = mosaicPinchState,
                    indicatorTopPadding = mosaicPaddingValues.calculateTopPadding() + 16.dp,
                ) { currentColumns ->
                TimelineScroller(
                    modifier = Modifier
                        .padding(mosaicPaddingValues)
                        .padding(top = 32.dp)
                        .padding(vertical = 32.dp),
                    mappedData = mappedData,
                    headers = headers,
                    state = mosaicGridState,
                ) {
                    MosaicMediaGrid(
                        modifier = Modifier.hazeSource(LocalHazeState.current),
                        gridState = mosaicGridState,
                        columns = currentColumns,
                        mediaState = mediaState,
                        metadataState = metadataState,
                        mappedData = mappedData,
                        paddingValues = mosaicPaddingValues,
                        allowSelection = true,
                        canScroll = !mosaicPinchState.isZooming,
                        allowHeaders = true,
                        aboveGridContent = if (constituentAlbums.size > 1 && showMergedBanner) {
                            {
                                AlbumsMergedBanner(
                                    constituentAlbums = constituentAlbums,
                                    onDismiss = { showMergedBanner = false }
                                )
                            }
                        } else null,
                        isScrolling = isScrolling,
                        emptyContent = { EmptyMedia() },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMediaClick = {
                            eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(it.id, albumId))
                        },
                    )
                }
                }
            } else {
                GridPinchZoomLayout(
                    state = pinchState,
                    modifier = Modifier.hazeSource(LocalHazeState.current),
                    indicatorTopPadding = it.calculateTopPadding() + 16.dp,
                ) {
                    MediaGridView(
                        mediaState = mediaState,
                        metadataState = metadataState,
                        allowSelection = true,
                        showSearchBar = false,
                        enableStickyHeaders = !hideTimelineOnAlbum,
                        paddingValues = PaddingValues(
                            top = it.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + 128.dp
                        ),
                        canScroll = canScroll,
                        allowHeaders = !hideTimelineOnAlbum,
                        showMonthlyHeader = false,
                        aboveGridContent = if (constituentAlbums.size > 1 && showMergedBanner) {
                            {
                                AlbumsMergedBanner(
                                    constituentAlbums = constituentAlbums,
                                    onDismiss = { showMergedBanner = false }
                                )
                            }
                        } else null,
                        isScrolling = isScrolling,
                        emptyContent = { EmptyMedia() },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope
                    ) {
                        eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(it.id, albumId))
                    }
                }
            }
            } // PullToRefreshBox
        }
        val selectedMediaList by selectedMedia(
            media = mediaState.value.media,
            selectedSet = selectedMedia
        )
        SelectionSheet(
            modifier = Modifier
                .align(Alignment.BottomEnd),
            allMedia = mediaState.value,
            selectedMedia = selectedMediaList
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumsMergedBanner(
    constituentAlbums: List<Album>,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.albums_merged_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.albums_merged_description, constituentAlbums.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = constituentAlbums,
                    key = { it.id }
                ) { album ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(72.dp)
                    ) {
                        GlideImage(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            model = album.uri,
                            contentDescription = album.label,
                            contentScale = ContentScale.Crop,
                            requestBuilderTransform = {
                                it.centerCrop()
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .thumbnail(it.clone().sizeMultiplier(0.4f))
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = album.relativePath.removeSuffix("/"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}