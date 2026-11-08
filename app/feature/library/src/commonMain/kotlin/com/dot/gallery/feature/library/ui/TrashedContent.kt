/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.presentation.components.EmptyTrash
import com.dot.gallery.core.presentation.components.MediaScreen
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.trash


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Media> TrashedContent(
    paddingValues: PaddingValues,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    lastCellIndex: MutableState<Int>,
    lastMosaicCellIndex: MutableState<Int>,
    mediaViewerRoute: String,
    searchBar: @Composable (
        isScrolling: MutableState<Boolean>,
        menuItems: @Composable RowScope.() -> Unit,
    ) -> Unit,
    /** Trash selection sheet — the app supplies its launcher-bound impl. */
    selectionSheetContent: @Composable BoxScope.(
        allMedia: MediaState<T>,
        selectedMediaList: SnapshotStateList<T>,
    ) -> Unit,
    /** Auto-delete footer row — the app supplies its launcher-bound impl. */
    aboveGridContent: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    /** Docs seam: opens the grid scrolled to this item. */
    initialFirstVisibleItemIndex: Int = 0,
) {
    MediaScreen(
        paddingValues = paddingValues,
        target = TARGET_TRASH,
        albumName = stringResource(Res.string.trash),
        mediaState = mediaState,
        metadataState = metadataState,
        allowHeaders = false,
        mediaViewerRoute = mediaViewerRoute,
        emptyContent = { EmptyTrash() },
        aboveGridContent = aboveGridContent,
        selectionSheetContent = selectionSheetContent,
        lastCellIndex = lastCellIndex,
        lastMosaicCellIndex = lastMosaicCellIndex,
        searchBar = searchBar,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
    )
}
