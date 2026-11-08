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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Details and Lens blur tools end to end on a live session: the
 * tabbed Details sheet with its shared ruler and settings rows, the
 * Radial/Linear mode chips, the canvas overlay (48 dp centre target,
 * rings vs line, a one-transaction centre drag), the Edits-pane rows a
 * non-neutral spatial stage lists, and the lens-blur -> CPU-preview
 * gating contract.
 */
@OptIn(ExperimentalTestApi::class)
class EditorSpatialToolsUiTest {

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

    private fun spatial(s: PhotoEditorSession) =
        s.state.value.document?.spatial

    /** Drags the adjustment sheet from its 220 dp peek to Expanded —
     * below-fold settings rows are only on-screen once it is open. */
    private fun ComposeUiTest.expandToolSheet() {
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, -400f))
                up()
            }
        waitForIdle()
    }

    // ── Details ───────────────────────────────────────────────────────

    @Test
    fun detailsTabsDriveTheSharedRulerAndRowsReflectEveryField() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_DETAILS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        expandToolSheet()

        // Four tabs, Texture selected by default; four settings rows
        // mirror the whole recipe — the sheet peeks, so rows past the
        // fold scroll into view first.
        for (key in listOf(
            "detailTexture", "detailClarity",
            "detailSharpening", "detailNoiseReduction"
        )) {
            onNodeWithTag("editor.details.tab.$key").assertIsDisplayed()
            onNodeWithTag("editor.details.row.$key")
                .performScrollTo()
                .assertIsDisplayed()
        }
        onNodeWithTag("editor.details.tab.detailTexture")
            .assertIsSelected()

        // -30 on the Texture ruler lands texture -.3.
        onNodeWithTag("editor.slider.detailTexture")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(-30f)
            }
        waitUntil(timeoutMillis = 15_000) {
            spatial(s)?.details?.texture == -0.3f
        }
        onNodeWithTag("editor.details.row.detailTexture")
            .assertTextHas("-30")

        // A row tap selects the tab and re-points the shared ruler.
        onNodeWithTag("editor.details.row.detailNoiseReduction")
            .performScrollTo()
            .performClick()
        onNodeWithTag("editor.details.tab.detailNoiseReduction")
            .assertIsSelected()
        onNodeWithTag("editor.slider.detailNoiseReduction")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(40f)
            }
        waitUntil(timeoutMillis = 15_000) {
            spatial(s)?.details?.noiseReduction == 0.4f
        }
    }

    @Test
    fun backCancelsTheWholeDetailsTransaction() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_DETAILS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag("editor.slider.detailTexture")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(50f)
            }
        waitUntil(timeoutMillis = 15_000) {
            spatial(s)?.details?.texture == 0.5f
        }

        onNodeWithTag(TAG_BACK).performClick()
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
        assertEquals(true, spatial(s)?.isNeutral)
    }

    // ── Lens blur sheet ───────────────────────────────────────────────

    @Test
    fun lensBlurModeChipsAndRowsReflectTheRecipe() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LENS_BLUR)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        expandToolSheet()

        onNodeWithTag("editor.lensBlur.mode.radial")
            .assertIsDisplayed()
            .assertIsSelected()
        onNodeWithTag("editor.lensBlur.mode.linear").assertIsDisplayed()
        // Radial lists three rows — Angle is a Linear-only field.
        for (key in listOf(
            "lensBlurStrength", "lensBlurTransition",
            "lensBlurHighlightBokeh"
        )) {
            onNodeWithTag("editor.lensBlur.row.$key")
                .performScrollTo()
                .assertIsDisplayed()
        }
        onNodeWithTag("editor.lensBlur.row.lensBlurAngle")
            .assertDoesNotExist()

        onNodeWithTag("editor.slider.lensBlurStrength")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(65f)
            }
        waitUntil(timeoutMillis = 15_000) {
            spatial(s)?.lensBlur?.strength == 0.65f
        }
        onNodeWithTag("editor.lensBlur.row.lensBlurStrength")
            .assertTextHas("65")

        // Switching bands is a transactional applyEdits — Linear gains
        // the Angle row and keeps the shared ones.
        onNodeWithTag("editor.lensBlur.mode.linear").performClick()
        waitUntil(timeoutMillis = 15_000) {
            spatial(s)?.lensBlur?.mode == LensBlurMode.Linear
        }
        onNodeWithTag("editor.lensBlur.row.lensBlurAngle")
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag("editor.lensBlur.row.lensBlurAngle")
            .performClick()
        onNodeWithTag("editor.slider.lensBlurAngle")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(-30f)
            }
        waitUntil(timeoutMillis = 15_000) {
            spatial(s)?.lensBlur?.angleDegrees == -30f
        }
    }

    // ── Canvas overlay ────────────────────────────────────────────────

    @Test
    fun lensBlurOverlayShowsRingsForRadialAndALineForLinear() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LENS_BLUR)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag(TAG_LENS_BLUR_OVERLAY).assertIsDisplayed()
        onNodeWithTag(TAG_LENS_BLUR_RINGS).assertIsDisplayed()
        onNodeWithTag(TAG_LENS_BLUR_CENTER).assertIsDisplayed()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)

        onNodeWithTag("editor.lensBlur.mode.linear").performClick()
        waitUntil(timeoutMillis = 15_000) {
            spatial(s)?.lensBlur?.mode == LensBlurMode.Linear
        }
        onNodeWithTag(TAG_LENS_BLUR_LINE).assertIsDisplayed()
        onNodeWithTag(TAG_LENS_BLUR_RINGS).assertDoesNotExist()
    }

    @Test
    fun lensBlurCentreDragCommitsAsOneUndoStep() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LENS_BLUR)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag(TAG_LENS_BLUR_CENTER).performTouchInput {
            down(center)
            moveBy(Offset(-60f, -30f))
            up()
        }
        waitUntil(timeoutMillis = 15_000) {
            val blur = spatial(s)?.lensBlur ?: return@waitUntil false
            blur.centerX != 0.5f && !s.state.value.interactionActive
        }

        // Top Apply lands the whole transaction as one document step:
        // a single undo restores the default focus point.
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        waitUntil(timeoutMillis = 15_000) {
            !s.state.value.toolEditing && s.state.value.canUndo
        }
        runOnUiThread { s.undo() }
        waitUntil(timeoutMillis = 15_000) {
            spatial(s)?.isNeutral == true && !s.state.value.canUndo
        }
    }

    // ── Shader gating ─────────────────────────────────────────────────

    /**
     * The same contract grain proved: lens blur has no honest runtime
     * shader form — the canvas hands off to the CPU preview while the
     * recipe is non-neutral and the shader draws nothing, then resumes
     * once the recipe returns to neutral. Details alone must NOT gate:
     * it is a shader-supported field.
     */
    @Test
    fun activeLensBlurHandsTheCanvasToTheCpuPreview() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val draws = java.util.concurrent.atomic.AtomicInteger()
        photoShaderRendererFactory = { _, _ ->
            object : PhotoShaderRenderer {
                override fun draw(
                    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
                    edits: PhotoEdits
                ): Boolean {
                    draws.incrementAndGet()
                    return true
                }

                override fun close() {}
            }
        }
        try {
            val s = liveSession()
            waitUntil(timeoutMillis = 15_000) { draws.get() > 0 }

            // Details keeps the shader drawing — supported in-shader.
            runOnUiThread {
                s.applyEdits(
                    "Details",
                    PhotoEdits(
                        spatial = PhotoSpatialEffects(
                            details = PhotoDetails(sharpening = 0.5f)
                        )
                    )
                )
            }
            waitUntil(timeoutMillis = 15_000) {
                s.state.value.document?.spatial?.details?.sharpening ==
                    0.5f
            }
            val detailsDraws = draws.get()
            waitUntil(timeoutMillis = 10_000) {
                draws.get() > detailsDraws
            }

            // Lens blur freezes the shader draw count while the CPU
            // preview republishes the recipe.
            runOnUiThread {
                s.applyEdits(
                    "Lens blur",
                    PhotoEdits(
                        spatial = PhotoSpatialEffects(
                            details = PhotoDetails(sharpening = 0.5f),
                            lensBlur = PhotoLensBlur(strength = 0.4f)
                        )
                    )
                )
            }
            val before = s.state.value.preview
            val frozenAt = draws.get()
            // The CPU preview republishes the lens-blur recipe while the
            // shader's draw count stays frozen — no approximated blur.
            waitUntil(timeoutMillis = 15_000) {
                val st = s.state.value
                st.document?.spatial?.lensBlur?.strength == 0.4f &&
                    st.preview != null && st.preview !== before
            }
            assertEquals(
                frozenAt, draws.get(),
                "shader drew a frame while lens blur was active"
            )

            // Neutral restores the shader's ownership.
            runOnUiThread {
                s.applyEdits("Reset", PhotoEdits())
            }
            waitUntil(timeoutMillis = 15_000) {
                s.state.value.document?.spatial?.isNeutral == true &&
                    draws.get() > frozenAt
            }
        } finally {
            photoShaderRendererFactory =
                { source, onFailure ->
                    createPhotoShaderRenderer(source, onFailure)
                }
        }
    }

    // ── Edits summary + reachability ──────────────────────────────────

    @Test
    fun editsPaneListsEveryNonNeutralSpatialField() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        runOnUiThread {
            s.applyEdits(
                "Spatial",
                PhotoEdits(
                    spatial = PhotoSpatialEffects(
                        details = PhotoDetails(
                            texture = 0.4f, clarity = -0.2f,
                            sharpening = 0.7f, noiseReduction = 0.1f
                        ),
                        lensBlur = PhotoLensBlur(
                            mode = LensBlurMode.Linear,
                            strength = 0.8f,
                            transition = 0.35f,
                            highlightBokeh = 0.5f,
                            centerX = 0.25f, centerY = 0.75f,
                            angleDegrees = -45f
                        )
                    )
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.spatial?.isNeutral == false
        }

        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        openPhonePane(TAG_PANEL_EDITS)

        // Every Details field lists a row.
        onNodeWithText("Texture").assertIsDisplayed()
        onNodeWithText("+40").assertIsDisplayed()
        onNodeWithText("Clarity").assertIsDisplayed()
        onNodeWithText("-20").assertIsDisplayed()
        onNodeWithText("Sharpening").assertIsDisplayed()
        onNodeWithText("70.00").assertIsDisplayed()
        onNodeWithText("Noise reduction").assertIsDisplayed()
        onNodeWithText("10.00").assertIsDisplayed()

        // The Lens blur recipe lists its mode and every moved field.
        onNodeWithText("Lens blur").assertIsDisplayed()
        onNodeWithText("Linear").assertIsDisplayed()
        onNodeWithText("Strength").assertIsDisplayed()
        onNodeWithText("80.00").assertIsDisplayed()
        onNodeWithText("Transition").assertIsDisplayed()
        onNodeWithText("35.00").assertIsDisplayed()
        onNodeWithText("Highlight bokeh").assertIsDisplayed()
        onNodeWithText("50.00").assertIsDisplayed()
        onNodeWithText("Angle").assertIsDisplayed()
        onNodeWithText("-45.0°").assertIsDisplayed()
    }

    @Test
    fun spatialSurfacesStayReachableAt2x() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(fontScale = 2f)
        openPhotoTool(TAG_CATEGORY_DETAILS)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        expandToolSheet()

        // The sheet scrolls: the last settings row stays reachable and
        // the shared ruler is still composed.
        onNodeWithTag("editor.details.row.detailNoiseReduction")
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag("editor.slider.detailTexture").assertIsDisplayed()
    }
}
