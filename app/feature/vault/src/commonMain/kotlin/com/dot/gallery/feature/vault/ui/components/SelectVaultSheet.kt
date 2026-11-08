/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.OptionItem
import androidx.compose.material.icons.outlined.Lock
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.domain.model.VaultGateAction
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.ui.core.Icons
import com.dot.gallery.ui.core.icons.Encrypted
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.select_a_vault
import com.dot.gallery.feature.vault.generated.resources.vault_create_first
import com.dot.gallery.feature.vault.generated.resources.vault_create_vault
import com.dot.gallery.feature.vault.generated.resources.vault_icon_cd
import com.dot.gallery.feature.vault.generated.resources.vault_locked_out
import com.dot.gallery.feature.vault.generated.resources.vault_unlock
import com.dot.gallery.feature.vault.generated.resources.vault_wrong_password_attempts

/**
 * Vault picker sheet. The selector gate (device credential or custom secret, resolved
 * app-side via [resolveGateAction]) protects the vault list itself, so this sheet
 * re-challenges on every open and only reveals names after it is satisfied.
 *
 * @param resolveGateAction resolves the configured gate into a [VaultGateAction]
 *        (wraps the credential store + [com.dot.gallery.feature.vault.domain.util.VaultGatePolicy]);
 *        only consulted when [requireGateAuth] is true. A missing resolver fails closed.
 * @param verifyGateSecret verifies a secret against the stored gate credential.
 * @param deviceAuth platform device-credential prompt bridge (BiometricPrompt on Android).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectVaultSheet(
    state: AppBottomSheetState,
    vaultState: VaultState,
    excludeVault: Vault? = null,
    requireGateAuth: Boolean = true,
    resolveGateAction: (suspend () -> VaultGateAction)? = null,
    verifyGateSecret: (suspend (String) -> VerifyResult)? = null,
    deviceAuth: VaultDeviceAuthBridge? = null,
    onCreateVault: (() -> Unit)? = null,
    onVaultSelected: (Vault) -> Unit
) {
    val scope = rememberCoroutineScope()
    val vaults = remember(vaultState, excludeVault) {
        if (excludeVault != null) vaultState.vaults.filter { it.uuid != excludeVault.uuid }
        else vaultState.vaults
    }
    val vaultOptions = remember(vaults, state) {
        vaults.map { vault ->
            OptionItem(
                text = vault.name,
                onClick = {
                    onVaultSelected(vault)
                    scope.launch {
                        state.hide()
                    }
                }
            )
        }.toMutableStateList()
    }

    var gateAuthenticated by remember { mutableStateOf(false) }
    var gateChallenge by remember { mutableStateOf<GateMode?>(null) }
    var gateAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
    var gatePasswordError by remember { mutableStateOf<String?>(null) }
    var gateDeviceAuthPending by remember { mutableStateOf(false) }

    // Device-credential prompt result, delivered one-shot through the bridge.
    val deviceOutcome = deviceAuth?.outcome
    LaunchedEffect(deviceOutcome) {
        when (deviceOutcome) {
            true -> {
                deviceAuth.consumeOutcome()
                gateDeviceAuthPending = false
                gateChallenge = null
                gateAuthenticated = true
            }

            false -> {
                deviceAuth.consumeOutcome()
                val wasPending = gateDeviceAuthPending
                gateDeviceAuthPending = false
                if (wasPending) state.hide()
            }

            null -> Unit
        }
    }

    LaunchedEffect(state.isVisible) {
        gateAuthenticated = false
        gateChallenge = null
        gateAuthType = null
        gatePasswordError = null
        gateDeviceAuthPending = false
        if (!state.isVisible) {
            deviceAuth?.cancel()
            return@LaunchedEffect
        }
        if (!requireGateAuth) {
            gateAuthenticated = true
            return@LaunchedEffect
        }
        val action = resolveGateAction?.invoke() ?: VaultGateAction.Deny
        when (action) {
            VaultGateAction.Reveal -> gateAuthenticated = true
            VaultGateAction.ChallengeDevice -> {
                if (deviceAuth != null && deviceAuth.isSupported) {
                    gateChallenge = GateMode.DEVICE
                    gateDeviceAuthPending = true
                    deviceAuth.authenticate()
                } else {
                    state.hide()
                }
            }
            is VaultGateAction.ChallengeCustom -> {
                gateChallenge = GateMode.CUSTOM
                gateAuthType = action.authType
            }
            VaultGateAction.Deny -> state.hide()
        }
    }

    if (state.isVisible) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = {
                scope.launch {
                    state.hide()
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            if (!gateAuthenticated) {
                if (gateChallenge == GateMode.CUSTOM) {
                    VaultPasswordUnlockDialog(
                        authType = gateAuthType,
                        onDismiss = { scope.launch { state.hide() } },
                        onSubmit = { secret ->
                            scope.launch {
                                val result = verifyGateSecret?.invoke(secret)
                                    ?: VerifyResult.Failed(0)
                                when (result) {
                                    is VerifyResult.Success -> {
                                        gateChallenge = null
                                        gatePasswordError = null
                                        gateAuthenticated = true
                                    }
                                    is VerifyResult.Failed -> {
                                        gatePasswordError = getString(
                                            Res.string.vault_wrong_password_attempts,
                                            result.attemptsLeft
                                        )
                                    }
                                    is VerifyResult.LockedOut -> {
                                        val seconds = (result.cooldownMs / 1000).coerceAtLeast(1)
                                        gatePasswordError = getString(Res.string.vault_locked_out, seconds)
                                    }
                                }
                            }
                        },
                        errorMessage = gatePasswordError
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Encrypted,
                            contentDescription = stringResource(Res.string.vault_icon_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(Res.string.vault_unlock),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                return@ModalBottomSheet
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                letterSpacing = MaterialTheme.typography.titleLarge.letterSpacing
                            )
                        ) {
                            append(stringResource(Res.string.select_a_vault))
                        }
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                )
                if (vaultOptions.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.vault_create_first),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (onCreateVault != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SetupButton(
                            onClick = {
                                scope.launch { state.hide() }
                                onCreateVault()
                            },
                            applyHorizontalPadding = false,
                            applyBottomPadding = false,
                            applyInsets = false,
                            text = stringResource(Res.string.vault_create_vault)
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        vaults.forEachIndexed { index, vault ->
                            VaultRow(
                                icon = androidx.compose.material.icons.Icons.Outlined.Lock,
                                title = vault.name,
                                position = vaultRowPosition(index, vaults.size),
                                trailing = VaultRowTrailing.Chevron,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                onClick = {
                                    onVaultSelected(vault)
                                    scope.launch { state.hide() }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
