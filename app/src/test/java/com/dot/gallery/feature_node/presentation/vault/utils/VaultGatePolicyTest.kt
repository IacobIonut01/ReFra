package com.dot.gallery.feature_node.presentation.vault.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultGatePolicyTest {

    @Test
    fun noneRevealsWithoutChallenge() {
        assertEquals(
            VaultGateAction.Reveal,
            VaultGatePolicy.resolve(
                mode = GateMode.NONE,
                deviceAuthSupported = false,
                credential = null
            )
        )
    }

    @Test
    fun deviceChallengesWhenDeviceAuthAvailable() {
        assertEquals(
            VaultGateAction.ChallengeDevice,
            VaultGatePolicy.resolve(
                mode = GateMode.DEVICE,
                deviceAuthSupported = true,
                credential = null
            )
        )
    }

    @Test
    fun deviceFailsClosedWhenDeviceAuthUnavailable() {
        assertEquals(
            VaultGateAction.Deny,
            VaultGatePolicy.resolve(
                mode = GateMode.DEVICE,
                deviceAuthSupported = false,
                credential = null
            )
        )
    }

    @Test
    fun customChallengesWithStoredAuthType() {
        assertEquals(
            VaultGateAction.ChallengeCustom(VaultAuthType.PATTERN),
            VaultGatePolicy.resolve(
                mode = GateMode.CUSTOM,
                deviceAuthSupported = false,
                credential = VaultCredentialStatus.Valid(VaultAuthType.PATTERN)
            )
        )
    }

    @Test
    fun customFailsClosedWhenCredentialMissing() {
        assertEquals(
            VaultGateAction.Deny,
            VaultGatePolicy.resolve(
                mode = GateMode.CUSTOM,
                deviceAuthSupported = true,
                credential = VaultCredentialStatus.Missing
            )
        )
    }

    @Test
    fun customFailsClosedWhenCredentialCorrupt() {
        assertEquals(
            VaultGateAction.Deny,
            VaultGatePolicy.resolve(
                mode = GateMode.CUSTOM,
                deviceAuthSupported = true,
                credential = VaultCredentialStatus.Corrupt
            )
        )
    }

    @Test
    fun customFailsClosedWhenCredentialNotLoaded() {
        assertEquals(
            VaultGateAction.Deny,
            VaultGatePolicy.resolve(
                mode = GateMode.CUSTOM,
                deviceAuthSupported = true,
                credential = null
            )
        )
    }
}
