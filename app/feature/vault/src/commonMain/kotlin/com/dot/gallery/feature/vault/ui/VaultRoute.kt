/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import kotlinx.serialization.Serializable

/**
 * Typed routes of the vault-internal NavHost hosted by [VaultContent]. These are
 * destinations *inside* the vault flow, not app-level `Screen.*` routes — the app
 * wrapper only maps the events that leave the vault.
 */
@Serializable
sealed class VaultRoute {

    @Serializable
    data object VaultSetup : VaultRoute()

    @Serializable
    data object VaultPasswordSetup : VaultRoute()

    @Serializable
    data object VaultGateAuth : VaultRoute()

    @Serializable
    data object VaultGateSetup : VaultRoute()

    @Serializable
    data object VaultReady : VaultRoute()

    @Serializable
    data object VaultSelect : VaultRoute()

    @Serializable
    data object VaultDisplay : VaultRoute()

    @Serializable
    data class EncryptedMediaViewScreen(val mediaId: Long) : VaultRoute()

    @Serializable
    data object LoadingScreen : VaultRoute()
}
