/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.favorites

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.common.MediaScreen
import com.dot.gallery.feature_node.presentation.favorites.components.EmptyFavorites
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FavoriteScreen(
    paddingValues: PaddingValues,
    albumName: String = stringResource(id = R.string.favorites),
    mediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    clearSelection: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) = MediaScreen(
    paddingValues = paddingValues,
    target = TARGET_FAVORITES,
    albumName = albumName,
    mediaState = mediaState,
    metadataState = metadataState,
    navActionsContent = { _: MutableState<Boolean>,
                          _: ActivityResultLauncher<IntentSenderRequest> ->
    },
    emptyContent = { EmptyFavorites() },
    aboveGridContent = {
        SettingsItem(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            item = SettingsEntity.Preference(
                icon = Icons.Outlined.FavoriteBorder,
                title = stringResource(R.string.favorites_info_title),
                summary = stringResource(R.string.favorites_info_summary),
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