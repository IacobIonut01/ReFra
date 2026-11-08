/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.picker

import com.dot.gallery.feature.picker.ui.PickerSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerSelectionTest {

    @Test
    fun `multi-pick tap toggles the item`() {
        assertEquals(setOf(1L, 2L), PickerSelection.tap(setOf(1L), 2L, allowMultiple = true))
        assertEquals(setOf(1L), PickerSelection.tap(setOf(1L, 2L), 2L, allowMultiple = true))
    }

    @Test
    fun `single pick tap replaces the current pick`() {
        assertEquals(setOf(2L), PickerSelection.tap(setOf(1L), 2L, allowMultiple = false))
    }

    @Test
    fun `single pick tap on the picked item clears it`() {
        assertEquals(emptySet<Long>(), PickerSelection.tap(setOf(1L), 1L, allowMultiple = false))
    }

    @Test
    fun `long press always leaves the item picked`() {
        assertEquals(setOf(1L, 2L), PickerSelection.longPress(setOf(1L, 2L), 2L, allowMultiple = true))
        assertEquals(setOf(2L), PickerSelection.longPress(setOf(1L), 2L, allowMultiple = false))
    }

    @Test
    fun `day toggle picks the whole day unless it is already picked`() {
        assertEquals(setOf(9L, 1L, 2L), PickerSelection.toggleDay(setOf(9L, 1L), listOf(1L, 2L)))
        assertEquals(setOf(9L), PickerSelection.toggleDay(setOf(9L, 1L, 2L), listOf(1L, 2L)))
        assertEquals(setOf(9L), PickerSelection.toggleDay(setOf(9L), emptyList()))
    }
}
