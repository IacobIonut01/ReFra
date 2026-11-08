/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.ui.components.VaultButton
import com.dot.gallery.feature.vault.ui.components.VaultButtonStyle
import com.dot.gallery.feature.vault.ui.components.VaultRow
import com.dot.gallery.feature.vault.ui.components.VaultSectionLabel
import com.dot.gallery.feature.vault.ui.components.VaultWizardScaffold
import com.dot.gallery.feature.vault.ui.components.icon
import com.dot.gallery.feature.vault.ui.components.vaultRowPosition
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Encrypted
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.vault_add_photos
import com.dot.gallery.feature.vault.generated.resources.vault_close_cd
import com.dot.gallery.feature.vault.generated.resources.vault_later
import com.dot.gallery.feature.vault.generated.resources.vault_list_locked_custom
import com.dot.gallery.feature.vault.generated.resources.vault_list_locked_device
import com.dot.gallery.feature.vault.generated.resources.vault_list_unlocked
import com.dot.gallery.feature.vault.generated.resources.vault_ready_body
import com.dot.gallery.feature.vault.generated.resources.vault_ready_lock_all
import com.dot.gallery.feature.vault.generated.resources.vault_ready_lock_each
import com.dot.gallery.feature.vault.generated.resources.vault_ready_title
import com.dot.gallery.feature.vault.generated.resources.vault_ready_unlock_summary
import com.dot.gallery.feature.vault.generated.resources.vault_your_setup

/**
 * Last setup step: what was chosen, then Later (back to the vault list) or Add photos (open the
 * new vault and its media picker). [unlockType] is the vault's custom lock, null for device
 * security; [gateMode] is shown only when the vault-list lock was part of this setup.
 */
@Composable
fun VaultReadyContent(
    vaultName: String,
    unlockType: VaultAuthType?,
    gateMode: GateMode?,
    lockAllVaults: Boolean,
    onLater: () -> Unit,
    onAddPhotos: () -> Unit,
    modifier: Modifier = Modifier,
    totalSteps: Int = 4,
) {
    VaultWizardScaffold(
        modifier = modifier,
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(Res.string.vault_ready_title, vaultName),
        body = stringResource(Res.string.vault_ready_body),
        navigationContentDescription = stringResource(Res.string.vault_close_cd),
        onNavigation = onLater,
        step = totalSteps,
        totalSteps = totalSteps,
        bottomBar = {
            VaultButton(
                text = stringResource(Res.string.vault_later),
                style = VaultButtonStyle.Tonal,
                modifier = Modifier.weight(1f),
                onClick = onLater,
            )
            VaultButton(
                text = stringResource(Res.string.vault_add_photos),
                modifier = Modifier.weight(1f),
                onClick = onAddPhotos,
            )
        },
    ) {
        Column {
            VaultSectionLabel(stringResource(Res.string.vault_your_setup))
            val rows = if (gateMode != null) 2 else 1
            VaultRow(
                icon = unlockType?.icon() ?: Icons.Outlined.Fingerprint,
                title = vaultLockLabel(unlockType),
                summary = stringResource(Res.string.vault_ready_unlock_summary, vaultName),
                position = vaultRowPosition(0, rows),
            )
            if (gateMode != null) {
                VaultRow(
                    icon = GalleryIcons.Encrypted,
                    title = stringResource(
                        when (gateMode) {
                            GateMode.DEVICE -> Res.string.vault_list_locked_device
                            GateMode.CUSTOM -> Res.string.vault_list_locked_custom
                            GateMode.NONE -> Res.string.vault_list_unlocked
                        }
                    ),
                    summary = stringResource(
                        if (lockAllVaults && gateMode != GateMode.NONE) Res.string.vault_ready_lock_all
                        else Res.string.vault_ready_lock_each
                    ),
                    position = vaultRowPosition(1, rows),
                )
            }
        }
    }
}
