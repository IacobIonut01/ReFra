/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.main

import com.dot.gallery.core.presentation.components.HomeShortcutVisibility
import com.dot.gallery.core.presentation.components.homeShortcutVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShortcutVisibilityTest {
    private fun visibility(
        timelineShortcuts: Boolean = true,
        selectionActive: Boolean = false,
        supportsFavorites: Boolean = true,
        showFavorites: Boolean = true,
        showSelect: Boolean = true,
    ) = homeShortcutVisibility(
        timelineShortcuts = timelineShortcuts,
        selectionActive = selectionActive,
        supportsFavorites = supportsFavorites,
        showFavorites = showFavorites,
        showSelect = showSelect,
    )

    @Test
    fun timelineShowsSelectAndFavoritesByDefault() {
        assertEquals(
            HomeShortcutVisibility(select = true, favorites = true, search = false),
            visibility(),
        )
    }

    @Test
    fun libraryHasNoTimelineShortcuts() {
        assertEquals(
            HomeShortcutVisibility(select = false, favorites = false, search = false),
            visibility(timelineShortcuts = false),
        )
    }

    @Test
    fun eachShortcutFollowsItsSetting() {
        assertEquals(
            HomeShortcutVisibility(select = true, favorites = false, search = false),
            visibility(showFavorites = false),
        )
        assertEquals(
            HomeShortcutVisibility(select = false, favorites = true, search = false),
            visibility(showSelect = false),
        )
    }

    @Test
    fun favoritesNeedsPlatformSupport() {
        assertEquals(false, visibility(supportsFavorites = false).favorites)
    }

    @Test
    fun searchReplacesTheShortcutsDuringSelection() {
        // The dock with the search pill hides while selecting, so search must move to the
        // top bar or it cannot be opened at all (issue #1400).
        assertEquals(
            HomeShortcutVisibility(select = false, favorites = false, search = true),
            visibility(selectionActive = true),
        )
    }

    @Test
    fun searchStaysReachableDuringSelectionWhateverTheSettings() {
        assertTrue(visibility(selectionActive = true, showSelect = false, showFavorites = false).search)
        assertTrue(visibility(selectionActive = true, timelineShortcuts = false).search)
    }
}
