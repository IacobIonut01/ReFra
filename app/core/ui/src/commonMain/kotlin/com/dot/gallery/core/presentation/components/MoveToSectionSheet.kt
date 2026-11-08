/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.move_to_section
import com.dot.gallery.core.ui.generated.resources.section_auto
import com.dot.gallery.core.ui.generated.resources.section_auto_summary
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.AlbumSectionWithAlbums
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.ui.generated.resources.section_album_count

@Composable
fun MoveToSectionSheet(
    sheetState: AppBottomSheetState,
    sections: List<AlbumSectionWithAlbums>,
    currentSectionId: Long?,
    onMoveToSection: (Long) -> Unit,
    onResetOverride: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.move_to_section)
    ExpressiveSheet(sheetState = sheetState, paneTitle = title) {
        MoveToSectionSheetContent(
            sections = sections.map { MoveToSectionOption(it.section.id, it.section.label, it.albums.size) },
            currentSectionId = currentSectionId,
            onAutomatic = {
                scope.launch {
                    sheetState.hide()
                    onResetOverride()
                }
            },
            onSection = { id ->
                scope.launch {
                    sheetState.hide()
                    onMoveToSection(id)
                }
            },
        )
    }
}

/** One section offered by [MoveToSectionSheetContent]. */
data class MoveToSectionOption(val id: Long, val label: String, val albumCount: Int)

/** Stateless body of [MoveToSectionSheet]: Automatic first, then every section as a radio row. */
@Composable
fun ColumnScope.MoveToSectionSheetContent(
    sections: List<MoveToSectionOption>,
    currentSectionId: Long?,
    onAutomatic: () -> Unit,
    onSection: (Long) -> Unit,
) {
    SheetHeader(title = stringResource(Res.string.move_to_section))
    val count = sections.size + 1
    SheetOptionRow(
        title = stringResource(Res.string.section_auto),
        summary = stringResource(Res.string.section_auto_summary),
        icon = Icons.Outlined.AutoAwesome,
        position = sheetRowPosition(0, count),
        trailing = SheetRowTrailing.Radio(currentSectionId == null),
        onClick = onAutomatic,
    )
    sections.forEachIndexed { index, section ->
        SheetOptionRow(
            title = section.label,
            summary = pluralStringResource(Res.plurals.section_album_count, section.albumCount, section.albumCount),
            icon = Icons.AutoMirrored.Outlined.DriveFileMove,
            position = sheetRowPosition(index + 1, count),
            trailing = SheetRowTrailing.Radio(section.id == currentSectionId),
            onClick = { onSection(section.id) },
        )
    }
}
