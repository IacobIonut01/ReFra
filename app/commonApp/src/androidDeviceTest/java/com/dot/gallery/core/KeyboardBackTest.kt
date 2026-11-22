/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dot.gallery.core.ui.util.backClosesKeyboard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue 1451: one back gesture on the search field must close the keyboard and run the field's
 * back action. Without [backClosesKeyboard] the keyboard takes the first gesture on its own,
 * which the control test pins so this test keeps proving something on newer keyboards.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class KeyboardBackTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var imeVisible by mutableStateOf(false)
    private var fieldBacks by mutableStateOf(0)
    private var screenBacks by mutableStateOf(0)

    @Before
    fun edgeToEdge() {
        // Compose only reports keyboard insets once the window draws behind the system bars.
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Field(modifier: Modifier) {
        var text by remember { mutableStateOf("") }
        val visible = WindowInsets.isImeVisible
        SideEffect { imeVisible = visible }
        BackHandler { screenBacks++ }
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = modifier.safeDrawingPadding().fillMaxWidth().testTag(FIELD),
        )
    }

    private fun openKeyboard() {
        rule.onNodeWithTag(FIELD).performClick()
        rule.waitUntil(TIMEOUT) { imeVisible }
    }

    @Test
    fun oneBackClosesTheKeyboardAndRunsTheFieldBack() {
        rule.setContent { Field(Modifier.backClosesKeyboard { fieldBacks++ }) }
        openKeyboard()

        device.pressBack()

        rule.waitUntil(TIMEOUT) { fieldBacks == 1 && !imeVisible }
        rule.waitForIdle()
        assertEquals(1, fieldBacks)
        assertEquals("the screen's own back must not run as well", 0, screenBacks)
    }

    @Test
    fun withoutItTheKeyboardTakesTheFirstBack() {
        rule.setContent { Field(Modifier) }
        openKeyboard()

        device.pressBack()

        rule.waitUntil(TIMEOUT) { !imeVisible }
        rule.waitForIdle()
        assertEquals(0, screenBacks)
    }

    @Test
    fun closedKeyboardLeavesBackToTheScreen() {
        rule.setContent { Field(Modifier.backClosesKeyboard { fieldBacks++ }) }

        device.pressBack()

        rule.waitUntil(TIMEOUT) { screenBacks == 1 }
        assertEquals(0, fieldBacks)
    }

    private companion object {
        const val FIELD = "KeyboardBackTest.Field"
        const val TIMEOUT = 10_000L
    }
}
