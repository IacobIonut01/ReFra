/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.action_cancel
import com.dot.gallery.core.ui.generated.resources.create_section
import com.dot.gallery.core.ui.generated.resources.create_section_body
import com.dot.gallery.core.ui.generated.resources.rename_section
import com.dot.gallery.core.ui.generated.resources.section_name
import com.dot.gallery.core.ui.generated.resources.section_name_hint
import com.dot.gallery.core.ui.util.AppBottomSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AlbumSectionNameSheet(
    sheetState: AppBottomSheetState,
    mode: SectionDialogMode = SectionDialogMode.Create,
    initialName: String = "",
    onCreateSection: (String) -> Unit = {},
    onRenameSection: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var sectionName by remember(initialName) { mutableStateOf(initialName) }
    val title = stringResource(
        when (mode) {
            SectionDialogMode.Rename -> Res.string.rename_section
            SectionDialogMode.Create -> Res.string.create_section
        }
    )
    ExpressiveSheet(sheetState = sheetState, paneTitle = title) {
        SheetNameInputContent(
            title = title,
            body = if (mode == SectionDialogMode.Create) stringResource(Res.string.create_section_body) else null,
            value = sectionName,
            onValueChange = { sectionName = it },
            label = stringResource(Res.string.section_name),
            placeholder = stringResource(Res.string.section_name_hint),
            confirmText = title,
            dismissText = stringResource(Res.string.action_cancel),
            onDismiss = { scope.launch { sheetState.hide() } },
            onConfirm = { name ->
                scope.launch {
                    sheetState.hide()
                    when (mode) {
                        SectionDialogMode.Rename -> onRenameSection(name)
                        SectionDialogMode.Create -> onCreateSection(name)
                    }
                }
            },
        )
    }
}

/**
 * Shared body of the "name something" sheets (section, group, collection, album): header, one
 * focused text field, then Cancel and the confirm button. [onConfirm] receives the trimmed name
 * and only runs while it is not blank.
 */
@Composable
fun ColumnScope.SheetNameInputContent(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    confirmText: String,
    dismissText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    body: String? = null,
    placeholder: String? = null,
    requestFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    val submit = { if (value.isNotBlank()) onConfirm(value.trim()) }
    SheetHeader(title = title, body = body)
    SheetTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.focusRequester(focusRequester),
    )
    SheetDismissConfirmActions(
        dismissText = dismissText,
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = submit,
        confirmEnabled = value.isNotBlank(),
    )
    if (requestFocus) {
        RequestSheetFieldFocus(focusRequester)
    }
}

/**
 * Focuses a sheet text field as soon as it is composed and asks the IME to show
 * right away, so the keyboard animates together with the sheet instead of waiting
 * for the dialog window to settle. Focus requests can still be dropped while the
 * sheet window is being set up, so retry briefly.
 */
@Composable
fun RequestSheetFieldFocus(focusRequester: FocusRequester) {
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequester) {
        repeat(3) { attempt ->
            val focused = runCatching { focusRequester.requestFocus() }.isSuccess
            if (focused) keyboardController?.show()
            if (attempt < 2) delay(150)
        }
    }
}
