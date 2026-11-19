/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.feature.viewer.insight.TextSelectionBar
import com.dot.gallery.feature.viewer.insight.TextSelectionControls
import com.dot.gallery.ui.theme.GalleryTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the text selection controls: the count and Select all sit above the
 * selection bar, Select all is disabled once every line is selected, and the bar no longer carries it.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class TextSelectionControlsDeviceTest {

    @get:Rule
    val rule = createComposeRule()

    private val closes = AtomicInteger(0)
    private val selectAlls = AtomicInteger(0)

    private fun setControls(count: Int, allSelected: Boolean) {
        closes.set(0)
        selectAlls.set(0)
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                TextSelectionControls(
                    count = count,
                    allSelected = allSelected,
                    onClose = { closes.incrementAndGet() },
                    onSelectAll = { selectAlls.incrementAndGet() },
                )
            }
        }
    }

    @Test
    fun countShowsSelectedLineCount() {
        setControls(count = 3, allSelected = true)

        rule.onAllNodesWithText("3 lines selected").assertCountEquals(1)
    }

    @Test
    fun closeFiresOnClose() {
        setControls(count = 2, allSelected = false)

        rule.onNodeWithContentDescription("Close selection").performClick()
        rule.waitForIdle()

        assertEquals(1, closes.get())
        assertEquals(0, selectAlls.get())
    }

    @Test
    fun selectAllFiresWhenNotEverythingIsSelected() {
        setControls(count = 2, allSelected = false)

        rule.onNodeWithText("Select all").performClick()
        rule.waitForIdle()

        assertEquals(1, selectAlls.get())
    }

    @Test
    fun selectAllIsDisabledWhenEverythingIsSelected() {
        setControls(count = 3, allSelected = true)

        rule.onNodeWithText("Select all").assertIsNotEnabled()
        rule.waitForIdle()

        assertEquals(0, selectAlls.get())
    }

    @Test
    fun selectionBarHasCopyAndShareButNoSelectAll() {
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                TextSelectionBar(
                    showSearch = true,
                    onCopy = {},
                    onShare = {},
                    onSearch = {},
                )
            }
        }

        rule.onAllNodesWithText("Copy").assertCountEquals(1)
        rule.onAllNodesWithText("Share").assertCountEquals(1)
        rule.onAllNodesWithText("Select all").assertCountEquals(0)
            }
}
