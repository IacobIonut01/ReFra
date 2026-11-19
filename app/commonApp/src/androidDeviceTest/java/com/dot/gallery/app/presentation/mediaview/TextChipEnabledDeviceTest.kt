/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.feature.viewer.insight.TextChip
import com.dot.gallery.feature.viewer.insight.TextChipMode
import com.dot.gallery.ui.theme.GalleryTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the Text chip staying tappable while the info sheet is open: a chip
 * rendered with enabled = false must ignore its click.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class TextChipEnabledDeviceTest {

    @get:Rule
    val rule = createComposeRule()

    private val clicks = AtomicInteger(0)

    private fun setChip(enabled: Boolean) {
        clicks.set(0)
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                TextChip(
                    mode = TextChipMode.READ,
                    lineCount = 0,
                    onClick = { clicks.incrementAndGet() },
                    enabled = enabled,
                )
            }
        }
    }

    @Test
    fun disabledChipIgnoresClick() {
        setChip(enabled = false)

        rule.onNodeWithContentDescription("Read text").performClick()
        rule.waitForIdle()

        assertEquals(0, clicks.get())
    }

    @Test
    fun enabledChipFiresClick() {
        setChip(enabled = true)

        rule.onNodeWithContentDescription("Read text").performClick()
        rule.waitForIdle()

        assertEquals(1, clicks.get())
    }
}
