/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class MiniHomeScreenTest {
    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    /**
     * The home bar has two destinations (Timeline, Library) plus a separate search button;
     * the mock must not draw a third tab the app no longer has.
     */
    @Test
    fun floatingNavShowsTwoTabsAndOneSearch() {
        rule.setContent {
            PreviewHost {
                MiniHomeScreen(modifier = Modifier.width(156.dp))
            }
        }

        rule.onAllNodesWithTag(MiniHomeNavTabTag, useUnmergedTree = true)
            .assertCountEquals(2)
        rule.onAllNodesWithTag(MiniHomeSearchTag, useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
