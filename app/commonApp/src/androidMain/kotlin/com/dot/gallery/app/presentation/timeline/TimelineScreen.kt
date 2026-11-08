/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.work.WorkSignals
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.core.startup.LocalStartupWorkGate
import com.dot.gallery.core.startup.StartupContentEffect
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.timeline.TimelineContent
import com.dot.gallery.feature.timeline.storycards.StoryCardsViewModel
import com.dot.gallery.app.presentation.storycards.components.StoryCardsRow
import com.dot.gallery.app.presentation.main.MainScreenTopBar
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.presentation.util.rememberBottomBarInset
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import com.dot.gallery.core.workers.CAPTURE_TIME_INDEX_PROGRESS
import com.dot.gallery.core.workers.CAPTURE_TIME_INDEX_WORK
import com.dot.gallery.core.workers.CaptureTimeIndexWorker

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    mediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    storyCardsViewModel: StoryCardsViewModel? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val eventHandler = LocalEventHandler.current
    val distributor = LocalMediaDistributor.current
    val isRefreshing by distributor.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val captureTimeWork = remember {
        WorkSignals.observe(CAPTURE_TIME_INDEX_WORK, CaptureTimeIndexWorker.WORKER_NAME)
    }.collectAsStateWithLifecycle(initialValue = null)
    val captureTimeIndexProgress = captureTimeWork.value
        ?.takeIf { it.state is TaskState.Enqueued || it.state is TaskState.Running }
        ?.progress?.getInt(CAPTURE_TIME_INDEX_PROGRESS, 0)

    val startupGate = LocalStartupWorkGate.current
    val filterAlbumsFlow = remember(startupGate, distributor) {
        if (startupGate == null) distributor.albumsFlow
        else flow {
            startupGate.awaitFirstContent()
            emitAll(distributor.albumsFlow)
        }
    }
    val albumsState = filterAlbumsFlow.collectAsStateWithLifecycle(initialValue = com.dot.gallery.domain.model.AlbumState())
    val dateFormats by distributor.dateFormatsFlow.collectAsStateWithLifecycle()
    val timelineSettings by distributor.settingsFlow.collectAsStateWithLifecycle()
    val groupSimilarMedia by distributor.groupSimilarMedia.collectAsStateWithLifecycle()
    val enabledGroupTypes by distributor.enabledGroupTypes.collectAsStateWithLifecycle()

    StartupContentEffect(
        route = Screen.TimelineScreen(),
        ready = !mediaState.value.isLoading
    )
    val storyCards = if (storyCardsViewModel != null) {
        storyCardsViewModel.allCards.collectAsStateWithLifecycle().value
    } else {
        emptyList()
    }

    val aboveGridContent: @Composable (() -> Unit)? = if (storyCards == null || storyCards.isNotEmpty()) {
        {
            StoryCardsRow(
                cards = storyCards,
                onCardClick = { card -> storyCardsViewModel?.prepareViewer(card.id) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedContentScope,
                modifier = Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
        }
    } else null

    val refreshScope = rememberCoroutineScope()
    val lastCellIndex = rememberGridSize()
    val lastMosaicCellIndex = rememberMosaicGridSize()

    TimelineContent(
        paddingValues = paddingValues,
        isScrolling = isScrolling,
        mediaState = mediaState,
        metadataState = metadataState,
        albums = albumsState.value.albums,
        dateFormats = dateFormats,
        groupByMonth = timelineSettings?.groupTimelineByMonth == true,
        groupByYear = timelineSettings?.groupTimelineByYear == true,
        groupSimilarMedia = groupSimilarMedia,
        enabledGroupTypes = enabledGroupTypes,
        isRefreshing = isRefreshing,
        onRefresh = { refreshScope.launch { distributor.invalidate() } },
        aboveGridContent = aboveGridContent,
        bottomBarInset = rememberBottomBarInset(paddingValues),
        lastCellIndex = lastCellIndex,
        lastMosaicCellIndex = lastMosaicCellIndex,
        indexProgress = captureTimeIndexProgress,
        scrollToTopRoute = Screen.TimelineScreen.route,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        onMediaClick = {
            eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(it.id, -1L))
        },
        onOpenPlace = { place ->
            eventHandler.navigate(
                Screen.LocationTimelineScreen.location(
                    gpsLocationNameCity = place.city,
                    gpsLocationNameCountry = place.country,
                )
            )
        },
        onPlayMonth = { month ->
            eventHandler.navigate(Screen.MediaViewScreen.idAndAlbumSlideshow(month.firstMediaId, -1L))
        },
        selectionOverlay = { allMedia, selectedMediaList ->
            SelectionSheet(
                modifier = Modifier.align(Alignment.BottomEnd),
                allMedia = allMedia,
                selectedMedia = selectedMediaList
            )
        },
        topBar = { trailingIcon ->
            MainScreenTopBar(
                isScrolling = isScrolling,
                filter = trailingIcon,
            )
        },
    )
}
