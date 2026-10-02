/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.library.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.R
import com.dot.gallery.ui.theme.GalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class LibrarySectionsEditCardTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun allSectionsRenderWithWorkingToggles() {
        var locations: Boolean? = null
        var people: Boolean? = null
        var categories: Boolean? = null
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                LibrarySectionsEditCard(
                    showLocations = true,
                    onLocationsChange = { locations = it },
                    hasLocations = true,
                    peopleAvailable = true,
                    showPeople = true,
                    onPeopleChange = { people = it },
                    categoriesAvailable = true,
                    showCategories = false,
                    onCategoriesChange = { categories = it }
                )
            }
        }

        rule.onNodeWithText(string(R.string.library_sections_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.locations)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.cloud_people)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.categories)).assertIsDisplayed()
        // Row order is fixed: locations, people, categories. Flipping the third
        // switch emits onChange(true) — categories starts unchecked.
        rule.onAllNodes(isToggleable())[2]
            .assertIsDisplayed().performClick()
        assertEquals(true, categories)
        assertNull(locations)
        assertNull(people)
    }

    @Test
    fun unavailableSectionsShowAHintAndDisableTheSwitch() {
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                LibrarySectionsEditCard(
                    showLocations = true,
                    onLocationsChange = {},
                    hasLocations = false,
                    peopleAvailable = false,
                    showPeople = false,
                    onPeopleChange = {},
                    categoriesAvailable = false,
                    showCategories = true,
                    onCategoriesChange = {}
                )
            }
        }

        // Disabled rows explain themselves instead of silently refusing (#1262 —
        // the section can be hidden while people are still reachable elsewhere).
        rule.onNodeWithText(string(R.string.library_section_people_unavailable))
            .assertIsDisplayed()
        rule.onNodeWithText(string(R.string.library_section_categories_unavailable))
            .assertIsDisplayed()
        // Disabled rows disable their switch too.
        rule.onAllNodes(isToggleable())[1].assertIsNotEnabled() // people
        rule.onAllNodes(isToggleable())[2].assertIsNotEnabled() // categories
        // An empty locations section explains itself but stays toggleable — the
        // switch gates data collection, not only display.
        rule.onNodeWithText(string(R.string.library_section_locations_empty))
            .assertIsDisplayed()
        rule.onAllNodes(isToggleable())[0].assertIsEnabled()
    }
}
