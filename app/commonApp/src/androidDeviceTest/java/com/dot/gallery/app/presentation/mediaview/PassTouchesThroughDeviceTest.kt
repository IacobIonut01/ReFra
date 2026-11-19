/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the hidden info sheet: an overlay kept composed at alpha 0 must not take
 * taps and long presses from the content beneath it once [passTouchesThrough] is applied.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class PassTouchesThroughDeviceTest {

    @get:Rule
    val rule = createComposeRule()

    private val taps = AtomicInteger(0)
    private val longPresses = AtomicInteger(0)

    private fun setScene(passThrough: Boolean) {
        taps.set(0)
        longPresses.set(0)
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { taps.incrementAndGet() },
                                onLongPress = { longPresses.incrementAndGet() },
                            )
                        }
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { passTouchesThrough(passThrough) }
                        .pointerInput(Unit) { detectTapGestures { } }
                )
            }
        }
    }

    @Test
    fun passThroughLayerLetsTapReachContent() {
        setScene(passThrough = true)

        rule.onRoot().performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(1, taps.get())
    }

    @Test
    fun passThroughLayerLetsLongPressReachContent() {
        setScene(passThrough = true)

        rule.onRoot().performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(1, longPresses.get())
    }

    @Test
    fun clippedOverlayWithoutPassThroughSwallowsTapAndLongPress() {
        setScene(passThrough = false)

        rule.onRoot().performTouchInput { click() }
        rule.onRoot().performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(0, taps.get())
        assertEquals(0, longPresses.get())
    }
}
