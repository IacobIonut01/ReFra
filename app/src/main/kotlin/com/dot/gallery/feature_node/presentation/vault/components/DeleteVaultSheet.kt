package com.dot.gallery.feature_node.presentation.vault.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState

@Composable
fun DeleteVaultSheet(
    state: AppBottomSheetState,
    vaultName: String? = null,
    onConfirm: () -> Unit
) {
    ConfirmationSheet(
        state = state,
        title = if (vaultName != null) stringResource(R.string.vault_deletion_title_named, vaultName)
                else stringResource(R.string.vault_deletion_title),
        summary = if (vaultName != null) stringResource(R.string.vault_deletion_summary_named, vaultName)
                  else stringResource(R.string.vault_deletion_summary),
        onConfirm = onConfirm
    )
}