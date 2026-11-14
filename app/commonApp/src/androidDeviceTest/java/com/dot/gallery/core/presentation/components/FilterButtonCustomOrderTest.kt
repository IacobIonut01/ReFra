/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.material3.Text
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
import com.dot.gallery.core.ui.components.PreviewHost
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.util.getString
import com.dot.gallery.domain.model.AlbumViewType
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.core.ui.generated.resources.Res as ResCoreUi
import com.dot.gallery.core.ui.generated.resources.filter_type_date
import com.dot.gallery.core.ui.generated.resources.filter_type_name
import com.dot.gallery.core.ui.generated.resources.sort_clears_custom_order
import com.dot.gallery.core.ui.generated.resources.sort_custom
import org.jetbrains.compose.resources.StringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * While a custom album order is in effect the sort chip reads "Custom", the direction
 * arrow hides, and the menu turns each sort into a "clears custom order" action
 * (issue #1428).
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class FilterButtonCustomOrderTest {

    @get:Rule
    val rule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun label(res: StringResource) = context.getString(res)

    private fun showFilterButton(customOrderActive: Boolean, onClearCustomOrder: () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
                PreviewHost {
                    FilterButton(
                        filterOptions = arrayOf(
                            FilterOption(
                                titleRes = ResCoreUi.string.filter_type_date,
                                filterKind = FilterKind.DATE,
                            ),
                            FilterOption(
                                titleRes = ResCoreUi.string.filter_type_name,
                                filterKind = FilterKind.NAME,
                            ),
                        ),
                        viewType = AlbumViewType.GRID,
                        onViewTypeChange = {},
                        title = { Text("Albums") },
                        customOrderActive = customOrderActive,
                        onClearCustomOrder = onClearCustomOrder,
                    )
                }
            }
        }
    }

    @Test
    fun customOrderHidesDirectionArrowAndMenuPicksClearIt() {
        var clearedCount = 0
        showFilterButton(customOrderActive = true) { clearedCount++ }

        val customLabel = label(ResCoreUi.string.sort_custom)
        val clearsLabel = label(ResCoreUi.string.sort_clears_custom_order)
        val nameLabel = label(ResCoreUi.string.filter_type_name)

        rule.onNodeWithText(customLabel).assertIsDisplayed()
        rule.onAllNodesWithTag(SORT_DIRECTION_TAG).assertCountEquals(0)

        rule.onNodeWithText(customLabel).performClick()
        rule.waitUntil("sort menu open", 5_000) {
            rule.onAllNodesWithText(clearsLabel).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(clearsLabel).assertCountEquals(2)

        rule.onNodeWithText(nameLabel).performClick()
        rule.waitForIdle()
        assertEquals(1, clearedCount)
        rule.onAllNodesWithText(clearsLabel).assertCountEquals(0)
    }

    @Test
    fun withoutCustomOrderDirectionArrowIsShownAndCustomIsAbsent() {
        showFilterButton(customOrderActive = false) {}

        rule.onNodeWithTag(SORT_DIRECTION_TAG).assertIsDisplayed()
        rule.onAllNodesWithText(label(ResCoreUi.string.sort_custom)).assertCountEquals(0)
        rule.onNodeWithText(label(ResCoreUi.string.filter_type_date)).assertIsDisplayed()
    }
}
