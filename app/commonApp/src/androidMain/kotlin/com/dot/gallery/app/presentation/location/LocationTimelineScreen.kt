/*
 * SPDX-FileCopyrightText: 2025-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.location

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.GalleryDetailTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.galleryDetailTopBarHeight
import com.dot.gallery.core.presentation.components.rememberDetailHeaderCollapsed
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.app.presentation.albumtimeline.components.SlideshowOptionsSheet
import com.dot.gallery.feature.timeline.TimelineMediaContent
import com.dot.gallery.feature.timeline.rememberTimelineGridState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.presentation.util.shareMedia
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import kotlinx.coroutines.launch
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.feature.library.location.locationLabel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.place_detail_map
import com.dot.gallery.generated.resources.place_detail_year_range
import com.dot.gallery.generated.resources.share
import com.dot.gallery.generated.resources.slideshow
import com.dot.gallery.generated.resources.unknown_location
import com.dot.gallery.generated.resources.s_items
import java.time.Instant
import java.time.ZoneId


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun LocationTimelineScreen(
    gpsLocationNameCity: String,
    gpsLocationNameCountry: String,
    latitude: Double?,
    longitude: Double?,
    mediaState: State<MediaState<UriMedia>>,
    latestGeoMedia: GeoMedia?,
    metadataState: State<MediaMetadataState>,
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val eventHandler = LocalEventHandler.current
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slideshowSheetState = rememberAppBottomSheetState()
    val unknownLocationLabel = stringResource(Res.string.unknown_location)
    val configuration = LocalConfiguration.current
    val locationTitle = remember(
        gpsLocationNameCity, gpsLocationNameCountry, latitude, longitude,
        unknownLocationLabel, configuration,
    ) {
        locationLabel(
            gpsLocationNameCity,
            gpsLocationNameCountry,
            latitude,
            longitude,
            unknownLocationLabel,
        )
    }
    val media = mediaState.value.media
    val years = remember(media) { placeYearSpan(media) }
    val stats = listOfNotNull(
        stringResource(Res.string.s_items, media.size.toString()),
        gpsLocationNameCountry.takeIf { gpsLocationNameCity.isNotBlank() && it.isNotBlank() },
        years?.let { (first, last) ->
            if (first == last) first.toString() else stringResource(Res.string.place_detail_year_range, first, last)
        },
    ).joinToString(" · ")

    val hazeState = LocalHazeState.current
    val topBarHeight = galleryDetailTopBarHeight()
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
    // Only the active layout's grid moves, so either one passing the header collapses the bar.
    val gridState = rememberTimelineGridState()
    val mosaicGridState = rememberTimelineGridState()
    val gridCollapsed by rememberDetailHeaderCollapsed(gridState)
    val mosaicCollapsed by rememberDetailHeaderCollapsed(mosaicGridState)
    val onMap: (() -> Unit)? = latestGeoMedia?.let { geo ->
        { eventHandler.navigate(Screen.LocationsScreen.withMediaId(geo.mediaId)) }
    }
    val onSlideshow: (() -> Unit)? = if (media.isNotEmpty()) {
        { scope.launch { slideshowSheetState.show() } }
    } else null
    val onShare: (() -> Unit)? = if (media.isNotEmpty()) {
        { scope.launch { context.shareMedia(media) } }
    } else null
    val mapLabel = stringResource(Res.string.place_detail_map)
    val slideshowLabel = stringResource(Res.string.slideshow)
    val shareLabel = stringResource(Res.string.share)
    Box(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
    ) {
        TimelineMediaContent(
            mediaState = mediaState,
            metadataState = metadataState,
            scaffoldPadding = PaddingValues(top = topBarHeight),
            bottomPadding = paddingValues.calculateBottomPadding() + 128.dp,
            screenTopPadding = paddingValues.calculateTopPadding(),
            showSearchBar = false,
            stickyHeaders = false,
            gridState = gridState,
            mosaicGridState = mosaicGridState,
            aboveGridContent = {
                val cover = latestGeoMedia?.media ?: media.firstOrNull()
                PlaceDetailHeader(
                    title = locationTitle,
                    stats = stats,
                    coverModel = cover?.getUri(),
                    coverSignature = cover,
                    onMap = onMap,
                    onSlideshow = onSlideshow,
                    onShare = onShare,
                )
            },
            isScrolling = isScrolling,
            lastCellIndex = rememberGridSize(),
            lastMosaicCellIndex = rememberMosaicGridSize(),
            emptyContent = { EmptyMedia(modifier = Modifier.padding(paddingValues)) },
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            onMediaClick = {
                eventHandler.navigate(
                    Screen.MediaViewScreen.idAndLocation(
                        id = it.id,
                        gpsLocationNameCity = gpsLocationNameCity,
                        gpsLocationNameCountry = gpsLocationNameCountry,
                        latitude = latitude,
                        longitude = longitude,
                    )
                )
            },
        )
        GalleryDetailTopBar(
            title = locationTitle,
            count = media.size.toString(),
            collapsed = gridCollapsed || mosaicCollapsed,
            hazeState = hazeState,
            onBack = if (isSelectionActive) selector::clearSelection else null,
            navigationIcon = if (isSelectionActive) Icons.Outlined.Close else null,
            collapsedActions = listOfNotNull(
                onMap?.let { action ->
                    @Composable { GalleryPageTopBarAction(icon = Icons.Outlined.Map, contentDescription = mapLabel, onClick = action) }
                },
                onSlideshow?.let { action ->
                    @Composable { GalleryPageTopBarAction(icon = Icons.Outlined.Slideshow, contentDescription = slideshowLabel, onClick = action) }
                },
                onShare?.let { action ->
                    @Composable { GalleryPageTopBarAction(icon = Icons.Outlined.Share, contentDescription = shareLabel, onClick = action) }
                },
            ),
        )
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
        SlideshowOptionsSheet(
            state = slideshowSheetState,
            canStart = media.isNotEmpty(),
            onStart = {
                val startId = mediaState.value.pagerMedia.firstOrNull()?.id ?: media.firstOrNull()?.id
                if (startId != null) {
                    eventHandler.navigate(
                        Screen.MediaViewScreen.idAndLocation(
                            id = startId,
                            gpsLocationNameCity = gpsLocationNameCity,
                            gpsLocationNameCountry = gpsLocationNameCountry,
                            latitude = latitude,
                            longitude = longitude,
                            slideshow = true,
                        )
                    )
                }
            }
        )
    }
}

/** Earliest and latest capture year of [media], or null when it is empty. */
internal fun placeYearSpan(media: List<Media>, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int>? {
    if (media.isEmpty()) return null
    val years = media.map { Instant.ofEpochSecond(it.definedTimestamp).atZone(zone).year }
    return years.min() to years.max()
}
