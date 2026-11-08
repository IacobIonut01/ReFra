/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.setup

import android.os.Build
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.setup.ui.LocalSetupSharedTransitionScope
import com.dot.gallery.feature.setup.ui.SetupWizardScaffold
import com.dot.gallery.ui.theme.GalleryTheme
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: [SetupWizardScaffold] provided a blur style that ignored the `allowBlur` setting, so
 * turning blur off still blurred frosted surfaces on the setup and cloud setup pages. The style is
 * opaque, so this compares what a child of the scaffold draws with the setting on and off.
 *
 * The scaffold's animated background changes the pixels by itself and would make this pass without
 * the fix. Providing [LocalSetupSharedTransitionScope] makes the scaffold leave it out, as it does
 * inside the wizard, and the control assertion proves the output is stable without a change.
 */
@MediumTest
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class SetupWizardBlurSettingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun blurSettingChangesWhatPagesDraw() {
        assumeTrue("Haze only blurs on API 31 and newer", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val store = MapSettingsStore()
        runBlocking { store.put(SettingsKeys.allowBlur, true) }
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides store) {
                GalleryTheme(ignoreUserPreference = true) {
                    SharedTransitionLayout {
                        CompositionLocalProvider(LocalSetupSharedTransitionScope provides this) {
                            SetupWizardScaffold(showBack = false) {
                                val hazeState = LocalHazeState.current
                                val blurStyle = LocalHazeBlurStyle.current
                                Box(Modifier.fillMaxWidth().height(200.dp).testTag(TAG)) {
                                    Row(Modifier.fillMaxSize().hazeSource(hazeState)) {
                                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black))
                                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                                    }
                                    Box(Modifier.fillMaxSize().hazeEffectScaled(hazeState, blurStyle))
                                }
                            }
                        }
                    }
                }
            }
        }

        val blurred = middleRow()
        assertEquals("Output must be stable while the setting is unchanged", blurred, middleRow())
        runBlocking { store.put(SettingsKeys.allowBlur, false) }
        val notBlurred = middleRow()

        assertNotEquals(blurred, notBlurred)
    }

    private fun middleRow(): List<Color> {
        rule.waitForIdle()
        val pixels = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        return List(pixels.width) { x -> pixels[x, pixels.height / 2] }
    }

    private companion object {
        const val TAG = "setup-haze"
    }
}
