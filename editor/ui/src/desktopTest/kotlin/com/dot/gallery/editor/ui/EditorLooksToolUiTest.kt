package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoLook
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Looks tool end to end on a live session: the six real-preview
 * tiles (rendered through the shared finishing processor, never a
 * gradient), the shared Strength ruler that disables while Original is
 * selected, the one-history-step tile apply, the Back/Apply transaction
 * semantics and the Edits-pane row a non-neutral look lists.
 */
@OptIn(ExperimentalTestApi::class)
class EditorLooksToolUiTest {

    private var session: PhotoEditorSession? = null

    @AfterTest
    fun tearDown() {
        session?.close()
        session = null
    }

    private fun ComposeUiTest.liveSession(
        widthDp: Float = 412f,
        heightDp: Float = 832f,
        fontScale: Float = 1f
    ): PhotoEditorSession {
        val s = openedSession(samplePixels())
        session = s
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(LocalDensity.current.density, fontScale)
                ) {
                    Box(Modifier.size(widthDp.dp, heightDp.dp).fillMaxSize()) {
                        PhotoEditorScreen(
                            session = s,
                            onClose = {},
                            onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                        )
                    }
                }
            }
        }
        awaitImage(s)
        return s
    }

    private fun look(s: PhotoEditorSession) =
        s.state.value.document?.finishing?.look

    @Test
    fun looksIsPinnableAndCatalogued() {
        // Registry/pin contract: Looks sits after Grain before Crop and
        // survives a pin save.
        assertTrue(
            EditorToolIds.PhotoLooks.value in PHOTO_PINNABLE_DESTINATIONS
        )
        val workspace = EditorWorkspaceState(
            pinnedPhotoDestinations = emptyList()
        )
        workspace.togglePhotoPin(EditorToolIds.PhotoLooks.value)
        assertEquals(
            listOf(EditorToolIds.PhotoLooks.value),
            workspace.pinnedPhotoDestinations
        )
    }

    @Test
    fun theCatalogueCategorisesLargeTilesWithIntensityAboveThem() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // Small borderless tiles: no card, more per screen.
        val tile = onNodeWithTag("editor.look.none").getBoundsInRoot()
        assertTrue(
            tile.right - tile.left <= 92.dp,
            "the look tile is still card-sized: ${tile.right - tile.left}"
        )
        // The intensity is above the filters, bare (no title), and the
        // catalogue chips sit under them at the card's bottom.
        val sliderTop = onNodeWithTag("editor.slider.lookStrength")
            .getBoundsInRoot().top
        assertTrue(
            sliderTop < tile.top,
            "the strength ruler $sliderTop sits below the tiles ${tile.top}"
        )
        val chipsTop = onNodeWithTag("editor.look.category.all")
            .getBoundsInRoot().top
        assertTrue(
            chipsTop > tile.bottom,
            "the chips $chipsTop sit above the tiles ${tile.bottom}"
        )
        assertTrue(
            onAllNodesWithText("Strength").fetchSemanticsNodes().isEmpty(),
            "the strength title is gone"
        )

        // Cinematic narrows the catalogue to Original + the cinema looks.
        onNodeWithTag("editor.look.category.cinematic")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("editor.look.none").assertIsDisplayed()
        onNodeWithTag("editor.look.cinematic").assertIsDisplayed()
        onNodeWithTag("editor.look.tealOrange").assertIsDisplayed()
        onNodeWithTag("editor.look.clean").assertDoesNotExist()

        // B&W keeps Original + Mono + Noir.
        onNodeWithTag("editor.look.category.mono")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("editor.look.mono").assertIsDisplayed()
        onNodeWithTag("editor.look.noir").assertIsDisplayed()
        onNodeWithTag("editor.look.cool").assertDoesNotExist()

        // Film holds the film-stock looks.
        onNodeWithTag("editor.look.category.film")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("editor.look.vintage").assertIsDisplayed()
        onNodeWithTag("editor.look.sepia").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun userPresetsCaptureApplyRenameAndDelete() = runDesktopComposeUiTest(
        width = 412, height = 832
    ) {
        val s = liveSession()
        // A distinctive grade to capture: +1 EV from the Light tool.
        runOnUiThread {
            s.applyEdits(
                "Grade",
                PhotoEdits(color = ColorAdjustments(exposureStops = 1f))
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.adjustments?.exposureStops == 1f
        }
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag("editor.look.cinematic").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            look(s)?.id == PhotoLookId.Cinematic
        }
        onNodeWithTag("editor.slider.lookStrength").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(60f) }
        waitUntil(timeoutMillis = 15_000) { look(s)?.amount == 0.6f }

        // Save the current grade as a preset.
        onNodeWithTag("editor.look.category.mine")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TAG_LOOK_NEW_PRESET).performClick()
        onNodeWithTag(TAG_LOOK_PRESET_NAME_FIELD)
            .performTextReplacement("Nostalgia")
        onNodeWithTag(TAG_LOOK_PRESET_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithText("Nostalgia").fetchSemanticsNodes().isNotEmpty()
        }

        // Leave the preset's grade, then apply it back.
        onNodeWithTag("editor.look.category.all")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("editor.look.mono").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { look(s)?.id == PhotoLookId.Mono }
        onNodeWithTag("editor.look.category.mine")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Nostalgia").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            val doc = s.state.value.document
            doc?.adjustments?.exposureStops == 1f &&
                doc.finishing.look == PhotoLook(PhotoLookId.Cinematic, 0.6f)
        }

        // Rename through the tile's overflow.
        onNodeWithContentDescription("Preset options for Nostalgia")
            .performClick()
        onNodeWithTag(TAG_LOOK_PRESET_RENAME).performClick()
        onNodeWithTag(TAG_LOOK_PRESET_NAME_FIELD)
            .performTextReplacement("Faded")
        onNodeWithTag(TAG_LOOK_PRESET_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithText("Faded").fetchSemanticsNodes().isNotEmpty()
        }

        // Delete it through the same overflow.
        onNodeWithContentDescription("Preset options for Faded")
            .performClick()
        onNodeWithTag(TAG_LOOK_PRESET_DELETE).performClick()
        waitForIdle()
        onNodeWithTag(TAG_LOOK_PRESET_DELETE_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithText("Faded").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun anEmptyGradeCannotBeSavedAndSaysWhy() = runDesktopComposeUiTest(
        width = 412, height = 832
    ) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag("editor.look.category.mine")
            .performScrollTo().performClick()
        waitForIdle()
        // Nothing adjusted yet: the create tile is inert and the card
        // explains the requirement.
        onNodeWithTag(TAG_LOOK_NEW_PRESET).assertIsNotEnabled()
        onNodeWithTag(TAG_LOOK_PRESET_EMPTY_HINT).assertIsDisplayed()
        onNodeWithTag(TAG_LOOK_NEW_PRESET).performClick()
        onNodeWithTag(TAG_LOOK_PRESET_NAME_FIELD).assertDoesNotExist()
    }

    @Test
    fun allListsMyPresetsRightAfterOriginal() = runDesktopComposeUiTest(
        width = 412, height = 832
    ) {
        val s = liveSession()
        runOnUiThread {
            s.applyEdits(
                "Grade",
                PhotoEdits(color = ColorAdjustments(exposureStops = 1f))
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.adjustments?.exposureStops == 1f
        }
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag("editor.look.category.mine")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TAG_LOOK_NEW_PRESET).performClick()
        onNodeWithTag(TAG_LOOK_PRESET_NAME_FIELD).performTextReplacement("Alpha")
        onNodeWithTag(TAG_LOOK_PRESET_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("editor.look.category.all")
            .performScrollTo().performClick()
        waitForIdle()
        val original = onNodeWithTag("editor.look.none").getBoundsInRoot()
        val preset = onNodeWithText("Alpha").getBoundsInRoot()
        assertTrue(
            preset.left > original.left,
            "the preset sits after Original: $original -> $preset"
        )
        assertTrue(
            preset.left < onNodeWithTag("editor.look.clean")
                .getBoundsInRoot().left,
            "the preset precedes the built-ins"
        )
    }

    @Test
    fun aPresetCanBeUpdatedWithTheCurrentGrade() = runDesktopComposeUiTest(
        width = 412, height = 832
    ) {
        val s = liveSession()
        runOnUiThread {
            s.applyEdits(
                "Grade",
                PhotoEdits(color = ColorAdjustments(exposureStops = 1f))
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.adjustments?.exposureStops == 1f
        }
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag("editor.look.category.mine")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TAG_LOOK_NEW_PRESET).performClick()
        onNodeWithTag(TAG_LOOK_PRESET_NAME_FIELD).performTextReplacement("Alpha")
        onNodeWithTag(TAG_LOOK_PRESET_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty()
        }

        // Change the grade, then re-capture it into the preset.
        onNodeWithTag("editor.look.category.all")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("editor.look.cinematic").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            look(s)?.id == PhotoLookId.Cinematic
        }
        onNodeWithTag("editor.slider.lookStrength")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                setProgress -> setProgress(40f)
            }
        waitUntil(timeoutMillis = 15_000) { look(s)?.amount == 0.4f }

        onNodeWithTag("editor.look.category.mine")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithContentDescription("Preset options for Alpha").performClick()
        onNodeWithTag(TAG_LOOK_PRESET_UPDATE).performClick()
        onNodeWithTag(TAG_LOOK_PRESET_CONFIRM).performClick()
        waitForIdle()

        // Leave that grade, then apply the preset: it now carries the
        // updated look and blend.
        onNodeWithTag("editor.look.category.all")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("editor.look.mono").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { look(s)?.id == PhotoLookId.Mono }
        onNodeWithTag("editor.look.category.mine")
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Alpha").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            val doc = s.state.value.document
            doc?.adjustments?.exposureStops == 1f &&
                doc.finishing.look == PhotoLook(PhotoLookId.Cinematic, 0.4f)
        }
    }

    @Test
    fun looksTilesRenderRealPreviewsAndOriginalDisablesStrength() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // All six presets paint; Original matches the untouched recipe.
        for (key in listOf(
            "none", "clean", "warmFade", "cool", "mono", "cinematic"
        )) {
            onNodeWithTag("editor.look.$key")
                .performScrollTo()
                .assertIsDisplayed()
        }
        onNodeWithTag("editor.look.none").assertIsSelected()

        // The thumbnails are decoded processor output, not placeholders —
        // every tile eventually exposes its Image node. The tile's
        // clickable merges descendants, so the thumbnail tag only exists
        // in the unmerged tree.
        waitUntil(timeoutMillis = 15_000) {
            listOf(
                "none", "clean", "warmFade", "cool", "mono", "cinematic"
            ).all { key ->
                onAllNodesWithTag(
                    "editor.look.thumb.$key", useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }

        // Original selected — the Strength ruler reports a range but is
        // disabled: a blend of nothing changes nothing.
        onNodeWithTag("editor.slider.lookStrength").assertIsNotEnabled()
        onNodeWithTag("editor.slider.lookStrength").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(40f) }
        waitForIdle()
        assertEquals(PhotoLook(), look(s))
    }

    @Test
    fun lookSelectionAndStrengthRideOneApplyStep() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // Cinematic through the tile — one applyEdits inside the tool
        // transaction; the ruler unlocks once a real look is selected.
        // The strip shows ~3.4 tiles — later presets scroll in first.
        onNodeWithTag("editor.look.cinematic")
            .performScrollTo()
            .performClick()
        waitUntil(timeoutMillis = 15_000) {
            look(s)?.id == PhotoLookId.Cinematic
        }
        onNodeWithTag("editor.look.cinematic").assertIsSelected()
        onNodeWithTag("editor.look.none").assertIsNotSelected()
        onNodeWithTag("editor.slider.lookStrength").assertIsEnabled()

        onNodeWithTag("editor.slider.lookStrength")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                setProgress -> setProgress(60f)
            }
        waitUntil(timeoutMillis = 15_000) {
            look(s)?.amount == 0.6f
        }
        onNodeWithTag("editor.value.lookStrength")
            .assertIsDisplayed()
            .assertTextHas("60")

        // A second tile keeps the blend — the strength survives the
        // preset swap.
        onNodeWithTag("editor.look.warmFade")
            .performScrollTo()
            .performClick()
        waitUntil(timeoutMillis = 15_000) {
            look(s)?.id == PhotoLookId.WarmFade
        }
        assertEquals(0.6f, look(s)?.amount)

        // Top Apply lands the whole transaction as one document step —
        // a single undo restores the neutral recipe.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        waitUntil(timeoutMillis = 15_000) {
            !s.state.value.toolEditing && s.state.value.canUndo
        }
        runOnUiThread { s.undo() }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.finishing?.isNeutral == true &&
                !s.state.value.canUndo
        }
    }

    @Test
    fun backCancelsTheLookTransaction() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag("editor.look.mono")
            .performScrollTo()
            .performClick()
        waitUntil(timeoutMillis = 15_000) {
            look(s)?.id == PhotoLookId.Mono
        }
        onNodeWithTag(TAG_BACK).performClick()
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
        assertEquals(PhotoLook(), look(s))
    }

    @Test
    fun editsPaneListsTheNonNeutralLook() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        runOnUiThread {
            s.applyEdits(
                "Look",
                PhotoEdits(
                    finishing = PhotoFinishing(
                        look = PhotoLook(PhotoLookId.WarmFade, 0.7f)
                    )
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            look(s)?.id == PhotoLookId.WarmFade
        }

        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        openPhonePane(TAG_PANEL_EDITS)

        onNodeWithText("Looks · Warm fade").assertIsDisplayed()
        onNodeWithText("70.00%").assertIsDisplayed()
    }

    @Test
    fun looksSurfaceStaysReachableAt2x() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(fontScale = 2f)
        openPhotoTool(TAG_CATEGORY_LOOKS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // The last tile scrolls into view and the Strength ruler stays
        // composed at doubled type.
        onNodeWithTag("editor.look.cinematic")
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag("editor.slider.lookStrength").assertIsDisplayed()
    }
}
