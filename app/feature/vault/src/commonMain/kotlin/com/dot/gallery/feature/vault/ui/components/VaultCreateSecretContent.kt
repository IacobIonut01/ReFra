/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.back
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_password
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_password_body
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_pattern
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_pattern_body
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_pin
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_pin_body
import com.dot.gallery.feature.vault.generated.resources.vault_continue
import com.dot.gallery.feature.vault.generated.resources.vault_create_password_body
import com.dot.gallery.feature.vault.generated.resources.vault_create_password_title
import com.dot.gallery.feature.vault.generated.resources.vault_create_pattern_body
import com.dot.gallery.feature.vault.generated.resources.vault_create_pattern_title
import com.dot.gallery.feature.vault.generated.resources.vault_create_pin_body
import com.dot.gallery.feature.vault.generated.resources.vault_create_pin_title
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_password_body
import com.dot.gallery.feature.vault.generated.resources.vault_list_lock_pin_body
import com.dot.gallery.feature.vault.generated.resources.vault_password_mismatch
import com.dot.gallery.feature.vault.generated.resources.vault_patterns_mismatch
import com.dot.gallery.feature.vault.generated.resources.vault_pins_mismatch

fun VaultAuthType.icon(): ImageVector = when (this) {
    VaultAuthType.PIN -> Icons.Outlined.Pin
    VaultAuthType.PATTERN -> Icons.Outlined.Pattern
    VaultAuthType.PASSWORD -> Icons.Outlined.Password
}

/**
 * Create-then-confirm step for a custom lock. [vaultName] names the vault in the copy; null
 * means the lock protects the vault list. Back from the confirm page returns to the first entry,
 * back from the first entry calls [onBack].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VaultCreateSecretContent(
    type: VaultAuthType,
    vaultName: String?,
    onBack: () -> Unit,
    onSecretSet: (type: VaultAuthType, secret: String) -> Unit,
    modifier: Modifier = Modifier,
    step: Int? = null,
    totalSteps: Int = 4,
) {
    var firstEntry by remember(type) { mutableStateOf<String?>(null) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    var attempt by remember(type) { mutableIntStateOf(0) }
    val confirming = firstEntry != null
    val mismatch = stringResource(
        when (type) {
            VaultAuthType.PIN -> Res.string.vault_pins_mismatch
            VaultAuthType.PATTERN -> Res.string.vault_patterns_mismatch
            VaultAuthType.PASSWORD -> Res.string.vault_password_mismatch
        }
    )

    fun back() {
        if (confirming) {
            firstEntry = null
            error = null
            attempt++
        } else {
            onBack()
        }
    }
    BackHandler(onBack = ::back)

    val title = stringResource(
        when (type) {
            VaultAuthType.PIN -> if (confirming) Res.string.vault_confirm_pin else Res.string.vault_create_pin_title
            VaultAuthType.PATTERN -> if (confirming) Res.string.vault_confirm_pattern else Res.string.vault_create_pattern_title
            VaultAuthType.PASSWORD -> if (confirming) Res.string.vault_confirm_password else Res.string.vault_create_password_title
        }
    )
    val body = when {
        confirming -> stringResource(
            when (type) {
                VaultAuthType.PIN -> Res.string.vault_confirm_pin_body
                VaultAuthType.PATTERN -> Res.string.vault_confirm_pattern_body
                VaultAuthType.PASSWORD -> Res.string.vault_confirm_password_body
            }
        )
        type == VaultAuthType.PATTERN -> stringResource(Res.string.vault_create_pattern_body)
        vaultName == null -> stringResource(
            if (type == VaultAuthType.PIN) Res.string.vault_list_lock_pin_body else Res.string.vault_list_lock_password_body
        )
        else -> stringResource(
            if (type == VaultAuthType.PIN) Res.string.vault_create_pin_body else Res.string.vault_create_password_body,
            vaultName
        )
    }

    VaultWizardScaffold(
        modifier = modifier,
        icon = type.icon(),
        title = title,
        body = body,
        navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
        navigationContentDescription = stringResource(Res.string.back),
        onNavigation = ::back,
        step = step,
        totalSteps = totalSteps,
        scrollable = false,
    ) {
        VaultSecretInput(
            type = type,
            errorMessage = error,
            creating = true,
            submitLabel = stringResource(Res.string.vault_continue),
            resetKey = attempt to confirming,
            onEdit = { error = null },
            onSubmit = { secret ->
                val first = firstEntry
                when {
                    first == null -> firstEntry = secret
                    first == secret -> onSecretSet(type, secret)
                    else -> error = mismatch
                }
            },
        )
    }
}
