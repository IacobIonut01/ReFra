package com.dot.gallery.feature_node.presentation.vault.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState

@Composable
fun NewVaultSheet(
    state: AppBottomSheetState,
    onConfirm: () -> Unit
) {
    ConfirmationSheet(
        state = state,
        title = stringResource(R.string.create_new_vault_title),
        summary = stringResource(R.string.create_new_vault_summary),
        onConfirm = onConfirm
    )
}