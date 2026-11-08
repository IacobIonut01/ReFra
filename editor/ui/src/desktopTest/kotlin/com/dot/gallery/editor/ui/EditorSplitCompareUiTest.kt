package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The compare toggle's split view: the original left of a draggable divider,
 * the edit to its right, a 48 dp labelled knob, hold-to-compare overriding
 * it full-frame, and the full Before fallback while the recipe has geometry
 * the original cannot line up with.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorSplitCompareUiTest {

    private var session: PhotoEditorSession? = null

    @AfterTest
    fun tearDown() {
        session?.close()
        session = null
    }

    private fun androidx.compose.ui.test.ComposeUiTest.splitCanvas(
        viewport: EditorViewportState
    ) {
        val state = fixtureState()
        val bitmap = state.preview!!.toEditorImageBitmap()
        setContent {
            TestEditorRoot {
                EditorCanvas(
                    state = state,
                    actions = noopActions(),
                    viewport = viewport,
                    bitmap = bitmap,
                    imageDescription = "Photo",
                    compactUtility = true,
                    showFooter = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @Test
    fun splitShowsALabelledFortyEightDpHandleAndBothStageLabels() = runComposeUiTest {
        val viewport = EditorViewportState(compareSplit = true)
        splitCanvas(viewport)
        waitForIdle()
        onNodeWithTag(TAG_SPLIT_HANDLE)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
        assertTrue(
            hasContentDescription("Comparison divider")
                .matches(onNodeWithTag(TAG_SPLIT_HANDLE).fetchSemanticsNode())
        )
        onNodeWithTag(TAG_SPLIT_ORIGINAL).assertExists()
        onNodeWithTag(TAG_SPLIT_BEFORE).assertIsDisplayed()
        onNodeWithTag(TAG_SPLIT_AFTER).assertIsDisplayed()
        onNodeWithText("Original").assertIsDisplayed()
        onNodeWithText("Edited").assertIsDisplayed()
        val range = onNodeWithTag(TAG_SPLIT_HANDLE).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.5f, range.current)
    }

    @Test
    fun setProgressMovesTheDividerAndHidesTheCoveredLabel() = runComposeUiTest {
        val viewport = EditorViewportState(compareSplit = true)
        splitCanvas(viewport)
        waitForIdle()
        onNodeWithTag(TAG_SPLIT_HANDLE).performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.05f)
        }
        waitForIdle()
        runOnUiThread { assertEquals(0.05f, viewport.splitFraction) }
        onNodeWithTag(TAG_SPLIT_BEFORE).assertDoesNotExist()
        onNodeWithTag(TAG_SPLIT_AFTER).assertIsDisplayed()
    }

    @Test
    fun draggingTheKnobMovesTheFraction() = runComposeUiTest {
        val viewport = EditorViewportState(compareSplit = true)
        splitCanvas(viewport)
        waitForIdle()
        onNodeWithTag(TAG_SPLIT_HANDLE).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(40f, 0f))
            up()
        }
        waitForIdle()
        runOnUiThread { assertTrue(viewport.splitFraction > 0.5f) }
    }

    @Test
    fun holdOverridesTheSplitFullFrameUntilRelease() = runComposeUiTest {
        mainClock.autoAdvance = false
        val viewport = EditorViewportState(compareSplit = true)
        splitCanvas(viewport)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        onNodeWithTag(TAG_SPLIT_HANDLE).assertIsDisplayed()
        // Off the knob, which sits on the card's centre.
        onNodeWithTag(TAG_IMAGE).performTouchInput { down(percentOffset(0.25f, 0.5f)) }
        mainClock.advanceTimeBy(700)
        runOnUiThread {
            assertTrue(viewport.compareBefore)
            assertFalse(viewport.showsSplit)
        }
        onNodeWithTag(TAG_SPLIT_HANDLE).assertDoesNotExist()
        onNodeWithTag(TAG_IMAGE).performTouchInput { up() }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(32)
        runOnUiThread {
            assertFalse(viewport.compareBefore)
            assertTrue(viewport.compareSplit)
        }
        onNodeWithTag(TAG_SPLIT_HANDLE).assertIsDisplayed()
    }

    @Test
    fun geometryFallsBackToTheFullBeforeFrame() = runComposeUiTest {
        val s = openedSession(samplePixels())
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)
        runOnUiThread {
            s.applyEdits(
                "Rotate",
                s.state.value.document!!.toEdits().copy(
                    geometry = PhotoGeometry(quarterTurns = 1)
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.geometry?.quarterTurns == 1 && !s.state.value.isDrafting
        }
        onAllNodesWithTag(TAG_BEFORE_CHIP).onFirst().performClick()
        waitForIdle()
        onNodeWithTag(TAG_SPLIT_HANDLE).assertDoesNotExist()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(TAG_CANVAS).fetchSemanticsNodes().any { node ->
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .any { it.contains("showing before edits") }
            }
        }
    }

    @Test
    fun saverRoundTripsTheCompareFields() {
        val original = EditorViewportState(
            zoom = 2f, panX = 3f, panY = -4f,
            compareBefore = false, compareSplit = true, splitFraction = 0.3f
        )
        val scope = SaverScope { true }
        val saved = with(EditorViewportState.Saver) { scope.save(original) }!!
        val restored = EditorViewportState.Saver.restore(saved)!!
        assertEquals(2f, restored.zoom)
        assertEquals(3f, restored.panX)
        assertEquals(-4f, restored.panY)
        assertFalse(restored.compareBefore)
        assertTrue(restored.compareSplit)
        assertTrue(abs(restored.splitFraction - 0.3f) < 1e-6f)
        assertTrue(restored.splitAvailable)
    }

    private fun noopActions() = PhotoEditorActions(
        beginEdit = {},
        updateEdits = {},
        commitEdit = {},
        cancelEdit = {},
        applyEdits = { _, _ -> },
        undo = {},
        redo = {},
        reset = {},
        saveCopy = {},
        cancelExport = {},
        retryOpen = {},
        retryPreview = {},
        clearError = {},
        close = {}
    )
}
