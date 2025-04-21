/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.favorites.components

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.util.selectedMedia

@Composable
fun <T: Media> FavoriteNavActions(
    toggleFavorite: (ActivityResultLauncher<IntentSenderRequest>, List<T>, Boolean) -> Unit,
    mediaState: State<MediaState<T>>,
    selectedMedia: MutableState<Set<Long>>,
    selectionState: MutableState<Boolean>,
    result: ActivityResultLauncher<IntentSenderRequest>
) {
    val selectedMediaList = mediaState.value.media.selectedMedia(selectedSet = selectedMedia)
    val removeAllTitle = stringResource(R.string.remove_all)
    val removeSelectedTitle = stringResource(R.string.remove_selected)
    val title = if (selectionState.value) removeSelectedTitle else removeAllTitle
    AnimatedVisibility(
        visible = mediaState.value.media.isNotEmpty(),
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        TextButton(
            onClick = {
                toggleFavorite(result, selectedMediaList.ifEmpty { mediaState.value.media }, false)
            }
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

