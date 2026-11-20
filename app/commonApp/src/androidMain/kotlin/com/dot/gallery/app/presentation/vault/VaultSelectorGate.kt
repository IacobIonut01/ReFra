/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultGateAction
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.vault.domain.util.VaultGatePolicy
import com.dot.gallery.feature.vault.ui.components.VaultDeviceAuthBridge
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.biometric_authentication
import com.dot.gallery.generated.resources.verify_identity
import org.jetbrains.compose.resources.stringResource

/**
 * App-side gate for [com.dot.gallery.feature.vault.ui.components.SelectVaultSheet]: resolves
 * the configured gate mode into a [VaultGateAction], verifies custom secrets against the gate
 * credential and owns the device-credential prompt bridge, mirroring the gate wiring in
 * [VaultScreen].
 */
@Stable
class VaultSelectorGate(
    val resolveGateAction: suspend () -> VaultGateAction,
    val verifyGateSecret: suspend (String) -> VerifyResult,
    val deviceAuth: VaultDeviceAuthBridge,
)

@Composable
fun rememberVaultSelectorGate(): VaultSelectorGate {
    val context = LocalContext.current
    val outcome = remember { mutableStateOf<Boolean?>(null) }
    val biometricState = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.verify_identity),
        onSuccess = { outcome.value = true },
        onFailed = { outcome.value = false }
    )
    return remember(context, biometricState) {
        val deviceAuth = VaultDeviceAuthBridge(
            isSupported = biometricState.isSupported,
            authenticateAction = biometricState::authenticate,
            cancelAction = biometricState::cancelAuthentication,
            outcomeState = outcome
        )
        VaultSelectorGate(
            resolveGateAction = {
                val mode = VaultPasswordManager.getGateMode(context)
                val credential = if (mode == GateMode.CUSTOM) {
                    VaultPasswordManager.getCredentialStatus(context, VaultPasswordManager.GATE_UUID)
                } else null
                VaultGatePolicy.resolve(mode, deviceAuth.isSupported, credential)
            },
            verifyGateSecret = { secret ->
                VaultPasswordManager.verifyPassword(context, VaultPasswordManager.GATE_UUID, secret)
            },
            deviceAuth = deviceAuth
        )
    }
}
