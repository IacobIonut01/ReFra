/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.app.presentation.util.PreviewHost
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.core.presentation.components.HomeShortcut
import com.dot.gallery.core.presentation.components.HomeShortcutSupport
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.settings.ui.TopBarPreviewTag
import com.dot.gallery.feature.settings.ui.TopBarSettingsDetail
import com.dot.gallery.feature.settings.ui.topBarShortcutTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Top bar settings page: its preview follows the switches and the Browsing / Selecting
 * chips, and every switch writes the setting the home top bar reads. The settings module keeps
 * its string resources internal, so rows are matched by their English labels.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class TopBarSettingsDetailTest {

    @get:Rule
    val rule = createComposeRule()

    private val store = MapSettingsStore()

    private fun show(support: HomeShortcutSupport = HomeShortcutSupport()) {
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides store) {
                PreviewHost { TopBarSettingsDetail(support = support) }
            }
        }
    }

    private fun previewShows(buttons: String) {
        rule.onNodeWithTag(TopBarPreviewTag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, buttons))
    }

    private fun stored(key: String) = runBlocking { store.snapshot()[key] }

    @Test
    fun previewListsTheDefaultButtons() {
        show()

        previewShows("Select, Favorites, Sort and filter")
    }

    @Test
    fun selectingPreviewTurnsSelectIntoCloseAndFavoritesIntoSearch() {
        show()

        rule.onNodeWithText("Selecting").performClick()

        previewShows("Close selection, Search, Sort and filter")
    }

    @Test
    fun turningOffSelectUpdatesThePreviewAndTheSetting() {
        show()

        rule.onNodeWithText("Select").performScrollTo().performClick()

        previewShows("Favorites, Sort and filter")
        assertEquals("false", stored("show_home_select_button"))
    }

    @Test
    fun hideOnScrollWritesTheExistingAutoHideSetting() {
        show()

        rule.onNodeWithText("Hide on scroll").performScrollTo().performClick()

        assertEquals("false", stored("auto_hide_searchbar"))
    }

    @Test
    fun favoritesIsNotOfferedWithoutPlatformSupport() {
        show(HomeShortcutSupport(favorites = false))

        rule.onAllNodesWithText("Favorites").assertCountEquals(0)
        previewShows("Select, Sort and filter")
    }

    @Test
    fun choosingAShortcutUpdatesThePreviewAndTheSetting() {
        show()

        rule.onNodeWithTag(topBarShortcutTag(HomeShortcut.TRASH)).performScrollTo().performClick()

        previewShows("Select, Trash, Sort and filter")
        assertEquals("trash", stored("home_shortcut"))

        rule.onNodeWithTag(topBarShortcutTag(HomeShortcut.NONE)).performScrollTo().performClick()

        previewShows("Select, Sort and filter")
        assertEquals("none", stored("home_shortcut"))
    }

    @Test
    fun turnedOffFavoritesButtonMigratesToNoShortcut() {
        store.seed(SettingsKeys.showHomeFavoritesButton to false)
        show()

        previewShows("Select, Sort and filter")
    }

    @Test
    fun unsupportedShortcutsAreNotOffered() {
        show(HomeShortcutSupport(trash = false))

        rule.onAllNodesWithTag(topBarShortcutTag(HomeShortcut.TRASH)).assertCountEquals(0)
        rule.onNodeWithTag(topBarShortcutTag(HomeShortcut.VAULT)).assertExists()
    }
}
