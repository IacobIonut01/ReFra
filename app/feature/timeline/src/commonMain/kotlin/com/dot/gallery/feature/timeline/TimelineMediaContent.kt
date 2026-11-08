package com.dot.gallery.feature.timeline

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.dot.gallery.ui.theme.isDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ScrollToTopHandler
import com.dot.gallery.core.animateOrJumpToTop
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.core.presentation.components.FloatingTopBarScrim
import com.dot.gallery.core.presentation.components.TopBarScrimScrollFade
import com.dot.gallery.core.presentation.components.topScrimVisibility
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.MediaGridView
import com.dot.gallery.core.presentation.components.MosaicPinchZoomLayout
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.components.rememberMosaicPinchZoomState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.roundSpToPx
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.GROUP_MONTHLY
import com.dot.gallery.domain.settings.GROUP_NORMAL
import com.dot.gallery.domain.settings.GROUP_YEARLY
import com.dot.gallery.domain.settings.LAYOUT_MOSAIC
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.model.isHeaderKey
import com.dot.gallery.domain.model.isIgnoredKey
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.core.presentation.components.MosaicMediaGrid
import com.dot.gallery.core.presentation.components.TimelineScroller
import com.dot.gallery.core.presentation.components.rememberMosaicMonthSegments
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.presentation.components.rememberStickyHeaderItem
import com.dot.gallery.core.presentation.components.StickyHeaderGrid
import com.dot.gallery.core.presentation.components.LocalTimelineDecorations
import com.dot.gallery.core.presentation.components.TimelineDecorations
import com.dot.gallery.core.presentation.components.TimelineFloatingDate
import com.dot.gallery.core.presentation.components.TimelineIndex
import com.dot.gallery.core.presentation.components.TimelineMonth
import com.dot.gallery.core.presentation.components.TimelinePlace
import com.dot.gallery.core.presentation.components.TimelineSignals
import com.dot.gallery.core.presentation.components.buildTimelineIndex
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineMediaContent(
    mediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    scaffoldPadding: PaddingValues,
    bottomPadding: Dp,
    screenTopPadding: Dp,
    showSearchBar: Boolean,
    aboveGridContent: (@Composable () -> Unit)?,
    isScrolling: MutableState<Boolean>,
    lastCellIndex: MutableState<Int>,
    lastMosaicCellIndex: MutableState<Int>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMediaClick: (UriMedia) -> Unit,
    emptyContent: @Composable () -> Unit,
    onRetry: (() -> Unit)? = null,
    scrollToTopRoute: String? = null,
    /** Date headers pin to the top while scrolling; pages under a blur bar turn this off. */
    stickyHeaders: Boolean = true,
    /** Grid states of the grid and mosaic layouts, hoisted for pages that track the scroll. */
    gridState: LazyGridState = rememberTimelineGridState(),
    mosaicGridState: LazyGridState = rememberTimelineGridState(),
    /** Month highlight cards, places, the floating date and the months view (main timeline only). */
    decorate: Boolean = false,
    onOpenPlace: ((TimelinePlace) -> Unit)? = null,
    onPlayMonth: ((TimelineMonth) -> Unit)? = null,
) {
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndexState by lastCellIndex
    val timelineLayoutType by rememberPreference(SettingsKeys.timelineLayoutType)
    val timelineGroupByDate by rememberPreference(SettingsKeys.timelineGroupByDate)
    val timelineGroupMethod by rememberPreference(SettingsKeys.timelineGroupMethod)
    val isMosaicLayout = timelineLayoutType == LAYOUT_MOSAIC && timelineGroupByDate
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndexState,
        gridState = gridState,
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.Default) {
            canScroll = !pinchState.isZooming
            lastCellIndexState = cellsList.indexOf(pinchState.currentCells)
        }
    }

    if (!isMosaicLayout && scrollToTopRoute != null) {
        ScrollToTopHandler(scrollToTopRoute) { pinchState.gridState.animateOrJumpToTop() }
    }

    // Scaffold updates its PaddingValues in place when the backup header expands.
    val contentPadding = PaddingValues(
        top = scaffoldPadding.calculateTopPadding(),
        bottom = bottomPadding,
    )

    val signals = remember { TimelineSignals() }
    var showMonths by rememberSaveable { mutableStateOf(false) }
    val monthHighlights by rememberPreference(SettingsKeys.timelineMonthHighlights)
    val monthRecaps by rememberPreference(SettingsKeys.timelineMonthRecaps)
    val headerPlaces by rememberPreference(SettingsKeys.timelineHeaderPlaces)
    val floatingDateLabel by rememberPreference(SettingsKeys.timelineFloatingDateLabel)
    val index by produceState(TimelineIndex.Empty, decorate, mediaState.value, metadataState.value, timelineGroupMethod) {
        if (!decorate) {
            value = TimelineIndex.Empty
            return@produceState
        }
        val state = mediaState.value
        val metadata = metadataState.value
        value = withContext(Dispatchers.Default) {
            val mapped = when (timelineGroupMethod) {
                GROUP_MONTHLY -> state.mappedMediaWithMonthly
                GROUP_YEARLY -> state.mappedMediaWithYearly
                else -> state.mappedMedia
            }
            buildTimelineIndex(state.media, mapped, metadata.metadataMap, state.dateSource)
        }
    }
    val decorations = if (decorate) {
        remember(index, monthHighlights, monthRecaps, headerPlaces, floatingDateLabel, timelineGroupMethod, onOpenPlace, onPlayMonth) {
            TimelineDecorations(
                index = index,
                signals = signals,
                // Yearly grouping turns the big headers into years, which keep their plain header.
                monthHighlights = monthHighlights && timelineGroupMethod != GROUP_YEARLY,
                monthRecaps = monthRecaps,
                headerPlaces = headerPlaces,
                floatingDateLabel = floatingDateLabel,
                onOpenPlace = onOpenPlace,
                onPlayMonth = onPlayMonth,
                onOpenMonths = { showMonths = true },
            )
        }
    } else null
    val openMonths: (() -> Unit)? = if (decorate && timelineGroupByDate) {
        { showMonths = true }
    } else null

    CompositionLocalProvider(LocalTimelineDecorations provides decorations) {
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
    if (!isMosaicLayout) {
        GridPinchZoomLayout(
            state = pinchState,
            modifier = Modifier.hazeSource(LocalHazeState.current),
            indicatorTopPadding = contentPadding.calculateTopPadding() + 16.dp,
            onPinchOutPastMin = openMonths,
        ) {
            MediaGridView(
                mediaState = mediaState,
                metadataState = metadataState,
                paddingValues = contentPadding,
                searchBarPaddingTop = if (showSearchBar) screenTopPadding else 0.dp,
                showSearchBar = showSearchBar,
                allowSelection = true,
                canScroll = canScroll,
                allowHeaders = timelineGroupByDate,
                enableStickyHeaders = timelineGroupByDate && stickyHeaders,
                groupMethod = if (timelineGroupByDate) timelineGroupMethod else GROUP_NORMAL,
                aboveGridContent = aboveGridContent,
                isScrolling = isScrolling,
                emptyContent = emptyContent,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                onMediaClick = onMediaClick,
                onRetry = onRetry,
            )
        }
        return@Box
    }

    var lastMosaicCellIndexState by lastMosaicCellIndex
    val mosaicPinchState = rememberMosaicPinchZoomState(
        initialColumnsIndex = lastMosaicCellIndexState,
        gridState = mosaicGridState,
    )
    LaunchedEffect(mosaicPinchState.isZooming) {
        lastMosaicCellIndexState = mosaicPinchState.currentColumnsIndex
    }
    if (scrollToTopRoute != null) {
        ScrollToTopHandler(scrollToTopRoute) { mosaicGridState.animateOrJumpToTop() }
    }

    val mappedData by rememberedDerivedState(mediaState.value, timelineGroupMethod) {
        when (timelineGroupMethod) {
            GROUP_MONTHLY -> mediaState.value.mappedMediaWithMonthly
            GROUP_YEARLY -> mediaState.value.mappedMediaWithYearly
            else -> mediaState.value.mappedMedia
        }
    }
    val headers by rememberedDerivedState(mediaState.value) { mediaState.value.headers }
    val stickyHeaderItem by rememberStickyHeaderItem(gridState = mosaicGridState, mediaState = mediaState)
    val hideSearchBarSetting by rememberPreference(SettingsKeys.autoHideSearchBar)
    val searchBarPadding by animateDpAsState(
        targetValue = when {
            !showSearchBar -> scaffoldPadding.calculateTopPadding()
            !isScrolling.value || !hideSearchBarSetting -> maxOf(
                SearchBarDefaults.InputFieldHeight + screenTopPadding + 8.dp,
                scaffoldPadding.calculateTopPadding(),
            )
            else -> screenTopPadding
        },
        label = "sharedTimelineHeaderPadding",
    )
    val density = LocalDensity.current
    val headerOffsetPx by remember(density, searchBarPadding, showSearchBar) {
        derivedStateOf {
            val base = with(density) { searchBarPadding.roundToPx() }
            if (showSearchBar) 28.roundSpToPx(density) + base else base
        }
    }

    StickyHeaderGrid(
        state = mosaicGridState,
        modifier = Modifier.fillMaxSize(),
        headerMatcher = { item -> item.key.isHeaderKey || item.key.isIgnoredKey },
        searchBarOffset = { headerOffsetPx },
        toolbarOffset = { 0 },
        stickyHeader = {
            val show by remember {
                derivedStateOf { stickyHeaders && mediaState.value.media.isNotEmpty() && stickyHeaderItem != null }
            }
            AnimatedVisibility(visible = show, enter = enterAnimation, exit = exitAnimation) {
                val text by rememberedDerivedState(stickyHeaderItem) { stickyHeaderItem ?: "" }
                if (decorations?.floatingDateLabel == true) {
                    TimelineFloatingDate(text = text, topPadding = searchBarPadding, decorations = decorations, gridState = mosaicGridState)
                    return@AnimatedVisibility
                }
                val darkTheme = isDarkTheme()
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.let { style ->
                        if (!darkTheme) style.copy(
                            shadow = Shadow(Color.White, Offset.Zero, 10f),
                        ) else style
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                    Color.Transparent,
                                )
                            )
                        )
                        .padding(horizontal = 16.dp)
                        .padding(top = 20.dp + searchBarPadding, bottom = 20.dp)
                        .fillMaxWidth(),
                )
            }
        },
    ) {
        MosaicPinchZoomLayout(
            state = mosaicPinchState,
            indicatorTopPadding = contentPadding.calculateTopPadding() + 16.dp,
            onPinchOutPastMin = openMonths,
        ) { currentColumns ->
            TimelineScroller(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(top = 32.dp)
                    .padding(vertical = 32.dp),
                segments = rememberMosaicMonthSegments(
                    mappedData = mappedData,
                    columns = currentColumns,
                    allowHeaders = timelineGroupByDate,
                    leadingItemCount = if (aboveGridContent != null) 1 else 0,
                    dateSource = mediaState.value.dateSource,
                ),
                headers = headers,
                state = mosaicGridState,
                snapScrollOffset = remember(density, headerOffsetPx) {
                    with(density) { 80.dp.roundToPx() } - headerOffsetPx
                },
            ) {
                MosaicMediaGrid(
                    modifier = Modifier.hazeSource(LocalHazeState.current),
                    gridState = mosaicGridState,
                    columns = currentColumns,
                    mediaState = mediaState,
                    metadataState = metadataState,
                    mappedData = mappedData,
                    paddingValues = contentPadding,
                    allowSelection = true,
                    canScroll = !mosaicPinchState.isZooming,
                    allowHeaders = timelineGroupByDate,
                    bigHeaders = timelineGroupMethod != GROUP_NORMAL,
                    aboveGridContent = aboveGridContent,
                    isScrolling = isScrolling,
                    emptyContent = emptyContent,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onMediaClick = onMediaClick,
                    onRetry = onRetry,
                )
            }
        }
        if (showSearchBar) {
            FloatingTopBarScrim(
                barZoneHeight = searchBarPadding,
                visibility = {
                    topScrimVisibility(
                        mosaicGridState.firstVisibleItemIndex,
                        mosaicGridState.firstVisibleItemScrollOffset,
                        TopBarScrimScrollFade.toPx(),
                    )
                },
            )
        }
    }
    }
    if (decorate) {
        @OptIn(ExperimentalComposeUiApi::class)
        BackHandler(enabled = showMonths) { showMonths = false }
        AnimatedVisibility(
            visible = showMonths,
            enter = fadeIn() + scaleIn(initialScale = 1.08f),
            exit = fadeOut() + scaleOut(targetScale = 1.08f),
        ) {
            TimelineMonthsOverview(
                months = index.months,
                topPadding = contentPadding.calculateTopPadding(),
                bottomPadding = bottomPadding,
                onMonthClick = { month ->
                    signals.pendingMonthJump = month.key
                    showMonths = false
                },
                onClose = { showMonths = false },
            )
        }
    }
    }
    }
}

/** Grid state with the timeline's look-ahead cache window. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTimelineGridState(initialFirstVisibleItemIndex: Int = 0): LazyGridState {
    val cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 2f) }
    return rememberLazyGridState(cacheWindow = cacheWindow, initialFirstVisibleItemIndex = initialFirstVisibleItemIndex)
}
