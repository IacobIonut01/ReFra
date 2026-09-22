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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.runtime.produceState

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.LocalScrollToTop
import com.dot.gallery.core.ScrollToTopHandler
import com.dot.gallery.core.animateOrJumpToTop
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberLastSeenVersion
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberShowFilterButton
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.Settings.Misc.rememberTimelineSort
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.core.startup.LocalStartupWorkGate
import com.dot.gallery.core.startup.StartupContentEffect
import com.dot.gallery.core.workers.CAPTURE_TIME_INDEX_PROGRESS
import com.dot.gallery.core.workers.CAPTURE_TIME_INDEX_WORK
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.MediaTypeFilter
import com.dot.gallery.feature_node.domain.model.TimelineFilter
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.domain.model.timestampFor
import com.dot.gallery.feature_node.domain.model.isIgnoredKey
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.domain.util.isFavorite
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.MosaicMediaGrid
import com.dot.gallery.feature_node.presentation.common.components.MosaicPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.StickyHeaderGrid
import com.dot.gallery.feature_node.presentation.common.components.TimelineScroller
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicMonthSegments
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
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberBottomBarInset
import com.dot.gallery.feature_node.presentation.util.roundSpToPx
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun resolveTimelineAlbumSourceIds(
    selectedAlbumIds: Set<Long>,
    sourceIdsByAlbumId: Map<Long, Set<Long>>,
): Set<Long> = selectedAlbumIds.flatMapTo(hashSetOf()) { albumId ->
    sourceIdsByAlbumId[albumId].orEmpty()
}

internal fun <T> filterTimelineItems(
    items: List<T>,
    filter: TimelineFilter,
    selectedAlbumSourceIds: Set<Long>,
    isImage: (T) -> Boolean,
    isVideo: (T) -> Boolean,
    isFavorite: (T) -> Boolean,
    timestampSeconds: (T) -> Long,
    albumId: (T) -> Long,
): List<T> = items.filter { item ->
    val typeMatch = when (filter.mediaType) {
        MediaTypeFilter.ALL -> true
        MediaTypeFilter.PHOTOS -> isImage(item)
        MediaTypeFilter.VIDEOS -> isVideo(item)
    }
    val favoriteMatch = !filter.favoritesOnly || isFavorite(item)
    val yearMatch = filter.selectedYears.isEmpty() || run {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestampSeconds(item) * 1000L
        calendar.get(java.util.Calendar.YEAR) in filter.selectedYears
    }
    val albumMatch = filter.selectedAlbumIds.isEmpty() ||
        selectedAlbumSourceIds.isEmpty() || albumId(item) in selectedAlbumSourceIds
    typeMatch && favoriteMatch && yearMatch && albumMatch
}

