/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.picker.components

import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PickerVaultExclusionTest {

    private val private = Vault(name = "Private")
    private val documents = Vault(name = "Documents")
    private val state = VaultState(vaults = listOf(private, documents), isLoading = false)

    @Test
    fun `the vault being added to is hidden from the picker`() {
        val result = excludedVaultState(state, private.uuid.toString())

        assertEquals(listOf(documents), result.vaults)
        assertEquals(false, result.isLoading)
    }

    @Test
    fun `every vault stays when nothing is excluded`() {
        assertSame(state, excludedVaultState(state, null))
    }

    @Test
    fun `an unknown uuid leaves the vaults unchanged`() {
        assertEquals(state.vaults, excludedVaultState(state, "not-a-vault").vaults)
    }
}
