/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.startup

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dot.gallery.core.Settings
import com.dot.gallery.core.presentation.util.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.core.presentation.components.util.permissionGranted
import com.dot.gallery.app.presentation.albums.AlbumsScreen
import com.dot.gallery.app.presentation.library.LibraryScreen
import com.dot.gallery.app.presentation.setup.SetupScreen
import com.dot.gallery.app.presentation.timeline.TimelineScreen


class StartupDestinationTest {

    private val lastScreen = stringPreferencesKey("last_screen")
    private val setupCompleted = intPreferencesKey("setup_completed_version")
    private val secureMode = booleanPreferencesKey("secure_mode")

    private fun prefsWithSetupDone(vararg pairs: Preferences.Pair<*>) =
        preferencesOf(setupCompleted to Settings.Misc.CURRENT_SETUP_VERSION, *pairs)

    @Test
    fun `stored timeline route resolves to timeline`() {
        val prefs = prefsWithSetupDone(lastScreen to Screen.TimelineScreen())
        assertEquals(
            Screen.TimelineScreen(),
            Settings.Misc.startupDestination(prefs, permissionGranted = true)
        )
    }

    @Test
    fun `stored albums route resolves to library since they merged`() {
        val prefs = prefsWithSetupDone(lastScreen to Screen.AlbumsScreen())
        assertEquals(
            Screen.LibraryScreen(),
            Settings.Misc.startupDestination(prefs, permissionGranted = true)
        )
    }

    @Test
    fun `stored library route resolves to library`() {
        val prefs = prefsWithSetupDone(lastScreen to Screen.LibraryScreen())
        assertEquals(
            Screen.LibraryScreen(),
            Settings.Misc.startupDestination(prefs, permissionGranted = true)
        )
    }

    @Test
    fun `invalid stored route falls back to timeline`() {
        val prefs = prefsWithSetupDone(lastScreen to "media_screen")
        assertEquals(
            Screen.TimelineScreen(),
            Settings.Misc.startupDestination(prefs, permissionGranted = true)
        )
    }

    @Test
    fun `missing stored route falls back to timeline`() {
        val prefs = prefsWithSetupDone()
        assertEquals(
            Screen.TimelineScreen(),
            Settings.Misc.startupDestination(prefs, permissionGranted = true)
        )
    }

    @Test
    fun `setup incomplete forces setup screen`() {
        val prefs = preferencesOf(lastScreen to Screen.LibraryScreen())
        assertEquals(
            Screen.SetupScreen(),
            Settings.Misc.startupDestination(prefs, permissionGranted = true)
        )
        assertTrue(Settings.Misc.isSetupNeeded(prefs))
    }

    @Test
    fun `outdated setup version forces setup screen`() {
        val prefs = preferencesOf(
            setupCompleted to 0,
            lastScreen to Screen.AlbumsScreen()
        )
        assertEquals(
            Screen.SetupScreen(),
            Settings.Misc.startupDestination(prefs, permissionGranted = true)
        )
    }

    @Test
    fun `denied permission forces setup screen regardless of stored route`() {
        val prefs = prefsWithSetupDone(lastScreen to Screen.AlbumsScreen())
        assertEquals(
            Screen.SetupScreen(),
            Settings.Misc.startupDestination(prefs, permissionGranted = false)
        )
    }

    @Test
    fun `completed setup is not needed`() {
        val prefs = prefsWithSetupDone()
        assertFalse(Settings.Misc.isSetupNeeded(prefs))
    }

    @Test
    fun `secure mode reads the same stored flag`() {
        assertTrue(Settings.Misc.secureMode(preferencesOf(secureMode to true)))
        assertFalse(Settings.Misc.secureMode(preferencesOf()))
    }
}
