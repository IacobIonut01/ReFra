/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.hazeEffectScaled
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
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.Constants.cellsList


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
    enableStickyHeaders: Boolean = true,
    customDateHeader: String? = null,
    customViewingNavigation: ((media: T) -> Unit)? = null,
    /** Route prefix for the default click-through to the media viewer. */
    mediaViewerRoute: String,
    /** Overflow actions, drawn in the app bar and the search-bar menu. */
    navActionsContent: @Composable (RowScope.(expandedDropDown: MutableState<Boolean>) -> Unit),
    emptyContent: @Composable () -> Unit = { EmptyMedia() },
    aboveGridContent: @Composable (() -> Unit)? = remember { null },
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
) {
    val showSearchBar = remember(albumId, target) { albumId == -1L && target == null }
    var canScroll by rememberSaveable { mutableStateOf(true) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { canScroll },
        flingAnimationSpec = null
    )
    var lastCellIndexState by lastCellIndex

    val dpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndexState,
        gridState = rememberLazyGridState(
            cacheWindow = dpCacheWindow
        )
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.Default) {
            canScroll = !pinchState.isZooming
            lastCellIndexState = cellsList.indexOf(pinchState.currentCells)
        }
    }
    val eventHandler = LocalEventHandler.current
    val distributor = LocalMediaDistributor.current
    val isRefreshing by distributor.isRefreshing.collectAsState()
    val refreshScope = rememberCoroutineScope()
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsState()

    Box(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
    ) {
        val scaffoldModifier = remember(showSearchBar) {
            if (!showSearchBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            else Modifier
        }
        Scaffold(
            modifier = scaffoldModifier,
            topBar = {
                AnimatedVisibility(
                    visible = !showSearchBar,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    LargeTopAppBar(
                        modifier = Modifier.hazeEffectScaled(
                            state = LocalHazeState.current,
                            style = LocalHazeBlurStyle.current
                        ),
                        title = {
                            TwoLinedDateToolbarTitle(
                                albumName = albumName,
                                dateHeader = customDateHeader ?: mediaState.value.dateHeader
                            )
                        },
                        navigationIcon = {
                            NavigationButton(
                                albumId = albumId,
                                target = target,
                                alwaysGoBack = true,
                            )
                        },
                        actions = {
                            val expandedDropDown = rememberSaveable { mutableStateOf(false) }
                            navActionsContent(expandedDropDown)
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    searchBar(isScrolling) {
                        val expandedDropDown = rememberSaveable { mutableStateOf(false) }
                        navActionsContent(expandedDropDown)
                    }
                }
            }
        ) { it ->
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
                            .padding(top = it.calculateTopPadding())
                    )
                },
            ) {
                val timelineLayoutType by rememberPreference(SettingsKeys.timelineLayoutType)
                val isMosaicLayout = timelineLayoutType == LAYOUT_MOSAIC && allowHeaders
                if (isMosaicLayout) {
                    var lastMosaicCellIndexState by lastMosaicCellIndex
                    val mosaicPinchState = rememberMosaicPinchZoomState(
                        initialColumnsIndex = lastMosaicCellIndexState,
                        gridState = rememberLazyGridState(
                            cacheWindow = dpCacheWindow
                        )
                    )
                    val mosaicGridState = mosaicPinchState.gridState

                    LaunchedEffect(mosaicPinchState.isZooming) {
                        lastMosaicCellIndexState = mosaicPinchState.currentColumnsIndex
                    }

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
                        top = it.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 128.dp
                    )
                    val hideSearchBarSetting by rememberPreference(SettingsKeys.autoHideSearchBar)
                    val topBarScrimZone by animateDpAsState(
                        targetValue = if (showSearchBar && (!isScrolling.value || !hideSearchBarSetting)) {
                            SearchBarDefaults.InputFieldHeight + paddingValues.calculateTopPadding() + 8.dp
                        } else if (showSearchBar && isScrolling.value) {
                            paddingValues.calculateTopPadding()
                        } else 0.dp,
                        label = "topBarScrimZone"
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
                                aboveGridContent = aboveGridContent,
                                isScrolling = isScrolling,
                                emptyContent = emptyContent,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = animatedContentScope,
                                onMediaClick = {
                                    if (customViewingNavigation == null) {
                                        val albumRoute = "albumId=$albumId"
                                        val targetRoute = "target=$target"
                                        val param =
                                            if (target != null) targetRoute else albumRoute
                                        eventHandler.navigate(mediaViewerRoute + "?mediaId=${it.id}&$param")
                                    } else {
                                        customViewingNavigation(it)
                                    }
                                },
                            )
                        }
                    }
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
                } else {
                    GridPinchZoomLayout(
                        state = pinchState,
                        modifier = Modifier.hazeSource(LocalHazeState.current),
                        indicatorTopPadding = it.calculateTopPadding() + 16.dp,
                    ) {
                        MediaGridView(
                            mediaState = mediaState,
                            metadataState = metadataState,
                            paddingValues = PaddingValues(
                                top = it.calculateTopPadding(),
                                bottom = paddingValues.calculateBottomPadding() + 128.dp
                            ),
                            searchBarPaddingTop = remember(paddingValues) {
                                paddingValues.calculateTopPadding()
                            },
                            showSearchBar = showSearchBar,
                            allowSelection = true,
                            canScroll = canScroll,
                            allowHeaders = allowHeaders,
                            enableStickyHeaders = enableStickyHeaders,
                            groupMethod = groupMethod,
                            aboveGridContent = aboveGridContent,
                            isScrolling = isScrolling,
                            emptyContent = emptyContent,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onMediaClick = {
                                if (customViewingNavigation == null) {
                                    val albumRoute = "albumId=$albumId"
                                    val targetRoute = "target=$target"
                                    val param =
                                        if (target != null) targetRoute else albumRoute
                                    eventHandler.navigate(mediaViewerRoute + "?mediaId=${it.id}&$param")
                                } else {
                                    customViewingNavigation(it)
                                }
                            },
                        )
                    }
                }
            } // PullToRefreshBox
        }
        val selectedMediaList = mediaState.value.media.selectedMedia(selectedSet = selectedMedia)
        selectionSheetContent(mediaState.value, selectedMediaList)
    }
}
