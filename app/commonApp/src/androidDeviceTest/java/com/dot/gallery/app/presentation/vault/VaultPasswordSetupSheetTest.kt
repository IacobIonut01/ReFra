/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.vault

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.feature.vault.ui.components.VaultPasswordSetupSheet
import com.dot.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The custom-password setup sheet must respect the system-bar insets and scroll
 * its content, so titles, the pattern pad and the Back button stay reachable at
 * any font/display size.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class VaultPasswordSetupSheetTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var sheetState: AppBottomSheetState
    private lateinit var scope: CoroutineScope

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
                GalleryTheme(ignoreUserPreference = true) {
                    scope = rememberCoroutineScope()
                    sheetState = rememberAppBottomSheetState()
                    VaultPasswordSetupSheet(
                        state = sheetState,
                        onSecretSet = { _, _ -> },
                    )
                }
            }
        }
        rule.runOnUiThread { scope.launch { sheetState.show() } }
        rule.onNodeWithText("Choose lock type").assertExists()
    }

    @Test
    fun patternTitleStaysBelowStatusBar() {
        show()
        rule.onNodeWithText("Pattern").performClick()
        rule.onNodeWithText("Draw unlock pattern").assertExists()
        rule.waitForIdle()

        val statusTop = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)!!
            .getInsets(
                WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            .top
        assumeTrue(statusTop > 0)
        val titleTop = rule.onNodeWithText("Draw unlock pattern")
            .fetchSemanticsNode().boundsInWindow.top
        assertTrue(
            "title top $titleTop should be at or below status bar top $statusTop",
            titleTop >= statusTop
        )
    }

    @Test
    fun patternStepContentIsScrollable() {
        show()
        rule.onNodeWithText("Pattern").performClick()
        rule.onNodeWithText("Draw unlock pattern").assertExists()
        rule.onNode(
            hasScrollAction() and hasAnyDescendant(hasText("Draw unlock pattern"))
        ).assertExists()
    }

    @Test
    fun drawnPatternReachesConfirmStep() {
        show()
        rule.onNodeWithText("Pattern").performClick()
        rule.onNodeWithText("Draw unlock pattern").assertExists()

        // Dots 0 → 3 → 6 → 7: down on dot 0, interpolated moves through the
        // other centres, up. Proves the scroll container doesn't steal the drag.
        rule.onNodeWithTag("vault-pattern-pad").performTouchInput {
            fun dot(index: Int) = Offset(
                x = ((index % 3) + 0.5f) / 3f * width,
                y = ((index / 3) + 0.5f) / 3f * height,
            )
            var from = dot(0)
            down(from)
            for (target in listOf(3, 6, 7)) {
                val to = dot(target)
                repeat(12) { step ->
                    val t = (step + 1) / 12f
                    moveTo(
                        Offset(
                            x = from.x + (to.x - from.x) * t,
                            y = from.y + (to.y - from.y) * t
                        ),
                        delayMillis = 16L
                    )
                }
                from = to
            }
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithText("Confirm pattern").assertExists()
    }
}
