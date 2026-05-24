/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.common.MediaScreen
import com.dot.gallery.feature_node.presentation.trashed.components.AutoDeleteFooter
import com.dot.gallery.feature_node.presentation.trashed.components.EmptyTrash
import com.dot.gallery.feature_node.presentation.trashed.components.TrashSelectionSheet
import com.dot.gallery.feature_node.presentation.util.selectedMedia

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T: Media> TrashedGridScreen(
    paddingValues: PaddingValues,
    albumName: String = stringResource(id = R.string.trash),
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    clearSelection: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) = MediaScreen(
    paddingValues = paddingValues,
    target = TARGET_TRASH,
    albumName = albumName,
    mediaState = mediaState,
    metadataState = metadataState,
    allowHeaders = false,
    enableStickyHeaders = false,
    navActionsContent = { _: MutableState<Boolean>,
                          _: ActivityResultLauncher<IntentSenderRequest> ->
    },
    emptyContent = { EmptyTrash() },
    aboveGridContent = { AutoDeleteFooter(mediaState) },
    selectionSheetContent = {
        val selector = LocalMediaSelector.current
        val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()
        val selectedMediaList = mediaState.value.media.selectedMedia(selectedSet = selectedMedia)
        TrashSelectionSheet(
            modifier = Modifier.align(Alignment.BottomEnd),
            allMedia = mediaState.value,
            selectedMedia = selectedMediaList
        )
    },
    sharedTransitionScope = sharedTransitionScope,
    animatedContentScope = animatedContentScope
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        clearSelection()
    }
}