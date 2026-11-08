/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.ui.components.VaultButton
import com.dot.gallery.feature.vault.ui.components.VaultButtonStyle
import com.dot.gallery.feature.vault.ui.components.VaultCreateSecretContent
import com.dot.gallery.feature.vault.ui.components.VaultRow
import com.dot.gallery.feature.vault.ui.components.VaultRowTrailing
import com.dot.gallery.feature.vault.ui.components.VaultSectionLabel
import com.dot.gallery.feature.vault.ui.components.VaultWizardScaffold
import com.dot.gallery.feature.vault.ui.components.vaultRowPosition
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Encrypted
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.back
import com.dot.gallery.feature.vault.generated.resources.vault_after_unlocking
import com.dot.gallery.feature.vault.generated.resources.vault_choose_lock_type
import com.dot.gallery.feature.vault.generated.resources.vault_continue
import com.dot.gallery.feature.vault.generated.resources.vault_finish
import com.dot.gallery.feature.vault.generated.resources.vault_gate_lock_all
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_body
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_custom
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_custom_summary
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_none
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_none_summary
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_title
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_type_body
import com.dot.gallery.feature.vault.generated.resources.vault_lock_all_summary
import com.dot.gallery.feature.vault.generated.resources.vault_method_device
import com.dot.gallery.feature.vault.generated.resources.vault_method_device_summary
import com.dot.gallery.feature.vault.generated.resources.vault_recommended

/**
 * Gate-setup screen: how the vault list itself is protected (device security, a custom lock
 * or none) plus the "one lock for all vaults" toggle. A custom lock continues to a type picker
 * and the create-and-confirm page. Gate-mode persistence lives in the host — the callbacks fire
 * after the user finishes, mirroring the app's ordering (persist, then navigate).
 *
 * [step] shows the wizard segments when the screen is part of the first-vault setup.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VaultGateSetupContent(
    lockAllVaults: Boolean,
    modifier: Modifier = Modifier,
    initialMode: GateMode = GateMode.DEVICE,
    step: Int? = null,
    totalSteps: Int = 4,
    onBack: (() -> Unit)? = null,
    onNoneSelected: () -> Unit,
    onDeviceSecuritySelected: () -> Unit,
    onCustomSecretSet: (type: VaultAuthType, secret: String) -> Unit,
    onLockAllVaultsChanged: (Boolean) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var pickingType by rememberSaveable { mutableStateOf(false) }
    var customType by rememberSaveable { mutableStateOf(VaultAuthType.PIN) }
    var creating by rememberSaveable { mutableStateOf(false) }

    if (creating) {
        VaultCreateSecretContent(
            modifier = modifier,
            type = customType,
            vaultName = null,
            step = step,
            totalSteps = totalSteps,
            onBack = { creating = false },
            onSecretSet = onCustomSecretSet,
        )
        return
    }

    if (pickingType) {
        BackHandler { pickingType = false }
        VaultWizardScaffold(
            modifier = modifier,
            icon = Icons.Outlined.Key,
            title = stringResource(Res.string.vault_choose_lock_type),
            body = stringResource(Res.string.vault_list_lock_type_body),
            navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            navigationContentDescription = stringResource(Res.string.back),
            onNavigation = { pickingType = false },
            step = step,
            totalSteps = totalSteps,
            bottomBar = {
                VaultButton(
                    text = stringResource(Res.string.back),
                    style = VaultButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                    onClick = { pickingType = false },
                )
                VaultButton(
                    text = stringResource(Res.string.vault_continue),
                    modifier = Modifier.weight(1f),
                    onClick = { creating = true },
                )
            },
        ) {
            Column {
                VaultUnlockMethodRows(
                    selected = customType,
                    onSelect = { customType = it ?: VaultAuthType.PIN },
                    includeDevice = false,
                )
            }
        }
        return
    }

    VaultWizardScaffold(
        modifier = modifier,
        icon = GalleryIcons.Encrypted,
        title = stringResource(Res.string.vault_list_lock_title),
        body = stringResource(Res.string.vault_list_lock_body),
        navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
        navigationContentDescription = stringResource(Res.string.back),
        onNavigation = onBack,
        step = step,
        totalSteps = totalSteps,
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
                text = stringResource(if (mode == GateMode.CUSTOM) Res.string.vault_continue else Res.string.vault_finish),
                modifier = Modifier.weight(1f),
                onClick = {
                    when (mode) {
                        GateMode.NONE -> onNoneSelected()
                        GateMode.DEVICE -> onDeviceSecuritySelected()
                        GateMode.CUSTOM -> pickingType = true
                    }
                },
            )
        },
    ) {
        Column {
            val options = listOf(GateMode.DEVICE, GateMode.CUSTOM, GateMode.NONE)
            options.forEachIndexed { index, option ->
                VaultRow(
                    icon = when (option) {
                        GateMode.DEVICE -> Icons.Outlined.Fingerprint
                        GateMode.CUSTOM -> Icons.Outlined.Key
                        GateMode.NONE -> Icons.Outlined.LockOpen
                    },
                    title = stringResource(
                        when (option) {
                            GateMode.DEVICE -> Res.string.vault_method_device
                            GateMode.CUSTOM -> Res.string.vault_list_lock_custom
                            GateMode.NONE -> Res.string.vault_list_lock_none
                        }
                    ),
                    summary = stringResource(
                        when (option) {
                            GateMode.DEVICE -> Res.string.vault_method_device_summary
                            GateMode.CUSTOM -> Res.string.vault_list_lock_custom_summary
                            GateMode.NONE -> Res.string.vault_list_lock_none_summary
                        }
                    ),
                    status = if (option == GateMode.DEVICE) stringResource(Res.string.vault_recommended) else null,
                    position = vaultRowPosition(index, options.size),
                    trailing = VaultRowTrailing.Radio(mode == option),
                    onClick = { mode = option },
                )
            }
        }
        Column {
            VaultSectionLabel(stringResource(Res.string.vault_after_unlocking))
            VaultRow(
                icon = Icons.Outlined.LockReset,
                title = stringResource(Res.string.vault_gate_lock_all),
                summary = stringResource(Res.string.vault_lock_all_summary),
                trailing = VaultRowTrailing.Switch(lockAllVaults && mode != GateMode.NONE),
                enabled = mode != GateMode.NONE,
                onClick = { onLockAllVaultsChanged(!lockAllVaults) },
            )
        }
    }
}
