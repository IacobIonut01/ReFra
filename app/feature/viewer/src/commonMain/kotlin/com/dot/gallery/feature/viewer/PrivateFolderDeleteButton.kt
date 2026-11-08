/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.Media
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.action_delete_permanently


/**
 * Delete button for items shown from the private folder. Private-folder media
 * are SAF documents, not MediaStore entries, so deletion must go through the
 * document provider rather than a MediaStore delete request — the latter
 * crashes on tree document URIs. The confirmation dialog is supplied by
 * the app via [dialog].
 */
@Composable
fun <T : Media> PrivateFolderDeleteButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false,
    dialog: @Composable (AppBottomSheetState) -> Unit
) {
    val state = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.DeleteOutline,
        followTheme = followTheme,
        title = stringResource(Res.string.action_delete_permanently),
        enabled = enabled
    ) {
        scope.launch { state.show() }
    }

    dialog(state)
}
