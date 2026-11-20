/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.vault.utils

import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.domain.model.VaultGateAction
import com.dot.gallery.feature.vault.domain.util.VaultSheetGate
import com.dot.gallery.feature.vault.domain.util.VaultSheetGateEffect
import com.dot.gallery.feature.vault.domain.util.VaultSheetGateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSheetGateTest {

    @Test
    fun openedWithoutGateIsAuthenticated() {
        val state = VaultSheetGate.opened(requireGateAuth = false)
        assertTrue(state.authenticated)
        assertFalse(state.showsUnlockPlaceholder)
    }

    @Test
    fun openedWithGateShowsPlaceholder() {
        val state = VaultSheetGate.opened(requireGateAuth = true)
        assertFalse(state.authenticated)
        assertTrue(state.showsUnlockPlaceholder)
    }

    @Test
    fun revealAuthenticatesWithoutEffect() {
        val step = VaultSheetGate.resolved(VaultGateAction.Reveal, deviceAuthSupported = true)
        assertTrue(step.state.authenticated)
        assertFalse(step.state.showsUnlockPlaceholder)
        assertEquals(VaultSheetGateEffect.NONE, step.effect)
    }

    @Test
    fun deviceChallengePromptsWhenSupported() {
        val step = VaultSheetGate.resolved(VaultGateAction.ChallengeDevice, deviceAuthSupported = true)
        assertEquals(GateMode.DEVICE, step.state.challenge)
        assertTrue(step.state.deviceAuthPending)
        assertTrue(step.state.showsUnlockPlaceholder)
        assertEquals(VaultSheetGateEffect.AUTHENTICATE_DEVICE, step.effect)
    }

    @Test
    fun deviceChallengeHidesWhenUnsupported() {
        val step = VaultSheetGate.resolved(VaultGateAction.ChallengeDevice, deviceAuthSupported = false)
        assertEquals(VaultSheetGateEffect.HIDE, step.effect)
        assertFalse(step.state.authenticated)
    }

    @Test
    fun customChallengeShowsInlineEntry() {
        val step = VaultSheetGate.resolved(
            VaultGateAction.ChallengeCustom(VaultAuthType.PIN),
            deviceAuthSupported = true
        )
        assertEquals(GateMode.CUSTOM, step.state.challenge)
        assertEquals(VaultAuthType.PIN, step.state.authType)
        assertFalse(step.state.showsUnlockPlaceholder)
        assertEquals(VaultSheetGateEffect.NONE, step.effect)
    }

    @Test
    fun denyHides() {
        val step = VaultSheetGate.resolved(VaultGateAction.Deny, deviceAuthSupported = true)
        assertEquals(VaultSheetGateEffect.HIDE, step.effect)
        assertFalse(step.state.authenticated)
    }

    @Test
    fun deviceSuccessAuthenticates() {
        val pending = VaultSheetGate.resolved(VaultGateAction.ChallengeDevice, true).state
        val done = VaultSheetGate.deviceOutcome(pending, success = true)
        assertTrue(done.authenticated)
        assertFalse(done.deviceAuthPending)
        assertFalse(done.showsUnlockPlaceholder)
    }

    @Test
    fun deviceFailureKeepsPlaceholderForRetry() {
        val pending = VaultSheetGate.resolved(VaultGateAction.ChallengeDevice, true).state
        val failed = VaultSheetGate.deviceOutcome(pending, success = false)
        assertFalse(failed.authenticated)
        assertFalse(failed.deviceAuthPending)
        assertEquals(GateMode.DEVICE, failed.challenge)
        assertTrue(failed.showsUnlockPlaceholder)
    }

    @Test
    fun deviceOutcomeIgnoredWhenNotPending() {
        val idle = VaultSheetGateState(challenge = GateMode.CUSTOM, authType = VaultAuthType.PIN)
        assertEquals(idle, VaultSheetGate.deviceOutcome(idle, success = true))
        assertEquals(idle, VaultSheetGate.deviceOutcome(idle, success = false))
    }

    @Test
    fun retryAfterFailurePromptsAgain() {
        // A failed/cancelled prompt leaves the placeholder; the next Unlock tap
        // re-resolves the gate and asks for the prompt again.
        val retry = VaultSheetGate.resolved(VaultGateAction.ChallengeDevice, deviceAuthSupported = true)
        assertEquals(VaultSheetGateEffect.AUTHENTICATE_DEVICE, retry.effect)
        assertTrue(retry.state.deviceAuthPending)
        assertTrue(retry.state.showsUnlockPlaceholder)
    }

    @Test
    fun secretAcceptedOnlyOnCustomChallenge() {
        val custom = VaultSheetGate.resolved(
            VaultGateAction.ChallengeCustom(VaultAuthType.PATTERN),
            deviceAuthSupported = false
        ).state
        val accepted = VaultSheetGate.secretAccepted(custom)
        assertTrue(accepted.authenticated)
        assertFalse(accepted.showsUnlockPlaceholder)

        val device = VaultSheetGate.resolved(VaultGateAction.ChallengeDevice, true).state
        assertEquals(device, VaultSheetGate.secretAccepted(device))
    }
}
