package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end UI tests against a real [PhotoEditorSession] over the in-memory
 * [FixturePhotoMediaAccess] — recording-only actions cannot reproduce the
 * draft/enabled interlock these tests cover.
 */
@OptIn(ExperimentalTestApi::class)
class EditorSessionUiTest {

    private var session: PhotoEditorSession? = null

    /** Counts real shader frames so a layered document can prove the
     * runtime shader stopped drawing. */
    private class RecordingShaderRenderer : PhotoShaderRenderer {
        val draws = AtomicInteger()

        override fun draw(scope: DrawScope, edits: PhotoEdits): Boolean {
            draws.incrementAndGet()
            return true
        }

        override fun close() {}
    }

    @AfterTest
    fun tearDown() {
        photoShaderRendererFactory =
            { source, onFailure -> createPhotoShaderRenderer(source, onFailure) }
        compositePhotoShaderRendererFactory = {
            base, layers, referenceSourceSize, onFailure ->
            if (layers.isEmpty()) {
                photoShaderRendererFactory(base, onFailure)
            } else {
                createCompositePhotoShaderRenderer(
                    base, layers, referenceSourceSize, onFailure
                )
            }
        }
        session?.close()
        session = null
    }

    private fun androidx.compose.ui.test.ComposeUiTest.liveSession(
        widthDp: Float = 390f,
        heightDp: Float = 844f
    ): PhotoEditorSession {
        val s = openedSession(samplePixels())
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(widthDp.dp, heightDp.dp).fillMaxSize()) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)
        return s
    }

    private fun androidx.compose.ui.test.ComposeUiTest.awaitState(
        timeoutMs: Long = 15_000,
        condition: (com.dot.gallery.editor.render.photo.PhotoEditorState) -> Boolean
    ) {
        val s = session ?: error("no session")
        try {
            waitUntil(timeoutMillis = timeoutMs) { condition(s.state.value) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            // Name the flags that were stuck so an intermittent timeout is
            // diagnosable from the report alone.
            val st = s.state.value
            throw AssertionError(
                "state never matched: toolEditing=${st.toolEditing} " +
                    "isDrafting=${st.isDrafting} interactionActive=${st.interactionActive} " +
                    "rendering=${st.rendering} exporting=${st.exporting} " +
                    "previewCurrent=${st.previewDocument == (st.displayDocument ?: st.document)} " +
                    "error=${st.errorKind}",
                e
            )
        }
    }

    @Test
    fun dragOnExposureRulerAppliesAsOneGlobalStep() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.4f, 0f))
            moveTo(center + Offset(center.x * 0.8f, 0f))
            moveTo(centerRight)
            up()
        }
        // The gesture commits as a tool-local step: the interaction is
        // over but the tool's draft spans the whole session.
        awaitState {
            !it.interactionActive && it.toolEditing &&
                it.document?.adjustments?.exposureStops != 0f
        }

        // Undo/redo inside the tool rewind one interaction at a time.
        runOnUiThread { s.undo() }
        awaitState { it.document?.adjustments?.exposureStops == 0f }
        assertFalse(s.state.value.canUndo)
        runOnUiThread { s.redo() }
        awaitState { it.document?.adjustments?.exposureStops != 0f }

        // Apply lands the whole tool as exactly one global undo step.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
        runOnUiThread { s.undo() }
        awaitState { it.document?.adjustments?.exposureStops == 0f }
        assertFalse(s.state.value.canUndo)
        runOnUiThread { s.redo() }
        awaitState {
            !it.isDrafting && it.document?.adjustments?.exposureStops != 0f
        }
    }

    @Test
    fun saveStaysDisabledThroughoutToolTransaction() = runComposeUiTest {
        val s = liveSession()
        // D8: Save is enabled only while the session is dirty — the
        // clean session starts with the pill disabled.
        onNodeWithTag(TAG_SAVE).assertIsNotEnabled()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitState { it.toolEditing && it.isDrafting }
        // While a tool owns the draft the header's action is the tool's
        // Apply — there is no Save to reach, so a half-finished transaction
        // can never be exported.
        onNodeWithTag(TAG_SAVE).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        // One committed interaction changes nothing — the draft is the
        // tool's, so Save only returns after Apply.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(centerRight)
            up()
        }
        awaitState { !it.interactionActive }
        onNodeWithTag(TAG_SAVE).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        onNodeWithTag(TAG_SAVE).assertIsEnabled()
    }

    @Test
    fun midGestureDisposalCancelsDraft() = runComposeUiTest {
        // Skiko's test dispatcher can't inject a pointer cancel
        // (InputDispatcher.enqueueCancel is a no-op on desktop), so the
        // reachable cancel path is tearing the slider down mid-gesture —
        // the per-slider DisposableEffect must discard the draft. A
        // compact→sidebar resize replaces the ParamSlider instance while
        // the pointer is still held.
        val s = openedSession(samplePixels())
        session = s
        var width by mutableStateOf(390.dp)
        var height by mutableStateOf(844.dp)
        setContent {
            TestEditorRoot {
                Box(Modifier.size(width, height)) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)
        openPhotoTool(TAG_CATEGORY_LIGHT)

        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.5f, 0f))
            moveTo(centerRight)
            // Pointer deliberately left down.
        }
        waitForIdle()
        assertTrue(s.state.value.interactionActive)

        width = 1280.dp
        height = 800.dp
        waitForIdle()
        // The compact ruler was disposed mid-drag — the *interaction* is
        // cancelled and restored, but the tool transaction survives the
        // layout flip.
        assertFalse(s.state.value.interactionActive)
        assertTrue(s.state.value.toolEditing)
        assertEquals(
            0f,
            s.state.value.document?.adjustments?.exposureStops ?: -1f,
            0.001f
        )
        assertFalse(s.state.value.canUndo)

        // Release the held pointer; a fresh drag still works afterwards.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput { up() }
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.5f, 0f))
            up()
        }
        awaitState {
            !it.interactionActive && it.document?.adjustments?.exposureStops != 0f
        }
        // Leave the tool the way it applies in real use.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
    }

    @Test
    fun toolSelectionCancelsOpenGesture() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread {
            s.beginEdit("Exposure")
            s.updateEdits(
                PhotoEdits(color = ColorAdjustments(exposureStops = 2f))
            )
        }
        awaitState { it.interactionActive }
        openPhotoTool(TAG_CATEGORY_COLOUR)
        // The interaction was cancelled, then the Colour tool opened its
        // own transaction — isDrafting stays true for the tool's lifetime.
        awaitState { it.toolEditing }
        assertEquals(
            0f,
            s.state.value.document?.adjustments?.exposureStops ?: -1f,
            0.001f
        )
        onNodeWithTag(TAG_SLIDER_SATURATION).assertIsDisplayed()
        onNodeWithTag(TAG_BACK).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
    }

    @Test
    fun resizeKeepsViewportZoomCompareAndCategory() = runComposeUiTest {
        val s = openedSession(samplePixels())
        session = s
        var width by mutableStateOf(390.dp)
        var height by mutableStateOf(844.dp)
        setContent {
            TestEditorRoot {
                Box(Modifier.size(width, height)) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)
        // The home has no zoom control; pinch the photo. Compare is the
        // home canvas's standalone Before chip.
        pinchToZoomIn()
        onNodeWithTag(TAG_BEFORE_CHIP).performClick()
        openPhotoTool(TAG_CATEGORY_CROP)
        // The document header keeps the compare toggle while the draft is
        // open — the tool card never owns compare.
        awaitState { it.toolEditing }
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsSelected()
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing }
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsSelected()

        // Same composition, master-detail layout: the viewport survives,
        // the inspector keeps the open tool and the capsule's compare
        // toggle mirrors the home's Before toggle.
        width = 1100.dp
        height = 700.dp
        waitForIdle()
        awaitImage(s)
        // The pinched zoom survived the layout switch: the field exists
        // and no longer reads the fit.
        onNodeWithTag(TAG_TABLET_ZOOM).assertIsDisplayed()
        onNode(
            hasTestTag(TAG_TABLET_ZOOM) and
                hasAnyDescendant(hasText("100%"))
        ).assertDoesNotExist()
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsSelected()
        onNodeWithTag(TAG_CROP_ASPECT).assertIsDisplayed()

        // A new source reference resets the viewport entirely.
        runOnUiThread { s.open("fixture://other.jpg") }
        awaitImage(s)
        // Back at fit the field reads 100%, not a 1.0× figure.
        onNode(
            hasTestTag(TAG_TABLET_ZOOM) and
                hasAnyDescendant(hasText("100", substring = true))
        ).assertIsDisplayed()
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsNotSelected()
    }

    // ---- numeric entry dialog ----

    @Test
    fun aDraftOpenedMidExportSurvivesTheReceiptUnderTheSaveModal() = runComposeUiTest {
        // A live session on a gated media port: the export's publish
        // receipt can be held to observe the exact mid-export state the
        // activity's !isDrafting close guard inspects.
        val media = FixturePhotoMediaAccess { samplePixels() }
        lateinit var s: PhotoEditorSession
        runOnUiThread {
            s = PhotoEditorSession(
                CoroutineScope(Dispatchers.Main), media, onError = {}
            )
            s.open("fixture://editor_sample.jpg")
        }
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp).fillMaxSize()) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)

        media.exportPrePublishGate = CompletableDeferred()
        runOnUiThread { s.exportCopy() }
        awaitState { it.exporting }
        // The save modal covers the editor for the whole export, so no
        // tool or numeric field opens under it from the UI.
        onNodeWithTag(TAG_SAVE_PROGRESS).assertIsDisplayed()

        // A draft opened mid-export through the session still owns a real
        // draft — otherwise the receipt below sees a clean idle session and
        // the host's !isDrafting close guard closes the editor under it.
        runOnUiThread { s.beginEdit("Exposure") }
        awaitState { it.interactionActive }

        // The receipt lands while the modal is up; the draft survives it.
        media.exportPrePublishGate?.complete(Unit)
        awaitState { it.exported != null }
        assertTrue(s.state.value.isDrafting)
        assertTrue(s.state.value.interactionActive)
        onNodeWithTag(TAG_SAVE_PROGRESS).assertDoesNotExist()

        // The committed draft is one undo step on the typed value.
        runOnUiThread {
            s.updateEdits(
                s.state.value.document!!.toEdits().copy(
                    color = ColorAdjustments(exposureStops = 1.5f)
                )
            )
            s.commitEdit()
        }
        awaitState { !it.interactionActive && !it.isDrafting && it.isDirty }
        assertEquals(
            1.5f,
            s.state.value.document?.adjustments?.exposureStops ?: 0f,
            0.01f
        )
        runOnUiThread { s.undo() }
        awaitState { it.document?.adjustments?.exposureStops == 0f }
        assertFalse(s.state.value.canUndo)
    }

    @Test
    fun numericEntryUnchangedConfirmLeavesNoUndoStep() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_VALUE_EXPOSURE).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).assertIsDisplayed()
        awaitState { it.interactionActive }
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState { !it.interactionActive }
        // Confirming "0" is a no-op on the local history, not a phantom
        // edit — and applying an unchanged tool lands no global step.
        assertFalse(s.state.value.canUndo)
        assertFalse(s.state.value.isDirty)
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertFalse(s.state.value.canUndo)
        assertFalse(s.state.value.isDirty)
    }

    /**
     * A focused text field owns its keys: F while the numeric entry is up
     * types into the field — it must never reach the workspace's focus
     * toggle. Workspace shortcuts bubble (`onKeyEvent`), so this is the
     * regression for the preview-intercept bug.
     */
    @Test
    fun numericEntryConsumesFWithoutEnteringFocusMode() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_VALUE_EXPOSURE).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).assertIsDisplayed()
        awaitState { it.interactionActive }

        onNodeWithTag(TAG_VALUE_FIELD).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        // The field still owns the interaction; no focus chrome appeared.
        onNodeWithTag(TAG_VALUE_FIELD).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS_EXIT).assertDoesNotExist()
        assertTrue(s.state.value.interactionActive)
    }

    @Test
    fun numericEntryCancelRestoresTheCommittedRecipe() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread {
            s.applyEdits(
                "Set exposure",
                PhotoEdits(color = ColorAdjustments(exposureStops = 1f))
            )
        }
        awaitState { it.document?.adjustments?.exposureStops == 1f }

        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_VALUE_EXPOSURE).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("2.5")
        onNodeWithTag(TAG_VALUE_CANCEL).performClick()
        awaitState { !it.interactionActive }
        // Cancel discards the interaction — the committed 1.0 stands and
        // the typed text never touched the document. Locally there is
        // nothing to undo yet; the tool stays open.
        assertEquals(
            1f,
            s.state.value.document?.adjustments?.exposureStops ?: 0f,
            0.001f
        )
        assertTrue(s.state.value.toolEditing)
        // Applying the untouched tool adds nothing: exactly one history
        // entry survives — the original apply.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
        runOnUiThread { s.undo() }
        awaitState { it.document?.adjustments?.exposureStops == 0f }
        assertFalse(s.state.value.canUndo)
    }

    // ---- tool parameter strip ----

    @Test
    fun parameterSwitchKeepsCommittedToolEdits() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LIGHT)

        // Commit one local step on Exposure, then switch to Shadows.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.5f, 0f))
            moveTo(centerRight)
            up()
        }
        awaitState {
            !it.interactionActive &&
                it.document?.adjustments?.exposureStops != 0f
        }
        // The +2 EV chip grew the strip past the 390 dp viewport — scroll
        // the Shadows chip into view before tapping it.
        onNodeWithTag(TAG_PARAM_SHADOWS).performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithTag("editor.value.shadows").fetchSemanticsNodes().forEach {
            println("PSKCE value shadows exists")
        }
        // The ruler retargets to the new parameter; Exposure's step stays
        // on the tool's local history.
        onNodeWithTag(TAG_SLIDER_SHADOWS).assertIsDisplayed()
        onNodeWithTag(TAG_SLIDER_SHADOWS).performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.5f, 0f))
            up()
        }
        awaitState {
            !it.interactionActive && it.document?.adjustments?.shadows != 0f
        }
        assertTrue(s.state.value.document?.adjustments?.exposureStops != 0f)

        // Header Back cancels — restores the whole pre-tool recipe, no
        // steps, no edits.
        onNodeWithTag(TAG_BACK).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertEquals(
            0f,
            s.state.value.document?.adjustments?.exposureStops ?: -1f,
            0.001f
        )
        assertEquals(
            0f,
            s.state.value.document?.adjustments?.shadows ?: -1f,
            0.001f
        )
        assertFalse(s.state.value.canUndo)
    }

    @Test
    fun rulerKeyboardAndSetProgressEachCommitOneStep() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LIGHT)

        // Semantics SetProgress speaks display units inside the ruler's
        // ±5 EV precision window and commits one interaction.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(1.5f) }
        awaitState {
            !it.interactionActive &&
                (it.document?.adjustments?.exposureStops ?: 0f)
                    .let { e -> e > 1.49f && e < 1.51f }
        }

        // Arrow keys act on the focused ruler: Right = +1 step (0.1 EV),
        // Shift+Right = +10 steps; Home/End jump to the window bounds.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performSemanticsAction(
            SemanticsActions.RequestFocus
        )
        waitForIdle()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        awaitState {
            (it.document?.adjustments?.exposureStops ?: 0f)
                .let { e -> e > 1.59f && e < 1.61f }
        }
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performKeyInput {
            keyDown(Key.MoveHome); keyUp(Key.MoveHome)
        }
        awaitState { it.document?.adjustments?.exposureStops == -5f }
    }

    /**
     * Contract regression: an unchanged numeric confirm must not round-trip
     * the displayed amount through `contrastFactor` into a different stored
     * factor — a non-stepped factor (1.3) would silently mutate if the UI
     * value comparison were missing.
     */
    @Test
    fun numericEntryUnchangedContrastKeepsTheExactFactor() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread {
            s.applyEdits(
                "Set contrast",
                PhotoEdits(color = ColorAdjustments(contrast = 1.3f))
            )
        }
        awaitState { it.document?.adjustments?.contrast == 1.3f }

        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_PARAM_CONTRAST).performClick()
        onNodeWithTag(TAG_VALUE_CONTRAST).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).assertIsDisplayed()
        awaitState { it.interactionActive }
        // Confirm the displayed amount untouched — no write, no step.
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState { !it.interactionActive }
        assertEquals(
            ColorAdjustments(contrast = 1.3f),
            s.state.value.document?.adjustments
        )

        // Applying lands nothing: one undo still reaches the clean recipe.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        runOnUiThread { s.undo() }
        awaitState { it.document?.adjustments == ColorAdjustments() }
        assertFalse(s.state.value.canUndo)
    }

    /**
     * Fixed-scale contract: a tap maps x directly onto the track and
     * quantizes to the parameter step — the position is truth, not the
     * stored value. Keyboard keeps exact-delta semantics and SetProgress
     * still round-trips a raw finite value.
     */
    @Test
    fun rulerTapQuantizesPositionButKeyboardKeepsExactDelta() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        // Type an off-step value through the numeric dialog.
        onNodeWithTag(TAG_VALUE_EXPOSURE).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("0.73")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState {
            !it.interactionActive &&
                it.document?.adjustments?.exposureStops == 0.73f
        }

        // A tap at the needle's own x quantizes the touched position:
        // raw 0.73 snaps to its 0.1 step rather than surviving bit-exact.
        // The needle's fraction sits inside the ±5 EV ruler window.
        val wPx = onNodeWithTag(TAG_SLIDER_EXPOSURE)
            .fetchSemanticsNode().size.width.toFloat()
        val insetPx = with(density) { 12.dp.toPx() }
        val track = wPx - 2 * insetPx
        val needleX = insetPx + ((0.73f + 5f) / 10f) * track
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(Offset(needleX, center.y)); up()
        }
        awaitState {
            !it.interactionActive &&
                it.document?.adjustments?.exposureStops == 0.7f
        }

        // Arrow Right still takes an exact delta from the committed value.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        awaitState {
            !it.interactionActive &&
                it.document?.adjustments?.exposureStops == 0.7f + 0.1f
        }

        // SetProgress clamps the raw value — 0.73 round-trips unchanged.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(0.73f) }
        awaitState {
            it.document?.adjustments?.exposureStops == 0.73f
        }
    }

    // ── Crop & straighten / Perspective (P2b) ────────────────────────────

    @Test
    fun cropPresetAndHandleDragKeepAspectThroughToolApply() =
        runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }

        // Preset click → centred 4:5 on the 400x400 fixture.
        selectCropAspect(TAG_CROP_4_5)
        awaitState {
            !it.interactionActive &&
                it.document?.geometry?.crop?.right == 0.9
        }
        var crop = s.state.value.document!!.geometry.crop
        assertEquals(0.1, crop.left, 1e-9)

        // Drag the bottom-right handle inward — one interaction keeps
        // the displayed 4:5 pixel ratio and anchors the opposite
        // (top-left) corner.
        onNodeWithTag(TAG_CROP_HANDLE_BR).performTouchInput {
            down(center)
            moveTo(center + Offset(-30f, -30f))
            up()
        }
        awaitState { !it.interactionActive }
        crop = s.state.value.document!!.geometry.crop
        // Drag-derived crops ride the cropper's float pixel maths — the
        // anchor holds to ~1e-7, not bit-exact like the preset writes.
        assertEquals(0.1, crop.left, 1e-6)
        assertEquals(0.0, crop.top, 1e-6)
        val pxW = (crop.right - crop.left) * 400
        val pxH = (crop.bottom - crop.top) * 400
        assertEquals(0.8, pxW / pxH, 0.001)
        assertTrue(pxW < 320.0 || pxH < 400.0)

        // Apply lands the whole tool edit as a single undo step.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
        runOnUiThread { s.undo() }
        awaitState { it.document?.geometry == PhotoGeometry() }
        assertFalse(s.state.value.canUndo)
    }

    @Test
    fun cropHandleMidGestureDisposalCancelsTheDrag() = runComposeUiTest {
        // Skiko's test dispatcher cannot inject a real pointer cancel —
        // tearing the overlay down mid-gesture is the reachable cancel
        // path, so a compact→sidebar resize while the pointer is held
        // must discard the drag draft and restore the crop.
        val s = openedSession(samplePixels())
        session = s
        var width by mutableStateOf(390.dp)
        var height by mutableStateOf(844.dp)
        setContent {
            TestEditorRoot {
                Box(Modifier.size(width, height)) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        selectCropAspect(TAG_CROP_4_5)
        awaitState {
            !it.interactionActive &&
                it.document?.geometry?.crop?.right == 0.9
        }
        val before = s.state.value.document!!.geometry.crop

        onNodeWithTag(TAG_CROP_HANDLE_BR).performTouchInput {
            down(center)
            moveTo(center + Offset(-30f, -30f))
            // Pointer deliberately left down.
        }
        waitForIdle()
        assertTrue(s.state.value.interactionActive)

        width = 1280.dp
        height = 800.dp
        waitForIdle()
        // The compact overlay was disposed mid-drag — the interaction
        // cancelled and restored, but the tool transaction survives.
        assertFalse(s.state.value.interactionActive)
        assertTrue(s.state.value.toolEditing)
        assertEquals(before, s.state.value.document?.geometry?.crop)

        // Release the held pointer — a still-pressed injection blocks
        // the next gesture, and the handle node is gone in wide mode.
        onRoot().performTouchInput { up() }
        onNodeWithTag(TAG_TOOL_CANCEL).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
    }

    @Test
    fun straightenSetProgressExactEntryAndLocalUndo() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }

        // The row is a slider — SetProgress is one interaction.
        onNodeWithTag(TAG_STRAIGHTEN).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(-1.2f) }
        awaitState {
            !it.interactionActive &&
                it.document?.geometry?.warp?.rotationDegrees == -1.2f
        }
        // Local undo/redo live inside the tool transaction.
        runOnUiThread { s.undo() }
        awaitState {
            it.document?.geometry?.warp?.rotationDegrees == 0f
        }
        runOnUiThread { s.redo() }
        awaitState {
            it.document?.geometry?.warp?.rotationDegrees == -1.2f
        }

        // The right-side field opens the numeric dialog; exact entry
        // lands one step.
        onNodeWithTag(TAG_STRAIGHTEN_VALUE).performClick()
        // The value also takes a double-tap (reset), so a single tap
        // resolves after the double-tap window.
        mainClock.advanceTimeBy(500)
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(TAG_VALUE_FIELD).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("-5")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState {
            !it.interactionActive &&
                it.document?.geometry?.warp?.rotationDegrees == -5f
        }

        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
        runOnUiThread { s.undo() }
        awaitState { it.document?.geometry?.warp?.isNeutral == true }
        assertFalse(s.state.value.canUndo)
    }

    @Test
    fun keystoneTabsShareTheRulerAndApplyAsOneGlobalStep() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }

        selectCropAxis(TAG_CROP_TAB_VERTICAL)
        onNodeWithTag("editor.crop.ruler.vertical").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(6f) }
        awaitState {
            !it.interactionActive &&
                it.document?.geometry?.warp?.verticalPerspective == 6f
        }
        selectCropAxis(TAG_CROP_TAB_STRAIGHTEN)
        onNodeWithTag(TAG_STRAIGHTEN).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(-1.2f) }
        awaitState {
            !it.interactionActive &&
                it.document?.geometry?.warp?.rotationDegrees == -1.2f
        }
        // The keystone survives the straighten, and the crop was pulled
        // inside the warped photo — never a blank edge.
        val geometry = s.state.value.document!!.geometry
        assertEquals(6f, geometry.warp.verticalPerspective)
        assertTrue(
            com.dot.gallery.editor.core.geometry.CropFitting.isValid(
                s.state.value.info!!.uprightSize, geometry
            ),
            "crop must stay inside the warped photo: $geometry"
        )

        // Both rulers land as one global undo step.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
        runOnUiThread { s.undo() }
        awaitState { it.document?.geometry == PhotoGeometry() }
        assertFalse(s.state.value.canUndo)
    }

    @Test
    fun perspectiveCancelRestoresTheCommittedRecipe() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread {
            s.applyEdits(
                "Geometry",
                PhotoEdits(
                    geometry = PhotoGeometry(
                        crop = NormalizedCrop(0.2, 0.0, 0.8, 1.0),
                        warp = PhotoWarp(
                            rotationDegrees = -1.2f,
                            verticalPerspective = 6f,
                            scale = 1.04f
                        )
                    )
                )
            )
        }
        awaitState { it.document?.geometry?.warp?.scale == 1.04f }

        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        selectCropAxis(TAG_CROP_TAB_VERTICAL)
        onNodeWithTag("editor.crop.ruler.vertical").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(30f) }
        awaitState {
            it.document?.geometry?.warp?.verticalPerspective == 30f
        }

        // Header Back cancels inside the tool — restores the committed
        // recipe wholesale.
        onNodeWithTag(TAG_BACK).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertEquals(
            6f,
            s.state.value.document?.geometry?.warp?.verticalPerspective
        )
        assertEquals(
            0.8,
            s.state.value.document?.geometry?.crop?.right
        )
    }

    @Test
    fun aLayeredDocumentDrawsTheCompositeThroughTheShader() = runComposeUiTest {
        val renderers = CopyOnWriteArrayList<RecordingShaderRenderer>()
        compositePhotoShaderRendererFactory = {
            _: ImageBitmap,
            _: List<PhotoShaderLayerSource>,
            _: PixelSize,
            _: (Throwable) -> Unit ->
            RecordingShaderRenderer().also(renderers::add)
        }
        val s = liveSession()

        // The runtime shader genuinely owns the canvas before the layer.
        waitUntil(timeoutMillis = 15_000) {
            renderers.isNotEmpty() && renderers.first().draws.get() > 0
        }
        val basePreview = s.state.value.preview

        runOnUiThread { s.addImageLayer("fixture://editor_sample.jpg") }
        awaitState {
            !it.importingLayer && (it.document?.layers?.size ?: 0) == 1
        }
        // While the layer inputs decode the CPU pipeline may paint the
        // first composite frame — but with the instant test renderer the
        // shader can win the race and invalidate that render mid-flight.
        // Either ordering is honest; the invariant is the shader drawing
        // the layered document.
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.preview !== basePreview ||
                (renderers.size >= 2 && renderers[1].draws.get() > 0)
        }
        waitUntil(timeoutMillis = 15_000) {
            renderers.size >= 2 && renderers[1].draws.get() > 0
        }
        waitForIdle()
        val shader = renderers[1]
        val gpuPreview = s.state.value.preview

        // A real tool gesture stays on the shader: the blend-mode click
        // repaints frames through uniforms while the CPU pipeline sleeps.
        awaitState { !it.toolEditing && !it.isDrafting }
        openPhotoTool(TAG_CATEGORY_BLEND)
        awaitState { it.toolEditing }
        val beforeBlend = shader.draws.get()
        onNodeWithTag(TAG_BLEND_MODE_PREFIX + "Screen").performClick()
        awaitState {
            it.document?.layers?.single()?.blendMode == PhotoBlendMode.Screen
        }
        waitForIdle()
        assertTrue(
            shader.draws.get() > beforeBlend,
            "the composite shader did not draw the blend change"
        )
        assertTrue(
            s.state.value.preview === gpuPreview,
            "the CPU preview re-rendered while the shader owned the canvas"
        )

        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }

        // The mask tool hands the canvas to the CPU pipeline for the
        // whole transaction — strokes must never wait on a shader
        // rebuild, and the preview repaint proves the CPU woke.
        openLayersWorkspace()
        val layerId = s.state.value.document!!.layers.single().id
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + layerId).performClick()
        onNodeWithText("Edit mask").performClick()
        awaitState { it.toolEditing }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.preview !== gpuPreview &&
                s.state.value.preview != null
        }
        val maskPreview = s.state.value.preview
        onNodeWithTag(TAG_MASK_CANVAS).performTouchInput {
            down(center); up()
        }
        awaitState {
            !it.interactionActive &&
                it.document?.layers?.single()?.mask?.strokes?.size == 1
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.preview !== maskPreview
        }

        // Apply ends the transaction — the new mask rebuilds the
        // composite program once and the shader draws again.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        waitUntil(timeoutMillis = 15_000) {
            renderers.size >= 3 && renderers[2].draws.get() > 0
        }
        onNodeWithTag(TAG_IMAGE).assertIsDisplayed()
    }

    @Test
    fun aFailedLayerShaderFallsBackToTheCpuComposite() = runComposeUiTest {
        // A composite renderer that rejects every frame: the canvas must
        // fall back to the CPU composite — and the failure must reach the
        // session's diagnostics exactly once.
        val errors = CopyOnWriteArrayList<Throwable>()
        compositePhotoShaderRendererFactory = {
            base: ImageBitmap,
            layers: List<PhotoShaderLayerSource>,
            _: PixelSize,
            onFailure: (Throwable) -> Unit ->
            if (layers.isEmpty()) {
                // A healthy base keeps the failing layer shader's report
                // count meaningful — exactly one, not one per renderer.
                createPhotoShaderRenderer(base, onFailure)
            } else {
                object : PhotoShaderRenderer {
                    override fun draw(scope: DrawScope, edits: PhotoEdits) =
                        false

                    override fun close() {}
                }
            }
        }
        val opens = AtomicInteger()
        val layerPixels =
            SrgbPixels(PixelSize(4, 4), IntArray(16) { 0xFF0000FF.toInt() })
        lateinit var s: PhotoEditorSession
        runOnUiThread {
            s = PhotoEditorSession(
                scope = CoroutineScope(Dispatchers.Main),
                media = FixturePhotoMediaAccess {
                    if (opens.incrementAndGet() == 1) samplePixels()
                    else layerPixels
                },
                onError = { errors += it }
            )
            s.open("fixture://editor_sample.jpg")
        }
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp).fillMaxSize()) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)
        val basePreview = s.state.value.preview

        runOnUiThread { s.addImageLayer("fixture://layer.png") }
        awaitState {
            !it.importingLayer && (it.document?.layers?.size ?: 0) == 1
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.preview !== basePreview
        }

        // The composite is the real graph output: the opaque blue layer
        // owns the frame centre while the base photo survives at the edge.
        val pixels = s.state.value.preview ?: error("no CPU preview published")
        val argb = pixels.copyPixels()
        val centre = argb[(pixels.size.height / 2) * pixels.size.width +
            pixels.size.width / 2]
        assertTrue(
            (centre and 0xFF) > 0xC0 && (centre shr 16 and 0xFF) < 0x40,
            "layered preview centre is not the layer colour: " +
                Integer.toHexString(centre)
        )
        assertTrue(
            argb[0] != centre,
            "layered preview edge matches the layer colour — " +
                "the base composite is missing"
        )
        waitForIdle()
        assertEquals(
            1, errors.count { it is ShaderCanvasUnavailableException },
            "the layer shader failure must be reported exactly once: $errors"
        )
        onNodeWithTag(TAG_IMAGE).assertIsDisplayed()
    }

    @Test
    fun layersWorkspaceRowsSelectAndPaintThePlacementOutline() =
        runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://layer.png", name = "Sky") }
        awaitState {
            (it.document?.layers?.size ?: 0) == 1 && !it.importingLayer
        }

        openLayersWorkspace()
        val layerId = s.state.value.document!!.layers.single().id
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).assertIsDisplayed()
        onNodeWithText("Sky").assertIsDisplayed()
        onNodeWithText("Normal · 100%").assertIsDisplayed()

        // Selecting the row paints the transformed placement quad over
        // the composite canvas.
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).performClick()
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).assertIsSelected()
        onNodeWithTag(TAG_LAYER_SELECTION).assertIsDisplayed()

        // Selecting the base clears it — no outline on the base layer.
        onNodeWithTag(TAG_LAYER_BASE).performClick()
        onNodeWithTag(TAG_LAYER_BASE).assertIsSelected()
        onNodeWithTag(TAG_LAYER_SELECTION).assertDoesNotExist()
    }

    @Test
    fun layerRowActionsApplyHonestRecipeSteps() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "One") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        runOnUiThread { s.addImageLayer("fixture://two.png", name = "Two") }
        awaitState { (it.document?.layers?.size ?: 0) == 2 }

        openLayersWorkspace()
        var layers = s.state.value.document!!.layers
        val bottomId = layers[0].id

        // Rename goes through the dialog and lands as one undoable step.
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + bottomId).performClick()
        onNodeWithText("Rename").performClick()
        onNodeWithTag(TAG_LAYER_RENAME_FIELD)
            .performTextReplacement("Renamed")
        onNodeWithTag(TAG_LAYER_RENAME_CONFIRM).performClick()
        awaitState { it.document?.layers?.get(0)?.name == "Renamed" }
        runOnUiThread { s.undo() }
        awaitState { it.document?.layers?.get(0)?.name == "One" }
        runOnUiThread { s.redo() }
        awaitState { it.document?.layers?.get(0)?.name == "Renamed" }

        // Duplicate inserts directly above in the recipe, reusing the
        // same immutable source frame under a new layer id.
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + bottomId).performClick()
        onNodeWithText("Duplicate").performClick()
        awaitState { (it.document?.layers?.size ?: 0) == 3 }
        layers = s.state.value.document!!.layers
        assertEquals("$bottomId-copy", layers[1].id)
        assertEquals(layers[0].source.id, layers[1].source.id)

        // Move down swaps the duplicate below the original.
        val copyId = layers[1].id
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + copyId).performClick()
        onNodeWithText("Move down").performClick()
        awaitState { it.document?.layers?.get(0)?.id == copyId }

        // Lock disables Delete; Unlock restores it; Delete is one step.
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + copyId).performClick()
        onNodeWithText("Lock").performClick()
        awaitState { it.document?.layers?.get(0)?.locked == true }
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + copyId).performClick()
        onNodeWithText("Delete").assertIsNotEnabled()
        onNodeWithText("Unlock").performClick()
        awaitState { it.document?.layers?.get(0)?.locked == false }
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + copyId).performClick()
        onNodeWithText("Delete").performClick()
        awaitState { (it.document?.layers?.size ?: 0) == 2 }
        runOnUiThread { s.undo() }
        awaitState { (it.document?.layers?.size ?: 0) == 3 }

        // Toggling visibility flips the flag while the recipe survives.
        onNodeWithTag(TAG_LAYER_VISIBILITY_PREFIX + layers[2].id)
            .performClick()
        awaitState { it.document?.layers?.get(2)?.visible == false }
        runOnUiThread { s.undo() }
        awaitState { it.document?.layers?.get(2)?.visible == true }
    }

    // ── Contextual composition tools (P3b2a) ────────────────────────────

    @Test
    fun contextualToolOpensOnTopmostLayerAndCatalogueDisablesWithoutOne() =
        runComposeUiTest {
        val s = liveSession()

        // With no image layer the catalogue paints Transform and Blend
        // disabled — a disabled cell swallows the click: no transaction.
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID)
            .performScrollToNode(hasTestTag(TAG_CATEGORY_TRANSFORM))
        onNodeWithTag(TAG_CATEGORY_TRANSFORM).assertIsNotEnabled()
        onNodeWithTag(TAG_CATEGORY_BLEND).assertIsNotEnabled()
        onNodeWithTag(TAG_CATEGORY_DOUBLE_EXPOSURE).assertIsEnabled()
        onNodeWithTag(TAG_CATEGORY_TRANSFORM).performClick()
        assertFalse(s.state.value.toolEditing)
        // The contextual-only mask has no cell at all.
        onNodeWithTag("editor.category.mask").assertDoesNotExist()
        // Back collapses the catalogue before the imports.
        onNodeWithTag(TAG_BACK).performClick()

        runOnUiThread { s.addImageLayer("fixture://one.png", name = "One") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        runOnUiThread { s.addImageLayer("fixture://two.png", name = "Two") }
        awaitState { (it.document?.layers?.size ?: 0) == 2 }

        // Reopen the catalogue — the compose cells wake up with a layer.
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID)
            .performScrollToNode(hasTestTag(TAG_CATEGORY_TRANSFORM))
        onNodeWithTag(TAG_CATEGORY_TRANSFORM).performClick()
        awaitState { it.toolEditing }
        // The selection was invalid (base) — the tool auto-selected the
        // topmost recipe layer and the breadcrumb names it.
        onNodeWithText("Selected · Two").assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_SELECTION).assertIsDisplayed()
    }

    @Test
    fun transformRowsHandlesAndMoveApplyAsOneStep() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        openPhotoTool(TAG_CATEGORY_TRANSFORM)
        awaitState { it.toolEditing }
        onNodeWithText("Selected · Sky").assertIsDisplayed()

        // Exact rows: SetProgress commits one local step each.
        onNodeWithTag(TAG_LAYER_TRANSFORM_X).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(40f) }
        awaitState {
            it.document?.layers?.single()?.transform?.translationX == 40f
        }
        onNodeWithTag(TAG_LAYER_TRANSFORM_SCALE).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(65f) }
        awaitState {
            it.document?.layers?.single()?.transform?.scale == 0.65f
        }
        // Exact entry through the pair dialog — the X·Y field opens it,
        // the second field carries Y and the confirm lands both
        // atomically as one local interaction.
        onNodeWithTag(TAG_LAYER_TRANSFORM_Y).performClick()
        onNodeWithTag(TAG_VALUE_FIELD_SECOND).performTextReplacement("-25")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState {
            !it.interactionActive &&
                it.document?.layers?.single()?.transform?.translationY == -25f
        }
        // The untouched field confirmed its current value — X is still
        // the 40 px the SetProgress wrote.
        assertEquals(
            40f,
            s.state.value.document!!.layers.single().transform.translationX
        )

        // The interactive overlay: a move drag slides the translation.
        // The hit region is measured while the layer is still centred —
        // once the move drag slides it toward the window edge the
        // corner box is window-clipped, which is expected.
        onNodeWithTag(TAG_LAYER_HANDLE_BR).assertTouchWidthIsEqualTo(48.dp)
        onNodeWithTag(TAG_LAYER_MOVE).assertIsDisplayed()
        val t0 = s.state.value.document!!.layers.single().transform
        onNodeWithTag(TAG_LAYER_MOVE).performTouchInput {
            down(center)
            moveTo(center + Offset(30f, 0f))
            up()
        }
        awaitState { !it.interactionActive }
        assertTrue(
            s.state.value.document!!.layers.single().transform
                .translationX > t0.translationX
        )

        // Corner handles carry the ≥48 dp hit region; a drag scales
        // uniformly around the pinned opposite corner.
        val scale0 =
            s.state.value.document!!.layers.single().transform.scale
        onNodeWithTag(TAG_LAYER_HANDLE_BR).performTouchInput {
            down(center - Offset(8f, 8f))
            moveTo(center + Offset(30f, 30f))
            up()
        }
        awaitState { !it.interactionActive }
        assertTrue(
            s.state.value.document!!.layers.single().transform.scale > scale0
        )

        // The whole tool — rows, entry, move and scale — is one
        // global step under the import step.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        runOnUiThread { s.undo() }
        awaitState {
            it.document?.layers?.single()?.transform == PhotoLayerTransform()
        }
        assertTrue(s.state.value.canUndo) // the import step remains
    }

    @Test
    fun blendModeOpacityClipAndCompareApplyAsOneStep() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        openPhotoTool(TAG_CATEGORY_BLEND)
        awaitState { it.toolEditing }

        onNodeWithTag(TAG_BLEND_MODE_PREFIX + "Screen").performClick()
        awaitState {
            it.document?.layers?.single()?.blendMode == PhotoBlendMode.Screen
        }
        onNodeWithTag(TAG_LAYER_OPACITY).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(65f) }
        awaitState { it.document?.layers?.single()?.opacity == 0.65f }
        onNodeWithTag(TAG_LAYER_CLIP).performClick()
        awaitState { it.document?.layers?.single()?.clipToBelow == true }

        // Compare is the header's own toggle now: it drives the
        // viewport split, never the recipe and never a history step.
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsNotSelected().performClick()
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsSelected()
        onNodeWithTag(TAG_BEFORE_CHIP).performClick()
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsNotSelected()
        awaitState {
            it.document?.layers?.single()?.blendMode ==
                PhotoBlendMode.Screen &&
                it.document?.layers?.single()?.opacity == 0.65f &&
                it.document?.layers?.single()?.clipToBelow == true
        }

        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        runOnUiThread { s.undo() }
        awaitState {
            it.document?.layers?.single()?.let { l ->
                l.blendMode == PhotoBlendMode.Normal && l.opacity == 1f &&
                    !l.clipToBelow
            } == true
        }
    }

    @Test
    fun doubleExposureRoutesSettleTheOutgoingTransaction() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        openPhotoTool(TAG_CATEGORY_DOUBLE_EXPOSURE)
        awaitState { it.toolEditing }

        // The breadcrumb names the base document — p-double's context
        // is the photo itself, not the selected layer.
        onNodeWithText("editor_sample.jpg").assertIsDisplayed()

        // The summary rows read the real sources and blend values.
        onNodeWithText("Base · Overlay").assertIsDisplayed()
        onNodeWithText("editor_sample.jpg · Sky").assertIsDisplayed()
        onNodeWithTag(TAG_DOUBLE_BASE).assertIsDisplayed()
        onNodeWithText("Normal").assertIsDisplayed()
        onNodeWithText("100%").assertIsDisplayed()

        // Route to the contextual tools — the outgoing transaction
        // settles; no draft nests.
        onNodeWithTag(TAG_DOUBLE_TRANSFORM).performClick()
        awaitState { it.toolEditing }
        onNodeWithTag(TAG_LAYER_TRANSFORM_X).assertIsDisplayed()

        // One edit in the routed tool, Apply → a single undo step back
        // to the pre-tool recipe.
        onNodeWithTag(TAG_LAYER_TRANSFORM_X).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(30f) }
        awaitState {
            it.document?.layers?.single()?.transform?.translationX == 30f
        }
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        runOnUiThread { s.undo() }
        awaitState {
            it.document?.layers?.single()?.transform == PhotoLayerTransform()
        }
        assertTrue(s.state.value.canUndo) // the import step remains
    }

    @Test
    fun maskEraseStrokeCutsAndUndoesInOneTool() = runComposeUiTest {
        val opens = AtomicInteger()
        val layerPixels =
            SrgbPixels(PixelSize(4, 4), IntArray(16) { 0xFF0000FF.toInt() })
        lateinit var s: PhotoEditorSession
        runOnUiThread {
            s = PhotoEditorSession(
                scope = CoroutineScope(Dispatchers.Main),
                media = FixturePhotoMediaAccess {
                    if (opens.incrementAndGet() == 1) samplePixels()
                    else layerPixels
                },
                onError = {}
            )
            s.open("fixture://editor_sample.jpg")
        }
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp).fillMaxSize()) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)
        runOnUiThread { s.addImageLayer("fixture://layer.png", name = "Blue") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }

        // Route through the layer row's More menu — the same openTool
        // path the catalogue takes.
        openLayersWorkspace()
        val layerId = s.state.value.document!!.layers.single().id
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + layerId).performClick()
        onNodeWithText("Edit mask").performClick()
        awaitState { it.toolEditing }

        // Erase is the initial segment on a fresh full mask.
        onNodeWithTag(TAG_MASK_ERASE).assertIsSelected()
        onNodeWithTag(TAG_MASK_CANVAS).assertIsDisplayed()

        // A tap paints a dot stroke — the mask draft repaints the
        // composite with the layer erased under the brush.
        val before = s.state.value.preview
        onNodeWithTag(TAG_MASK_CANVAS).performTouchInput {
            down(center); up()
        }
        awaitState {
            !it.interactionActive &&
                it.document?.layers?.single()?.mask?.strokes?.size == 1
        }
        // A re-rendered preview is not yet proof — wait for the one that
        // actually shows the erased centre (a render queued before the
        // commit can land first).
        fun centrePixel(): Int? {
            val pixels = s.state.value.preview ?: return null
            val argb = pixels.copyPixels()
            return argb[(pixels.size.height / 2) * pixels.size.width +
                pixels.size.width / 2]
        }
        fun centreIsErased(value: Int?): Boolean = value != null &&
            ((value and 0xFF) <= 0xC0 || (value shr 16 and 0xFF) >= 0x40)
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.preview !== before && centreIsErased(centrePixel())
        }
        val centre = centrePixel()
        assertTrue(
            centreIsErased(centre),
            "erase stroke left the layer colour at centre: " +
                Integer.toHexString(centre ?: 0)
        )

        // Undo stroke pops the mask's last stroke as one local step.
        onNodeWithTag(TAG_MASK_UNDO_STROKE).performClick()
        awaitState {
            it.document?.layers?.single()?.mask?.strokes?.isEmpty() == true
        }

        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
    }

    @Test
    fun twoEraseDragsAccumulateStrokesAndUndoPopsNewest() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        openLayersWorkspace()
        val layerId = s.state.value.document!!.layers.single().id
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + layerId).performClick()
        onNodeWithText("Edit mask").performClick()
        awaitState { it.toolEditing }

        // Two separate erase drags — each publishes onto the strokes
        // the earlier drag already committed (regression: publish()
        // once dropped the pre-gesture strokes wholesale).
        onNodeWithTag(TAG_MASK_CANVAS).performTouchInput {
            down(center - Offset(60f, 0f))
            moveTo(center - Offset(20f, 0f))
            up()
        }
        awaitState {
            !it.interactionActive &&
                it.document?.layers?.single()?.mask?.strokes?.size == 1
        }
        onNodeWithTag(TAG_MASK_CANVAS).performTouchInput {
            down(center + Offset(20f, 0f))
            moveTo(center + Offset(60f, 0f))
            up()
        }
        awaitState {
            !it.interactionActive &&
                it.document?.layers?.single()?.mask?.strokes?.size == 2
        }
        // Both masked regions recorded: the strokes sit on opposite
        // sides of the layer's normalized space.
        val strokes =
            s.state.value.document!!.layers.single().mask.strokes
        assertTrue(
            strokes[0].points.first().x < strokes[1].points.first().x,
            "second drag did not record its own region: $strokes"
        )

        // Undo stroke removes only the newest drag.
        onNodeWithTag(TAG_MASK_UNDO_STROKE).performClick()
        awaitState {
            it.document?.layers?.single()?.mask?.strokes?.size == 1
        }
        assertEquals(
            strokes[0],
            s.state.value.document!!.layers.single().mask.strokes.single()
        )

        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
    }

    @Test
    fun maskToolCancelRestoresTheUntouchedMask() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        openLayersWorkspace()
        val layerId = s.state.value.document!!.layers.single().id
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + layerId).performClick()
        onNodeWithText("Edit mask").performClick()
        awaitState { it.toolEditing }

        onNodeWithTag(TAG_MASK_CANVAS).performTouchInput {
            down(center); up()
        }
        awaitState {
            !it.interactionActive &&
                it.document?.layers?.single()?.mask?.strokes?.size == 1
        }

        // Header Back cancels — restores the mask captured at tool open;
        // the stroke and the transaction are gone together.
        onNodeWithTag(TAG_BACK).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertEquals(
            0,
            s.state.value.document!!.layers.single().mask.strokes.size
        )
    }

    @Test
    fun maskFocusKeepsTheBrushSurfaceAndToolChrome() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        openLayersWorkspace()
        val layerId = s.state.value.document!!.layers.single().id
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + layerId).performClick()
        onNodeWithText("Edit mask").performClick()
        awaitState { it.toolEditing }

        // Focus editing on a selected image layer swaps the generic
        // navigator chrome for the composite layer chrome — the brush
        // surface and the transaction's Apply stay.
        onNodeWithTag(TAG_MASK_FOCUS).performClick()
        waitForIdle()
        onNodeWithTag(TAG_MASK_CANVAS).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_FOCUS_TOP).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_FOCUS_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertDoesNotExist()

        // A stroke drawn on the focus canvas lands in the same tool
        // transaction; Apply on the layer chrome commits it, then exits.
        onNodeWithTag(TAG_MASK_CANVAS).performTouchInput {
            down(center); up()
        }
        awaitState {
            !it.interactionActive &&
                it.document?.layers?.single()?.mask?.strokes?.size == 1
        }
        onNodeWithTag(TAG_LAYER_FOCUS_APPLY).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertEquals(
            1,
            s.state.value.document!!.layers.single().mask.strokes.size
        )
        assertTrue(s.state.value.canUndo)
    }

    @Test
    fun lockedLayerDisablesTheContextualSurfaces() = runComposeUiTest {
        val s = liveSession()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        awaitState { (it.document?.layers?.size ?: 0) == 1 }
        runOnUiThread {
            val doc = s.state.value.document!!
            s.applyEdits(
                "Lock layer",
                doc.toEdits().copy(
                    layers = doc.layers.replacingAt(
                        0, doc.layers.single().copy(locked = true)
                    )
                )
            )
        }
        awaitState { it.document?.layers?.single()?.locked == true }

        openPhotoTool(TAG_CATEGORY_TRANSFORM)
        awaitState { it.toolEditing }
        // The inspector paints honestly disabled — actions are off.
        onNodeWithTag(TAG_LAYER_ALIGN).assertIsNotEnabled()
        onNodeWithTag(TAG_LAYER_FLIP).assertIsNotEnabled()
        // And the row's SetProgress refuses: the recipe stays put.
        onNodeWithTag(TAG_LAYER_TRANSFORM_X).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(80f) }
        waitForIdle()
        assertEquals(
            0f,
            s.state.value.document!!.layers.single().transform.translationX
        )
        onNodeWithTag(TAG_BACK).performClick()
        awaitState { !it.toolEditing }
    }
}
