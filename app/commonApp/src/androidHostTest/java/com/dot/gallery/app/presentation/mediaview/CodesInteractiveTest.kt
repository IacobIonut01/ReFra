/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import com.dot.gallery.feature.viewer.components.media.codesInteractive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for issue #1416: with the viewer chrome hidden a tap on a found
 * code must reach the normal tap path (chrome toggle), so codes are neither hit-tested
 * nor drawn until the chrome is back.
 */
class CodesInteractiveTest {

    @Test
    fun codesInteractiveOnlyWithCodesAndVisibleChrome() {
        assertTrue(
            codesInteractive(
                hasCodes = true,
                chromeVisible = true,
                cutoutActive = false,
                cutoutProcessing = false,
            )
        )
    }

    @Test
    fun hiddenChromeDisablesCodesEvenWithCodesFound() {
        assertFalse(
            codesInteractive(
                hasCodes = true,
                chromeVisible = false,
                cutoutActive = false,
                cutoutProcessing = false,
            )
        )
    }

    @Test
    fun cutoutSessionDisablesCodes() {
        assertFalse(
            codesInteractive(
                hasCodes = true,
                chromeVisible = true,
                cutoutActive = true,
                cutoutProcessing = false,
            )
        )
        assertFalse(
            codesInteractive(
                hasCodes = true,
                chromeVisible = true,
                cutoutActive = false,
                cutoutProcessing = true,
            )
        )
    }

    @Test
    fun noCodesMeansNothingToInteractWith() {
        assertFalse(
            codesInteractive(
                hasCodes = false,
                chromeVisible = true,
                cutoutActive = false,
                cutoutProcessing = false,
            )
        )
    }
}
