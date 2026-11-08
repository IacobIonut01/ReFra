package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The opened histogram: the pill body opens it in the pill's place, every
 * control is a labelled 48 dp target, the channel selector is shared with
 * the pill, and the clip chips and channel cards print the exact figures
 * computed from the displayed preview.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorHistogramPanelUiTest {

    private var session: PhotoEditorSession? = null

    @AfterTest
    fun tearDown() {
        session?.close()
        session = null
    }

    private fun ComposeUiTest.phoneEditor(): PhotoEditorSession {
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

    private fun ComposeUiTest.openPanel() {
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL_OPEN).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_PHOTO_HISTOGRAM_PANEL).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.description(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()

    @Test
    fun thePillsBodyAndChannelMenuAreSeparateLabelledTargets() = runComposeUiTest {
        phoneEditor()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL_OPEN)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        assertEquals("Open histogram", description(TAG_PHOTO_HISTOGRAM_PILL_OPEN))
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL_MODE)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        assertEquals("Histogram mode", description(TAG_PHOTO_HISTOGRAM_PILL_MODE))
    }

    @Test
    fun thePillOpensThePanelInItsPlaceAndCloseRestoresIt() = runComposeUiTest {
        phoneEditor()
        openPanel()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PANEL_CLOSE)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        assertEquals("Close histogram", description(TAG_PHOTO_HISTOGRAM_PANEL_CLOSE))
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PANEL_CLOSE).performClick()
        waitForIdle()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PANEL).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL).assertIsDisplayed()
    }

    @Test
    fun escapeUnwindsThePanelBeforeTheOpenTool() = runComposeUiTest {
        val s = phoneEditor()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        openPanel()
        onNodeWithTag(TAG_CANVAS).requestFocus()
        onNodeWithTag(TAG_CANVAS).performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        waitForIdle()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PANEL).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL).assertIsDisplayed()
        assertTrue(s.state.value.toolEditing)
    }

    @Test
    fun theChannelSelectorIsSharedWithThePill() = runComposeUiTest {
        phoneEditor()
        openPanel()
        PhotoHistogramMode.entries.forEach { mode ->
            onNodeWithTag(photoHistogramPanelModeTag(mode)).assertHeightIsAtLeast(48.dp)
        }
        onNodeWithTag(photoHistogramPanelModeTag(PhotoHistogramMode.Rgb)).assertIsSelected()
        onNodeWithTag(photoHistogramPanelModeTag(PhotoHistogramMode.Green)).performClick()
        onNodeWithTag(photoHistogramPanelModeTag(PhotoHistogramMode.Green)).assertIsSelected()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PANEL_CLOSE).performClick()
        waitForIdle()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL_OPEN).assertTextContains("G")
    }

    @Test
    fun clipChipsAndCardsPrintTheExactPreviewFigures() = runComposeUiTest {
        val s = phoneEditor()
        openPanel()
        val preview = s.state.value.preview!!
        val histogram = calculatePhotoHistogram(preview)
        val stats = histogram.stats
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_PHOTO_HISTOGRAM_SHADOWS_CLIP).fetchSemanticsNodes().isNotEmpty()
        }
        val (low, high) = stats.clipping(PhotoHistogramMode.Rgb)
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_SHADOWS_CLIP)
            .assertTextContains("Shadows ${formatClipShare(low, histogram.alphaWeight)} clipped")
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_HIGHLIGHTS_CLIP)
            .assertTextContains("Highlights ${formatClipShare(high, histogram.alphaWeight)} clipped")

        onNodeWithTag(photoHistogramPanelModeTag(PhotoHistogramMode.Red)).performClick()
        waitForIdle()
        val (redLow, _) = stats.clipping(PhotoHistogramMode.Red)
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_SHADOWS_CLIP)
            .assertTextContains("Shadows ${formatClipShare(redLow, histogram.alphaWeight)} clipped")

        assertEquals(
            "Red: mean ${stats.red.mean.roundToInt()}, median ${stats.red.median}",
            description(photoHistogramStatTag(PhotoHistogramMode.Red))
        )
        assertEquals(
            "Luma: mean ${stats.luma.mean.roundToInt()}, median ${stats.luma.median}",
            description(photoHistogramStatTag(PhotoHistogramMode.Luma))
        )
    }

    @Test
    fun theOpenedPanelReadsTheCurrentRecipeNotAStaleShaderPreview() = runComposeUiTest {
        val s = phoneEditor()
        val originalMean = calculatePhotoHistogram(s.state.value.preview!!).stats.luma.mean
        runOnUiThread {
            s.applyEdits(
                "Brighten",
                PhotoEdits(color = ColorAdjustments(exposureStops = 1f))
            )
        }
        waitUntil(timeoutMillis = 15_000) { s.state.value.canUndo && !s.state.value.isDrafting }
        openPanel()
        // With the panel open the CPU preview renders the current recipe,
        // so the figures follow the edit rather than the decoded source.
        waitUntil(timeoutMillis = 15_000) {
            val st = s.state.value
            st.previewDocument == st.document && st.preview != null &&
                calculatePhotoHistogram(st.preview!!).stats.luma.mean > originalMean + 5f
        }
        val stats = calculatePhotoHistogram(s.state.value.preview!!).stats
        waitUntil(timeoutMillis = 15_000) {
            description(photoHistogramStatTag(PhotoHistogramMode.Luma)) ==
                "Luma: mean ${stats.luma.mean.roundToInt()}, median ${stats.luma.median}"
        }
    }

    @Test
    fun histogramsShowNoDataWhileThePreviewHoldsAnOlderRecipe() {
        val base = fixtureState()
        val doc = base.document!!
        val edited = doc.copy(adjustments = doc.adjustments.copy(exposureStops = 1f))
        val current = base.copy(previewDocument = doc)
        assertTrue(currentPreviewPixels(current) === current.preview)
        // The shader drew a newer recipe while the CPU preview slept.
        val stale = current.copy(document = edited)
        assertEquals(null, currentPreviewPixels(stale))
        // A live gesture keeps the trailing render on screen.
        assertTrue(currentPreviewPixels(stale.copy(interactionActive = true)) === stale.preview)
        // Crop preview mode renders the display document, not the recipe.
        val cropDisplay = stale.copy(previewDocument = doc, displayDocument = doc)
        assertTrue(currentPreviewPixels(cropDisplay) === cropDisplay.preview)
    }

    @Test
    fun theLightToolOpensThePanelOverItsCanvas() = runComposeUiTest {
        val s = phoneEditor()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        openPanel()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PANEL_CHART).assertIsDisplayed()
        onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
    }
}
