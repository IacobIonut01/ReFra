package com.dot.gallery.editor.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoVignette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The shared edit summaries: [photoToolEditCount] and the composable
 * [photoToolEditSummary]/[photoEditsStepSummary] strings the Studio
 * sections and History rows read.
 */
@OptIn(ExperimentalTestApi::class)
class PhotoToolEditSummaryTest {

    private fun edits(
        exposure: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f,
        shadows: Float = 0f,
        vignetteAmount: Float = 0f,
        quarterTurns: Int = 0,
        crop: NormalizedCrop = NormalizedCrop()
    ) = PhotoEdits(
        color = ColorAdjustments(
            exposureStops = exposure,
            contrast = contrast,
            saturation = saturation,
            shadows = shadows
        ),
        finishing = PhotoFinishing(
            vignette = PhotoVignette(amount = vignetteAmount)
        ),
        geometry = PhotoGeometry(
            crop = crop,
            quarterTurns = quarterTurns
        )
    )

    // ── Counts ──────────────────────────────────────────────────────────

    @Test
    fun countIsZeroOnTheDefaultRecipe() {
        val edits = PhotoEdits()
        listOf(
            EditorToolIds.PhotoLight,
            EditorToolIds.PhotoColour,
            EditorToolIds.PhotoVignette,
            EditorToolIds.PhotoCrop
        ).forEach { tool ->
            assertEquals(0, photoToolEditCount(tool, edits), "$tool")
        }
    }

    @Test
    fun countCoversOnlyTheToolsOwnParameters() {
        val edits = edits(exposure = 1f, contrast = 1.44f, saturation = 1.2f)
        // Exposure and contrast are Light's; saturation is Colour's.
        assertEquals(2, photoToolEditCount(EditorToolIds.PhotoLight, edits))
        assertEquals(1, photoToolEditCount(EditorToolIds.PhotoColour, edits))
        assertEquals(0, photoToolEditCount(EditorToolIds.PhotoVignette, edits))
        assertEquals(0, photoToolEditCount(EditorToolIds.PhotoCrop, edits))
    }

    @Test
    fun countReadsVignetteAndCropParameters() {
        val edits = edits(vignetteAmount = 0.4f, quarterTurns = 1)
        assertEquals(1, photoToolEditCount(EditorToolIds.PhotoVignette, edits))
        assertEquals(1, photoToolEditCount(EditorToolIds.PhotoCrop, edits))
    }

    // ── Collapsed-section summaries ─────────────────────────────────────

    private fun androidx.compose.ui.test.ComposeUiTest.showToolSummary(
        tool: EditorToolId,
        edits: PhotoEdits
    ) {
        setContent {
            TestEditorRoot {
                Text(photoToolEditSummary(tool, edits) ?: "∅")
            }
        }
        waitForIdle()
    }

    @Test
    fun colourSummaryReadsSaturationPlusTen() =
        runDesktopComposeUiTest(width = 400, height = 300) {
            showToolSummary(
                EditorToolIds.PhotoColour,
                edits(saturation = 1.1f)
            )
            onNodeWithText("Saturation +10").assertIsDisplayed()
        }

    @Test
    fun lightSummaryNamesTwoEditsThenCountsTheRest() =
        runDesktopComposeUiTest(width = 400, height = 300) {
            showToolSummary(
                EditorToolIds.PhotoLight,
                edits(exposure = 1f, contrast = 1.44f, shadows = 0.2f)
            )
            // Exposure +1 EV · Contrast +20 · 1 more — Shadows is third.
            onNodeWithText("Exposure +1 EV · Contrast +20 · 1 more")
                .assertIsDisplayed()
        }

    @Test
    fun vignetteAndCropSummariesUseTheirOwnLabels() =
        runDesktopComposeUiTest(width = 400, height = 300) {
            showToolSummary(
                EditorToolIds.PhotoVignette,
                edits(vignetteAmount = 0.4f)
            )
            onNodeWithText("Amount +40").assertIsDisplayed()
        }

    @Test
    fun emptySummaryRendersNothing() =
        runDesktopComposeUiTest(width = 400, height = 300) {
            showToolSummary(EditorToolIds.PhotoLight, PhotoEdits())
            onNodeWithText("∅").assertIsDisplayed()
        }

    // ── History step subtitles ──────────────────────────────────────────

    private fun androidx.compose.ui.test.ComposeUiTest.showStepSummary(
        before: PhotoEdits,
        after: PhotoEdits
    ) {
        setContent {
            TestEditorRoot {
                Text(photoEditsStepSummary(before, after) ?: "∅")
            }
        }
        waitForIdle()
    }

    @Test
    fun stepSummaryDescribesTheFieldsThatChanged() =
        runDesktopComposeUiTest(width = 400, height = 300) {
            showStepSummary(
                PhotoEdits(),
                edits(exposure = 1f, saturation = 1.1f)
            )
            onNodeWithText("Exposure +1 EV · Saturation +10").assertIsDisplayed()
        }

    @Test
    fun identicalRecipesHaveNoStepSummary() {
        var summary: String? = "unset"
        runDesktopComposeUiTest(width = 400, height = 300) {
            setContent {
                TestEditorRoot {
                    summary = photoEditsStepSummary(PhotoEdits(), PhotoEdits())
                    Text(summary ?: "∅")
                }
            }
            waitForIdle()
        }
        assertNull(summary)
    }
}
