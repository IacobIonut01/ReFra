package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.photo.PhotoEditorState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The save modal: it shows only while an export is in flight, its bar and
 * percentage are the session's export progress, Cancel cancels the export
 * job and is disabled while it winds down, and the preview thumbnail
 * appears only when the preview describes the recipe being saved.
 */
@OptIn(ExperimentalTestApi::class)
class EditorSaveModalUiTest {

    private fun actions(events: MutableList<String>) = PhotoEditorActions(
        beginEdit = {},
        updateEdits = {},
        commitEdit = {},
        cancelEdit = {},
        applyEdits = { _, _ -> },
        undo = { events += "undo" },
        redo = {},
        reset = {},
        saveCopy = { events += "save" },
        cancelExport = { events += "cancelExport" },
        retryOpen = {},
        retryPreview = {},
        clearError = {},
        close = { events += "close" }
    )

    @Test
    fun percentageIsTheRealExportProgressRoundedDown() {
        assertEquals(0, saveProgressPercent(null))
        assertEquals(87, saveProgressPercent(0.879f))
        assertEquals(99, saveProgressPercent(0.999f))
        assertEquals(100, saveProgressPercent(1f))
        assertEquals(0, saveProgressPercent(-0.2f))
    }

    @Test
    fun theModalFollowsTheExportAndCancelCancelsTheJob() = runComposeUiTest {
        val events = mutableListOf<String>()
        var state by mutableStateOf<PhotoEditorState>(fixtureState())
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(state, actions(events))
                }
            }
        }
        onNodeWithTag(TAG_SAVE_PROGRESS).assertDoesNotExist()

        state = fixtureState(exporting = true).copy(exportProgress = 0.87f)
        waitForIdle()
        onNodeWithTag(TAG_SAVE_PROGRESS).assertIsDisplayed()
        onNodeWithText("Saving copy…").assertIsDisplayed()
        onNodeWithTag(TAG_SAVE_PROGRESS_PERCENT).assertTextEquals("87%")
        onNodeWithTag(TAG_EXPORT_CANCEL)
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
        onNodeWithTag(TAG_EXPORT_CANCEL).performClick()
        assertEquals(listOf("cancelExport"), events)

        state = fixtureState(exporting = true, exportCancelling = true)
        waitForIdle()
        onNodeWithText("Cancelling…").assertIsDisplayed()
        onNodeWithTag(TAG_EXPORT_CANCEL).assertIsNotEnabled()

        state = fixtureState()
        waitForIdle()
        onNodeWithTag(TAG_SAVE_PROGRESS).assertDoesNotExist()
    }

    @Test
    fun editorShortcutsDoNotReachTheEditorUnderTheModal() = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(fixtureState(exporting = true, canUndo = true), actions(events))
                }
            }
        }
        waitForIdle()
        onNodeWithTag(TAG_EXPORT_CANCEL).assertIsFocused()
        onNodeWithTag(TAG_EXPORT_CANCEL).performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.Z); keyUp(Key.Z); keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        assertEquals(emptyList(), events)
    }

    @Test
    fun theThumbnailAppearsOnlyForAPreviewOfTheRecipeBeingSaved() = runComposeUiTest {
        val events = mutableListOf<String>()
        val stale = fixtureState(exporting = true)
        var state by mutableStateOf(stale)
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(state, actions(events))
                }
            }
        }
        waitForIdle()
        onNodeWithTag(TAG_SAVE_PROGRESS).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE_PROGRESS_THUMBNAIL).assertDoesNotExist()

        state = stale.copy(previewDocument = stale.document)
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithTag(TAG_SAVE_PROGRESS_THUMBNAIL).assertIsDisplayed() }.isSuccess
        }
    }
}
