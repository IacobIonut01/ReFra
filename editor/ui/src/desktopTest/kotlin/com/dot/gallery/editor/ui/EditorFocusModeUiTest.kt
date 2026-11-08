package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Focus mode from the phone Light sheet: dragging the card down shrinks it
 * to the active sub-tool's ruler, the carousel title swipes between
 * sub-tools, and an upward drag restores the full card on the same
 * parameter. The card carries no Focus chip; the active sub-tool's reset
 * sits in the floating row under the header (the crop reset's slot).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorFocusModeUiTest {

    private var session: PhotoEditorSession? = null

    @AfterTest
    fun tearDown() {
        session?.close()
        session = null
    }

    private fun ComposeUiTest.phoneLightTool(): PhotoEditorSession {
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
        return s
    }

    private fun ComposeUiTest.openLight(s: PhotoEditorSession) {
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
    }

    /** The card's body overscrolls downward at rest — that enters focus. */
    private fun ComposeUiTest.enterFocus() {
        onNodeWithTag(TAG_V3_SHEET_BODY).performTouchInput {
            down(Offset(center.x, 40f))
            moveBy(Offset(0f, 140f))
            up()
        }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_FOCUS_PARAM_LABEL).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** An upward overscroll from the focused card restores the full card. */
    private fun ComposeUiTest.exitFocus() {
        onNodeWithTag(TAG_V3_SHEET_BODY).performTouchInput {
            down(Offset(center.x, 8f))
            moveBy(Offset(0f, -120f))
            up()
        }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_PARAM_EXPOSURE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun subtoolResetSitsInTheCropResetSlot() = runComposeUiTest {
        val s = phoneLightTool()
        openLight(s)
        val reset = onNodeWithTag(TAG_RESET_TOOL)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(40.dp)
        assertEquals(
            "Reset Exposure",
            reset.fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].joinToString()
        )
        // The card itself carries no reset.
        onNodeWithTag("editor.reset.exposure").assertDoesNotExist()
    }

    @Test
    fun draggingTheCardDownFocusesItAndDraggingUpRestoresTheFullCard() =
        runComposeUiTest {
        val s = phoneLightTool()
        openLight(s)
        // Dragging the card's body down (its scroller is at the top, so the
        // drag overscrolls) shrinks it to the single ruler.
        enterFocus()
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertIsDisplayed()
        onNodeWithTag(TAG_PARAM_EXPOSURE).assertDoesNotExist()
        // And an upward drag from the focused card restores it.
        exitFocus()
        onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
    }

    @Test
    fun focusKeepsOnlyTheActiveRulerAndItEditsThatParameter() = runComposeUiTest {
        val s = phoneLightTool()
        openLight(s)
        onNodeWithTag(TAG_PARAM_CONTRAST).performScrollTo().performClick()
        waitForIdle()
        enterFocus()
        // Focus is not a separate surface on a phone: the document header
        // and the six-slot dock stay, and only the card shrinks.
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        // The header still commits the open transaction; the card itself
        // carries no Apply row.
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_EDITOR_PINS).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertTextEquals("Contrast")
        onNodeWithTag(TAG_PARAM_EXPOSURE).assertDoesNotExist()
        onNodeWithTag(TAG_PARAM_CONTRAST).assertDoesNotExist()

        onNodeWithTag("editor.slider.contrast")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        waitUntil(timeoutMillis = 15_000) {
            val doc = s.state.value.document
            doc != null && doc.adjustments.contrast != 1f && !s.state.value.interactionActive
        }
        assertEquals(0f, s.state.value.document!!.adjustments.exposureStops)
    }

    @Test
    fun theCarouselTitleSwipesAndStepsBetweenSubTools() = runComposeUiTest {
        val s = phoneLightTool()
        openLight(s)
        enterFocus()
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertTextEquals("Exposure")

        // A left swipe on the title advances one sub-tool and re-targets
        // the ruler. The drag starts at the title, not at the card edge,
        // where the neighbouring sub-tool's hint button sits.
        onNodeWithTag(TAG_FOCUS_CAROUSEL).performTouchInput {
            down(center)
            moveBy(Offset(-240f, 0f))
            up()
        }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_SLIDER_CONTRAST).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertTextEquals("Contrast")

        // The resting edge hints step by one without a swipe.
        onNodeWithTag(TAG_FOCUS_PREV).performClick()
        waitForIdle()
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertTextEquals("Exposure")
        assertTrue(s.state.value.toolEditing)
    }

    @Test
    fun applyingAFocusedToolLandsOnTheDocumentHome() = runComposeUiTest {
        // Apply after a focused tool must return to the document home —
        // not the retired full-screen focus chrome (filename/Fit/Done).
        val s = phoneLightTool()
        openLight(s)
        enterFocus()
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        waitUntil(timeoutMillis = 15_000) {
            !s.state.value.toolEditing && !s.state.value.isDrafting
        }
        waitForIdle()
        onNodeWithTag(TAG_EDITOR_PINS).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_HEADER).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS_EXIT).assertDoesNotExist()
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertDoesNotExist()
    }

    @Test
    fun exitingFocusRestoresTheSheetOnTheSameParameterWithTheToolOpen() = runComposeUiTest {
        val s = phoneLightTool()
        openLight(s)
        onNodeWithTag(TAG_PARAM_CONTRAST).performScrollTo().performClick()
        waitForIdle()
        enterFocus()
        exitFocus()
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertDoesNotExist()
        onNodeWithTag(TAG_PARAM_CONTRAST).assertIsSelected()
        assertTrue(s.state.value.toolEditing)
    }

    @Test
    fun focusKeepsTheSplitCompare() = runComposeUiTest {
        val s = phoneLightTool()
        onAllNodesWithTag(TAG_BEFORE_CHIP).onFirst().performClick()
        openLight(s)
        enterFocus()
        onNodeWithTag(TAG_SPLIT_HANDLE).assertIsDisplayed()
    }

    @Test
    fun saverRoundTripsTheLightParameterAndDropsUnknownKeys() {
        val scope = SaverScope { true }
        val original = PhotoToolUiState().apply { lightParamKey = "contrast" }
        val saved = with(PhotoToolUiState.Saver) { scope.save(original) }!!
        assertEquals("contrast", PhotoToolUiState.Saver.restore(saved)!!.lightParamKey)

        @Suppress("UNCHECKED_CAST")
        val stale = (saved as List<Any>).toMutableList().also { it[9] = "removed" }
        assertEquals("exposure", PhotoToolUiState.Saver.restore(stale)!!.lightParamKey)
    }
}
