/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.components.PreviewHost
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GalleryPaneLayoutTest {
    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    @Test
    fun splittingAndRejoiningPreservesGalleryStateAndReservesTheGutter() {
        var split by mutableStateOf(false)
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(960.dp, 640.dp)),
            ) {
                PreviewHost {
                    GalleryPaneLayout(
                        split = split,
                        masterContent = {
                            var opened by rememberSaveable { mutableIntStateOf(0) }
                            Button(onClick = { opened++ }) { Text("Opened $opened") }
                        },
                        detailContent = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(GalleryMasterPaneTag).assertWidthIsEqualTo(960.dp)
        rule.onNodeWithText("Opened 0").assertIsDisplayed().performClick()
        rule.runOnIdle { split = true }

        rule.onNodeWithText("Opened 1").assertIsDisplayed()
        rule.onNodeWithTag(GalleryMasterPaneTag).assertWidthIsEqualTo(432.dp)
        rule.onNodeWithTag(GalleryDetailPaneTag).assertWidthIsEqualTo(516.dp)
        val master = rule.onNodeWithTag(GalleryMasterPaneTag).getUnclippedBoundsInRoot()
        val detail = rule.onNodeWithTag(GalleryDetailPaneTag).getUnclippedBoundsInRoot()
        // Forced low densities accumulate rounding from both independently measured pane widths.
        (detail.left - master.right).assertIsEqualTo(
            12.dp, "gallery-to-viewer gutter", tolerance = 1.dp,
        )

        rule.runOnIdle { split = false }
        rule.onNodeWithTag(GalleryMasterPaneTag).assertWidthIsEqualTo(960.dp)
        rule.onNodeWithText("Opened 1").assertIsDisplayed()
    }
}
