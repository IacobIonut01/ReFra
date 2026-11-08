/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.edit

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.ui.theme.GalleryTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: [EditActivity] built its blur style without `blurEnabled(allowBlur)`, so turning
 * blur off in settings still blurred the editor's frosted surfaces. The style is opaque, so this
 * compares what it draws over a hard black and white edge with the setting on and off.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class EditHazeBlurStyleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun blurSettingChangesWhatTheEditorDraws() {
        assumeTrue("Haze only blurs on API 31 and newer", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        var allowBlur by mutableStateOf(true)
        val hazeState = HazeState()
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                Box(Modifier.size(200.dp).testTag(TAG)) {
                    Row(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black))
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .hazeEffectScaled(hazeState, editHazeBlurStyle(allowBlur))
                    )
                }
            }
        }

        val blurred = middleRow()
        allowBlur = false
        rule.waitForIdle()
        val notBlurred = middleRow()

        assertNotEquals(blurred, notBlurred)
    }

    private fun middleRow(): List<Color> {
        val pixels = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        return List(pixels.width) { x -> pixels[x, pixels.height / 2] }
    }

    private companion object {
        const val TAG = "edit-haze"
    }
}
