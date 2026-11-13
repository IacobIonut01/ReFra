/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.app.presentation.util.PreviewHost
import com.dot.gallery.feature.settings.ui.ClassicNavPreviewSearchTag
import com.dot.gallery.feature.settings.ui.ClassicNavPreviewTabTag
import com.dot.gallery.feature.settings.ui.ClassicNavigationPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "Classic navigation style" preview on the Appearance page: on, it is the labelled
 * Material bar (Timeline, Search, Library — the app's real bar puts Search in the middle);
 * off, the default floating dock with two tabs and a separate search button. Rows are
 * matched by their English labels.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ClassicNavigationPreviewTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(isChecked: Boolean) {
        rule.setContent {
            PreviewHost { ClassicNavigationPreview(isChecked = isChecked) }
        }
    }

    @Test
    fun classicBarShowsTimelineSearchLibraryInOrder() {
        show(isChecked = true)

        rule.onNodeWithText("Timeline").assertIsDisplayed()
        rule.onNodeWithText("Search").assertIsDisplayed()
        rule.onNodeWithText("Library").assertIsDisplayed()
        rule.onAllNodesWithText("Albums").assertCountEquals(0)

        val navMatcher = SemanticsMatcher("classic nav item") { node ->
            val tag = node.config.getOrElse(SemanticsProperties.TestTag) { "" }
            tag == ClassicNavPreviewTabTag || tag == ClassicNavPreviewSearchTag
        }
        val nodes = rule.onAllNodes(navMatcher, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(3, nodes.size)
        assertEquals(
            listOf(ClassicNavPreviewTabTag, ClassicNavPreviewSearchTag, ClassicNavPreviewTabTag),
            nodes.map { it.config[SemanticsProperties.TestTag] },
        )
    }

    @Test
    fun dockShowsTwoTabsAndOneSearch() {
        show(isChecked = false)

        rule.onAllNodesWithTag(ClassicNavPreviewTabTag, useUnmergedTree = true)
            .assertCountEquals(2)
        rule.onAllNodesWithTag(ClassicNavPreviewSearchTag, useUnmergedTree = true)
            .assertCountEquals(1)
        rule.onAllNodesWithText("Albums").assertCountEquals(0)
    }
}
