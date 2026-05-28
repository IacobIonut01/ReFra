/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.timeline

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberLastSeenVersion
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberShowFilterButton
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.core.toggleNavigationBar
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.MediaTypeFilter
import com.dot.gallery.feature_node.domain.model.TimelineFilter
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.domain.model.isIgnoredKey
import com.dot.gallery.feature_node.domain.util.isFavorite
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.MosaicMediaGrid
import com.dot.gallery.feature_node.presentation.common.components.MosaicPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.StickyHeaderGrid
import com.dot.gallery.feature_node.presentation.common.components.TimelineScroller
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicPinchZoomState
import com.dot.gallery.feature_node.presentation.common.components.rememberStickyHeaderItem
import com.dot.gallery.feature_node.presentation.help.components.WhatsNewHeroCard
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.search.MainSearchBar
import com.dot.gallery.feature_node.presentation.storycards.StoryCardsViewModel
import com.dot.gallery.feature_node.presentation.storycards.components.StoryCardsRow
import com.dot.gallery.feature_node.presentation.timeline.components.TimelineFilterSheet
import com.dot.gallery.feature_node.presentation.timeline.components.TimelineNavActions
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.roundSpToPx
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun TimelineScreen(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    mediaState: State<MediaState<Media.UriMedia>>,
    metadataState: State<MediaMetadataState>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by rememberGridSize()
    val timelineLayoutType by rememberTimelineLayoutType()
    val isMosaicLayout = timelineLayoutType == Settings.Misc.LAYOUT_MOSAIC
    val eventHandler = LocalEventHandler.current
    val distributor = LocalMediaDistributor.current
    val isRefreshing by distributor.isRefreshing.collectAsStateWithLifecycle()
    val refreshScope = rememberCoroutineScope()

    // Filter state
    var timelineFilter by remember { mutableStateOf(TimelineFilter()) }
    val filterSheetState = rememberAppBottomSheetState()

    val albumsState = distributor.albumsFlow.collectAsStateWithLifecycle()
    val availableAlbums by rememberedDerivedState(albumsState.value) {
        albumsState.value.albums.sortedBy { it.label }
    }

    val availableYears by rememberedDerivedState(mediaState.value) {
        val cal = java.util.Calendar.getInstance()
        mediaState.value.media.mapTo(mutableSetOf()) { media ->
            cal.timeInMillis = media.definedTimestamp * 1000L
            cal.get(java.util.Calendar.YEAR)
        }.sortedDescending()
    }

    val filteredMediaState: State<MediaState<Media.UriMedia>> = remember(mediaState, timelineFilter) {
        derivedStateOf {
            val state = mediaState.value
            if (!timelineFilter.isActive) return@derivedStateOf state

            val filtered = state.media.filter { media ->
                val typeMatch = when (timelineFilter.mediaType) {
                    MediaTypeFilter.ALL -> true
                    MediaTypeFilter.PHOTOS -> media.isImage
                    MediaTypeFilter.VIDEOS -> media.isVideo
                }
                val favMatch = if (timelineFilter.favoritesOnly) media.isFavorite else true
                val yearMatch = if (timelineFilter.selectedYears.isNotEmpty()) {
                    val cal = java.util.Calendar.getInstance()
                    cal.timeInMillis = media.definedTimestamp * 1000L
                    cal.get(java.util.Calendar.YEAR) in timelineFilter.selectedYears
                } else true
                val albumMatch = if (timelineFilter.selectedAlbumIds.isNotEmpty()) {
                    media.albumID in timelineFilter.selectedAlbumIds
                } else true
                typeMatch && favMatch && yearMatch && albumMatch
            }
            val filteredIds = filtered.mapTo(HashSet(filtered.size)) { it.id }
            state.copy(
                media = filtered,
                pagerMedia = state.pagerMedia.filter { it.id in filteredIds },
                mappedMedia = state.mappedMedia.filter { item ->
                    when (item) {
                        is com.dot.gallery.feature_node.domain.model.MediaItem.MediaViewItem -> item.media.id in filteredIds
                        is com.dot.gallery.feature_node.domain.model.MediaItem.Header -> item.data.any { it in filteredIds }
                    }
                },
                mappedMediaWithMonthly = state.mappedMediaWithMonthly.filter { item ->
                    when (item) {
                        is com.dot.gallery.feature_node.domain.model.MediaItem.MediaViewItem -> item.media.id in filteredIds
                        is com.dot.gallery.feature_node.domain.model.MediaItem.Header -> item.data.any { it in filteredIds }
                    }
                },
                headers = state.headers.filter { header -> header.data.any { it in filteredIds } }
            )
        }
    }
    var lastSeenVersion by rememberLastSeenVersion()
    val showWhatsNew = remember(lastSeenVersion) { lastSeenVersion != BuildConfig.VERSION_NAME }

    // Story Cards
    val storyCardsViewModel = hiltViewModel<StoryCardsViewModel>()
    val storyCards by storyCardsViewModel.allCards.collectAsStateWithLifecycle()

    val hasStoryCards = storyCards?.isNotEmpty() == true
    val aboveGridContent: @Composable (() -> Unit)? = if (showWhatsNew || hasStoryCards) {
        {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showWhatsNew) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WhatsNewHeroCard(
                            versionName = BuildConfig.VERSION_NAME,
                            onClick = {
                                lastSeenVersion = BuildConfig.VERSION_NAME
                                eventHandler.navigate(Screen.WhatsNewScreen())
                            }
                        )
                    }
                }
                if (hasStoryCards) {
                    StoryCardsRow(
                        cards = storyCards.orEmpty(),
                        onCardClick = { _, card ->
                            eventHandler.navigate(Screen.StoryViewerScreen.cardId(card.id))
                        },
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp)
                    )
                }
            }
        }
    } else null
    val selector = LocalMediaSelector.current
    val selectionState = selector.isSelectionActive.collectAsStateWithLifecycle()
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    val dpCacheWindow = LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 2f)
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

    LaunchedEffect(selectionState.value) {
        eventHandler.toggleNavigationBar(!selectionState.value)
    }

    Box(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
    ) {
        Scaffold(
            topBar = {
                val showFilterButton by rememberShowFilterButton()
                MainSearchBar(
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    menuItems = { TimelineNavActions() },
                    searchBarTrailingIcon = if (showFilterButton) {
                        {
                            Box {
                                IconButton(
                                    onClick = {
                                        refreshScope.launch { filterSheetState.show() }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = stringResource(R.string.filter),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (timelineFilter.isActive) {
                                    Badge(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    } else null,
                )
            }
        ) { it ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshScope.launch { distributor.invalidate() } },
            ) {
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

                val mappedData by rememberedDerivedState(filteredMediaState.value) {
                    filteredMediaState.value.mappedMediaWithMonthly
                }
                val headers by rememberedDerivedState(filteredMediaState.value) {
                    filteredMediaState.value.headers
                }
                val mosaicPaddingValues = remember(paddingValues, it) {
                    PaddingValues(
                        top = it.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 128.dp
                    )
                }
                val stickyHeaderItem by rememberStickyHeaderItem(
                    gridState = mosaicGridState,
                    mediaState = filteredMediaState
                )

                val hideSearchBarSetting by rememberAutoHideSearchBar()
                val searchBarPaddingTop = remember(paddingValues) {
                    paddingValues.calculateTopPadding()
                }
                val searchBarPadding by animateDpAsState(
                    targetValue = remember(
                        isScrolling.value,
                        searchBarPaddingTop,
                        hideSearchBarSetting
                    ) {
                        if (!isScrolling.value || !hideSearchBarSetting) {
                            SearchBarDefaults.InputFieldHeight + searchBarPaddingTop + 8.dp
                        } else searchBarPaddingTop
                    },
                    label = "mosaicSearchBarPadding"
                )

                val density = LocalDensity.current
                val searchBarPaddingPx by remember(density, searchBarPadding) {
                    derivedStateOf { with(density) { searchBarPadding.roundToPx() } }
                }

                StickyHeaderGrid(
                    state = mosaicGridState,
                    modifier = Modifier.fillMaxSize(),
                    headerMatcher = { item -> item.key.isHeaderKey || item.key.isIgnoredKey },
                    searchBarOffset = { 28.roundSpToPx(density) + searchBarPaddingPx },
                    toolbarOffset = { 0 },
                    stickyHeader = {
                        val show by remember {
                            derivedStateOf {
                                filteredMediaState.value.media.isNotEmpty() && stickyHeaderItem != null
                            }
                        }
                        AnimatedVisibility(
                            visible = show,
                            enter = enterAnimation,
                            exit = exitAnimation
                        ) {
                            val text by rememberedDerivedState(stickyHeaderItem) { stickyHeaderItem ?: "" }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 24.dp + searchBarPadding, bottom = 24.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                ) {
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
                            mediaState = filteredMediaState,
                            metadataState = metadataState,
                            mappedData = mappedData,
                            paddingValues = mosaicPaddingValues,
                            allowSelection = true,
                            canScroll = !mosaicPinchState.isZooming,
                            allowHeaders = true,
                            aboveGridContent = aboveGridContent,
                            isScrolling = isScrolling,
                            emptyContent = { EmptyMedia() },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onMediaClick = {
                                eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(it.id, -1L))
                            },
                        )
                    }
                    }
                }
            } else {
                GridPinchZoomLayout(
                    state = pinchState,
                    modifier = Modifier.hazeSource(LocalHazeState.current),
                    indicatorTopPadding = it.calculateTopPadding() + 16.dp,
                ) {
                    MediaGridView(
                        mediaState = filteredMediaState,
                        metadataState = metadataState,
                        paddingValues = remember(paddingValues, it) {
                            PaddingValues(
                                top = it.calculateTopPadding(),
                                bottom = paddingValues.calculateBottomPadding() + 128.dp
                            )
                        },
                        searchBarPaddingTop = remember(paddingValues) {
                            paddingValues.calculateTopPadding()
                        },
                        showSearchBar = true,
                        allowSelection = true,
                        canScroll = canScroll,
                        enableStickyHeaders = true,
                        showMonthlyHeader = true,
                        aboveGridContent = aboveGridContent,
                        isScrolling = isScrolling,
                        emptyContent = { EmptyMedia() },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMediaClick = {
                            eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(it.id, -1L))
                        },
                    )
                }
            }
            } // PullToRefreshBox
        }
        val selectedMediaList by selectedMedia(
            media = filteredMediaState.value.media,
            selectedSet = selectedMedia
        )
        SelectionSheet(
            modifier = Modifier.align(Alignment.BottomEnd),
            allMedia = filteredMediaState.value,
            selectedMedia = selectedMediaList
        )
    }

    TimelineFilterSheet(
        sheetState = filterSheetState,
        currentFilter = timelineFilter,
        availableYears = availableYears,
        availableAlbums = availableAlbums,
        onApply = { newFilter -> timelineFilter = newFilter }
    )
}