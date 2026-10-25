package com.dot.gallery.feature.timeline

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.LocalScrollToTop
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.LocalGalleryHeaderReveal
import com.dot.gallery.core.presentation.components.LocalGalleryPaneChrome
import com.dot.gallery.core.presentation.components.rememberGalleryHeaderRevealState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.TimelineFilter
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.MediaGroupType
import com.dot.gallery.domain.util.OrderType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.timeline.generated.resources.Res
import com.dot.gallery.feature.timeline.generated.resources.sort_and_filter

/**
 * Timeline scaffold: search-bar slot, pull-to-refresh, pinch-zoom media content,
 * selection sheet, and the filter bottom sheet. Filtering, year extraction, and
 * filter/sort state are owned here — the platform wrapper supplies the already
 * collected [mediaState], top bar chrome, and navigation callbacks.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineContent(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    mediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    albums: List<Album>,
    dateFormats: Triple<String, String, String>,
    groupByMonth: Boolean,
    groupByYear: Boolean,
    groupSimilarMedia: Boolean,
    enabledGroupTypes: Set<MediaGroupType>,
    isRefreshing: Boolean,
    onRefresh: suspend () -> Unit,
    aboveGridContent: (@Composable () -> Unit)?,
    bottomBarInset: Dp,
    lastCellIndex: MutableState<Int>,
    lastMosaicCellIndex: MutableState<Int>,
    indexProgress: Int?,
    scrollToTopRoute: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMediaClick: (UriMedia) -> Unit,
    emptyContent: @Composable () -> Unit = { EmptyMedia() },
    selectionOverlay: @Composable BoxScope.(
        allMedia: MediaState<UriMedia>,
        selectedMediaList: androidx.compose.runtime.snapshots.SnapshotStateList<UriMedia>,
    ) -> Unit,
    topBar: @Composable (searchBarTrailingIcon: (@Composable () -> Unit)?) -> Unit,
) {
    val scrollToTop = LocalScrollToTop.current
    val refreshScope = rememberCoroutineScope()
    val paneChrome = LocalGalleryPaneChrome.current
    val headerReveal = rememberGalleryHeaderRevealState()

    var timelineFilter by remember { mutableStateOf(TimelineFilter()) }
    var timelineSort by rememberPreference(SettingsKeys.timelineSort)
    val filterSheetState = rememberAppBottomSheetState()

    val availableAlbums by rememberedDerivedState(albums) {
        albums.filterableOnTimeline().sortedBy { it.label }
    }

    LaunchedEffect(availableAlbums) {
        val supportedIds = availableAlbums.mapTo(hashSetOf()) { it.id }
        val supportedSelection = timelineFilter.selectedAlbumIds.intersect(supportedIds)
        if (supportedSelection != timelineFilter.selectedAlbumIds) {
            timelineFilter = timelineFilter.copy(selectedAlbumIds = supportedSelection)
        }
    }

    val availableYears = rememberAvailableTimelineYears(mediaState)

    val albumSourceIdsById = remember(availableAlbums) {
        availableAlbums.associate { album -> album.id to album.sourceAlbumIds.toSet() }
    }
    val filteredMediaState = rememberFilteredTimelineState(
        mediaState = mediaState,
        timelineFilter = timelineFilter,
        albumSourceIdsById = albumSourceIdsById,
        dateFormats = dateFormats,
        groupByMonth = groupByMonth,
        groupByYear = groupByYear,
        groupSimilarMedia = groupSimilarMedia,
        enabledGroupTypes = enabledGroupTypes,
    )

    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsState()

    Box(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
    ) {
        CompositionLocalProvider(LocalGalleryHeaderReveal provides headerReveal) {
            Scaffold(
                containerColor = if (paneChrome) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.background,
                topBar = {
                    val showFilterButton by rememberPreference(SettingsKeys.showFilterButton)
                    topBar(
                        if (showFilterButton) {
                            {
                                Box {
                                    IconButton(
                                        onClick = {
                                            refreshScope.launch { filterSheetState.show() }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FilterList,
                                            contentDescription = stringResource(Res.string.sort_and_filter),
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
            ) { scaffoldPadding ->
                val ptrState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { refreshScope.launch { onRefresh() } },
                    state = ptrState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = ptrState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = scaffoldPadding.calculateTopPadding())
                        )
                    },
                ) {
                    // Inside pull-to-refresh, so a pull at the top opens backup progress first.
                    Box(Modifier.nestedScroll(headerReveal.nestedScrollConnection)) {
                        TimelineMediaContent(
                            mediaState = filteredMediaState,
                            metadataState = metadataState,
                            scaffoldPadding = scaffoldPadding,
                            bottomPadding = bottomBarInset + 128.dp,
                            screenTopPadding = if (paneChrome) 0.dp else paddingValues.calculateTopPadding(),
                            showSearchBar = true,
                            aboveGridContent = aboveGridContent,
                            isScrolling = isScrolling,
                            lastCellIndex = lastCellIndex,
                            lastMosaicCellIndex = lastMosaicCellIndex,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onMediaClick = onMediaClick,
                            emptyContent = emptyContent,
                            onRetry = { refreshScope.launch { onRefresh() } },
                            scrollToTopRoute = scrollToTopRoute,
                        )
                    }
                }
            }
        }
        val selectedMediaList by selectedMedia(
            media = filteredMediaState.value.media,
            selectedSet = selectedMedia
        )
        selectionOverlay(filteredMediaState.value, selectedMediaList)
    }

    TimelineFilterSheet(
        sheetState = filterSheetState,
        currentFilter = timelineFilter,
        currentSort = timelineSort,
        availableYears = availableYears,
        availableAlbums = availableAlbums,
        indexProgress = indexProgress,
        onApply = { newFilter, newSort ->
            val sortChanged = newSort != timelineSort
            timelineFilter = newFilter
            timelineSort = newSort
            if (sortChanged) scrollToTop.requestScrollToTop(scrollToTopRoute)
        }
    )
}
