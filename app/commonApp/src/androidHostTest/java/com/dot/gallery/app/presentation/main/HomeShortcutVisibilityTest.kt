/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.main

import com.dot.gallery.core.backup.PortableBackupSettings
import com.dot.gallery.core.backup.PortableSettingType
import com.dot.gallery.core.presentation.components.HomeShortcut
import com.dot.gallery.core.presentation.components.HomeShortcutSupport
import com.dot.gallery.core.presentation.components.HomeShortcutVisibility
import com.dot.gallery.core.presentation.components.homeShortcutVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShortcutVisibilityTest {
    private fun visibility(
        timelineShortcuts: Boolean = true,
        selectionActive: Boolean = false,
        support: HomeShortcutSupport = HomeShortcutSupport(),
        shortcut: HomeShortcut = HomeShortcut.FAVORITES,
        showSelect: Boolean = true,
    ) = homeShortcutVisibility(
        timelineShortcuts = timelineShortcuts,
        selectionActive = selectionActive,
        support = support,
        shortcut = shortcut,
        showSelect = showSelect,
    )

    @Test
    fun timelineShowsSelectAndFavoritesByDefault() {
        assertEquals(
            HomeShortcutVisibility(select = true, shortcut = HomeShortcut.FAVORITES, search = false),
            visibility(),
        )
    }

    @Test
    fun libraryHasNoTimelineShortcuts() {
        assertEquals(
            HomeShortcutVisibility(select = false, shortcut = null, search = false),
            visibility(timelineShortcuts = false),
        )
    }

    @Test
    fun eachShortcutFollowsItsSetting() {
        assertEquals(
            HomeShortcutVisibility(select = true, shortcut = null, search = false),
            visibility(shortcut = HomeShortcut.NONE),
        )
        assertEquals(
            HomeShortcutVisibility(select = false, shortcut = HomeShortcut.FAVORITES, search = false),
            visibility(showSelect = false),
        )
    }

    @Test
    fun theSlotShowsTheChosenShortcut() {
        // The fixed Favorites button became one configurable slot (issue #1413).
        assertEquals(HomeShortcut.TRASH, visibility(shortcut = HomeShortcut.TRASH).shortcut)
        assertEquals(HomeShortcut.VAULT, visibility(shortcut = HomeShortcut.VAULT).shortcut)
    }

    @Test
    fun shortcutsNeedPlatformSupport() {
        assertNull(visibility(support = HomeShortcutSupport(favorites = false)).shortcut)
        assertNull(
            visibility(support = HomeShortcutSupport(trash = false), shortcut = HomeShortcut.TRASH).shortcut,
        )
    }

    @Test
    fun settingsOfferOnlySupportedChoices() {
        assertEquals(
            listOf(
                HomeShortcut.FAVORITES, HomeShortcut.TRASH, HomeShortcut.VAULT,
                HomeShortcut.CAMERA, HomeShortcut.NONE,
            ),
            HomeShortcutSupport().choices,
        )
        assertEquals(
            listOf(HomeShortcut.VAULT, HomeShortcut.NONE),
            HomeShortcutSupport(favorites = false, trash = false, camera = false).choices,
        )
    }

    @Test
    fun cameraIsHiddenWithoutACameraApp() {
        // Camera is offered only when something handles the still camera intent (issue #1430).
        assertEquals(HomeShortcut.CAMERA, visibility(shortcut = HomeShortcut.CAMERA).shortcut)
        assertNull(
            visibility(support = HomeShortcutSupport(camera = false), shortcut = HomeShortcut.CAMERA).shortcut,
        )
        assertEquals(HomeShortcut.CAMERA, HomeShortcut.resolve("camera", legacyShowFavorites = true))
    }

    @Test
    fun anUnsetSlotFollowsTheOldFavoritesSwitch() {
        assertEquals(HomeShortcut.FAVORITES, HomeShortcut.resolve("", legacyShowFavorites = true))
        assertEquals(HomeShortcut.NONE, HomeShortcut.resolve("", legacyShowFavorites = false))
        assertEquals(HomeShortcut.FAVORITES, HomeShortcut.resolve("unknown", legacyShowFavorites = true))
    }

    @Test
    fun aChosenSlotIgnoresTheOldFavoritesSwitch() {
        assertEquals(HomeShortcut.TRASH, HomeShortcut.resolve("trash", legacyShowFavorites = false))
        assertEquals(HomeShortcut.FAVORITES, HomeShortcut.resolve("favorites", legacyShowFavorites = false))
        assertEquals(HomeShortcut.NONE, HomeShortcut.resolve("none", legacyShowFavorites = true))
    }

    @Test
    fun theSlotIsCarriedByPortableBackups() {
        assertEquals(PortableSettingType.STRING, PortableBackupSettings.typeFor("home_shortcut"))
    }

    @Test
    fun selectBecomesCloseAndSearchReplacesTheShortcutDuringSelection() {
        // The dock with the search pill hides while selecting, so search must move to the
        // top bar or it cannot be opened at all (issue #1400). Select stays in place as the
        // button that ends the selection (issue #1412).
        assertEquals(
            HomeShortcutVisibility(select = true, shortcut = null, search = true, selectCloses = true),
            visibility(selectionActive = true),
        )
    }

    @Test
    fun closeFollowsTheSelectSetting() {
        assertEquals(
            HomeShortcutVisibility(select = false, shortcut = null, search = true, selectCloses = false),
            visibility(selectionActive = true, showSelect = false),
        )
        assertFalse(visibility(selectionActive = true, timelineShortcuts = false).selectCloses)
        assertFalse(visibility().selectCloses)
    }

    @Test
    fun searchStaysReachableDuringSelectionWhateverTheSettings() {
        assertTrue(visibility(selectionActive = true, showSelect = false, shortcut = HomeShortcut.NONE).search)
        assertTrue(visibility(selectionActive = true, timelineShortcuts = false).search)
    }
}
