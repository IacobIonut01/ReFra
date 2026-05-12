package com.dot.gallery.feature_node.presentation.vault

import kotlinx.serialization.Serializable

@Serializable
sealed class VaultScreens {

    @Serializable
    data object VaultSetup : VaultScreens()

    @Serializable
    data object VaultPasswordSetup : VaultScreens()

    @Serializable
    data object VaultGateAuth : VaultScreens()

    @Serializable
    data object VaultGateSetup : VaultScreens()

    @Serializable
    data object VaultSelect : VaultScreens()

    @Serializable
    data object VaultDisplay : VaultScreens()

    @Serializable
    data class EncryptedMediaViewScreen(val mediaId: Long) : VaultScreens()

    @Serializable
    data object LoadingScreen : VaultScreens()
}