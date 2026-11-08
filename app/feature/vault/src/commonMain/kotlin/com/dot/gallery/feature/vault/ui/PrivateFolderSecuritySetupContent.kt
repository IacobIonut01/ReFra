/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.ui.components.VaultButton
import com.dot.gallery.feature.vault.ui.components.VaultButtonStyle
import com.dot.gallery.feature.vault.ui.components.VaultCreateSecretContent
import com.dot.gallery.feature.vault.ui.components.VaultRow
import com.dot.gallery.feature.vault.ui.components.VaultRowTrailing
import com.dot.gallery.feature.vault.ui.components.VaultWizardScaffold
import com.dot.gallery.feature.vault.ui.components.icon
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.back
import com.dot.gallery.feature.vault.generated.resources.private_folder_no_lock_summary
import com.dot.gallery.feature.vault.generated.resources.private_folder_pin_summary
import com.dot.gallery.feature.vault.generated.resources.private_folder_unlock_body
import com.dot.gallery.feature.vault.generated.resources.private_folder_unlock_title
import com.dot.gallery.feature.vault.generated.resources.security_private_folder
import com.dot.gallery.feature.vault.generated.resources.vault_continue
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_none

/**
 * Private-folder security setup in the vault wizard style: one radio list of device security
 * (recommended), PIN, Pattern and Password, with No lock in its own row under it, and Continue.
 * PIN, Pattern and Password continue to the shared create-and-confirm page. Mode persistence
 * lives in the host — the callbacks fire after the user finishes, mirroring the app's ordering
 * (persist, then navigate).
 */
@Composable
fun PrivateFolderSecuritySetupContent(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onNoneSelected: () -> Unit,
    onDeviceSecuritySelected: () -> Unit,
    onCustomSecretSet: (type: VaultAuthType, secret: String) -> Unit,
) {
    // null = device security; ignored while noLock is on.
    var selected by rememberSaveable { mutableStateOf<VaultAuthType?>(null) }
    var noLock by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf<VaultAuthType?>(null) }

    creating?.let { type ->
        VaultCreateSecretContent(
            modifier = modifier,
            type = type,
            vaultName = stringResource(Res.string.security_private_folder),
            onBack = { creating = null },
            onSecretSet = onCustomSecretSet,
        )
        return
    }

    VaultWizardScaffold(
        modifier = modifier,
        icon = when {
            noLock -> Icons.Outlined.LockOpen
            else -> selected?.icon() ?: Icons.Outlined.Fingerprint
        },
        title = stringResource(Res.string.private_folder_unlock_title),
        body = stringResource(Res.string.private_folder_unlock_body),
        navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
        navigationContentDescription = stringResource(Res.string.back),
        onNavigation = onBack,
        bottomBar = {
            if (onBack != null) {
                VaultButton(
                    text = stringResource(Res.string.back),
                    style = VaultButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                    onClick = onBack,
                )
            }
            VaultButton(
                text = stringResource(Res.string.vault_continue),
                modifier = Modifier.weight(1f),
                onClick = {
                    val type = selected
                    when {
                        noLock -> onNoneSelected()
                        type == null -> onDeviceSecuritySelected()
                        else -> creating = type
                    }
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                VaultUnlockMethodRows(
                    selected = selected,
                    noneSelected = noLock,
                    pinSummary = Res.string.private_folder_pin_summary,
                    onSelect = {
                        selected = it
                        noLock = false
                    },
                )
            }
            VaultRow(
                icon = Icons.Outlined.LockOpen,
                title = stringResource(Res.string.vault_list_lock_none),
                summary = stringResource(Res.string.private_folder_no_lock_summary),
                trailing = VaultRowTrailing.Radio(noLock),
                onClick = { noLock = true },
            )
        }
    }
}
