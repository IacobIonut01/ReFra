/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.common.MediaScreen
import com.dot.gallery.feature_node.presentation.favorites.components.EmptyFavorites
import com.dot.gallery.feature_node.presentation.favorites.components.FavoriteNavActions

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FavoriteScreen(
    paddingValues: PaddingValues,
    albumName: String = stringResource(id = R.string.favorites),
    mediaState: State<MediaState<UriMedia>>,
    clearSelection: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) = MediaScreen(
    paddingValues = paddingValues,
    target = TARGET_FAVORITES,
    albumName = albumName,
    mediaState = mediaState,
    navActionsContent = { _: MutableState<Boolean>,
                          result: ActivityResultLauncher<IntentSenderRequest> ->
        FavoriteNavActions(mediaState, result)
    },
    emptyContent = { EmptyFavorites() },
    sharedTransitionScope = sharedTransitionScope,
    animatedContentScope = animatedContentScope,
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        clearSelection()
    }
}