private fun List<Album>.filterableOnTimeline(): List<Album> = filterNot { album ->
    album.isLocked || album.relativePath.startsWith("cloud/")
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun TimelineScreen(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    mediaState: State<MediaState<Media.UriMedia>>,
    metadataState: State<MediaMetadataState>,
    storyCardsViewModel: StoryCardsViewModel? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val eventHandler = LocalEventHandler.current
    val scrollToTop = LocalScrollToTop.current
    val distributor = LocalMediaDistributor.current
    val isRefreshing by distributor.isRefreshing.collectAsStateWithLifecycle()
    val refreshScope = rememberCoroutineScope()

    // Filter state
    var timelineFilter by remember { mutableStateOf(TimelineFilter()) }
    var timelineSort by rememberTimelineSort()
    val filterSheetState = rememberAppBottomSheetState()
    val context = LocalContext.current
    val captureTimeWork = remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(CAPTURE_TIME_INDEX_WORK)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeCaptureTimeWork = captureTimeWork.value.lastOrNull {
        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED ||
            it.state == WorkInfo.State.RUNNING
    }
    val captureTimeIndexProgress = activeCaptureTimeWork?.progress
        ?.getInt(CAPTURE_TIME_INDEX_PROGRESS, 0)

    val startupGate = LocalStartupWorkGate.current
    val filterAlbumsFlow = remember(startupGate, distributor) {
        if (startupGate == null) distributor.albumsFlow
        else flow {
            startupGate.awaitFirstContent()
            emitAll(distributor.albumsFlow)
        }
    }
    val albumsState = filterAlbumsFlow.collectAsStateWithLifecycle(initialValue = AlbumState())
    val availableAlbums by rememberedDerivedState(albumsState.value) {
        albumsState.value.albums.filterableOnTimeline().sortedBy { it.label }
    }

    LaunchedEffect(availableAlbums) {
        val supportedIds = availableAlbums.mapTo(hashSetOf()) { it.id }
        val supportedSelection = timelineFilter.selectedAlbumIds.intersect(supportedIds)
        if (supportedSelection != timelineFilter.selectedAlbumIds) {
            timelineFilter = timelineFilter.copy(selectedAlbumIds = supportedSelection)
        }
    }

    val availableYears by rememberedDerivedState(mediaState.value) {
        val cal = java.util.Calendar.getInstance()
        mediaState.value.media.mapTo(mutableSetOf()) { media ->
            cal.timeInMillis = media.timestampFor(mediaState.value.dateSource) * 1000L
            cal.get(java.util.Calendar.YEAR)
        }.sortedDescending()
    }

    val dateFormats by distributor.dateFormatsFlow.collectAsStateWithLifecycle()
    val timelineSettings by distributor.settingsFlow.collectAsStateWithLifecycle()
    val groupSimilarMedia by distributor.groupSimilarMedia.collectAsStateWithLifecycle()
    val enabledGroupTypes by distributor.enabledGroupTypes.collectAsStateWithLifecycle()
    val albumSourceIdsById = remember(availableAlbums) {
        availableAlbums.associate { album -> album.id to album.sourceAlbumIds.toSet() }
    }
    val filteredMediaState: State<MediaState<Media.UriMedia>> = produceState(
        initialValue = if (timelineFilter.isActive) {
            MediaState(
                error = mediaState.value.error,
                isLoading = mediaState.value.error.isEmpty(),
            )
        } else mediaState.value,
        mediaState.value,
        timelineFilter,
        albumSourceIdsById,
        dateFormats,
        timelineSettings,
        groupSimilarMedia,
        enabledGroupTypes,
    ) {
        val source = mediaState.value
        if (!timelineFilter.isActive) {
            value = source
            return@produceState
        }
        val selectedAlbumSourceIds = resolveTimelineAlbumSourceIds(
            selectedAlbumIds = timelineFilter.selectedAlbumIds,
            sourceIdsByAlbumId = albumSourceIdsById,
        )
        val filtered = filterTimelineItems(
            items = source.media,
            filter = timelineFilter,
            selectedAlbumSourceIds = selectedAlbumSourceIds,
            isImage = { it.isImage },
            isVideo = { it.isVideo },
            isFavorite = { it.isFavorite },
            timestampSeconds = { it.timestampFor(source.dateSource) },
            albumId = { it.albumID },
        )
        val filteredIds = filtered.mapTo(HashSet(filtered.size)) { it.id }
        value = mapMediaToItem(
            data = filtered,
            error = source.error,
            albumId = -1L,
            groupByMonth = timelineSettings?.groupTimelineByMonth == true,
            groupByYear = timelineSettings?.groupTimelineByYear == true,
            groupSimilarMedia = groupSimilarMedia,
            enabledGroupTypes = enabledGroupTypes,
            cloudBackups = source.cloudBackups.filterKeys { it in filteredIds },
            defaultDateFormat = dateFormats.first,
            extendedDateFormat = dateFormats.second,
            weeklyDateFormat = dateFormats.third,
            dateSource = source.dateSource,
        ).copy(isLoading = source.isLoading)
    }
    StartupContentEffect(
        route = Screen.TimelineScreen(),
        ready = !filteredMediaState.value.isLoading
    )
    var lastSeenVersion by rememberLastSeenVersion()
    val showWhatsNew = remember(lastSeenVersion) { lastSeenVersion != BuildConfig.VERSION_NAME }

    // Story Cards
    val storyCards = if (storyCardsViewModel != null) {
        storyCardsViewModel.allCards.collectAsStateWithLifecycle().value
    } else {
        emptyList()
    }

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
                            },
                            onDismiss = {
                                lastSeenVersion = BuildConfig.VERSION_NAME
                            }
                        )
                    }
                }
                if (hasStoryCards) {
                    StoryCardsRow(
                        cards = storyCards.orEmpty(),
                        onCardClick = { card ->
                            storyCardsViewModel?.prepareViewer(card.id)
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedContentScope,
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
                                        contentDescription = stringResource(R.string.sort_and_filter),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (timelineFilter.isActive || timelineSort.kind != FilterKind.DATE ||
                                    timelineSort.orderType != OrderType.Descending
                                ) {
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
                val bottomBarInset = rememberBottomBarInset(paddingValues)
                TimelineMediaContent(
                    mediaState = filteredMediaState,
                    metadataState = metadataState,
                    scaffoldPadding = it,
                    bottomPadding = bottomBarInset + 128.dp,
                    screenTopPadding = paddingValues.calculateTopPadding(),
                    showSearchBar = true,
                    aboveGridContent = aboveGridContent,
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onMediaClick = {
                        eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(it.id, -1L))
                    },
                    emptyContent = { EmptyMedia() },
                    onRetry = { refreshScope.launch { distributor.invalidate() } },
                    scrollToTopRoute = Screen.TimelineScreen.route,
                )
            }
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
        currentSort = timelineSort,
        availableYears = availableYears,
        availableAlbums = availableAlbums,
        indexProgress = captureTimeIndexProgress,
        onApply = { newFilter, newSort ->
            val sortChanged = newSort != timelineSort
            timelineFilter = newFilter
            timelineSort = newSort
            if (sortChanged) scrollToTop.requestScrollToTop(Screen.TimelineScreen.route)
        }
    )
}