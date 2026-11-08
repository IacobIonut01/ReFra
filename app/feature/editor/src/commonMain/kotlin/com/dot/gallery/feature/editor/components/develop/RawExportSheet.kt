/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.editor.components.develop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ColumnScope
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetColumn
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.sheetRowPosition
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.util.isTiff
import com.dot.gallery.feature.editor.generated.resources.Res
import com.dot.gallery.feature.editor.generated.resources.raw_export_choose_format
import com.dot.gallery.feature.editor.generated.resources.raw_export_choose_format_subtitle
import com.dot.gallery.feature.editor.generated.resources.raw_export_tiff_unavailable


/**
 * Bottom sheet that lets the user pick an output format when saving a developed RAW. TIFF options
 * are hidden (with an explanatory note) when [allowTiff] is false, i.e. when post-develop editor
 * adjustments exist that the TIFF path cannot represent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawExportSheet(
    allowTiff: Boolean,
    onFormatSelected: (RawSaveFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
    ) {
        ExpressiveSheetColumn {
            RawExportSheetContent(allowTiff = allowTiff, onFormatSelected = onFormatSelected)
        }
    }
}

/** Stateless body of [RawExportSheet]: one grouped row per format; tapping a row saves. */
@Composable
fun ColumnScope.RawExportSheetContent(
    allowTiff: Boolean,
    onFormatSelected: (RawSaveFormat) -> Unit,
) {
    SheetHeader(
        title = stringResource(Res.string.raw_export_choose_format),
        body = stringResource(Res.string.raw_export_choose_format_subtitle),
    )
    val formats = if (allowTiff) RawSaveFormat.entries else RawSaveFormat.entries.filterNot { it.isTiff }
    formats.forEachIndexed { index, format ->
        SheetOptionRow(
            title = stringResource(format.labelRes),
            icon = iconFor(format),
            position = sheetRowPosition(index, formats.size),
            trailing = SheetRowTrailing.Chevron,
            onClick = { onFormatSelected(format) },
        )
    }
    if (!allowTiff) {
        Text(
            text = stringResource(Res.string.raw_export_tiff_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp),
        )
    }
}

private fun iconFor(format: RawSaveFormat): ImageVector = when (format) {
    RawSaveFormat.JPEG -> Icons.Outlined.Photo
    RawSaveFormat.PNG -> Icons.Outlined.Image
    RawSaveFormat.TIFF_8, RawSaveFormat.TIFF_16 -> Icons.Outlined.HighQuality
}
