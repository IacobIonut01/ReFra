/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.domain.util

/** Result of resolving the gate: the new state plus the effect to perform. */
data class VaultSheetGateStep(
    val state: VaultSheetGateState,
    val effect: VaultSheetGateEffect = VaultSheetGateEffect.NONE
)
