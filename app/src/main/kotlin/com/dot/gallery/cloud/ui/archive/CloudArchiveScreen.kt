/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.archive

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberCloudArchiveGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberCloudArchiveGroupMethod
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.common.MediaScreen
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CloudArchiveScreen(
    paddingValues: PaddingValues,
    mediaState: State<MediaState<Media.UriMedia>>,
    metadataState: State<MediaMetadataState>,
    clearSelection: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val cloudArchiveGroupByDate by rememberCloudArchiveGroupByDate()
    val cloudArchiveGroupMethod by rememberCloudArchiveGroupMethod()

    MediaScreen(
        paddingValues = paddingValues,
        target = "cloud_archive",
        albumName = stringResource(R.string.cloud_archive),
        mediaState = mediaState,
        metadataState = metadataState,
        allowHeaders = cloudArchiveGroupByDate,
        enableStickyHeaders = cloudArchiveGroupByDate,
        groupMethod = if (cloudArchiveGroupByDate) cloudArchiveGroupMethod else Settings.Misc.GROUP_NORMAL,
        navActionsContent = { _: MutableState<Boolean>,
                              _: ActivityResultLauncher<IntentSenderRequest> ->
        },
    emptyContent = { EmptyArchive() },
    aboveGridContent = {
        SettingsItem(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            item = SettingsEntity.Preference(
                icon = Icons.Outlined.Archive,
                title = stringResource(R.string.cloud_archive_info_title),
                summary = stringResource(R.string.cloud_archive_info_summary),
                screenPosition = Position.Alone,
            ),
        )
    },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            clearSelection()
        }
    }
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
            contentDescription = stringResource(R.string.cloud_archive),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.cloud_archive_empty),
            style = MaterialTheme.typography.titleLarge
        )
    }
}
