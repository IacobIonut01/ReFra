/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.domain.util

import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType

/**
 * Gate state of [com.dot.gallery.feature.vault.ui.components.SelectVaultSheet]: whether the
 * vault list may be shown, which challenge is pending and whether a device-credential prompt
 * is in flight. [showsUnlockPlaceholder] is true while a retryable "Unlock vault" entry is
 * offered — never for a custom secret, which renders its own input instead.
 */
data class VaultSheetGateState(
    val authenticated: Boolean = false,
    val challenge: GateMode? = null,
    val authType: VaultAuthType? = null,
    val deviceAuthPending: Boolean = false,
) {
    val showsUnlockPlaceholder: Boolean get() = !authenticated && challenge != GateMode.CUSTOM
}
