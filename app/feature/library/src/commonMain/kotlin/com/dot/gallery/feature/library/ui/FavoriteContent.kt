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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.EmptyFavorites
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.MediaScreen
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.GROUP_NORMAL
import com.dot.gallery.domain.settings.SettingsKeys
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.favorites
import com.dot.gallery.feature.library.generated.resources.favorites_group_by_date
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
) {
    var groupByDate by rememberPreference(SettingsKeys.favoritesGroupByDate)
    val favoritesGroupMethod by rememberPreference(SettingsKeys.favoritesGroupMethod)

    MediaScreen(
        paddingValues = paddingValues,
        target = TARGET_FAVORITES,
        albumName = stringResource(Res.string.favorites),
        mediaState = mediaState,
        metadataState = metadataState,
        allowHeaders = groupByDate,
        groupMethod = if (groupByDate) favoritesGroupMethod else GROUP_NORMAL,
        mediaViewerRoute = mediaViewerRoute,
        lastCellIndex = lastCellIndex,
        lastMosaicCellIndex = lastMosaicCellIndex,
        searchBar = searchBar,
        selectionSheetContent = selectionSheetContent,
        navActionsContent = {
            GalleryPageTopBarAction(
                icon = Icons.Outlined.CalendarMonth,
                contentDescription = stringResource(Res.string.favorites_group_by_date),
                onClick = { groupByDate = !groupByDate },
                tint = if (groupByDate) MaterialTheme.colorScheme.primary else Color.Unspecified,
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
    )
}
