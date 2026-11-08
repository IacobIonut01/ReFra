/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature.albums.ui.collections

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetColumn
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.SheetTextField
import com.dot.gallery.core.presentation.components.sheetRowPosition
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.add_to_collection
import com.dot.gallery.feature.albums.generated.resources.add_to_collection_body
import com.dot.gallery.feature.albums.generated.resources.collection_name
import com.dot.gallery.feature.albums.generated.resources.collection_name_hint
import com.dot.gallery.feature.albums.generated.resources.create_and_add
import com.dot.gallery.feature.albums.generated.resources.n_items_in_collection
import com.dot.gallery.feature.albums.generated.resources.new_collection
import com.dot.gallery.feature.albums.generated.resources.new_collection_summary
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionSheet(
    visible: Boolean,
    collections: List<CollectionWithCount>,
    onDismiss: () -> Unit,
    onCollectionSelected: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    collectionThumbnailModel: CollectionThumbnailModel = { null },
) {
    if (!visible) return

    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var showCreateField by rememberSaveable { mutableStateOf(false) }
    var newCollectionName by rememberSaveable { mutableStateOf("") }
    val reset = {
        showCreateField = false
        newCollectionName = ""
    }

    ModalBottomSheet(
        onDismissRequest = {
            reset()
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
    ) {
        ExpressiveSheetColumn {
            AddToCollectionSheetContent(
                collections = collections.map {
                    AddToCollectionOption(it.collection.id, it.collection.label, it.mediaCount, it.thumbnailMediaId?.let(collectionThumbnailModel))
                },
                showCreateField = showCreateField,
                newCollectionName = newCollectionName,
                onNewCollectionNameChange = { newCollectionName = it },
                onToggleCreate = { showCreateField = !showCreateField },
                onCreate = { name ->
                    onCreateAndAdd(name)
                    reset()
                    onDismiss()
                },
                onCollection = { id ->
                    onCollectionSelected(id)
                    onDismiss()
                },
            )
        }
    }
}

/** One collection offered by [AddToCollectionSheetContent]; [thumbnail] is an image model or null. */
data class AddToCollectionOption(val id: Long, val label: String, val mediaCount: Int, val thumbnail: Any?)

/** Stateless body of [AddToCollectionSheet]: existing collections, then New collection. */
@Composable
fun ColumnScope.AddToCollectionSheetContent(
    collections: List<AddToCollectionOption>,
    showCreateField: Boolean,
    newCollectionName: String,
    onNewCollectionNameChange: (String) -> Unit,
    onToggleCreate: () -> Unit,
    onCreate: (String) -> Unit,
    onCollection: (Long) -> Unit,
) {
    SheetHeader(
        title = stringResource(Res.string.add_to_collection),
        body = stringResource(Res.string.add_to_collection_body),
    )
    val count = collections.size + 1
    collections.forEachIndexed { index, option ->
        SheetOptionRow(
            title = option.label,
            summary = stringResource(Res.string.n_items_in_collection, option.mediaCount),
            position = sheetRowPosition(index, count),
            leading = {
                if (option.thumbnail != null) {
                    LocalMediaImageRenderer.current.RenderImage(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        model = option.thumbnail,
                        contentDescription = option.label,
                        contentScale = ContentScale.Crop,
                        signature = option,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.CollectionsBookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailing = SheetRowTrailing.Chevron,
            onClick = { onCollection(option.id) },
        )
    }
    SheetOptionRow(
        title = stringResource(Res.string.new_collection),
        summary = stringResource(Res.string.new_collection_summary),
        icon = Icons.Outlined.AddCircleOutline,
        position = sheetRowPosition(count - 1, count),
        trailing = SheetRowTrailing.Chevron,
        onClick = onToggleCreate,
    )
    if (showCreateField) {
        val focusRequester = remember { FocusRequester() }
        val submit = { if (newCollectionName.isNotBlank()) onCreate(newCollectionName.trim()) }
        SheetTextField(
            value = newCollectionName,
            onValueChange = onNewCollectionNameChange,
            label = stringResource(Res.string.collection_name),
            placeholder = stringResource(Res.string.collection_name_hint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .padding(top = 12.dp)
                .focusRequester(focusRequester),
        )
        SheetButton(
            text = stringResource(Res.string.create_and_add),
            onClick = submit,
            enabled = newCollectionName.isNotBlank(),
            style = SheetButtonStyle.Filled,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
        )
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
}
