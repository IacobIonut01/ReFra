/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.components.PreviewHost
import com.dot.gallery.feature.settings.ui.EditorChoice
import com.dot.gallery.feature.settings.ui.EditorChooser
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Editors page tiles are the selection target themselves (the bug was a
 * non-tappable preview card above a separate radio list): clicking a tile must
 * report its value and mark it selected after recomposition.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class EditorChooserTest {

    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    @Test
    fun tappingTileSelectsChoice() {
        val selected = mutableStateOf("builtin")
        val received = mutableListOf<String>()
        rule.setContent {
            PreviewHost {
                EditorChooser(
                    options = listOf(
                        EditorChoice("builtin", "Built-in editor"),
                        EditorChoice("studio", "ReFra editor"),
                        EditorChoice("com.example.editor", "Example Editor"),
                    ),
                    selected = selected.value,
                    onSelect = {
                        received += it
                        selected.value = it
                    },
                    icon = {},
                )
            }
        }

        rule.onNodeWithTag("EditorChoice.studio", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf("studio"), received)
        rule.onNodeWithTag("EditorChoice.studio", useUnmergedTree = true)
            .assertIsSelected()
        rule.onNodeWithTag("EditorChoice.builtin", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
