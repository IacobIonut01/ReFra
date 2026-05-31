/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.collection

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Album.rememberAlbumMediaSort
import com.dot.gallery.core.Settings.Album.rememberHideTimelineOnAlbum
import com.dot.gallery.core.Settings.Misc.rememberAlbumsGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.core.presentation.components.SelectionSheet
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
import kotlinx.coroutines.withContext

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun CollectionViewScreen(
    collectionId: Long,
    collectionName: String,
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    metadataState: State<MediaMetadataState>,
    onEditAlbums: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by rememberGridSize()
    val eventHandler = LocalEventHandler.current
    val distributor = LocalMediaDistributor.current
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    val collectionMediaFlow = remember(collectionId) {
        distributor.collectionMediaFlow(collectionId)
    }
    val mediaState = collectionMediaFlow.collectAsStateWithLifecycle()

    var albumMediaSort by rememberAlbumMediaSort()

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
                            albumName = collectionName,
                            dateHeader = mediaState.value.dateHeader
                        )
                    },
                    navigationIcon = {
                        NavigationBackButton()
                    },
                    actions = {
                        if (onEditAlbums != null) {
                            IconButton(onClick = onEditAlbums) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.edit)
                                )
                            }
                        }
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
            val hideTimelineOnAlbum by rememberHideTimelineOnAlbum()
            val albumsGroupMethod by rememberAlbumsGroupMethod()
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

                val mappedData by remember(mediaState, albumsGroupMethod) {
                    derivedStateOf {
                        when (albumsGroupMethod) {
                            Settings.Misc.GROUP_MONTHLY -> mediaState.value.mappedMediaWithMonthly
                            Settings.Misc.GROUP_YEARLY -> mediaState.value.mappedMediaWithYearly
                            else -> mediaState.value.mappedMedia
                        }.toMutableStateList()
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
                        aboveGridContent = null,
                        isScrolling = isScrolling,
                        emptyContent = {
                            EmptyMedia(
                                title = stringResource(R.string.collection_empty)
                            )
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMediaClick = {
                            eventHandler.navigate(Screen.MediaViewScreen.idAndCollection(it.id, collectionId))
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
                        groupMethod = if (!hideTimelineOnAlbum) albumsGroupMethod else Settings.Misc.GROUP_NORMAL,
                        paddingValues = PaddingValues(
                            top = it.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + 128.dp
                        ),
                        canScroll = canScroll,
                        allowHeaders = !hideTimelineOnAlbum,
                        aboveGridContent = null,
                        isScrolling = isScrolling,
                        emptyContent = {
                            EmptyMedia(
                                title = stringResource(R.string.collection_empty)
                            )
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope
                    ) {
                        eventHandler.navigate(Screen.MediaViewScreen.idAndCollection(it.id, collectionId))
                    }
                }
            }
        }
        val selectedMediaList by selectedMedia(
            media = mediaState.value.media,
            selectedSet = selectedMedia
        )
        SelectionSheet(
            modifier = Modifier
                .align(Alignment.BottomEnd),
            allMedia = mediaState.value,
            selectedMedia = selectedMediaList,
            collectionId = collectionId
        )
    }
}
