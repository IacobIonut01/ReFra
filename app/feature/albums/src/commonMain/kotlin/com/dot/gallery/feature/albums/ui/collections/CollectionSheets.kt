/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.collections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.SheetNameInputContent
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.add_to_collection_body
import com.dot.gallery.feature.albums.generated.resources.collection_name
import com.dot.gallery.feature.albums.generated.resources.collection_name_hint
import com.dot.gallery.feature.albums.generated.resources.create_collection
import com.dot.gallery.feature.albums.generated.resources.delete_collection
import com.dot.gallery.feature.albums.generated.resources.delete_collection_confirm
import com.dot.gallery.feature.albums.generated.resources.new_collection
import com.dot.gallery.feature.albums.generated.resources.rename_collection
import com.dot.gallery.feature.albums.generated.resources.sheet_cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun CollectionSheet(
    sheetState: AppBottomSheetState,
    mode: String, // "create" or "rename"
    initialName: String = "",
    onCreateCollection: (String) -> Unit = {},
    onRenameCollection: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var collectionName by remember(initialName) { mutableStateOf(initialName) }
    val rename = mode == "rename"
    val title = stringResource(if (rename) Res.string.rename_collection else Res.string.new_collection)
    ExpressiveSheet(sheetState = sheetState, paneTitle = title) {
        SheetNameInputContent(
            title = title,
            body = if (rename) null else stringResource(Res.string.add_to_collection_body),
            value = collectionName,
            onValueChange = { collectionName = it },
            label = stringResource(Res.string.collection_name),
            placeholder = stringResource(Res.string.collection_name_hint),
            confirmText = stringResource(if (rename) Res.string.rename_collection else Res.string.create_collection),
            dismissText = stringResource(Res.string.sheet_cancel),
            onDismiss = { scope.launch { sheetState.hide() } },
            onConfirm = { name ->
                scope.launch {
                    sheetState.hide()
                    if (rename) onRenameCollection(name) else onCreateCollection(name)
                }
            },
        )
    }
}

@Composable
fun DeleteCollectionSheet(
    sheetState: AppBottomSheetState,
    onConfirmDelete: () -> Unit
) {
    ConfirmationSheet(
        state = sheetState,
        title = stringResource(Res.string.delete_collection),
        summary = stringResource(Res.string.delete_collection_confirm),
        confirmText = stringResource(Res.string.delete_collection),
        icon = Icons.Outlined.Delete,
        tone = SheetTone.Error,
        onConfirm = onConfirmDelete,
    )
}
