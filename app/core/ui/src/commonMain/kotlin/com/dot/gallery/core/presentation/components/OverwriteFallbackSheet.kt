/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.action_cancel
import com.dot.gallery.core.ui.generated.resources.overwrite_unavailable_create_copy
import com.dot.gallery.core.ui.generated.resources.overwrite_unavailable_summary
import com.dot.gallery.core.ui.generated.resources.overwrite_unavailable_title
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet shown when the user asks to overwrite an image whose format has no Android encoder
 * (RAW/TIFF/PSD/JP2/SVG/animated). The original file cannot be re-encoded in place, so we offer to
 * create a new PNG copy instead. Dismissing leaves the original untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverwriteFallbackSheet(
    onCreateCopy: () -> Unit,
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
            OverwriteFallbackSheetContent(onCreateCopy = onCreateCopy, onDismiss = onDismiss)
        }
    }
}

/** Stateless body of [OverwriteFallbackSheet]. */
@Composable
fun ColumnScope.OverwriteFallbackSheetContent(
    onCreateCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    SheetHeader(
        title = stringResource(Res.string.overwrite_unavailable_title),
        body = stringResource(Res.string.overwrite_unavailable_summary),
        icon = Icons.Outlined.SaveAs,
        tone = SheetTone.Secondary,
    )
    SheetDismissConfirmActions(
        dismissText = stringResource(Res.string.action_cancel),
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.overwrite_unavailable_create_copy),
        onConfirm = onCreateCopy,
    )
}
