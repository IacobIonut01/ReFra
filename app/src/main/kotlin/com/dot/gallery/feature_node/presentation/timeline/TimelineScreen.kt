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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberLastSeenVersion
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.core.toggleNavigationBar
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.domain.model.isIgnoredKey
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.MosaicMediaGrid
import com.dot.gallery.feature_node.presentation.common.components.StickyHeaderGrid
import com.dot.gallery.feature_node.presentation.common.components.TimelineScroller
import com.dot.gallery.feature_node.presentation.common.components.rememberStickyHeaderItem
import com.dot.gallery.feature_node.presentation.help.components.WhatsNewHeroCard
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.search.MainSearchBar
import com.dot.gallery.feature_node.presentation.timeline.components.TimelineNavActions
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.roundSpToPx
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
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
    var lastSeenVersion by rememberLastSeenVersion()
    val showWhatsNew = remember(lastSeenVersion) { lastSeenVersion != BuildConfig.VERSION_NAME }
    val whatsNewContent: @Composable (() -> Unit)? = if (showWhatsNew) {
        {
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
    } else null
    val selector = LocalMediaSelector.current
    val selectionState = selector.isSelectionActive.collectAsStateWithLifecycle()
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    val dpCacheWindow = LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 2f)
    val pinchState = rememberPinchZoomGridState(
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
                MainSearchBar(
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    menuItems = { TimelineNavActions() },
                )
            }
        ) { it ->
            if (isMosaicLayout) {
                val mosaicGridState = rememberLazyGridState(
                    cacheWindow = dpCacheWindow
                )
                val mappedData by remember(mediaState) {
                    derivedStateOf {
                        mediaState.value.mappedMediaWithMonthly.toMutableStateList()
                    }
                }
                val headers by remember(mediaState) {
                    derivedStateOf {
                        mediaState.value.headers.toMutableStateList()
                    }
                }
                val mosaicPaddingValues = remember(paddingValues, it) {
                    PaddingValues(
                        top = it.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 128.dp
                    )
                }
                val stickyHeaderItem by rememberStickyHeaderItem(
                    gridState = mosaicGridState,
                    mediaState = mediaState
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
                                mediaState.value.media.isNotEmpty() && stickyHeaderItem != null
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
                            mediaState = mediaState,
                            metadataState = metadataState,
                            mappedData = mappedData,
                            paddingValues = mosaicPaddingValues,
                            allowSelection = true,
                            canScroll = true,
                            allowHeaders = true,
                            aboveGridContent = whatsNewContent,
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
            } else {
                PinchZoomGridLayout(
                    state = pinchState,
                    modifier = Modifier.hazeSource(LocalHazeState.current)
                ) {
                    MediaGridView(
                        mediaState = mediaState,
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
                        aboveGridContent = whatsNewContent,
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
        val selectedMediaList by selectedMedia(
            media = mediaState.value.media,
            selectedSet = selectedMedia
        )
        SelectionSheet(
            modifier = Modifier.align(Alignment.BottomEnd),
            allMedia = mediaState.value,
            selectedMedia = selectedMediaList
        )
    }
}