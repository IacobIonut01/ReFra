/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetNameInputContent
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.core.presentation.components.sheetRowPosition
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.add_to_group
import com.dot.gallery.feature.albums.generated.resources.add_to_group_body
import com.dot.gallery.feature.albums.generated.resources.create_album_group
import com.dot.gallery.feature.albums.generated.resources.create_new_group
import com.dot.gallery.feature.albums.generated.resources.delete_group
import com.dot.gallery.feature.albums.generated.resources.delete_group_confirm
import com.dot.gallery.feature.albums.generated.resources.group_album_count
import com.dot.gallery.feature.albums.generated.resources.group_name
import com.dot.gallery.feature.albums.generated.resources.group_name_hint
import com.dot.gallery.feature.albums.generated.resources.new_group_summary
import com.dot.gallery.feature.albums.generated.resources.rename_group
import com.dot.gallery.feature.albums.generated.resources.sheet_cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AlbumGroupSheet(
    sheetState: AppBottomSheetState,
    mode: String, // "create", "rename", "addToGroup"
    initialName: String = "",
    existingGroups: List<AlbumGroupWithAlbums> = emptyList(),
    onCreateGroup: (String) -> Unit = {},
    onRenameGroup: (String) -> Unit = {},
    onAddToExistingGroup: (Long) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var groupName by remember(initialName) { mutableStateOf(initialName) }
    var showCreateNew by remember(mode) { mutableStateOf(mode == "create" || mode == "rename") }
    val title = when (mode) {
        "rename" -> stringResource(Res.string.rename_group)
        "addToGroup" -> stringResource(Res.string.add_to_group)
        else -> stringResource(Res.string.create_album_group)
    }
    val close: (suspend () -> Unit) -> Unit = { then -> scope.launch { sheetState.hide(); then() } }
    ExpressiveSheet(sheetState = sheetState, paneTitle = title) {
        if (mode == "addToGroup" && existingGroups.isNotEmpty() && !showCreateNew) {
            AddToGroupSheetContent(
                groups = existingGroups.map { AlbumGroupOption(it.group.id, it.group.label, it.albums.size) },
                onGroup = { id -> close { onAddToExistingGroup(id) } },
                onNewGroup = { showCreateNew = true },
            )
        } else {
            SheetNameInputContent(
                title = title,
                value = groupName,
                onValueChange = { groupName = it },
                label = stringResource(Res.string.group_name),
                placeholder = stringResource(Res.string.group_name_hint),
                confirmText = if (mode == "rename") stringResource(Res.string.rename_group)
                else stringResource(Res.string.create_new_group),
                dismissText = stringResource(Res.string.sheet_cancel),
                onDismiss = { close {} },
                onConfirm = { name ->
                    close { if (mode == "rename") onRenameGroup(name) else onCreateGroup(name) }
                },
            )
        }
    }
}

/** One existing group offered by [AddToGroupSheetContent]. */
data class AlbumGroupOption(val id: Long, val label: String, val albumCount: Int)

/** Stateless body of the "Add to group" sheet: existing groups, then New group. */
@Composable
fun ColumnScope.AddToGroupSheetContent(
    groups: List<AlbumGroupOption>,
    onGroup: (Long) -> Unit,
    onNewGroup: () -> Unit,
) {
    SheetHeader(
        title = stringResource(Res.string.add_to_group),
        body = stringResource(Res.string.add_to_group_body),
    )
    val count = groups.size + 1
    groups.forEachIndexed { index, group ->
        SheetOptionRow(
            title = group.label,
            summary = pluralStringResource(Res.plurals.group_album_count, group.albumCount, group.albumCount),
            icon = Icons.Outlined.Folder,
            position = sheetRowPosition(index, count),
            trailing = SheetRowTrailing.Chevron,
            onClick = { onGroup(group.id) },
        )
    }
    SheetOptionRow(
        title = stringResource(Res.string.create_new_group),
        summary = stringResource(Res.string.new_group_summary),
        icon = Icons.Outlined.CreateNewFolder,
        position = sheetRowPosition(count - 1, count),
        trailing = SheetRowTrailing.Chevron,
        onClick = onNewGroup,
    )
}

@Composable
fun DeleteGroupSheet(
    sheetState: AppBottomSheetState,
    onConfirmDelete: () -> Unit
) {
    ConfirmationSheet(
        state = sheetState,
        title = stringResource(Res.string.delete_group),
        summary = stringResource(Res.string.delete_group_confirm),
        confirmText = stringResource(Res.string.delete_group),
        icon = Icons.Outlined.Delete,
        tone = SheetTone.Error,
        onConfirm = onConfirmDelete,
    )
}
