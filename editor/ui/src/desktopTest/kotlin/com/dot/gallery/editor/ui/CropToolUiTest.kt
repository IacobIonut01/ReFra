package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.geometry.CropFitting
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.render.photo.PhotoEditorState
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Crop & straighten 2 behaviour through the real session: one undo step
 * per ruler drag, no blank edges, Back restoring the pre-tool geometry,
 * the desktop shortcuts and guided upright.
 */
@OptIn(ExperimentalTestApi::class)
class CropToolUiTest {

    private var session: PhotoEditorSession? = null

    @AfterTest
    fun tearDown() {
        session?.close()
        session = null
    }

    private fun ComposeUiTest.liveSession(): PhotoEditorSession {
        val s = openedSession(samplePixels())
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(412.dp, 832.dp).fillMaxSize()) {
                    PhotoEditorScreen(session = s, onClose = {}, onRetryOpen = {})
                }
            }
        }
        awaitImage(s)
        return s
    }

    private fun ComposeUiTest.awaitState(condition: (PhotoEditorState) -> Boolean) {
        val s = session ?: error("no session")
        waitUntil(timeoutMillis = 15_000) { condition(s.state.value) }
    }

    @Test
    fun infoPillClearsTheFrameOnAShortWindow() = runComposeUiTest {
        // 412 × 832 is the shortest canonical window: the frame has to
        // give the read-out its own lane instead of letting the pill
        // clamp onto the frame's top bracket.
        liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_CROP_INFO).fetchSemanticsNodes().isNotEmpty()
        }
        val pill = onNodeWithTag(TAG_CROP_INFO).getBoundsInRoot()
        val frame = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
        assertTrue(pill.bottom <= frame.top, "the pill $pill must clear the frame $frame")
    }

    @Test
    fun infoPillKeepsTheOutputOnOneLine() = runComposeUiTest {
        // "Original · 1051 × 701 px" used to wrap inside a 176 dp cap,
        // leaving a stray "px" line under the pill.
        liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        // The longest label over the widest real output: "Original · 800 ×
        // 600 px" is the read-out that wrapped on device.
        selectCropAspect(TAG_CROP_ORIGINAL)
        awaitState { !it.interactionActive }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_CROP_INFO).fetchSemanticsNodes().isNotEmpty()
        }
        val pill = onNodeWithTag(TAG_CROP_INFO).getBoundsInRoot()
        // The 36 dp pill is one text line plus its vertical padding; a
        // wrapped second line made it ~52 dp.
        assertTrue(pill.bottom - pill.top < 44.dp, "the read-out wrapped: $pill")
        onNodeWithTag(TAG_CROP_OUTPUT).assertTextHas("px")
    }

    @Test
    fun panningOutsideTheFrameMovesTheViewNotTheCrop() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        val before = s.state.value.document!!.geometry
        val frame0 = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()

        // 30 dp left of the frame's edge: outside the 26 dp grip and well
        // clear of the floating chrome above.
        onNodeWithTag(TAG_CROP_CANVAS).performTouchInput {
            val midY = (frame0.top + frame0.bottom) / 2
            val start = Offset((frame0.left - 30.dp).toPx(), midY.toPx())
            down(start)
            moveBy(Offset(80f, 48f))
            up()
        }
        waitForIdle()

        val frame1 = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
        assertTrue(frame1.left - frame0.left > 20.dp, "camera panned: $frame0 -> $frame1")
        assertTrue(frame1.top - frame0.top > 10.dp, "camera panned: $frame0 -> $frame1")
        // The camera is view state only: no crop, no undo step.
        assertEquals(before, s.state.value.document!!.geometry)
        assertFalse(s.state.value.interactionActive)
    }

    @Test
    fun movingTheCropFollowsTheFingerAfterAPan() = runComposeUiTest {
        // Once the user pans the view, the camera is fixed on screen; a
        // drag inside the frame must then move the frame with the finger,
        // not against it.
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        // A locked 4:5 leaves room to move; the full-image crop cannot.
        selectCropAspect(TAG_CROP_4_5)
        awaitState { !it.interactionActive }
        waitForIdle()
        val frame0 = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
        onNodeWithTag(TAG_CROP_CANVAS).performTouchInput {
            val midY = (frame0.top + frame0.bottom) / 2
            down(Offset((frame0.left - 30.dp).toPx(), midY.toPx()))
            moveBy(Offset(80f, 0f))
            up()
        }
        waitForIdle()
        val panned = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
        assertTrue(panned.left > frame0.left + 20.dp, "the view panned: $frame0 -> $panned")

        onNodeWithTag(TAG_CROP_CANVAS).performTouchInput {
            down(center)
            moveBy(Offset(80f, 0f))
            up()
        }
        waitForIdle()
        val moved = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
        assertTrue(
            moved.left > panned.left + 20.dp,
            "the frame follows the finger: $panned -> $moved"
        )
    }

    @Test
    fun pinchZoomsTheViewEvenWhenItStartsOnTheCrop() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        val before = s.state.value.document!!.geometry
        val frame0 = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()

        // Two fingers land on the frame before it moves: the gesture is a
        // pinch, not a resize. Spreading zooms in; the frame may clip at
        // the window edge, so only the growth floor is asserted.
        onNodeWithTag(TAG_CROP_CANVAS).performTouchInput {
            down(0, center + Offset(-60f, 0f))
            down(1, center + Offset(60f, 0f))
            moveTo(0, center + Offset(-100f, 0f))
            moveTo(1, center + Offset(100f, 0f))
            up(0)
            up(1)
        }
        waitForIdle()

        val frame1 = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
        val w0 = frame0.right - frame0.left
        val w1 = frame1.right - frame1.left
        assertTrue(
            w1 > w0 * 1.1f,
            "camera zoomed: $frame0 -> $frame1, geometry ${s.state.value.document!!.geometry}"
        )
        assertEquals(before, s.state.value.document!!.geometry)
        assertFalse(s.state.value.interactionActive)

        // Pinching back out past the fit returns to the resting view.
        onNodeWithTag(TAG_CROP_CANVAS).performTouchInput {
            down(0, center + Offset(-150f, 0f))
            down(1, center + Offset(150f, 0f))
            moveTo(0, center + Offset(-20f, 0f))
            moveTo(1, center + Offset(20f, 0f))
            up(0)
            up(1)
        }
        waitForIdle()
        val frame2 = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
        assertTrue(
            abs((frame2.left - frame0.left).value) < 1.5f &&
                abs((frame2.right - frame0.right).value) < 1.5f,
            "the pinch-out detent re-fits: $frame0 -> $frame2"
        )
        assertEquals(before, s.state.value.document!!.geometry)
    }

    @Test
    fun rulerDragIsOneUndoStepAndNeverShowsBlankEdges() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        val before = s.state.value.document!!.geometry

        onNodeWithTag(TAG_STRAIGHTEN).performTouchInput {
            down(center)
            moveTo(center + Offset(width * 0.05f, 0f))
            moveTo(center + Offset(width * 0.1f, 0f))
            moveTo(center + Offset(width * 0.15f, 0f))
            up()
        }
        awaitState { !it.interactionActive && it.document?.geometry?.warp?.rotationDegrees != 0f }
        val rotated = s.state.value.document!!.geometry
        assertTrue(rotated.warp.rotationDegrees > 5f, "$rotated")
        assertTrue(CropFitting.isValid(s.state.value.info!!.uprightSize, rotated), "$rotated")
        assertTrue(rotated.crop.right - rotated.crop.left < 1.0)

        // The whole drag is one local undo step back to the start.
        runOnUiThread { s.undo() }
        awaitState { it.document?.geometry == before }
        runOnUiThread { s.redo() }
        awaitState { it.document?.geometry == rotated }
    }

    @Test
    fun backCancelsTheToolAndRestoresThePreToolGeometry() = runComposeUiTest {
        val s = liveSession()
        val committed = PhotoGeometry(warp = PhotoWarp(rotationDegrees = 2f))
        runOnUiThread {
            s.applyEdits(
                "Seed",
                s.state.value.document!!.toEdits().copy(
                    geometry = CropFitting.withWarp(
                        s.state.value.info!!.uprightSize, PhotoGeometry(), committed.warp, null
                    )
                )
            )
        }
        awaitState { it.document?.geometry?.warp?.rotationDegrees == 2f }
        val seeded = s.state.value.document!!.geometry

        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        selectCropAspect(TAG_CROP_16_9)
        awaitState { !it.interactionActive && it.document?.geometry != seeded }
        selectCropAxis(TAG_CROP_TAB_VERTICAL)
        onNodeWithTag("editor.crop.ruler.vertical").performTouchInput {
            down(center); moveTo(center + Offset(width * 0.2f, 0f)); up()
        }
        awaitState { !it.interactionActive && it.document?.geometry?.warp?.verticalPerspective != 0f }

        onNodeWithTag(TAG_BACK).performClick()
        awaitState { !it.toolEditing && !it.isDrafting }
        assertEquals(seeded, s.state.value.document!!.geometry)
    }

    @Test
    fun keyboardShortcutsRotateNudgeSwapAndCommit() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        // Lock 4:5 so there is room to nudge and an orientation to swap.
        selectCropAspect(TAG_CROP_4_5)
        awaitState { !it.interactionActive && it.document?.geometry?.crop?.right == 0.9 }

        onNodeWithTag(TAG_CROP_CANVAS).requestFocus()
        onNodeWithTag(TAG_CROP_CANVAS).performKeyInput { pressKey(Key.RightBracket) }
        awaitState { it.document?.geometry?.warp?.rotationDegrees == 0.1f }
        onNodeWithTag(TAG_CROP_CANVAS).performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.LeftBracket) }
        }
        awaitState { abs((it.document?.geometry?.warp?.rotationDegrees ?: 0f) + 0.9f) < 1e-4f }

        // X swaps 4:5 -> 5:4.
        onNodeWithTag(TAG_CROP_CANVAS).performKeyInput { pressKey(Key.X) }
        awaitState {
            val g = it.document?.geometry ?: return@awaitState false
            CropFitting.planeRect(it.info!!.uprightSize, g).aspect > 1.2
        }

        // R turns left 90°.
        onNodeWithTag(TAG_CROP_CANVAS).performKeyInput { pressKey(Key.R) }
        awaitState { it.document?.geometry?.quarterTurns == 3 }

        // Arrow nudges move the crop; Enter commits the tool.
        val beforeNudge = s.state.value.document!!.geometry.crop
        onNodeWithTag(TAG_CROP_CANVAS).performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) }
        }
        awaitState { it.document?.geometry?.crop != beforeNudge }
        onNodeWithTag(TAG_CROP_CANVAS).performKeyInput { pressKey(Key.Enter) }
        awaitState { !it.toolEditing && !it.isDrafting }
        assertTrue(s.state.value.canUndo)
        assertTrue(CropFitting.isValid(s.state.value.info!!.uprightSize, s.state.value.document!!.geometry))
    }

    @Test
    fun guidedUprightLevelsADrawnGuide() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CROP)
        awaitState { it.toolEditing }
        expandPhotoToolSheet()
        onNodeWithTag(TAG_CROP_GUIDES_TOGGLE).performClick()
        collapsePhotoToolSheet()

        // Draw a slightly tilted "vertical" line inside the frame.
        onNodeWithTag(TAG_CROP_BOX).performTouchInput {
            down(Offset(width * 0.45f, height * 0.2f))
            moveTo(Offset(width * 0.47f, height * 0.4f))
            moveTo(Offset(width * 0.5f, height * 0.6f))
            moveTo(Offset(width * 0.53f, height * 0.8f))
            up()
        }
        awaitState { it.document?.geometry?.warp?.rotationDegrees != 0f }
        val g = s.state.value.document!!.geometry
        assertNotEquals(0f, g.warp.rotationDegrees)
        assertTrue(CropFitting.isValid(s.state.value.info!!.uprightSize, g), "$g")
        assertFalse(s.state.value.interactionActive)
        // The drawn line leans by atan(0.08 / 0.6) ≈ 7.6° on the square
        // frame; levelling it needs a rotation of about that size.
        assertTrue(abs(g.warp.rotationDegrees) in 6f..9.5f, "rotation ${g.warp.rotationDegrees}")
    }
}
