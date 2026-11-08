/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.SheetNameInputContent
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.feature.exif.generated.resources.Res
import com.dot.gallery.feature.exif.generated.resources.action_cancel
import com.dot.gallery.feature.exif.generated.resources.action_confirm
import com.dot.gallery.feature.exif.generated.resources.album_name
import com.dot.gallery.feature.exif.generated.resources.create_new_album
import com.dot.gallery.feature.exif.generated.resources.location_new_album
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddAlbumSheet(
    sheetState: AppBottomSheetState,
    hasFullStorageAccess: Boolean,
    onFinish: (albumName: String) -> Unit,
    onCancel: () -> Unit
) {
    var newAlbum: String by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.create_new_album)
    val location = if (hasFullStorageAccess) newAlbum else "Pictures/$newAlbum"
    ExpressiveSheet(sheetState = sheetState, paneTitle = title, onDismissRequest = onCancel) {
        SheetNameInputContent(
            title = title,
            body = stringResource(Res.string.location_new_album, location),
            value = newAlbum,
            onValueChange = { newAlbum = it },
            label = stringResource(Res.string.album_name),
            confirmText = stringResource(Res.string.action_confirm),
            dismissText = stringResource(Res.string.action_cancel),
            onDismiss = {
                scope.launch {
                    sheetState.hide()
                    onCancel()
                }
            },
            onConfirm = { name ->
                scope.launch {
                    sheetState.hide()
                    onFinish(name)
                }
            },
        )
    }
}
