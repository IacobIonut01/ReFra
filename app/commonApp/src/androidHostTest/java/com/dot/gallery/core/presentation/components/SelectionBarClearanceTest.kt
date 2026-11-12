/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionBarClearanceTest {
    @Test
    fun noBarReservesNothing() {
        assertEquals(0.dp, SelectionBarClearance().height)
    }

    @Test
    fun theTallestShownBarIsReserved() {
        val clearance = SelectionBarClearance()
        val leaving = Any()
        val arriving = Any()

        clearance.report(leaving, 180.dp)
        clearance.report(arriving, 220.dp)
        assertEquals(220.dp, clearance.height)

        // A bar leaving with its screen must not clear the one that is still shown.
        clearance.clear(leaving)
        assertEquals(220.dp, clearance.height)

        clearance.clear(arriving)
        assertEquals(0.dp, clearance.height)
    }
}
