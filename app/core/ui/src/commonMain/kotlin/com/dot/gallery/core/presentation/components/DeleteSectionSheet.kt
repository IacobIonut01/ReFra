/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.delete_section
import com.dot.gallery.core.ui.generated.resources.delete_section_confirm
import com.dot.gallery.core.ui.util.AppBottomSheetState
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeleteSectionSheet(
    sheetState: AppBottomSheetState,
    onConfirmDelete: () -> Unit
) {
    ConfirmationSheet(
        state = sheetState,
        title = stringResource(Res.string.delete_section),
        summary = stringResource(Res.string.delete_section_confirm),
        confirmText = stringResource(Res.string.delete_section),
        icon = Icons.Outlined.Delete,
        tone = SheetTone.Error,
        onConfirm = onConfirmDelete,
    )
}
