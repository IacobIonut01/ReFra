/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.SheetActions
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.feature.exif.generated.resources.Res
import com.dot.gallery.feature.exif.generated.resources.locked
import com.dot.gallery.feature.exif.generated.resources.locked_album_security_error
import com.dot.gallery.feature.exif.generated.resources.ok
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun SecurityInfoSheet(
    sheetState: AppBottomSheetState
) {
    val scope = rememberCoroutineScope()
    ExpressiveSheet(sheetState = sheetState, paneTitle = stringResource(Res.string.locked)) {
        SecurityInfoSheetContent(onDismiss = { scope.launch { sheetState.hide() } })
    }
}

/** Stateless body of [SecurityInfoSheet]. */
@Composable
fun ColumnScope.SecurityInfoSheetContent(onDismiss: () -> Unit) {
    SheetHeader(
        title = stringResource(Res.string.locked),
        body = stringResource(Res.string.locked_album_security_error),
        icon = Icons.Outlined.Lock,
    )
    SheetActions {
        SheetButton(text = stringResource(Res.string.ok), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
