/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.favorites

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.dot.gallery.app.presentation.favorites.components.FavoritesSelectionSheet
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.library.ui.FavoriteContent
import com.dot.gallery.app.presentation.search.MainSearchBar
import com.dot.gallery.core.presentation.util.Screen


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FavoriteScreen(
    paddingValues: PaddingValues,
    mediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    clearSelection: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    FavoriteContent(
        paddingValues = paddingValues,
        mediaState = mediaState,
        metadataState = metadataState,
        lastCellIndex = rememberGridSize(),
        lastMosaicCellIndex = rememberMosaicGridSize(),
        mediaViewerRoute = Screen.MediaViewScreen.route,
        searchBar = { scrolling, menuItems ->
            MainSearchBar(
                isScrolling = scrolling,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                menuItems = menuItems,
            )
        },
        selectionSheetContent = { allMedia, selectedMediaList ->
            FavoritesSelectionSheet(
                allMedia = allMedia,
                selectedMedia = selectedMediaList
            )
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )
}
