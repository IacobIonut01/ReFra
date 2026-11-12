/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.core.presentation.components.classicSearchSlot
import com.dot.gallery.core.presentation.components.searchActionFor
import com.dot.gallery.core.presentation.util.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The classic navbar and rail must offer Search like the floating chrome does (issue 1392). */
class ClassicNavigationSearchTest {

    @Test
    fun searchSitsBetweenTheTwoPrimaryDestinations() {
        assertEquals(1, classicSearchSlot(itemCount = 2, showSearch = true))
    }

    @Test
    fun searchStaysInTheMiddleForOtherCounts() {
        assertEquals(0, classicSearchSlot(itemCount = 0, showSearch = true))
        assertEquals(0, classicSearchSlot(itemCount = 1, showSearch = true))
        assertEquals(1, classicSearchSlot(itemCount = 3, showSearch = true))
        assertEquals(2, classicSearchSlot(itemCount = 4, showSearch = true))
    }

    @Test
    fun noSlotWithoutSearchAction() {
        assertEquals(-1, classicSearchSlot(itemCount = 2, showSearch = false))
    }

    @Test
    fun searchActionIsOfferedOnPrimaryScreens() {
        var opened = false
        val action = searchActionFor(Screen.TimelineScreen.route) { opened = true }
        assertNotNull(action)
        action!!.invoke()
        assertEquals(true, opened)
        assertNotNull(searchActionFor(Screen.LibraryScreen()) {})
        assertNotNull(searchActionFor(null) {})
    }

    @Test
    fun searchActionHidesOnTheSearchScreen() {
        assertNull(searchActionFor(Screen.SearchScreen()) {})
    }
}
