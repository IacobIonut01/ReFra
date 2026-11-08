/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.BuildConfig
import com.dot.gallery.app.presentation.util.PreviewHost
import com.dot.gallery.core.presentation.components.OptionAttentionTag
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.util.getString
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.favorites
import com.dot.gallery.generated.resources.help_whats_new
import com.dot.gallery.core.util.SdkCompat
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the header's overflow sheet: the unread-release entry and the
 * dot that points at it travel together, and the entry is hidden once the release is read.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class TimelineNavActionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val whatsNewLabel: String
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(Res.string.help_whats_new)

    private fun showMenu(lastSeenVersion: String, showFavoritesPreference: Boolean = true) {
        val store = MapSettingsStore().apply {
            seed(
                SettingsKeys.lastSeenVersion to lastSeenVersion,
                SettingsKeys.showSearchBarFavButton to showFavoritesPreference,
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides store) {
                PreviewHost { TimelineNavActions() }
            }
        }
    }

    private fun openMenuSheet() {
        rule.onNodeWithTag(TimelineMenuTag).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(TimelineMenuSheetTag).fetchSemanticsNodes().isNotEmpty()
        }
        // Let the sheet finish sliding in before asserting that its rows are composed.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    @Test
    fun whatsNewLabelKeepsItsApostrophe() {
        // Compose resources only unescape \n, \t and \uXXXX, so an escaped apostrophe
        // would otherwise reach the UI as "What\'s new".
        assertEquals("What's new", whatsNewLabel)
    }

    @Test
    fun unreadReleaseIsListedWithAnAttentionDot() {
        // The library's VERSION_NAME is filled in by the app process, so seed a value that
        // can never match it instead of assuming an app build is running.
        showMenu(lastSeenVersion = "0.0.0-unseen")

        openMenuSheet()

        rule.onNodeWithText(whatsNewLabel).assertExists()
        rule.onAllNodesWithTag(OptionAttentionTag, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun seenReleaseHidesTheEntryAndItsDot() {
        showMenu(lastSeenVersion = BuildConfig.VERSION_NAME)

        openMenuSheet()

        rule.onNodeWithTag(TimelineMenuSheetTag).assertIsDisplayed()
        rule.onAllNodesWithText(whatsNewLabel).assertCountEquals(0)
        rule.onAllNodesWithTag(OptionAttentionTag, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun favoritesStaysListedAfterItsOldSettingWasTurnedOff() {
        // The Favourites shortcut setting left with the old search bar, so a stored "off"
        // must not hide the menu entry with no way left to bring it back.
        assumeTrue(SdkCompat.supportsFavorites)
        showMenu(lastSeenVersion = BuildConfig.VERSION_NAME, showFavoritesPreference = false)

        openMenuSheet()

        val favoritesLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(Res.string.favorites)
        rule.onNodeWithText(favoritesLabel).assertExists()
    }
}
