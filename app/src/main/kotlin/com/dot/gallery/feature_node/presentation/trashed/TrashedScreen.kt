/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.trashed

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
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.common.MediaScreen
import com.dot.gallery.feature_node.presentation.trashed.components.AutoDeleteFooter
import com.dot.gallery.feature_node.presentation.trashed.components.EmptyTrash
import com.dot.gallery.feature_node.presentation.trashed.components.TrashedNavActions

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T: Media> TrashedGridScreen(
    paddingValues: PaddingValues,
    albumName: String = stringResource(id = R.string.trash),
    mediaState: State<MediaState<T>>,
    clearSelection: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) = MediaScreen(
    paddingValues = paddingValues,
    target = TARGET_TRASH,
    albumName = albumName,
    mediaState = mediaState,
    allowHeaders = false,
    enableStickyHeaders = false,
    navActionsContent = { _: MutableState<Boolean>,
                          _: ActivityResultLauncher<IntentSenderRequest> ->
        TrashedNavActions(mediaState)
    },
    emptyContent = { EmptyTrash() },
    aboveGridContent = { AutoDeleteFooter() },
    sharedTransitionScope = sharedTransitionScope,
    animatedContentScope = animatedContentScope
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        clearSelection()
    }
}