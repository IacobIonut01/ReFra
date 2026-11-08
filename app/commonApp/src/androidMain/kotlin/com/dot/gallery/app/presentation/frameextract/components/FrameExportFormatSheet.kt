package com.dot.gallery.app.presentation.frameextract.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.app.presentation.frameextract.FrameExportFormat
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetColumn
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.SheetDismissConfirmActions
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cancel
import com.dot.gallery.generated.resources.frame_picker_format_jpeg
import com.dot.gallery.generated.resources.frame_picker_format_jpeg_description
import com.dot.gallery.generated.resources.frame_picker_format_png
import com.dot.gallery.generated.resources.frame_picker_format_png_description
import com.dot.gallery.generated.resources.frame_picker_format_title
import com.dot.gallery.generated.resources.frame_picker_save
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameExportFormatSheet(
    selected: FrameExportFormat,
    onSelect: (FrameExportFormat) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
    ) {
        ExpressiveSheetColumn {
            FrameExportFormatContent(selected = selected, onSelect = onSelect, onSave = onSave, onDismiss = onDismiss)
        }
    }
}

/** Stateless body of [FrameExportFormatSheet]: JPEG / PNG radio rows, then Cancel and Save. */
@Composable
fun ColumnScope.FrameExportFormatContent(
    selected: FrameExportFormat,
    onSelect: (FrameExportFormat) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    SheetHeader(title = stringResource(Res.string.frame_picker_format_title))
    SheetOptionRow(
        title = stringResource(Res.string.frame_picker_format_jpeg),
        summary = stringResource(Res.string.frame_picker_format_jpeg_description),
        icon = Icons.Outlined.Image,
        position = Position.Top,
        trailing = SheetRowTrailing.Radio(selected == FrameExportFormat.JPEG),
        onClick = { onSelect(FrameExportFormat.JPEG) },
    )
    SheetOptionRow(
        title = stringResource(Res.string.frame_picker_format_png),
        summary = stringResource(Res.string.frame_picker_format_png_description),
        icon = Icons.Outlined.PhotoSizeSelectLarge,
        position = Position.Bottom,
        trailing = SheetRowTrailing.Radio(selected == FrameExportFormat.PNG),
        onClick = { onSelect(FrameExportFormat.PNG) },
    )
    SheetDismissConfirmActions(
        dismissText = stringResource(Res.string.cancel),
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.frame_picker_save),
        onConfirm = onSave,
    )
}
