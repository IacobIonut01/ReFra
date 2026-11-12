/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.settings.GROUP_MONTHLY
import com.dot.gallery.domain.settings.GROUP_NORMAL
import com.dot.gallery.domain.settings.GROUP_YEARLY
import com.dot.gallery.domain.settings.LAYOUT_MOSAIC
import com.dot.gallery.domain.settings.SettingsKeys
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.Constants.cellsList


/**
 * Photo grid of a pushed page (Favorites, Trash, a category, a person, the cloud archive). The
 * grid scrolls under a [GalleryPageTopBar] that is opaque at rest and turns into the blur bar
 * once the grid moves. With [detailHeader] the [aboveGridContent] is a header carrying the
 * name, so the bar only shows the title once that header has scrolled under it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun <T: Media> MediaScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    albumId: Long = remember { -1L },
    target: String? = remember { null },
    albumName: String,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    allowHeaders: Boolean = true,
    groupMethod: String = GROUP_NORMAL,
    customViewingNavigation: ((media: T) -> Unit)? = null,
    /** Route prefix for the default click-through to the media viewer. */
    mediaViewerRoute: String,
    /**
     * Top-bar actions, built from [GalleryPageTopBarAction]; also drawn in the search-bar menu.
     * Null leaves the bar with only the back button.
     */
    navActionsContent: (@Composable RowScope.(expandedDropDown: MutableState<Boolean>) -> Unit)? = null,
    emptyContent: @Composable () -> Unit = { EmptyMedia() },
    aboveGridContent: @Composable (() -> Unit)? = remember { null },
    /** [aboveGridContent] is a detail header with the page name; the bar takes the name over once it scrolls away. */
    detailHeader: Boolean = false,
    /** Selection overlay — hidden for [TARGET_TRASH]. Callers supply the sheet composable. */
    selectionSheetContent: @Composable BoxScope.(
        allMedia: MediaState<T>,
        selectedMediaList: androidx.compose.runtime.snapshots.SnapshotStateList<T>,
    ) -> Unit,
    isScrolling: MutableState<Boolean> = remember { mutableStateOf(false) },
    /** Grid-cell index state — backed by a platform store on Android. */
    lastCellIndex: MutableState<Int>,
    lastMosaicCellIndex: MutableState<Int>,
    /** Search-bar chrome slot — the platform wrapper supplies `MainSearchBar`. */
    searchBar: @Composable (
        isScrolling: MutableState<Boolean>,
        menuItems: @Composable RowScope.() -> Unit,
    ) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    /** Docs seam: opens the grid scrolled to this item. */
    initialFirstVisibleItemIndex: Int = 0,
) {
    val showSearchBar = remember(albumId, target) { albumId == -1L && target == null }
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndexState by lastCellIndex

    val dpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndexState,
        gridState = rememberLazyGridState(
            cacheWindow = dpCacheWindow,
            initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        )
    )
    var lastMosaicCellIndexState by lastMosaicCellIndex
    val mosaicPinchState = rememberMosaicPinchZoomState(
        initialColumnsIndex = lastMosaicCellIndexState,
        gridState = rememberLazyGridState(
            cacheWindow = dpCacheWindow,
            initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        )
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.Default) {
            canScroll = !pinchState.isZooming
            lastCellIndexState = cellsList.indexOf(pinchState.currentCells)
        }
    }
    LaunchedEffect(mosaicPinchState.isZooming) {
        lastMosaicCellIndexState = mosaicPinchState.currentColumnsIndex
    }
    val eventHandler = LocalEventHandler.current
    val distributor = LocalMediaDistributor.current
    val isRefreshing by distributor.isRefreshing.collectAsState()
    val refreshScope = rememberCoroutineScope()
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsState()
    val isSelectionActive by selector.isSelectionActive.collectAsState()

    val timelineLayoutType by rememberPreference(SettingsKeys.timelineLayoutType)
    val isMosaicLayout = timelineLayoutType == LAYOUT_MOSAIC && allowHeaders
    val activeGridState = if (isMosaicLayout) mosaicPinchState.gridState else pinchState.gridState
    val headerCollapsed by rememberDetailHeaderCollapsed(activeGridState)
    val gridScrolled by remember(activeGridState) {
        derivedStateOf {
            activeGridState.firstVisibleItemIndex > 0 || activeGridState.firstVisibleItemScrollOffset > 0
        }
    }
    val hazeState = LocalHazeState.current
    val topBarHeight = galleryDetailTopBarHeight()
    // The search-bar variant keeps the timeline's floating bar; every pushed page gets the blur bar.
    val contentTopPadding = if (showSearchBar) paddingValues.calculateTopPadding() else topBarHeight
    val onMediaClick: (T) -> Unit = {
        if (customViewingNavigation == null) {
            val albumRoute = "albumId=$albumId"
            val targetRoute = "target=$target"
            val param = if (target != null) targetRoute else albumRoute
            eventHandler.navigate(mediaViewerRoute + "?mediaId=${it.id}&$param")
        } else {
            customViewingNavigation(it)
        }
    }

    Box(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
    ) {
        val ptrState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshScope.launch { distributor.invalidate() } },
            state = ptrState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = ptrState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentTopPadding)
                )
            },
        ) {
            if (isMosaicLayout) {
                val mosaicGridState = mosaicPinchState.gridState
                val mappedData by remember(mediaState, groupMethod) {
                    derivedStateOf {
                        when (groupMethod) {
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
                val mosaicPaddingValues = PaddingValues(
                    top = if (showSearchBar) {
                        SearchBarDefaults.InputFieldHeight + paddingValues.calculateTopPadding() + 8.dp
                    } else topBarHeight,
                    bottom = selectionAwareBottomPadding(paddingValues.calculateBottomPadding() + 128.dp)
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
                        segments = rememberMosaicMonthSegments(
                            mappedData = mappedData,
                            columns = currentColumns,
                            allowHeaders = true,
                            leadingItemCount = if (aboveGridContent != null) 1 else 0,
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
                            paddingValues = mosaicPaddingValues,
                            allowSelection = true,
                            canScroll = !mosaicPinchState.isZooming,
                            allowHeaders = true,
                            aboveGridContent = aboveGridContent,
                            isScrolling = isScrolling,
                            emptyContent = emptyContent,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onMediaClick = onMediaClick,
                        )
                    }
                }
                if (showSearchBar) {
                    val hideSearchBarSetting by rememberPreference(SettingsKeys.autoHideSearchBar)
                    val topBarScrimZone by animateDpAsState(
                        targetValue = if (!isScrolling.value || !hideSearchBarSetting) {
                            SearchBarDefaults.InputFieldHeight + paddingValues.calculateTopPadding() + 8.dp
                        } else paddingValues.calculateTopPadding(),
                        label = "topBarScrimZone"
                    )
                    FloatingTopBarScrim(
                        barZoneHeight = topBarScrimZone,
                        visibility = {
                            topScrimVisibility(
                                mosaicGridState.firstVisibleItemIndex,
                                mosaicGridState.firstVisibleItemScrollOffset,
                                TopBarScrimScrollFade.toPx(),
                            )
                        },
                    )
                }
            } else {
                GridPinchZoomLayout(
                    state = pinchState,
                    modifier = Modifier.hazeSource(hazeState),
                    indicatorTopPadding = contentTopPadding + 16.dp,
                ) {
                    MediaGridView(
                        mediaState = mediaState,
                        metadataState = metadataState,
                        paddingValues = PaddingValues(
                            top = contentTopPadding,
                            bottom = selectionAwareBottomPadding(paddingValues.calculateBottomPadding() + 128.dp)
                        ),
                        searchBarPaddingTop = remember(paddingValues) {
                            paddingValues.calculateTopPadding()
                        },
                        showSearchBar = showSearchBar,
                        allowSelection = true,
                        canScroll = canScroll,
                        allowHeaders = allowHeaders,
                        // Sticky date headers clash with the blur bar.
                        enableStickyHeaders = showSearchBar && allowHeaders,
                        groupMethod = groupMethod,
                        aboveGridContent = aboveGridContent,
                        isScrolling = isScrolling,
                        emptyContent = emptyContent,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMediaClick = onMediaClick,
                    )
                }
            }
        } // PullToRefreshBox
        if (showSearchBar) {
            searchBar(isScrolling) {
                val expandedDropDown = rememberSaveable { mutableStateOf(false) }
                navActionsContent?.invoke(this, expandedDropDown)
            }
        } else {
            val showTitle = !detailHeader || headerCollapsed
            val count = mediaState.value.media.size
            CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
                GalleryPageTopBar(
                    title = if (showTitle) albumName else "",
                    count = count.takeIf { showTitle && it > 0 }?.toString(),
                    onBack = if (isSelectionActive) selector::clearSelection else null,
                    navigationIcon = if (isSelectionActive) Icons.Outlined.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    hazeState = hazeState,
                    scrolled = if (detailHeader) headerCollapsed else gridScrolled,
                    actions = navActionsContent?.let { content ->
                        {
                            val expandedDropDown = rememberSaveable { mutableStateOf(false) }
                            content(expandedDropDown)
                        }
                    },
                )
            }
        }
        val selectedMediaList = mediaState.value.media.selectedMedia(selectedSet = selectedMedia)
        selectionSheetContent(mediaState.value, selectedMediaList)
    }
}
