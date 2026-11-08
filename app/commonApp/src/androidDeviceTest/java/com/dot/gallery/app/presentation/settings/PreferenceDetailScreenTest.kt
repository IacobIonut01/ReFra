/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.app.presentation.util.PreviewHost
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The preference detail template: its Off/On preview tiles are a control that agrees with the
 * main switch, a disabled page ignores both, and visual choosers swap the radio list for tiles.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class PreferenceDetailScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tappingTheOnTileTurnsTheSettingOnAndSelectsIt() {
        var checked by mutableStateOf(false)
        rule.setContent {
            PreviewHost {
                SwitchPreferenceDetailScreen(
                    title = "Preview test",
                    isChecked = checked,
                    onCheckedChange = { checked = it },
                    switchLabel = "Use preview test",
                    description = "Footnote",
                    preview = { },
                )
            }
        }

        rule.onNodeWithText("Off").assertIsSelected()
        rule.onNodeWithText("On").assertIsNotSelected()

        rule.onNodeWithText("On").performClick()
        rule.waitForIdle()

        assertEquals(true, checked)
        rule.onNodeWithText("On").assertIsSelected()
    }

    @Test
    fun theMainSwitchToggles() {
        var checked by mutableStateOf(true)
        rule.setContent {
            PreviewHost {
                SwitchPreferenceDetailScreen(
                    title = "Preview test",
                    isChecked = checked,
                    onCheckedChange = { checked = it },
                    switchLabel = "Use preview test",
                    description = "Footnote",
                )
            }
        }

        rule.onNodeWithText("Use preview test").performClick()
        rule.waitForIdle()

        assertEquals(false, checked)
    }

    @Test
    fun aDisabledPageIgnoresTheSwitchAndTheTiles() {
        var checked by mutableStateOf(false)
        rule.setContent {
            PreviewHost {
                SwitchPreferenceDetailScreen(
                    title = "Preview test",
                    isChecked = checked,
                    onCheckedChange = { checked = it },
                    switchLabel = "Use preview test",
                    description = "Footnote",
                    preview = { },
                    enabled = false,
                )
            }
        }

        rule.onNodeWithText("Use preview test").performClick()
        rule.onNodeWithText("On").performClick()
        rule.waitForIdle()

        assertEquals(false, checked)
    }

    @Test
    fun aChooserWithOptionPreviewsShowsTilesInsteadOfARadioList() {
        var picked by mutableStateOf("Grid")
        rule.setContent {
            PreviewHost {
                ChooserPreferenceDetailScreen(
                    title = "Layout",
                    description = "Footnote",
                    options = listOf(
                        PreferenceOption("Grid", "Grid", picked == "Grid"),
                        PreferenceOption("Mosaic", "Mosaic", picked == "Mosaic"),
                    ),
                    onOptionSelected = { picked = it },
                    optionPreview = { },
                )
            }
        }

        // Each option is labelled once, on its tile; a radio list would repeat the labels.
        rule.onAllNodesWithText("Mosaic").assertCountEquals(1)
        rule.onNode(hasText("Mosaic")).performClick()
        rule.waitForIdle()

        assertEquals("Mosaic", picked)
        rule.onNodeWithText("Mosaic").assertIsSelected()
    }
}
