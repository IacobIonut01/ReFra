package com.dot.gallery.feature_node.presentation.vault.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState

@Composable
fun RemovePasswordSheet(
    state: AppBottomSheetState,
    onConfirm: () -> Unit
) {
    ConfirmationSheet(
        state = state,
        title = stringResource(R.string.vault_remove_custom_password),
        summary = stringResource(R.string.vault_remove_custom_password_summary),
        onConfirm = onConfirm
    )
}
