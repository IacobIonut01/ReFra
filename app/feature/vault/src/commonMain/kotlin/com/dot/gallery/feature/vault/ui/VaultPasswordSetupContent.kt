/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.ui.components.VaultButton
import com.dot.gallery.feature.vault.ui.components.VaultButtonStyle
import com.dot.gallery.feature.vault.ui.components.VaultCreateSecretContent
import com.dot.gallery.feature.vault.ui.components.VaultRow
import com.dot.gallery.feature.vault.ui.components.VaultRowTrailing
import com.dot.gallery.feature.vault.ui.components.VaultWizardScaffold
import com.dot.gallery.feature.vault.ui.components.icon
import com.dot.gallery.feature.vault.ui.components.vaultRowPosition
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.action_cancel
import com.dot.gallery.feature.vault.generated.resources.vault_close_cd
import com.dot.gallery.feature.vault.generated.resources.vault_continue
import com.dot.gallery.feature.vault.generated.resources.vault_method_device
import com.dot.gallery.feature.vault.generated.resources.vault_method_device_summary
import com.dot.gallery.feature.vault.generated.resources.vault_method_password_summary
import com.dot.gallery.feature.vault.generated.resources.vault_method_pattern_summary
import com.dot.gallery.feature.vault.generated.resources.vault_method_pin_summary
import com.dot.gallery.feature.vault.generated.resources.vault_recommended
import com.dot.gallery.feature.vault.generated.resources.vault_type_password
import com.dot.gallery.feature.vault.generated.resources.vault_type_pattern
import com.dot.gallery.feature.vault.generated.resources.vault_type_pin
import com.dot.gallery.feature.vault.generated.resources.vault_unlock_method_body
import com.dot.gallery.feature.vault.generated.resources.vault_unlock_method_title

/**
 * Second vault-setup step: how the vault unlocks. Device security finishes the step through
 * [onSkip]; PIN, Pattern and Password open the create-and-confirm page for that type and hand
 * the result to [onSecretSet] (the host persists it in the credential store).
 */
@Composable
fun VaultPasswordSetupContent(
    modifier: Modifier = Modifier,
    vaultName: String = "",
    step: Int = 2,
    totalSteps: Int = 4,
    onCancel: (() -> Unit)? = null,
    onSkip: () -> Unit,
    onSecretSet: (type: VaultAuthType, secret: String) -> Unit,
) {
    // null = device security.
    var selected by rememberSaveable { mutableStateOf<VaultAuthType?>(null) }
    var creating by rememberSaveable { mutableStateOf<VaultAuthType?>(null) }

    creating?.let { type ->
        VaultCreateSecretContent(
            modifier = modifier,
            type = type,
            vaultName = vaultName,
            step = step + 1,
            totalSteps = totalSteps,
            onBack = { creating = null },
            onSecretSet = onSecretSet,
        )
        return
    }

    VaultWizardScaffold(
        modifier = modifier,
        icon = (selected?.icon() ?: Icons.Outlined.Fingerprint),
        title = stringResource(Res.string.vault_unlock_method_title, vaultName),
        body = stringResource(Res.string.vault_unlock_method_body),
        navigationContentDescription = stringResource(Res.string.vault_close_cd),
        onNavigation = onCancel,
        step = step,
        totalSteps = totalSteps,
        bottomBar = {
            if (onCancel != null) {
                VaultButton(
                    text = stringResource(Res.string.action_cancel),
                    style = VaultButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
            }
            VaultButton(
                text = stringResource(Res.string.vault_continue),
                modifier = Modifier.weight(1f),
                onClick = { selected?.let { creating = it } ?: onSkip() },
            )
        },
    ) {
        Column {
            VaultUnlockMethodRows(selected = selected, onSelect = { selected = it })
        }
    }
}

/**
 * Device security, PIN, Pattern and Password as one radio group; null selects device security.
 * [noneSelected] clears every radio, for a host that offers an option outside this group.
 * [pinSummary] names what the PIN protects (this vault by default).
 */
@Composable
internal fun VaultUnlockMethodRows(
    selected: VaultAuthType?,
    onSelect: (VaultAuthType?) -> Unit,
    includeDevice: Boolean = true,
    noneSelected: Boolean = false,
    pinSummary: StringResource = Res.string.vault_method_pin_summary,
) {
    val options: List<VaultAuthType?> = buildList {
        if (includeDevice) add(null)
        addAll(VaultAuthType.entries)
    }
    options.forEachIndexed { index, type ->
        VaultRow(
            icon = type?.icon() ?: Icons.Outlined.Fingerprint,
            title = stringResource(
                when (type) {
                    null -> Res.string.vault_method_device
                    VaultAuthType.PIN -> Res.string.vault_type_pin
                    VaultAuthType.PATTERN -> Res.string.vault_type_pattern
                    VaultAuthType.PASSWORD -> Res.string.vault_type_password
                }
            ),
            summary = stringResource(
                when (type) {
                    null -> Res.string.vault_method_device_summary
                    VaultAuthType.PIN -> pinSummary
                    VaultAuthType.PATTERN -> Res.string.vault_method_pattern_summary
                    VaultAuthType.PASSWORD -> Res.string.vault_method_password_summary
                }
            ),
            status = if (type == null) stringResource(Res.string.vault_recommended) else null,
            position = vaultRowPosition(index, options.size),
            trailing = VaultRowTrailing.Radio(!noneSelected && selected == type),
            onClick = { onSelect(type) },
        )
    }
}
