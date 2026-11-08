/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.feature.vault.ui.components.VaultButton
import com.dot.gallery.feature.vault.ui.components.VaultButtonStyle
import com.dot.gallery.feature.vault.ui.components.VaultRow
import com.dot.gallery.feature.vault.ui.components.VaultTextField
import com.dot.gallery.feature.vault.ui.components.VaultWizardScaffold
import com.dot.gallery.feature.vault.ui.components.vaultRowPosition
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Encrypted
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.action_cancel
import com.dot.gallery.feature.vault.generated.resources.vault_already_exists
import com.dot.gallery.feature.vault.generated.resources.vault_close_cd
import com.dot.gallery.feature.vault.generated.resources.vault_continue
import com.dot.gallery.feature.vault.generated.resources.vault_feature_aes_summary
import com.dot.gallery.feature.vault.generated.resources.vault_feature_aes_title
import com.dot.gallery.feature.vault.generated.resources.vault_feature_decrypted_summary
import com.dot.gallery.feature.vault.generated.resources.vault_feature_decrypted_title
import com.dot.gallery.feature.vault.generated.resources.vault_feature_lock_summary
import com.dot.gallery.feature.vault.generated.resources.vault_feature_lock_title
import com.dot.gallery.feature.vault.generated.resources.vault_name_label
import com.dot.gallery.feature.vault.generated.resources.vault_name_suggestion_documents
import com.dot.gallery.feature.vault.generated.resources.vault_name_suggestion_family
import com.dot.gallery.feature.vault.generated.resources.vault_name_suggestion_private
import com.dot.gallery.feature.vault.generated.resources.vault_name_suggestion_receipts
import com.dot.gallery.feature.vault.generated.resources.vault_name_suggestion_travel
import com.dot.gallery.feature.vault.generated.resources.vault_no_screen_lock_body
import com.dot.gallery.feature.vault.generated.resources.vault_no_screen_lock_title
import com.dot.gallery.feature.vault.generated.resources.vault_open_security_settings
import com.dot.gallery.feature.vault.generated.resources.vault_wizard_body
import com.dot.gallery.feature.vault.generated.resources.vault_wizard_title

/** Vault names keep letters and digits only. */
internal fun sanitizeVaultName(input: String): String = input.filter { it.isLetterOrDigit() }

/**
 * First vault-setup step: the name, a few suggested names, how the vault protects files, and
 * a blocking notice when the device has no screen lock.
 *
 * [deviceSecurityAvailable] is resolved by the host (BiometricManager/KeyguardManager on
 * Android); [onOpenSecuritySettings] opens the system security settings from that notice.
 * [onCreate] receives the newly named [Vault]; duplicate-name validation against
 * [existingVaults] stays inside the composable.
 */
@Composable
fun VaultSetupContent(
    existingVaults: List<Vault>,
    deviceSecurityAvailable: Boolean,
    modifier: Modifier = Modifier,
    totalSteps: Int = 4,
    onOpenSecuritySettings: (() -> Unit)? = null,
    onCancel: () -> Unit,
    onCreate: (Vault) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    val alreadyExists = stringResource(Res.string.vault_already_exists, name)
    val suggestions = listOf(
        stringResource(Res.string.vault_name_suggestion_private),
        stringResource(Res.string.vault_name_suggestion_documents),
        stringResource(Res.string.vault_name_suggestion_travel),
        stringResource(Res.string.vault_name_suggestion_family),
        stringResource(Res.string.vault_name_suggestion_receipts),
    ).map(::sanitizeVaultName).filter { suggestion -> existingVaults.none { it.name == suggestion } }

    fun submit() {
        if (existingVaults.any { it.name == name }) nameError = alreadyExists
        else onCreate(Vault(name = name))
    }
    val canContinue = deviceSecurityAvailable && nameError == null && name.isNotEmpty()

    VaultWizardScaffold(
        modifier = modifier,
        icon = GalleryIcons.Encrypted,
        title = stringResource(Res.string.vault_wizard_title),
        body = stringResource(Res.string.vault_wizard_body),
        navigationContentDescription = stringResource(Res.string.vault_close_cd),
        onNavigation = onCancel,
        step = 1,
        totalSteps = totalSteps,
        bottomBar = {
            VaultButton(
                text = stringResource(Res.string.action_cancel),
                style = VaultButtonStyle.Tonal,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            VaultButton(
                text = stringResource(Res.string.vault_continue),
                modifier = Modifier.weight(1f),
                enabled = canContinue,
                onClick = ::submit,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VaultTextField(
                value = name,
                onValueChange = {
                    nameError = null
                    name = sanitizeVaultName(it)
                },
                label = stringResource(Res.string.vault_name_label),
                enabled = deviceSecurityAvailable,
                isError = nameError != null,
                onImeAction = { if (canContinue) submit() },
            )
            nameError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .alpha(if (deviceSecurityAvailable) 1f else 0.38f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                GalleryFilterChip(
                    label = suggestion,
                    selected = name == suggestion,
                    onClick = {
                        if (deviceSecurityAvailable) {
                            nameError = null
                            name = suggestion
                        }
                    },
                )
            }
        }
        if (deviceSecurityAvailable) {
            Column {
                val features = listOf(
                    Triple(GalleryIcons.Encrypted, Res.string.vault_feature_aes_title, Res.string.vault_feature_aes_summary),
                    Triple(Icons.Outlined.VisibilityOff, Res.string.vault_feature_decrypted_title, Res.string.vault_feature_decrypted_summary),
                    Triple(Icons.Outlined.ScreenLockPortrait, Res.string.vault_feature_lock_title, Res.string.vault_feature_lock_summary),
                )
                features.forEachIndexed { index, (icon, title, summary) ->
                    VaultRow(
                        icon = icon,
                        title = stringResource(title),
                        summary = stringResource(summary),
                        position = vaultRowPosition(index, features.size),
                    )
                }
            }
        } else {
            NoScreenLockCard(onOpenSecuritySettings)
        }
    }
}

@Composable
private fun NoScreenLockCard(onOpenSecuritySettings: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (onOpenSecuritySettings != null) 4.dp else 16.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.vault_no_screen_lock_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(Res.string.vault_no_screen_lock_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (onOpenSecuritySettings != null) {
                TextButton(
                    onClick = onOpenSecuritySettings,
                    modifier = Modifier.align(Alignment.Start),
                    // Line the label up with the card text above it.
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.vault_open_security_settings),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
