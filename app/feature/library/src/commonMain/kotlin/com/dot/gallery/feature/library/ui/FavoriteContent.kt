/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.EmptyFavorites
import com.dot.gallery.core.presentation.components.MediaScreen
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.TimelineFilter
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.GROUP_NORMAL
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.timeline.KeepFilterAlbumsAvailable
import com.dot.gallery.feature.timeline.MediaPageFilterAction
import com.dot.gallery.feature.timeline.TimelineFilterSaver
import com.dot.gallery.feature.timeline.TimelineFilterSheet
import com.dot.gallery.feature.timeline.mediaPageControlsTitle
import com.dot.gallery.feature.timeline.rememberAvailableTimelineYears
import com.dot.gallery.feature.timeline.rememberFilteredMediaPageState
import com.dot.gallery.feature.timeline.sourceAlbumsFor
import com.dot.gallery.feature.timeline.timelineControlsActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.favorites
import com.dot.gallery.feature.library.generated.resources.favorites_info_summary
import com.dot.gallery.feature.library.generated.resources.favorites_info_title


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FavoriteContent(
    paddingValues: PaddingValues,
    mediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    lastCellIndex: MutableState<Int>,
    lastMosaicCellIndex: MutableState<Int>,
    mediaViewerRoute: String,
    searchBar: @Composable (
        isScrolling: MutableState<Boolean>,
        menuItems: @Composable RowScope.() -> Unit,
    ) -> Unit,
    selectionSheetContent: @Composable androidx.compose.foundation.layout.BoxScope.(
        allMedia: MediaState<UriMedia>,
        selectedMediaList: SnapshotStateList<UriMedia>,
    ) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    /** Docs seam: opens the grid scrolled to this item. */
    initialFirstVisibleItemIndex: Int = 0,
    /** Docs seam: opens with the sort and filter sheet showing. */
    initialFiltersOpen: Boolean = false,
) {
    var groupByDate by rememberPreference(SettingsKeys.favoritesGroupByDate)
    val favoritesGroupMethod by rememberPreference(SettingsKeys.favoritesGroupMethod)
    var favoritesSort by rememberPreference(SettingsKeys.favoritesSort)
    var filter by rememberSaveable(stateSaver = TimelineFilterSaver) { mutableStateOf(TimelineFilter()) }
    val filterSheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialFiltersOpen) { if (initialFiltersOpen) filterSheetState.show() }

    val albumsState by LocalMediaDistributor.current.albumsFlow.collectAsState()
    val sourceAlbums = remember(albumsState.albums, mediaState.value.media) {
        sourceAlbumsFor(albumsState.albums, mediaState.value.media)
    }
    KeepFilterAlbumsAvailable(sourceAlbums, filter) { filter = it }
    val filteredState = rememberFilteredMediaPageState(mediaState, filter, sourceAlbums)
    val availableYears = rememberAvailableTimelineYears(mediaState)

    MediaScreen(
        paddingValues = paddingValues,
        target = TARGET_FAVORITES,
        albumName = stringResource(Res.string.favorites),
        mediaState = filteredState,
        metadataState = metadataState,
        allowHeaders = groupByDate,
        groupMethod = if (groupByDate) favoritesGroupMethod else GROUP_NORMAL,
        mediaViewerRoute = mediaViewerRoute,
        lastCellIndex = lastCellIndex,
        lastMosaicCellIndex = lastMosaicCellIndex,
        searchBar = searchBar,
        selectionSheetContent = selectionSheetContent,
        navActionsContent = {
            MediaPageFilterAction(
                active = timelineControlsActive(filter, favoritesSort),
                onClick = { scope.launch { filterSheetState.show() } },
            )
        },
        emptyContent = { EmptyFavorites() },
        aboveGridContent = {
            SettingsItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                item = SettingsEntity.Preference(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(Res.string.favorites_info_title),
                    summary = stringResource(Res.string.favorites_info_summary),
                    screenPosition = Position.Alone,
                ),
            )
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        showFavoriteIcon = false,
    )

    TimelineFilterSheet(
        sheetState = filterSheetState,
        filter = filter,
        sort = favoritesSort,
        availableYears = availableYears,
        availableAlbums = sourceAlbums,
        indexProgress = null,
        onFilterChange = { filter = it },
        onSortCommit = { favoritesSort = it },
        title = mediaPageControlsTitle(),
        showFavoritesFilter = false,
        dateHeaders = groupByDate,
        onDateHeadersChange = { groupByDate = it },
    )
}
