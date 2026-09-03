/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.presentation.timeline.components.TIMELINE_FILTER_CHIP_VISUAL_TAG
import com.dot.gallery.feature_node.presentation.timeline.components.TimelineFilterChip
import org.junit.Rule
import org.junit.Test

class TimelineFilterChipAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chipExposesSelectionAndMinimumTouchTarget() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(false) }
            MaterialTheme {
                TimelineFilterChip(
                    label = "Favorites",
                    selected = selected,
                    onClick = { selected = !selected },
                )
            }
        }

        composeRule.onNodeWithTag(
            testTag = TIMELINE_FILTER_CHIP_VISUAL_TAG,
            useUnmergedTree = true,
        ).assertHeightIsEqualTo(32.dp)

        composeRule.onNodeWithText("Favorites")
            .assertHasClickAction()
            .assertIsNotSelected()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
            .assertIsSelected()
    }
}
