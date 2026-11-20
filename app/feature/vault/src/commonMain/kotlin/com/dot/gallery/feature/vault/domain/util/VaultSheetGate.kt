/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.domain.util

import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultGateAction

/**
 * Pure state machine for the vault selector gate so the open/retry/outcome matrix is
 * JVM-testable. Results nobody waits for are ignored; a failed or cancelled device prompt
 * leaves the placeholder up so Unlock can retry.
 */
object VaultSheetGate {
    fun opened(requireGateAuth: Boolean) = VaultSheetGateState(authenticated = !requireGateAuth)

    fun resolved(action: VaultGateAction, deviceAuthSupported: Boolean): VaultSheetGateStep = when (action) {
        VaultGateAction.Reveal -> VaultSheetGateStep(VaultSheetGateState(authenticated = true))
        VaultGateAction.ChallengeDevice -> if (deviceAuthSupported) VaultSheetGateStep(
            VaultSheetGateState(challenge = GateMode.DEVICE, deviceAuthPending = true),
            VaultSheetGateEffect.AUTHENTICATE_DEVICE
        ) else VaultSheetGateStep(VaultSheetGateState(), VaultSheetGateEffect.HIDE)
        is VaultGateAction.ChallengeCustom -> VaultSheetGateStep(
            VaultSheetGateState(challenge = GateMode.CUSTOM, authType = action.authType)
        )
        VaultGateAction.Deny -> VaultSheetGateStep(VaultSheetGateState(), VaultSheetGateEffect.HIDE)
    }

    fun deviceOutcome(state: VaultSheetGateState, success: Boolean): VaultSheetGateState = when {
        !state.deviceAuthPending -> state
        success -> VaultSheetGateState(authenticated = true)
        else -> state.copy(deviceAuthPending = false)
    }

    fun secretAccepted(state: VaultSheetGateState) =
        if (state.challenge == GateMode.CUSTOM) VaultSheetGateState(authenticated = true) else state
}
