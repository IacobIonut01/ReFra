/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.components.PreviewHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The selection bar used to cover the last row of the grids (issue #1432): they reserved a
 * fixed 128 dp while the bar is taller. With the bar's measured height reserved, the last row
 * scrolls clear of it.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class SelectionBarClearanceLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lastRowScrollsClearOfTheSelectionBar() {
        var barShown by mutableStateOf(true)
        val clearance = SelectionBarClearance()
        rule.setContent {
            CompositionLocalProvider(LocalSelectionBarClearance provides clearance) {
                PreviewHost {
                    Box(Modifier.size(360.dp, 640.dp)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("grid"),
                            contentPadding = PaddingValues(bottom = selectionAwareBottomPadding(BasePadding)),
                        ) {
                            items(30) { index ->
                                Box(Modifier.fillMaxWidth().height(100.dp).testTag("row$index"))
                            }
                        }
                        if (barShown) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .reportSelectionBarHeight()
                                    .fillMaxWidth()
                                    .height(BarHeight)
                                    .testTag("bar"),
                            )
                        }
                    }
                }
            }
        }

        rule.onNodeWithTag("grid").performScrollToIndex(29)
        rule.waitForIdle()
        val lastRow = rule.onNodeWithTag("row29").fetchSemanticsNode().boundsInRoot
        val bar = rule.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val gap = with(rule.density) { SelectionBarGridGap.toPx() }
        assertTrue("last row ${lastRow.bottom} overlaps bar ${bar.top}", lastRow.bottom <= bar.top - gap + 1f)

        rule.runOnIdle { barShown = false }
        rule.runOnIdle { assertEquals(0.dp, clearance.height) }
    }

    private companion object {
        val BasePadding = 128.dp
        val BarHeight = 220.dp
    }
}
