package com.dot.gallery.feature_node.presentation.vault.utils

/** What the selector gate demands before a vault list may be shown. */
sealed interface VaultGateAction {
    /** Vault names may be revealed. */
    data object Reveal : VaultGateAction

    /** Device biometric/credential authentication must succeed first. */
    data object ChallengeDevice : VaultGateAction

    /** The stored selector credential must be entered first. */
    data class ChallengeCustom(val authType: VaultAuthType) : VaultGateAction

    /** The configured challenge can no longer be satisfied — keep names hidden. */
    data object Deny : VaultGateAction
}

/**
 * Resolves the configured [GateMode] into a concrete [VaultGateAction]. This value is
 * intentionally immutable and platform-free so the gate matrix can be covered by JVM tests.
 * A gate whose challenge cannot be presented anymore (device security removed, stored
 * credential missing or corrupt) fails closed rather than exposing the vault list.
 */
object VaultGatePolicy {
    fun resolve(
        mode: GateMode,
        deviceAuthSupported: Boolean,
        credential: VaultCredentialStatus?
    ): VaultGateAction = when (mode) {
        GateMode.NONE -> VaultGateAction.Reveal
        GateMode.DEVICE ->
            if (deviceAuthSupported) VaultGateAction.ChallengeDevice else VaultGateAction.Deny
        GateMode.CUSTOM -> when (credential) {
            is VaultCredentialStatus.Valid -> VaultGateAction.ChallengeCustom(credential.authType)
            VaultCredentialStatus.Missing,
            VaultCredentialStatus.Corrupt,
            null -> VaultGateAction.Deny
        }
    }

    /**
     * Whether a satisfied gate is allowed to cover vault entry too, so one lock
     * protects every vault. Only a real gate (device or custom) can act as the
     * single lock — with no gate configured, each vault keeps its own challenge
     * rather than silently opening unprotected.
     */
    fun shouldSkipVaultAuth(mode: GateMode, lockAllVaults: Boolean): Boolean =
        lockAllVaults && mode != GateMode.NONE
}
