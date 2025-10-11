/*
 * SPDX-FileCopyrightText: 2025 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings.Album.rememberHideTimelineOnAlbum
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.NavigationButton
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.TwoLinedDateToolbarTitle
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun LocationTimelineScreen(
    gpsLocationNameCity: String,
    gpsLocationNameCountry: String,
    mediaState: State<MediaState<Media.UriMedia>>,
    metadataState: State<MediaMetadataState>,
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by rememberGridSize()
    val eventHandler = LocalEventHandler.current
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { canScroll },
        flingAnimationSpec = null
    )

    val dpCacheWindow = remember {
        LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
    }
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
                            albumName = "$gpsLocationNameCity, $gpsLocationNameCountry",
                            dateHeader = mediaState.value.dateHeader
                        )
                    },
                    navigationIcon = {
                        NavigationButton(
                            albumId = -1,
                            target = "location_${gpsLocationNameCity}_$gpsLocationNameCountry",
                            alwaysGoBack = true,
                        )
                    },
                    actions = {

                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        ) { it ->
            PinchZoomGridLayout(
                state = pinchState,
                modifier = Modifier.hazeSource(LocalHazeState.current)
            ) {
                val hideTimelineOnAlbum by rememberHideTimelineOnAlbum()
                MediaGridView(
                    modifier = Modifier.padding(top = it.calculateTopPadding()),
                    mediaState = mediaState,
                    metadataState = metadataState,
                    allowSelection = true,
                    showSearchBar = false,
                    enableStickyHeaders = !hideTimelineOnAlbum,
                    paddingValues = remember(paddingValues) {
                        PaddingValues(
                            bottom = paddingValues.calculateBottomPadding() + 128.dp
                        )
                    },
                    canScroll = canScroll,
                    allowHeaders = !hideTimelineOnAlbum,
                    showMonthlyHeader = false,
                    aboveGridContent = null,
                    isScrolling = isScrolling,
                    emptyContent = { EmptyMedia(modifier = Modifier.padding(paddingValues)) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope
                ) {
                    eventHandler.navigate(Screen.MediaViewScreen.idAndLocation(it.id, gpsLocationNameCity, gpsLocationNameCountry))
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
            selectedMedia = selectedMediaList
        )
    }
}