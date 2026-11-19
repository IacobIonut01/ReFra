package com.dot.gallery.editor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shared selector chip: selected semantics and its check, click
 * routing, and the sideways-scrolling row that never wraps.
 */
@OptIn(ExperimentalTestApi::class)
class EditorChipUiTest {

    @Test
    fun selectedChipExposesSelectionCheckAndClick() =
        runDesktopComposeUiTest(width = 412, height = 240) {
            var clicks = 0
            setContent {
                TestEditorRoot {
                    EditorChipRow {
                        EditorChip(
                            label = "One",
                            selected = false,
                            onClick = { clicks++ },
                            tag = "chip.one"
                        )
                        EditorChip(
                            label = "Two",
                            selected = true,
                            onClick = { clicks++ },
                            tag = "chip.two"
                        )
                    }
                }
            }

            onNodeWithTag("chip.two").assertIsSelected().assertIsDisplayed()
            onNodeWithTag("chip.two.check", useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithTag("chip.one").assertIsNotSelected()
            onNodeWithTag("chip.one.check").assertDoesNotExist()

            onNodeWithTag("chip.one").performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun chipRowScrollsToReachChipsPastTheViewport() =
        runDesktopComposeUiTest(width = 240, height = 120) {
            setContent {
                TestEditorRoot {
                    EditorChipRow {
                        repeat(10) { i ->
                            EditorChip(
                                label = "Chip $i",
                                selected = false,
                                onClick = {},
                                tag = "chip.$i"
                            )
                        }
                    }
                }
            }

            onNodeWithTag("chip.0").assertIsDisplayed()
            onNodeWithTag("chip.9").assertIsNotDisplayed()

            onNodeWithTag("chip.9").performScrollTo().assertIsDisplayed()
        }
}
