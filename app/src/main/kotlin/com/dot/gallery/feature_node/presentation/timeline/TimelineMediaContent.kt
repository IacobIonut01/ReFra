package com.dot.gallery.feature_node.presentation.timeline

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
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.ScrollToTopHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.animateOrJumpToTop
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.domain.model.isIgnoredKey
import com.dot.gallery.feature_node.presentation.common.components.FloatingTopBarScrim
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.MosaicMediaGrid
import com.dot.gallery.feature_node.presentation.common.components.MosaicPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.StickyHeaderGrid
import com.dot.gallery.feature_node.presentation.common.components.TimelineScroller
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicMonthSegments
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicPinchZoomState
import com.dot.gallery.feature_node.presentation.common.components.rememberStickyHeaderItem
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.roundSpToPx
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineMediaContent(
    mediaState: State<MediaState<Media.UriMedia>>,
    metadataState: State<MediaMetadataState>,
    scaffoldPadding: PaddingValues,
    bottomPadding: Dp,
    screenTopPadding: Dp,
    showSearchBar: Boolean,
    aboveGridContent: (@Composable () -> Unit)?,
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMediaClick: (Media.UriMedia) -> Unit,
    emptyContent: @Composable () -> Unit,
    onRetry: (() -> Unit)? = null,
    scrollToTopRoute: String? = null,
) {
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by rememberGridSize()
    val timelineLayoutType by rememberTimelineLayoutType()
    val timelineGroupByDate by rememberTimelineGroupByDate()
    val timelineGroupMethod by rememberTimelineGroupMethod()
    val isMosaicLayout = timelineLayoutType == Settings.Misc.LAYOUT_MOSAIC && timelineGroupByDate
    val cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 2f) }
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex,
        gridState = rememberLazyGridState(cacheWindow = cacheWindow),
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.IO) {
            canScroll = !pinchState.isZooming
            lastCellIndex = cellsList.indexOf(pinchState.currentCells)
        }
    }

    if (!isMosaicLayout && scrollToTopRoute != null) {
        ScrollToTopHandler(scrollToTopRoute) { pinchState.gridState.animateOrJumpToTop() }
    }

    val contentPadding = remember(scaffoldPadding, bottomPadding) {
        PaddingValues(
            top = scaffoldPadding.calculateTopPadding(),
            bottom = bottomPadding,
        )
    }

    if (!isMosaicLayout) {
        GridPinchZoomLayout(
            state = pinchState,
            modifier = Modifier.hazeSource(LocalHazeState.current),
            indicatorTopPadding = contentPadding.calculateTopPadding() + 16.dp,
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
                enableStickyHeaders = timelineGroupByDate,
                groupMethod = if (timelineGroupByDate) timelineGroupMethod else Settings.Misc.GROUP_NORMAL,
                aboveGridContent = aboveGridContent,
                isScrolling = isScrolling,
                emptyContent = emptyContent,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                onMediaClick = onMediaClick,
                onRetry = onRetry,
            )
        }
        return
    }

    var lastMosaicCellIndex by rememberMosaicGridSize()
    val mosaicPinchState = rememberMosaicPinchZoomState(
        initialColumnsIndex = lastMosaicCellIndex,
        gridState = rememberLazyGridState(cacheWindow = cacheWindow),
    )
    val mosaicGridState = mosaicPinchState.gridState
    LaunchedEffect(mosaicPinchState.isZooming) {
        lastMosaicCellIndex = mosaicPinchState.currentColumnsIndex
    }
    if (scrollToTopRoute != null) {
        ScrollToTopHandler(scrollToTopRoute) { mosaicGridState.animateOrJumpToTop() }
    }

    val mappedData by rememberedDerivedState(mediaState.value, timelineGroupMethod) {
        when (timelineGroupMethod) {
            Settings.Misc.GROUP_MONTHLY -> mediaState.value.mappedMediaWithMonthly
            Settings.Misc.GROUP_YEARLY -> mediaState.value.mappedMediaWithYearly
            else -> mediaState.value.mappedMedia
        }
    }
    val headers by rememberedDerivedState(mediaState.value) { mediaState.value.headers }
    val stickyHeaderItem by rememberStickyHeaderItem(gridState = mosaicGridState, mediaState = mediaState)
    val hideSearchBarSetting by rememberAutoHideSearchBar()
    val searchBarPadding by animateDpAsState(
        targetValue = when {
            !showSearchBar -> scaffoldPadding.calculateTopPadding()
            !isScrolling.value || !hideSearchBarSetting -> SearchBarDefaults.InputFieldHeight + screenTopPadding + 8.dp
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
                derivedStateOf { mediaState.value.media.isNotEmpty() && stickyHeaderItem != null }
            }
            AnimatedVisibility(visible = show, enter = enterAnimation, exit = exitAnimation) {
                val text by rememberedDerivedState(stickyHeaderItem) { stickyHeaderItem ?: "" }
                val darkTheme = isDarkTheme()
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.let { style ->
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
                        .padding(top = 24.dp + searchBarPadding, bottom = 24.dp)
                        .fillMaxWidth(),
                )
            }
        },
    ) {
        MosaicPinchZoomLayout(
            state = mosaicPinchState,
            indicatorTopPadding = contentPadding.calculateTopPadding() + 16.dp,
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
                    bigHeaders = timelineGroupMethod != Settings.Misc.GROUP_NORMAL,
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
            FloatingTopBarScrim(barZoneHeight = searchBarPadding)
        }
    }
}
