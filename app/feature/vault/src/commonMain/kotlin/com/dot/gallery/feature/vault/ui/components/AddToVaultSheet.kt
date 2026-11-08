/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.settings.ENCRYPT_DELETE
import com.dot.gallery.domain.settings.ENCRYPT_KEEP
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Encrypted
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.action_cancel
import com.dot.gallery.feature.vault.generated.resources.vault_add_items_title
import com.dot.gallery.feature.vault.generated.resources.vault_add_to_vault_title
import com.dot.gallery.feature.vault.generated.resources.vault_encrypt
import com.dot.gallery.feature.vault.generated.resources.vault_encrypt_delete_summary
import com.dot.gallery.feature.vault.generated.resources.vault_encrypt_delete_title
import com.dot.gallery.feature.vault.generated.resources.vault_encrypt_keep_summary
import com.dot.gallery.feature.vault.generated.resources.vault_encrypt_keep_title
import com.dot.gallery.feature.vault.generated.resources.vault_originals_question
import com.dot.gallery.feature.vault.generated.resources.vault_recommended
import com.dot.gallery.feature.vault.generated.resources.vault_remember_choice
import com.dot.gallery.feature.vault.generated.resources.vault_remember_choice_summary

/**
 * Asks what happens to the originals when media is added to a vault: encrypt and delete them
 * (the default) or encrypt and keep them. "Remember my choice" saves the answer through
 * [onBehaviorChanged] so the sheet is skipped next time. [itemCount] and [vaultName] title the
 * sheet when known.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToVaultSheet(
    state: AppBottomSheetState,
    onEncryptAndDelete: () -> Unit,
    onEncryptAndKeep: () -> Unit,
    onBehaviorChanged: (String) -> Unit,
    itemCount: Int = 0,
    vaultName: String? = null,
) {
    val scope = rememberCoroutineScope()
    var rememberChoice by remember { mutableStateOf(false) }
    var deleteOriginals by remember { mutableStateOf(true) }

    fun close() {
        scope.launch { state.hide() }
        rememberChoice = false
        deleteOriginals = true
    }

    if (state.isVisible) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = ::close,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .vaultContentWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (itemCount > 0 && vaultName != null) {
                            pluralStringResource(Res.plurals.vault_add_items_title, itemCount, itemCount, vaultName)
                        } else stringResource(Res.string.vault_add_to_vault_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(Res.string.vault_originals_question),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    VaultRow(
                        icon = GalleryIcons.Encrypted,
                        title = stringResource(Res.string.vault_encrypt_delete_title),
                        summary = stringResource(Res.string.vault_encrypt_delete_summary),
                        status = stringResource(Res.string.vault_recommended),
                        position = Position.Top,
                        trailing = VaultRowTrailing.Radio(deleteOriginals),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { deleteOriginals = true },
                    )
                    VaultRow(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = stringResource(Res.string.vault_encrypt_keep_title),
                        summary = stringResource(Res.string.vault_encrypt_keep_summary),
                        position = Position.Middle,
                        trailing = VaultRowTrailing.Radio(!deleteOriginals),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { deleteOriginals = false },
                    )
                    VaultRow(
                        title = stringResource(Res.string.vault_remember_choice),
                        summary = stringResource(Res.string.vault_remember_choice_summary),
                        position = Position.Bottom,
                        trailing = VaultRowTrailing.Switch(rememberChoice),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { rememberChoice = !rememberChoice },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton(
                        text = stringResource(Res.string.action_cancel),
                        style = VaultButtonStyle.Tonal,
                        modifier = Modifier.weight(1f),
                        onClick = ::close,
                    )
                    VaultButton(
                        text = stringResource(Res.string.vault_encrypt),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (rememberChoice) onBehaviorChanged(if (deleteOriginals) ENCRYPT_DELETE else ENCRYPT_KEEP)
                            if (deleteOriginals) onEncryptAndDelete() else onEncryptAndKeep()
                            close()
                        },
                    )
                }
            }
        }
    }
}
