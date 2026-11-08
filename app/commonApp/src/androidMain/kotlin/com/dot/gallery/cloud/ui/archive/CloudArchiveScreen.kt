/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.archive

import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.app.presentation.search.MainSearchBar
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.Settings.Misc.rememberCloudArchiveGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberCloudArchiveGroupMethod
import com.dot.gallery.core.presentation.components.MediaScreen
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_archive
import com.dot.gallery.generated.resources.cloud_archive_empty
import com.dot.gallery.generated.resources.cloud_archive_info_summary
import com.dot.gallery.generated.resources.cloud_archive_info_title


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CloudArchiveScreen(
    paddingValues: PaddingValues,
    mediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    clearSelection: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    /** Optional override for the selection overlay; null keeps the default SelectionSheet. */
    selectionContent: (@Composable BoxScope.(
        allMedia: MediaState<UriMedia>,
        selectedMediaList: androidx.compose.runtime.snapshots.SnapshotStateList<UriMedia>,
    ) -> Unit)? = null,
    /** Docs seam: opens the grid scrolled to this item. */
    initialFirstVisibleItemIndex: Int = 0,
) {
    val cloudArchiveGroupByDate by rememberCloudArchiveGroupByDate()
    val cloudArchiveGroupMethod by rememberCloudArchiveGroupMethod()

    MediaScreen(
        paddingValues = paddingValues,
        target = "cloud_archive",
        albumName = stringResource(Res.string.cloud_archive),
        mediaState = mediaState,
        metadataState = metadataState,
        allowHeaders = cloudArchiveGroupByDate,
        groupMethod = if (cloudArchiveGroupByDate) cloudArchiveGroupMethod else Settings.Misc.GROUP_NORMAL,
        mediaViewerRoute = Screen.MediaViewScreen.route,
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        selectionSheetContent = selectionContent ?: { allMedia, selectedMediaList ->
            SelectionSheet(
                modifier = Modifier.align(Alignment.BottomEnd),
                allMedia = allMedia,
                selectedMedia = selectedMediaList
            )
        },
    emptyContent = { EmptyArchive() },
    aboveGridContent = {
        SettingsItem(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            item = SettingsEntity.Preference(
                icon = Icons.Outlined.Archive,
                title = stringResource(Res.string.cloud_archive_info_title),
                summary = stringResource(Res.string.cloud_archive_info_summary),
                screenPosition = Position.Alone,
            ),
        )
    },
        lastCellIndex = rememberGridSize(),
        lastMosaicCellIndex = rememberMosaicGridSize(),
        searchBar = { scrolling, menuItems ->
            MainSearchBar(
                isScrolling = scrolling,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                menuItems = menuItems,
            )
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )
}

@Composable
private fun EmptyArchive(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(
            modifier = Modifier.size(128.dp),
            imageVector = Icons.Outlined.Archive,
            contentDescription = stringResource(Res.string.cloud_archive),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(Res.string.cloud_archive_empty),
            style = MaterialTheme.typography.titleLarge
        )
    }
}
