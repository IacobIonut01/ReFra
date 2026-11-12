/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.ui.theme.GalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.dot.gallery.feature.library.ui.components.LibrarySectionsEditCard
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.categories
import com.dot.gallery.generated.resources.cloud_people
import com.dot.gallery.generated.resources.library_sections_title
import com.dot.gallery.generated.resources.locations
import com.dot.gallery.core.util.getString
import org.jetbrains.compose.resources.StringResource


@MediumTest
@RunWith(AndroidJUnit4::class)
class LibrarySectionsEditCardTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(res: StringResource): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    @Test
    fun onlyTheSwitchesWithCallbacksRenderAndToggle() {
        var allShortcuts: Boolean? = null
        var pinned: Boolean? = null
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                LibrarySectionsEditCard(
                    showAllShortcuts = false,
                    onShowAllShortcutsChange = { allShortcuts = it },
                    showPinned = true,
                    onPinnedChange = { pinned = it },
                )
            }
        }

        rule.onNodeWithText(string(Res.string.library_sections_title)).assertIsDisplayed()
        rule.onAllNodes(isToggleable()).assertCountEquals(2)
        // Row order is fixed: all shortcuts, then pinned albums.
        rule.onAllNodes(isToggleable())[0].assertIsDisplayed().performClick()
        assertEquals(true, allShortcuts)
        assertNull(pinned)
    }

    @Test
    fun locationsPeopleAndCategoriesAreTilesNotSwitches() {
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                LibrarySectionsEditCard(
                    onShowAllShortcutsChange = {},
                    onPinnedChange = {},
                    onGroupAlbumsIntoSectionsChange = {},
                    onMediaTypesChange = {},
                )
            }
        }

        // They are shown, hidden and resized in the shortcut grid now.
        rule.onNodeWithText(string(Res.string.locations)).assertDoesNotExist()
        rule.onNodeWithText(string(Res.string.cloud_people)).assertDoesNotExist()
        rule.onNodeWithText(string(Res.string.categories)).assertDoesNotExist()
        rule.onAllNodes(isToggleable()).assertCountEquals(4)
    }
}
