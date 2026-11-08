package com.dot.gallery.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.dot.gallery.editor.core.model.PhotoHslBand
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Colour tool's modes drive the real recipe on a real session: each
 * control writes its own field, one gesture is one undoable edit, and the
 * mode's reset clears that mode alone.
 */
@OptIn(ExperimentalTestApi::class)
class ColourToolUiTest {

    private fun colourTest(
        width: Int = 412,
        height: Int = 832,
        body: androidx.compose.ui.test.ComposeUiTest.(PhotoEditorSession) -> Unit
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val session = openedSession(samplePixels())
        try {
            setContent {
                TestEditorRoot {
                    PhotoEditorScreen(
                        session, onClose = {}, onRetryOpen = {},
                        renderPreference = PhotoRenderPreference.Cpu
                    )
                }
            }
            awaitImage(session)
            openPhotoTool(TAG_CATEGORY_COLOUR)
            body(session)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    private fun grade(session: PhotoEditorSession) =
        session.state.value.document!!.finishing.grade

    // Regression: a measured card growing past the previous peek crashed the
    // bottom sheet scaffold, which read its offset before it was initialized.
    @Test
    fun switchingToolsAndModesSurvivesTheCardGrowing() = colourTest { session ->
        onNodeWithTag(TAG_BACK).performClick()
        waitUntil(timeoutMillis = 15_000) { !session.state.value.toolEditing }
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoColour.value)).performClick()
        waitForIdle()
        for (mode in ColourMode.entries) {
            onNodeWithTag("editor.colour.mode.${mode.key}").performClick()
            waitForIdle()
            onNodeWithTag("editor.colour.mode.${mode.key}").assertIsDisplayed()
        }
    }

    @Test
    fun thePhoneResetClearsTheOpenModeAlone() = colourTest { session ->
        onNodeWithTag("editor.slider.saturation").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { it(2f) }
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.document?.adjustments?.saturation == 2f
        }
        onNodeWithTag("editor.colour.mode.whiteBalance").performClick()
        onNodeWithTag("editor.wb.preset.tungsten").performClick()
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.document!!.finishing.whiteBalance.temperatureKelvin == 3200f
        }
        onNodeWithTag(TAG_RESET_TOOL).performClick()
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.document!!.finishing.whiteBalance.isNeutral
        }
        assertEquals(2f, session.state.value.document?.adjustments?.saturation)
    }

    @Test
    fun basicsOpensFirstAndProtectSkinWaitsForVibrance() = colourTest { session ->
        onNodeWithTag(TAG_SLIDER_SATURATION).assertIsDisplayed()
        onNodeWithTag(TAG_COLOUR_PROTECT_SKIN).assertIsNotEnabled()
        onNodeWithTag("editor.param.vibrance").performClick()
        onNodeWithTag("editor.slider.vibrance").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { it(40f) }
        waitUntil(timeoutMillis = 15_000) { grade(session).vibrance == 0.4f }
        onNodeWithTag(TAG_COLOUR_PROTECT_SKIN).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 15_000) { grade(session).protectSkin }
    }

    @Test
    fun hslEditsTheSelectedBandAndItsResetLeavesTheOthers() = colourTest { session ->
        onNodeWithTag("editor.colour.mode.hsl").performClick()
        onNodeWithTag("editor.slider.hsl.red.saturation").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { it(30f) }
        waitUntil(timeoutMillis = 15_000) {
            grade(session).adjustment(PhotoHslBand.Red).saturation == 0.3f
        }
        onNodeWithTag("editor.colour.band.blue").performClick()
        onNodeWithTag("editor.param.hsl.blue.luminance").performClick()
        onNodeWithTag("editor.slider.hsl.blue.luminance").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { it(-50f) }
        waitUntil(timeoutMillis = 15_000) {
            grade(session).adjustment(PhotoHslBand.Blue).luminance == -0.5f
        }
        onNodeWithTag(TAG_COLOUR_RESET_BAND).performClick()
        waitUntil(timeoutMillis = 15_000) {
            grade(session).adjustment(PhotoHslBand.Blue).isNeutral
        }
        assertEquals(0.3f, grade(session).adjustment(PhotoHslBand.Red).saturation)
    }

    @Test
    fun oneWheelDragIsOneUndoableEdit() = colourTest { session ->
        onNodeWithTag("editor.colour.mode.wheels").performClick()
        waitForIdle()
        onNodeWithTag("editor.colour.wheel.highlights").performTouchInput {
            down(center)
            moveTo(Offset(centerX, centerY - height / 4f))
            moveTo(Offset(centerX + width / 2f, centerY))
            up()
        }
        waitUntil(timeoutMillis = 15_000) {
            grade(session).highlights.amount > 0.9f
        }
        // Dragged last to the wheel's right edge: a quarter turn from red.
        assertEquals(90f, grade(session).highlights.hueDegrees)
        assertEquals(0f, grade(session).shadows.amount)
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        waitUntil(timeoutMillis = 15_000) { session.state.value.canUndo }
        onNodeWithTag(TAG_UNDO).performClick()
        waitUntil(timeoutMillis = 15_000) { grade(session).wheelsAreNeutral }
    }

    @Test
    fun whiteBalanceModeWritesTheSameRecipeAsItsTool() = colourTest { session ->
        onNodeWithTag("editor.colour.mode.whiteBalance").performClick()
        onNodeWithTag("editor.wb.preset.tungsten").performClick()
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.document!!.finishing.whiteBalance.temperatureKelvin == 3200f
        }
        onNodeWithTag(TAG_WB_PICK).performClick()
        waitForIdle()
        onNodeWithTag(TAG_WB_PICK).assertIsDisplayed()
    }

    @Test
    fun aWideSheetSetsTwoRulersSideBySideAndKeepsChipsForThree() = colourTest(width = 800, height = 1280) { _ ->
        onNodeWithTag("editor.colour.mode.whiteBalance").performClick()
        onNodeWithTag("editor.slider.temperature").assertIsDisplayed()
        onNodeWithTag("editor.slider.tint").assertIsDisplayed()
        onNodeWithTag("editor.colour.mode.hsl").performClick()
        onNodeWithTag("editor.slider.hsl.red.saturation").assertIsDisplayed()
        onNodeWithTag("editor.slider.hsl.red.hue").assertDoesNotExist()
        onNodeWithTag("editor.param.hsl.red.hue").assertIsDisplayed()
    }

    @Test
    fun theInspectorStacksEveryRulerAndOwnsItsReset() = colourTest(width = 1280, height = 1024) { session ->
        onNodeWithTag("editor.colour.mode.whiteBalance").performClick()
        onNodeWithTag("editor.slider.temperature").assertIsDisplayed()
        onNodeWithTag("editor.slider.tint").assertIsDisplayed()
        onNodeWithTag("editor.wb.preset.daylight").performClick()
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.document!!.finishing.whiteBalance.temperatureKelvin == 5500f
        }
        onNodeWithTag("editor.reset.colour.whiteBalance").performClick()
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.document!!.finishing.whiteBalance.isNeutral
        }
        assertTrue(session.state.value.document!!.finishing.grade.isNeutral)
    }
}
