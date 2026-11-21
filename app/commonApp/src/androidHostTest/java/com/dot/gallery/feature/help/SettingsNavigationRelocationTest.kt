/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help

import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.feature.help.domain.HelpRepository
import com.dot.gallery.feature.settings.ui.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The Navigation settings page was split up: the launch screen and the search keyboard moved to
 * General, the navigation-bar options to Appearance, and the selection options to Timeline &
 * albums. Help tips that pointed at the old page must open the page that now owns the setting.
 */
class SettingsNavigationRelocationTest {

    private fun deepLinkRoute(tipId: String): String {
        val tip = HelpRepository.getTip(tipId)
        assertNotNull("Missing tip $tipId", tip)
        val link = tip!!.deepLink ?: error("Tip $tipId has no deep link")
        return link.toScreenRoute()
    }

    @Test
    fun launchScreenTipsOpenGeneral() {
        assertEquals(Screen.SettingsGeneralScreen(), deepLinkRoute("nav_launch_screen"))
        assertEquals(Screen.SettingsGeneralScreen(), deepLinkRoute("settings_launch_screen"))
    }

    @Test
    fun navigationBarTipsOpenAppearance() {
        assertEquals(Screen.ColorPaletteScreen(), deepLinkRoute("nav_auto_hide_bars"))
        assertEquals(Screen.ColorPaletteScreen(), deepLinkRoute("settings_old_navbar"))
    }

    @Test
    fun selectionTitlesTipOpensTimelineAndAlbums() {
        assertEquals(Screen.SettingsTimelineAlbumsScreen(), deepLinkRoute("settings_selection_titles"))
    }

    @Test
    fun noTipOpensTheRemovedNavigationPage() {
        val stale = HelpRepository.getAllTips()
            .filter { it.deepLink?.toScreenRoute() == Screen.SettingsNavigationScreen() }
            .map { it.id }
        assertEquals(emptyList<String>(), stale)
    }

    @Test
    fun settingsHubHasNoNavigationEntry() {
        assertFalse(SettingsRoute.entries.any { it.name == "NAVIGATION" })
    }
}
