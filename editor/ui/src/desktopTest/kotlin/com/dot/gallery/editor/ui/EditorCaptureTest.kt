package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoColourGrade
import com.dot.gallery.editor.core.model.PhotoColourWheel
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoHslAdjustment
import com.dot.gallery.editor.core.model.PhotoHslBand
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.contrastFactor
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Native captures of the real [PhotoEditorScreen] for design review, saved
 * under `build/review/`. Every capture drives a live [PhotoEditorSession]
 * over [FixturePhotoMediaAccess] — the pixels on screen are what the real
 * render pipeline produced, never a recipe the pixels don't match.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorCaptureTest {

    private fun ComposeUiTest.stagedEditor(
        darkTheme: Boolean,
        fontScale: Float,
        withEdit: Boolean,
        blurEnabled: Boolean = true
    ): PhotoEditorSession {
        val session = openedSession(samplePixels())
        setContent {
            TestEditorRoot(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(LocalDensity.current.density, fontScale),
                    LocalEditorUiPreferences provides
                        EditorUiPreferences(blurEnabled = blurEnabled)
                ) {
                    PhotoEditorScreen(
                        session = session,
                        onClose = {},
                        onRetryOpen = {
                            session.open("fixture://editor_sample.jpg")
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        awaitImage(session)
        if (withEdit) {
            // A real edit through the session — undo enabled and the dirty
            // flag are the session's own truth. With the runtime-shader
            // preview active (Auto) the canvas shows the new recipe straight
            // from the shader uniforms and no CPU re-render is requested, so
            // wait on the document edit rather than a fresh preview bitmap.
            runOnUiThread {
                session.applyEdits(
                    "Boost",
                    PhotoEdits(color = ColorAdjustments(exposureStops = 0.5f))
                )
            }
            waitUntil(timeoutMillis = 15_000) {
                val s = session.state.value
                s.document?.adjustments?.exposureStops == 0.5f &&
                    s.canUndo && s.isDirty
            }
            awaitImage(session)
        }
        return session
    }

    @Test
    fun captureWide() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = true)
        try {
            // Wide layout: the tool lives in the inspector — open Light so
            // the chip strip, value and ruler are all in frame.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitForIdle()
            savePng(onRoot().captureToImage(), "photo-wide")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun capturePortrait() = runDesktopComposeUiTest(width = 390, height = 844) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            // Light tool open, Exposure selected, and one tonal control
            // carrying a real edit through the session pipeline.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            runOnUiThread {
                session.applyEdits(
                    "Shadows",
                    PhotoEdits(color = ColorAdjustments(shadows = 0.3f))
                )
            }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.adjustments?.shadows == 0.3f
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "photo-portrait")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureLandscape() = runDesktopComposeUiTest(width = 800, height = 360) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            savePng(onRoot().captureToImage(), "photo-landscape")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureLight() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val session = stagedEditor(darkTheme = false, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitForIdle()
            savePng(onRoot().captureToImage(), "photo-light")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureLargeFont() = runDesktopComposeUiTest(width = 390, height = 844) {
        val session = stagedEditor(darkTheme = true, fontScale = 2f, withEdit = false)
        try {
            // A tool open through the catalogue — the 2x pass must review
            // real context controls, not just the home shell.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitForIdle()
            savePng(onRoot().captureToImage(), "photo-font2")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFocus() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = true)
        try {
            // Full-screen focus at 2x with a pending tool: the navigator is
            // only drawn past 1x and the bottom pill carries the tool's
            // Cancel/Apply instead of Done. While a tool owns the draft the
            // floating toolbar is replaced, so focus enters via the F key
            // on the (refocused) canvas.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            onNodeWithTag(TAG_CANVAS).performKeyInput {
                keyDown(Key.F); keyUp(Key.F)
            }
            onNodeWithTag(TAG_ZOOM_LABEL).performClick()
            waitForIdle()
            onNodeWithTag(TAG_ZOOM_2X).performClick()
            waitForIdle()
            savePng(onRoot().captureToImage(), "photo-focus")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    /**
     * Focus has no chip: the phone card is dragged down (its body
     * overscrolls) to shrink to the active sub-tool's ruler.
     */
    private fun ComposeUiTest.enterPhoneFocus() {
        onNodeWithTag(TAG_V3_SHEET_BODY).performTouchInput {
            down(Offset(center.x, 40f))
            moveBy(Offset(0f, 220f))
            up()
        }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_FOCUS_PARAM_LABEL).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun captureFocusPortrait() = runDesktopComposeUiTest(width = 390, height = 844) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = true)
        try {
            // Phone focus keeps the document header and the six-slot dock;
            // the Light card shrinks to its single parameter's ruler.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            enterPhoneFocus()
            waitForIdle()
            savePng(onRoot().captureToImage(), "photo-focus-portrait")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3Focus() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            enterPhoneFocus()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "p-focus",
                FIGMA_V3_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFocusFont2() = runDesktopComposeUiTest(width = 390, height = 844) {
        val session = stagedEditor(darkTheme = true, fontScale = 2f, withEdit = true)
        try {
            // The focus card keeps its ruler at 2x text; the carousel
            // title and the step buttons are the only text that grows.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            enterPhoneFocus()
            waitForIdle()
            savePng(onRoot().captureToImage(), "photo-focus-font2")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun capturePortraitShadows() = runDesktopComposeUiTest(width = 390, height = 844) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            // Shadows chip selected with a real +30 committed through the
            // ruler's semantics path — the chip and value reflect it.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            onNodeWithTag(TAG_PARAM_SHADOWS).performScrollTo().performClick()
            onNodeWithTag(TAG_SLIDER_SHADOWS).performSemanticsAction(
                SemanticsActions.SetProgress
            ) { setProgress -> setProgress(30f) }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.adjustments?.shadows == 0.3f
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "photo-portrait-shadows")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // ── Figma V2 canonical-phone captures (412 × 832 editor content) ────

    @Test
    fun captureFigmaV2Home() = runDesktopComposeUiTest(width = 412, height = 832) {
        // The approved reference has an applied edit — Undo enabled is
        // staged through the real session, never painted over.
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = true)
        try {
            savePng(onRoot().captureToImage(), "p-home", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Light() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-light", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Tools() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            onNodeWithTag(TAG_CATALOGUE).performClick()
            onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
            waitForIdle()
            savePng(captureWindowImage(), "p-tools", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Crop() = runDesktopComposeUiTest(width = 412, height = 832) {
        // The reference recipe — a real 4:5 preset selection plus
        // -1.2° straighten — driven through the same surfaces a user
        // touches so the preset chip paints selected.
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_CROP)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            selectCropAspect(TAG_CROP_4_5)
            waitUntil(timeoutMillis = 15_000) {
                !session.state.value.interactionActive &&
                    session.state.value.document?.geometry?.crop?.right == 0.9
            }
            onNodeWithTag(TAG_STRAIGHTEN).performSemanticsAction(
                SemanticsActions.SetProgress
            ) { setProgress -> setProgress(-1.2f) }
            waitUntil(timeoutMillis = 15_000) {
                !session.state.value.interactionActive &&
                    session.state.value.document?.geometry?.warp
                        ?.rotationDegrees == -1.2f
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-crop", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Perspective() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        // The reference recipe — Vertical +6, Rotate -1.2, Scale 104 —
        // staged through the session so the live warp preview is real.
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_PERSPECTIVE)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            runOnUiThread {
                session.applyEdits(
                    "Perspective",
                    PhotoEdits(
                        geometry = PhotoGeometry(
                            warp = PhotoWarp(
                                verticalPerspective = 6f,
                                rotationDegrees = -1.2f,
                                scale = 1.04f
                            )
                        )
                    )
                )
            }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.geometry?.warp
                    ?.verticalPerspective == 6f
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-perspective", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Layers() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        // The reference recipe — one imported layer at scale .65,
        // rotation -8, Screen blend, 65% opacity — staged through the
        // session's real import path and one global recipe step.
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            runOnUiThread {
                session.addImageLayer("fixture://layer_sample.jpg")
            }
            waitUntil(timeoutMillis = 15_000) {
                (session.state.value.document?.layers?.size ?: 0) == 1 &&
                    !session.state.value.importingLayer
            }
            runOnUiThread {
                val doc = session.state.value.document!!
                val layer = doc.layers.single()
                session.applyEdits(
                    "Layer recipe",
                    doc.toEdits().copy(
                        layers = doc.layers.replacingAt(
                            0,
                            layer.copy(
                                transform = PhotoLayerTransform(
                                    scale = 0.65f,
                                    rotationDegrees = -8f
                                ),
                                blendMode = PhotoBlendMode.Screen,
                                opacity = 0.65f
                            )
                        )
                    )
                )
            }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.layers?.single()
                    ?.opacity == 0.65f
            }

            // Open the Layers workspace and select the layer so its
            // placement quad paints over the composite canvas.
            val layerId =
                session.state.value.document!!.layers.single().id
            openLayersWorkspace()
            onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId)
                .assertIsDisplayed()
                .performClick()
            onNodeWithTag(TAG_LAYER_SELECTION).assertIsDisplayed()
            // The V3 header Back is the surface's exit affordance.
            onNodeWithTag(TAG_BACK).assertIsDisplayed()
            // The injected click leaves the pointer hovering the row —
            // move it onto the canvas so the capture shows the exact
            // secondaryContainer fill, not the hover state layer.
            onRoot().performMouseInput { moveTo(Offset(206f, 200f)) }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-layers", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    /**
     * The approved layer recipe shared by the composition-tool captures —
     * a real import plus one global recipe step (scale .65, rotation -8,
     * Screen blend, 65% opacity) so every surface reads live values.
     */
    private fun ComposeUiTest.stageLayerRecipe(
        session: PhotoEditorSession,
        name: String = "Sky"
    ) {
        runOnUiThread { session.addImageLayer("fixture://layer_sample.jpg", name = name) }
        waitUntil(timeoutMillis = 15_000) {
            (session.state.value.document?.layers?.size ?: 0) == 1 &&
                !session.state.value.importingLayer
        }
        runOnUiThread {
            val doc = session.state.value.document!!
            val layer = doc.layers.single()
            session.applyEdits(
                "Layer recipe",
                doc.toEdits().copy(
                    layers = doc.layers.replacingAt(
                        0,
                        layer.copy(
                            transform = PhotoLayerTransform(
                                scale = 0.65f,
                                rotationDegrees = -8f
                            ),
                            blendMode = PhotoBlendMode.Screen,
                            opacity = 0.65f
                        )
                    )
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.document?.layers?.single()?.opacity == 0.65f
        }
    }

    @Test
    fun captureFigmaV2Transform() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            stageLayerRecipe(session)
            openPhotoTool(TAG_CATEGORY_TRANSFORM)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // The selected layer's interactive quad paints over the
            // composite — centre the hover off a handle so the capture
            // shows the plain chrome.
            onNodeWithTag(TAG_LAYER_SELECTION).assertIsDisplayed()
            onNodeWithText("Selected · Sky").assertIsDisplayed()
            onRoot().performMouseInput { moveTo(Offset(206f, 120f)) }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-transform", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Blend() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            stageLayerRecipe(session)
            openPhotoTool(TAG_CATEGORY_BLEND)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // Screen and 65% read back from the recipe — the strip's
            // selected cell and the opacity slider are the live values.
            onNodeWithTag(TAG_BLEND_MODE_PREFIX + "Screen").assertIsDisplayed()
            onNodeWithTag(TAG_LAYER_OPACITY).assertIsDisplayed()
            onRoot().performMouseInput { moveTo(Offset(206f, 120f)) }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-blend", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Double() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            stageLayerRecipe(session)
            openPhotoTool(TAG_CATEGORY_DOUBLE_EXPOSURE)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            onNodeWithTag(TAG_DOUBLE_BASE).assertIsDisplayed()
            onNodeWithTag(TAG_DOUBLE_TRANSFORM).assertIsDisplayed()
            onRoot().performMouseInput { moveTo(Offset(206f, 120f)) }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-double", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2LayerMask() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            stageLayerRecipe(session)
            // The mask tool is contextual-only — reached through the
            // layer row's More menu like a user does.
            openLayersWorkspace()
            val layerId = session.state.value.document!!.layers.single().id
            onNodeWithTag(TAG_LAYER_MORE_PREFIX + layerId).performClick()
            onNodeWithTag(TAG_LAYER_ROUTE_MASK).performClick()
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            onNodeWithTag(TAG_MASK_ERASE).assertIsDisplayed()
            onNodeWithTag(TAG_MASK_FOCUS).assertIsDisplayed()
            // The brush cursor follows the pointer — hover the composite
            // so the capture shows the diameter circle over the layer.
            onNodeWithTag(TAG_MASK_CANVAS).performMouseInput {
                moveTo(center)
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-layer-mask", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // ── Figma V2 desktop captures (1440 × 960 editor content) ───────

    @Test
    fun captureFigmaV2Desktop() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            stageLayerRecipe(session)
            // The Layers surface: the selected row, the selection quad
            // on the composite canvas and the layer's property rows.
            onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
            val layerId = session.state.value.document!!.layers.single().id
            onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).assertIsDisplayed()
            onNodeWithTag(TAG_LAYER_SELECTION).assertIsDisplayed()
            onNodeWithTag(TAG_DESKTOP_PROP_OPACITY).assertIsDisplayed()
            onRoot().performMouseInput { moveTo(Offset(400f, 300f)) }
            waitForIdle()
            savePng(onRoot().captureToImage(), "x-photo-desktop", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Keyboard() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            stageLayerRecipe(session)
            onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
            onNodeWithTag(TAG_DESKTOP_PROP_OPACITY).assertIsDisplayed()
            // Tab from the canvas until the Opacity row holds focus —
            // the capture shows the real 2 dp primary focus outline.
            var focused = false
            var steps = 0
            while (!focused && steps < 30) {
                onNodeWithTag(TAG_CANVAS).performKeyInput {
                    keyDown(Key.Tab); keyUp(Key.Tab)
                }
                focused = onNodeWithTag(TAG_DESKTOP_PROP_OPACITY)
                    .fetchSemanticsNode()
                    .let { node ->
                        node.config.getOrElse(
                            SemanticsProperties.Focused
                        ) { false }
                    }
                steps++
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "x-keyboard", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Composite() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            stageLayerRecipe(session)
            // The Layers surface with the recipe layer selected, then F
            // into the composite focus chrome — top and bottom layer
            // bars over the full composite.
            openLayersWorkspace()
            val layerId = session.state.value.document!!.layers.single().id
            onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).performClick()
            onNodeWithTag(TAG_CANVAS).performKeyInput {
                keyDown(Key.F); keyUp(Key.F)
            }
            onNodeWithTag(TAG_LAYER_FOCUS_TOP).assertIsDisplayed()
            onNodeWithTag(TAG_LAYER_FOCUS_BOTTOM).assertIsDisplayed()
            onRoot().performMouseInput { moveTo(Offset(206f, 400f)) }
            waitForIdle()
            savePng(onRoot().captureToImage(), "x-photo-composite", FIGMA_V2_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // ── Figma V3 captures (412 × 832 editor content) ─────────────────
    // The review set for `editor/figma-review/v3-latest/` — neutral
    // scheme, pill + modal catalogue, sheet/inline tool presentations
    // and the contextual layer menu.

    @Test
    fun captureFigmaV3Home() = runDesktopComposeUiTest(width = 412, height = 832) {
        // One applied edit so Undo lights up — the approved reference
        // shows a dirty document, so the session carries a real one.
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = true)
        try {
            savePng(onRoot().captureToImage(), "p-home", FIGMA_V3_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3LightCollapsed() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            // The AdjustmentSheet presentation at its 220 dp peek —
            // canvas above, param strip and ruler inside the sheet.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-light-collapsed", FIGMA_V3_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3CropInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        // Inline presentation: breadcrumb under the header, the flat
        // inspector at the foot, crop chrome over the live 4:5 preview.
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_CROP)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            selectCropAspect(TAG_CROP_4_5)
            waitUntil(timeoutMillis = 15_000) {
                !session.state.value.interactionActive &&
                    session.state.value.document?.geometry?.crop?.right == 0.9
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-crop-inline", FIGMA_V3_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3CurvesInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_CURVES)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // A real S-curve through the master channel.
            onNodeWithTag("editor.curves.preset.strong").performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.curves?.master?.isNeutral == false
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "p-curves-inline", FIGMA_V3_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3WhiteBalanceInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_WHITE_BALANCE)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // Figma draws the open tool surface expanded — the handle
            // drag lands the presets, ruler and settings rows in frame.
            onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
                .performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -400f))
                    up()
                }
            // A real preset selection — Tungsten lands 3200 K / 0 and the
            // chip paints selected against the live recipe.
            onNodeWithTag("editor.wb.preset.tungsten").performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.whiteBalance
                    ?.temperatureKelvin == 3200f
            }
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "p-white-balance-inline", FIGMA_V3_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3VignetteInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_VIGNETTE)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // Figma draws the open tool surface expanded — the handle
            // drag lands the tabs, ruler and settings rows in frame.
            onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
                .performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -400f))
                    up()
                }
            // Amount -40 through the shared ruler's SetProgress — the
            // tab, value row and settings rows all read the live recipe.
            onNodeWithTag("editor.slider.vignetteAmount")
                .performSemanticsAction(SemanticsActions.SetProgress) {
                    setProgress -> setProgress(-40f)
                }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.vignette
                    ?.amount == -0.4f
            }
            waitForIdle()
            savePng(
                onRoot().captureToImage(), "p-vignette-inline", FIGMA_V3_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3GrainInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_GRAIN)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // Figma draws the open tool surface expanded — the handle
            // drag lands the tabs, ruler and settings rows in frame.
            onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
                .performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -400f))
                    up()
                }
            val previewBefore = session.state.value.preview
            onNodeWithTag("editor.slider.grainAmount")
                .performSemanticsAction(SemanticsActions.SetProgress) {
                    setProgress -> setProgress(30f)
                }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.grain
                    ?.amount == 0.3f
            }
            // Active grain gates the canvas to the CPU preview — wait for
            // the republished frame so the capture shows real noise.
            waitUntil(timeoutMillis = 15_000) {
                val preview = session.state.value.preview
                preview != null && preview !== previewBefore
            }
            waitForIdle()
            savePng(
                onRoot().captureToImage(), "p-grain-inline", FIGMA_V3_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3DetailsInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_DETAILS)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // Figma draws the open tool surface expanded — the handle
            // drag lands the tabs, ruler and settings rows in frame.
            onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
                .performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -400f))
                    up()
                }
            // Texture +40 through the shared ruler's SetProgress — the
            // tab, value chip and settings row all read the live recipe.
            onNodeWithTag("editor.slider.detailTexture")
                .performSemanticsAction(SemanticsActions.SetProgress) {
                    setProgress -> setProgress(40f)
                }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.spatial?.details
                    ?.texture == 0.4f
            }
            waitForIdle()
            savePng(
                onRoot().captureToImage(), "p-details-inline", FIGMA_V3_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3BlurInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LENS_BLUR)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // Figma draws the open tool surface expanded — the handle
            // drag lands the mode chips, ruler and settings rows in
            // frame while the focus rings stay over the canvas.
            onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
                .performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -400f))
                    up()
                }
            val previewBefore = session.state.value.preview
            // Strength 55 through the shared ruler — the focus rings
            // paint over the canvas while the blur goes non-neutral.
            onNodeWithTag("editor.slider.lensBlurStrength")
                .performSemanticsAction(SemanticsActions.SetProgress) {
                    setProgress -> setProgress(55f)
                }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.spatial?.lensBlur
                    ?.strength == 0.55f
            }
            // Lens blur gates the canvas to the CPU preview — wait for
            // the republished frame so the capture shows the real blur.
            waitUntil(timeoutMillis = 15_000) {
                val preview = session.state.value.preview
                preview != null && preview !== previewBefore
            }
            waitForIdle()
            savePng(
                onRoot().captureToImage(), "p-blur-inline", FIGMA_V3_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3LooksInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LOOKS)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // Wait for the real preview tiles — the clickable tile
            // merges descendants, so the Image lives in the unmerged
            // tree — then pick Cinematic and pull Strength to 65.
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(
                    "editor.look.thumb.cinematic", useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("editor.look.cinematic")
                .performScrollTo()
                .performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.look?.id ==
                    com.dot.gallery.editor.core.model.PhotoLookId.Cinematic
            }
            onNodeWithTag("editor.slider.lookStrength")
                .performSemanticsAction(SemanticsActions.SetProgress) {
                    setProgress -> setProgress(65f)
                }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.look
                    ?.amount == 0.65f
            }
            // A shader-supported look keeps the runtime canvas — the
            // preview frame is not republished like the CPU-gated
            // stages, so the recipe state is the honest wait.
            waitForIdle()
            savePng(
                onRoot().captureToImage(), "p-looks-inline", FIGMA_V3_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3ToolsModal() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            onNodeWithTag(TAG_CATALOGUE).performClick()
            onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
            waitForIdle()
            savePng(captureWindowImage(), "p-tools-modal", FIGMA_V3_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3PinTools() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            // Catalogue -> Edit pins: the pin manager rides over the
            // tools sheet with the pinned rows' reorder actions.
            onNodeWithTag(TAG_CATALOGUE).performClick()
            onNodeWithTag(TAG_EDITOR_PINS_EDIT).performClick()
            onNodeWithTag(TAG_PIN_MANAGER).assertIsDisplayed()
            waitForIdle()
            savePng(captureWindowImage(), "p-pin-tools", FIGMA_V3_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3LayerMenu() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            stageLayerRecipe(session)
            openLayersWorkspace()
            val layerId = session.state.value.document!!.layers.single().id
            onNodeWithTag(TAG_LAYER_MORE_PREFIX + layerId).performClick()
            onNodeWithTag(TAG_LAYER_ROUTE_TRANSFORM).assertIsDisplayed()
            waitForIdle()
            savePng(captureWindowImage(), "p-layer-menu", FIGMA_V3_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // ── Stitch V3 family 1 captures (390 × 884) ──────────────────────
    // The phone canonical set for the Stitch review: the zero state, the
    // dirty HUD and the Contrast selection. Every value on screen comes
    // from the live session — no readout is painted from a mock recipe.

    /** One committed Light recipe: +0.35 EV, +12 contrast, -1 highlights. */
    private fun ComposeUiTest.stageStitchRecipe(session: PhotoEditorSession) {
        runOnUiThread {
            session.applyEdits(
                "Stitch HUD",
                PhotoEdits(
                    color = ColorAdjustments(
                        exposureStops = 0.35f,
                        contrast = contrastFactor(12f),
                        highlights = -0.01f
                    )
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            val s = session.state.value
            val adjustments = s.document?.adjustments
            adjustments != null &&
                kotlin.math.abs(adjustments.exposureStops - 0.35f) < 0.001f &&
                kotlin.math.abs(
                    adjustments.contrast - contrastFactor(12f)
                ) < 0.001f &&
                s.canUndo
        }
        awaitImage(session)
    }

    // Crop with the 4:5 aspect, as the mock stages it.
    @Test
    fun captureStitchCropFloatingCard() = captureStitchCrop(1f, "desktop-crop-floating-card")

    @Test
    fun captureStitchCropFloatingCardFont2x() =
        captureStitchCrop(2f, "desktop-crop-floating-card-font2x")

    @Test
    fun captureStitchCropFloatingCardBlurOff() =
        captureStitchCrop(1f, "desktop-crop-floating-card-blur-off", blurEnabled = false)

    // A pinch-zoomed crop: the photo runs under the header and the action
    // row, and the scrim must not leave a field-edge seam across it (it is
    // clipped to the photo's own rect).
    @Test
    fun captureStitchCropFloatingCardZoomed() =
        runDesktopComposeUiTest(width = 390, height = 884) {
            val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
            try {
                openPhotoTool(TAG_CATEGORY_CROP)
                waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
                selectCropAspect(TAG_CROP_4_5)
                waitUntil(timeoutMillis = 15_000) {
                    !session.state.value.interactionActive &&
                        session.state.value.document?.geometry?.crop?.right == 0.9
                }
                // Zoom the crop's own camera, like a user: the home no
                // longer carries a zoom menu.
                onNodeWithTag(TAG_CROP_CANVAS).performTouchInput {
                    pinch(
                        start0 = center + Offset(-60f, 0f),
                        end0 = center + Offset(-150f, 0f),
                        start1 = center + Offset(60f, 0f),
                        end1 = center + Offset(150f, 0f)
                    )
                }
                waitForIdle()
                savePng(
                    onRoot().captureToImage(),
                    "desktop-crop-floating-card-zoomed",
                    STITCH_FAMILY_DIR
                )
            } finally {
                runOnUiThread { session.close() }
            }
        }

    private fun captureStitchCrop(fontScale: Float, name: String, blurEnabled: Boolean = true) =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(
            darkTheme = true, fontScale = fontScale, withEdit = false, blurEnabled = blurEnabled
        )
        try {
            openPhotoTool(TAG_CATEGORY_CROP)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            selectCropAspect(TAG_CROP_4_5)
            waitUntil(timeoutMillis = 15_000) {
                !session.state.value.interactionActive &&
                    session.state.value.document?.geometry?.crop?.right == 0.9
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // Curves with a real S-curve on the master channel.
    @Test
    fun captureStitchCurvesFloatingCard() = captureStitchCurves(1f, "desktop-curves-floating-card")

    @Test
    fun captureStitchCurvesFloatingCardFont2x() =
        captureStitchCurves(2f, "desktop-curves-floating-card-font2x")

    @Test
    fun captureStitchCurvesFloatingCardBlurOff() =
        captureStitchCurves(1f, "desktop-curves-floating-card-blur-off", blurEnabled = false)

    private fun captureStitchCurves(fontScale: Float, name: String, blurEnabled: Boolean = true) =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(
            darkTheme = true, fontScale = fontScale, withEdit = false, blurEnabled = blurEnabled
        )
        try {
            openPhotoTool(TAG_CATEGORY_CURVES)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // A real S-curve through the master channel.
            onNodeWithTag("editor.curves.preset.strong").performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.curves?.master?.isNeutral == false
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // Curves in each of the design's forms, a point selected and a second
    // channel edited so the ghost and guides show.
    @Test
    fun captureCurvesPhone() = captureCurvesLayout(390, 884, "curves-phone")

    @Test
    fun captureCurvesPhoneLandscape() = captureCurvesLayout(884, 390, "curves-phone-landscape")

    @Test
    fun captureCurvesTabletLandscape() = captureCurvesLayout(1280, 800, "curves-tablet-landscape")

    @Test
    fun captureCurvesTabletPortrait() = captureCurvesLayout(800, 1280, "curves-tablet-portrait")

    private fun captureCurvesLayout(windowWidth: Int, windowHeight: Int, name: String) =
        runDesktopComposeUiTest(width = windowWidth, height = windowHeight) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_CURVES)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            runOnUiThread {
                session.applyEdits(
                    "Curves",
                    PhotoEdits(
                        finishing = PhotoFinishing(
                            curves = PhotoCurves(
                                master = PhotoToneCurve.ofSteps(
                                    0 to 0, 64 to 54, 128 to 128, 192 to 202, 255 to 255
                                ),
                                red = PhotoToneCurve.ofSteps(0 to 0, 104 to 112, 255 to 255)
                            )
                        )
                    )
                )
            }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.curves?.master?.isNeutral == false
            }
            waitForIdle()
            // Between the master's own points, so the tap adds a fifth.
            onNodeWithTag("editor.curves.editor").performTouchInput {
                click(Offset(width * 0.62f, height / 2f))
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // Looks: Cinematic at 85 % with the split compare on, as the mock.
    @Test
    fun captureStitchLooksFloatingCard() = captureStitchLooks(1f, "desktop-filters-floating-card")

    @Test
    fun captureStitchLooksFloatingCardFont2x() =
        captureStitchLooks(2f, "desktop-filters-floating-card-font2x")

    @Test
    fun captureStitchLooksFloatingCardBlurOff() =
        captureStitchLooks(1f, "desktop-filters-floating-card-blur-off", blurEnabled = false)

    private fun captureStitchLooks(fontScale: Float, name: String, blurEnabled: Boolean = true) =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(
            darkTheme = true, fontScale = fontScale, withEdit = false, blurEnabled = blurEnabled
        )
        try {
            onAllNodesWithTag(TAG_BEFORE_CHIP).onFirst().performClick()
            openPhotoTool(TAG_CATEGORY_LOOKS)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(
                    "editor.look.thumb.cinematic", useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("editor.look.cinematic").performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.look?.id ==
                    com.dot.gallery.editor.core.model.PhotoLookId.Cinematic
            }
            onNodeWithTag("editor.slider.lookStrength")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(85f) }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.look?.amount == 0.85f
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // The user-presets catalogue: a saved look beside the create tile.
    @Test
    fun captureStitchLooksPresets() = runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LOOKS)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(
                    "editor.look.thumb.cinematic", useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("editor.look.cinematic").performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.look?.id ==
                    com.dot.gallery.editor.core.model.PhotoLookId.Cinematic
            }
            onNodeWithTag("editor.slider.lookStrength")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.look?.amount == 0.6f
            }
            onNodeWithTag("editor.look.category.mine")
                .performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(TAG_LOOK_NEW_PRESET).performClick()
            onNodeWithTag(TAG_LOOK_PRESET_NAME_FIELD)
                .performTextReplacement("Nordic 60")
            onNodeWithTag(TAG_LOOK_PRESET_CONFIRM).performClick()
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithText("Nordic 60").fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), "desktop-looks-presets", STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // The save-preset sheet: preview, name and the captured adjustments,
    // each in its own card.
    @Test
    fun captureStitchLooksPresetSheet() = runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LOOKS)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(
                    "editor.look.thumb.cinematic", useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("editor.look.cinematic").performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.look?.id ==
                    com.dot.gallery.editor.core.model.PhotoLookId.Cinematic
            }
            onNodeWithTag("editor.slider.lookStrength")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.look?.amount == 0.6f
            }
            onNodeWithTag("editor.look.category.mine")
                .performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(TAG_LOOK_NEW_PRESET).performClick()
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(TAG_LOOK_PRESET_PREVIEW).fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            savePng(
                onAllNodes(isRoot())[0].captureToImage(),
                "desktop-looks-preset-sheet",
                STITCH_FAMILY_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // Colour: saturation is the only colour operation the recipe defines,
    // on the shared ruler slab with the expanded sheet's live histogram.
    @Test
    fun captureColourModes() {
        val layouts = listOf(
            Triple("phone", 390, 884),
            Triple("phone-landscape", 884, 390),
            Triple("tablet-landscape", 1280, 800),
            Triple("tablet-portrait", 800, 1280)
        )
        for ((layout, width, height) in layouts) {
            for (mode in ColourMode.entries) {
                captureColourMode(width, height, mode, "$layout-${mode.key}")
            }
        }
    }

    @Test
    fun captureColourModesFont2x() {
        for (mode in ColourMode.entries) {
            captureColourMode(390, 884, mode, "phone-${mode.key}-font2x", fontScale = 2f)
        }
    }

    private fun captureColourMode(
        width: Int,
        height: Int,
        mode: ColourMode,
        name: String,
        fontScale: Float = 1f
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val session = stagedEditor(darkTheme = true, fontScale = fontScale, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_COLOUR)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            val modeTab = onNodeWithTag("editor.colour.mode.${mode.key}")
            // The group only scrolls at large text.
            if (fontScale > 1.15f) modeTab.performScrollTo()
            modeTab.performClick()
            runOnUiThread {
                session.applyEdits(
                    "Colour modes",
                    PhotoEdits(
                        color = ColorAdjustments(saturation = 1.18f),
                        finishing = PhotoFinishing(
                            whiteBalance = PhotoWhiteBalance(6900f, 4f),
                            grade = PhotoColourGrade(
                                vibrance = 0.08f,
                                protectSkin = true,
                                shadows = PhotoColourWheel(215f, 0.12f),
                                midtones = PhotoColourWheel(45f, 0.06f),
                                highlights = PhotoColourWheel(68f, 0.14f, 0.04f)
                            ).withAdjustment(
                                PhotoHslBand.Red,
                                PhotoHslAdjustment(0f, 0.18f, -0.06f)
                            )
                        )
                    )
                )
            }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.finishing?.grade?.isNeutral == false
            }
            awaitImage(session)
            waitForIdle()
            savePng(onRoot().captureToImage(), name, COLOUR_MODES_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureStitchColourMastering() = captureStitchColour(1f, "desktop-colour-mastering")

    @Test
    fun captureStitchColourMasteringFont2x() =
        captureStitchColour(2f, "desktop-colour-mastering-font2x")

    @Test
    fun captureStitchColourMasteringBlurOff() =
        captureStitchColour(1f, "desktop-colour-mastering-blur-off", blurEnabled = false)

    private fun captureStitchColour(fontScale: Float, name: String, blurEnabled: Boolean = true) =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(
            darkTheme = true, fontScale = fontScale, withEdit = false, blurEnabled = blurEnabled
        )
        try {
            openPhotoTool(TAG_CATEGORY_COLOUR)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            // The expanded card adds the Adjust group's tool switcher under
            // the one saturation ruler; the histogram lives in the pill's
            // panel, not in the card.
            expandPhotoToolSheet()
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(TAG_PHOTO_SWITCHER)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(TAG_SLIDER_SATURATION).performSemanticsAction(
                SemanticsActions.SetProgress
            ) { setProgress -> setProgress(1.18f) }
            waitUntil(timeoutMillis = 15_000) {
                kotlin.math.abs(
                    (session.state.value.document?.adjustments?.saturation ?: 0f) - 1.18f
                ) < 0.001f
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // The save modal mid-export: the state is staged because a real export
    // finishes too quickly to freeze, but every figure on it comes from that
    // state (progress 0.87, the current-recipe preview as the thumbnail).
    @Test
    fun captureStitchSavingImageModal() = captureSaveModal(1f, "desktop-saving-image-modal")

    @Test
    fun captureStitchSavingImageModalFont2x() =
        captureSaveModal(2f, "desktop-saving-image-modal-font2x")

    @Test
    fun captureStitchSavingImageModalBlurOff() =
        captureSaveModal(1f, "desktop-saving-image-modal-blur-off", blurEnabled = false)

    private fun captureSaveModal(fontScale: Float, name: String, blurEnabled: Boolean = true) =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val base = fixtureState(exporting = true)
        val state = base.copy(
            preview = samplePixels(),
            previewDocument = base.document,
            exportProgress = 0.87f
        )
        setContent {
            TestEditorRoot(darkTheme = true) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalEditorUiPreferences provides EditorUiPreferences(blurEnabled = blurEnabled)
                ) {
                    PhotoEditorContent(state, PhotoEditorActions(
                        beginEdit = {}, updateEdits = {}, commitEdit = {}, cancelEdit = {},
                        applyEdits = { _, _ -> }, undo = {}, redo = {}, reset = {},
                        saveCopy = {}, cancelExport = {}, retryOpen = {}, retryPreview = {},
                        clearError = {}, close = {}
                    ))
                }
            }
        }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_SAVE_PROGRESS_THUMBNAIL).fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
        // The modal is its own root; the first root's capture is the
        // composited window, editor and modal together. The desktop test
        // harness leaves the dialog window's own background unpainted
        // (it reads as a red band across the card's lane); that band is
        // a harness artifact, not UI — see the screen's progress page.
        savePng(onAllNodes(isRoot())[0].captureToImage(), name, STITCH_FAMILY_DIR)
    }

    // Tablet master-detail: canvas plus the right inspector, Light open
    // with the staged exposure/contrast recipe the mock shows.
    @Test
    fun captureStitchTabletMasterDetail() =
        captureStitchTablet(1f, "desktop-master-detail")

    @Test
    fun captureStitchTabletMasterDetailFont2x() =
        captureStitchTablet(2f, "desktop-master-detail-font2x")

    @Test
    fun captureStitchTabletMasterDetailBlurOff() =
        captureStitchTablet(1f, "desktop-master-detail-blur-off", blurEnabled = false)

    // The design's split: the original left of the divider, the edit to
    // its right, the Light inspector still live.
    @Test
    fun captureStitchTabletMasterDetailCompare() =
        runDesktopComposeUiTest(width = 1280, height = 1024) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            stageStitchRecipe(session)
            onNodeWithTag(TAG_BEFORE_CHIP).performClick()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "desktop-master-detail-compare",
                STITCH_TABLET_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    private fun captureStitchTablet(
        fontScale: Float,
        name: String,
        blurEnabled: Boolean = true
    ) = runDesktopComposeUiTest(width = 1280, height = 1024) {
        val session = stagedEditor(
            darkTheme = true, fontScale = fontScale, withEdit = false, blurEnabled = blurEnabled
        )
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            stageStitchRecipe(session)
            onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_TABLET_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureStitchEditorZeroState() =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "desktop-zero-state",
                STITCH_FAMILY_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureStitchMobileGesturalHud() =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(darkTheme = true, fontScale = 1f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            stageStitchRecipe(session)
            // Exposure stays the selected parameter, so the readout pill
            // carries the +0.35 EV the mock shows.
            onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "desktop-gestural-hud",
                STITCH_FAMILY_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureStitchEditorContrastSelected() =
        captureContrastSelected(1f, "desktop-contrast-selected")

    @Test
    fun captureStitchEditorContrastSelectedFont2x() =
        captureContrastSelected(2f, "desktop-contrast-selected-font2x")

    // Compare off on the home header: one unsplit canvas under the edit,
    // and the toggle is the single way into the split.
    @Test
    fun captureStitchNoComparisonMode() =
        captureNoComparison(1f, "desktop-no-comparison-mode")

    @Test
    fun captureStitchNoComparisonModeFont2x() =
        captureNoComparison(2f, "desktop-no-comparison-mode-font2x")

    private fun captureNoComparison(fontScale: Float, name: String) =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(darkTheme = true, fontScale = fontScale, withEdit = false)
        try {
            // The design's frame for this state is the open Light card
            // with compare switched off — the pair with contrast-selected
            // differs only in the toggle.
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            stageStitchRecipe(session)
            onAllNodesWithTag(TAG_BEFORE_CHIP).onFirst().assertIsDisplayed()
            onNodeWithTag(TAG_SPLIT_HANDLE).assertDoesNotExist()
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    private fun captureContrastSelected(fontScale: Float, name: String) =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(darkTheme = true, fontScale = fontScale, withEdit = false)
        try {
            // The mock's canvas split is the header compare toggle, switched
            // on before the tool opens; the viewport keeps it across the tool.
            onAllNodesWithTag(TAG_BEFORE_CHIP).onFirst().performClick()
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            stageStitchRecipe(session)
            // Selecting the Contrast chip is the real parameter switch —
            // its readout and the -100 / +100 caption follow the recipe.
            onNodeWithTag(TAG_PARAM_CONTRAST)
                .performScrollTo()
                .performClick()
            waitUntil(timeoutMillis = 15_000) {
                !session.state.value.interactionActive
            }
            onNodeWithTag(TAG_SPLIT_HANDLE).assertIsDisplayed()
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // The opaque fallback of the HUD — same recipe and state as
    // captureStitchMobileGesturalHud, so the pair proves identical geometry.
    @Test
    fun captureStitchMobileGesturalHudBlurOff() =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(
            darkTheme = true, fontScale = 1f, withEdit = false,
            blurEnabled = false
        )
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            stageStitchRecipe(session)
            onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "desktop-gestural-hud-blur-off",
                STITCH_FAMILY_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureStitchEditorZeroStateFont2x() =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(darkTheme = true, fontScale = 2f, withEdit = false)
        try {
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "desktop-zero-state-font2x",
                STITCH_FAMILY_DIR
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // The opened histogram over the HUD recipe with compare on, as the
    // mock stages it: the panel takes the pill's place over the canvas.
    @Test
    fun captureStitchOpenedHistogram() =
        captureOpenedHistogram(1f, "desktop-opened-histogram")

    @Test
    fun captureStitchOpenedHistogramFont2x() =
        captureOpenedHistogram(2f, "desktop-opened-histogram-font2x")

    @Test
    fun captureStitchOpenedHistogramBlurOff() =
        captureOpenedHistogram(1f, "desktop-opened-histogram-blur-off", blurEnabled = false)

    // The home surface at 2x: the panel stays clear of the toolbar region.
    @Test
    fun captureStitchOpenedHistogramHomeFont2x() =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(darkTheme = true, fontScale = 2f, withEdit = false)
        try {
            stageStitchRecipe(session)
            onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL_OPEN)
                .performSemanticsAction(SemanticsActions.OnClick)
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(TAG_PHOTO_HISTOGRAM_SHADOWS_CLIP)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            val panelBottom = onNodeWithTag(TAG_PHOTO_HISTOGRAM_PANEL)
                .getUnclippedBoundsInRoot().bottom
            assertTrue(panelBottom <= (884 - 70).dp, "panel bottom $panelBottom under the toolbar")
            savePng(onRoot().captureToImage(), "desktop-opened-histogram-home-font2x", STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    private fun captureOpenedHistogram(
        fontScale: Float,
        name: String,
        blurEnabled: Boolean = true
    ) = runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(
            darkTheme = true, fontScale = fontScale, withEdit = false,
            blurEnabled = blurEnabled
        )
        try {
            onAllNodesWithTag(TAG_BEFORE_CHIP).onFirst().performClick()
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            stageStitchRecipe(session)
            // The click action, not a pointer tap, so no hover state is left
            // on whichever tab lands under the pointer.
            onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL_OPEN)
                .performSemanticsAction(SemanticsActions.OnClick)
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(TAG_PHOTO_HISTOGRAM_SHADOWS_CLIP)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // Focus mode over the HUD recipe with compare on: only the Exposure
    // ruler and its label stay with the exit and zoom pill.
    @Test
    fun captureStitchMinimalModeExposure() = captureMinimalMode(1f, "desktop-minimal-mode-exposure")

    @Test
    fun captureStitchMinimalModeExposureFont2x() =
        captureMinimalMode(2f, "desktop-minimal-mode-exposure-font2x")

    // The opaque fallback: same state, so the pair proves identical geometry.
    @Test
    fun captureStitchMinimalModeExposureBlurOff() =
        captureMinimalMode(1f, "desktop-minimal-mode-exposure-blur-off", blurEnabled = false)

    private fun captureMinimalMode(fontScale: Float, name: String, blurEnabled: Boolean = true) =
        runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedEditor(
            darkTheme = true, fontScale = fontScale, withEdit = false,
            blurEnabled = blurEnabled
        )
        try {
            onAllNodesWithTag(TAG_BEFORE_CHIP).onFirst().performClick()
            openPhotoTool(TAG_CATEGORY_LIGHT)
            waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
            stageStitchRecipe(session)
            enterPhoneFocus()
            onNodeWithTag(TAG_SPLIT_HANDLE).assertIsDisplayed()
            waitForIdle()
            // The stage labels stay above the floating focus card.
            val cardTop = onNodeWithTag(TAG_V3_SHEET_BODY)
                .getUnclippedBoundsInRoot().top
            for (tag in listOf(TAG_SPLIT_BEFORE, TAG_SPLIT_AFTER)) {
                val labelBottom = onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom
                assertTrue(
                    labelBottom < cardTop,
                    "$tag bottom $labelBottom overlaps the card at $cardTop"
                )
            }
            savePng(onRoot().captureToImage(), name, STITCH_FAMILY_DIR)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    private fun savePng(
        bitmap: ImageBitmap,
        name: String,
        dirName: String = "light-v1"
    ) {
        val data = Image.makeFromBitmap(bitmap.asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG encode failed")
        val dir = File("build/review/$dirName").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(data.bytes)
    }

    private companion object {
        const val FIGMA_V2_DIR = "figma-v2-implementation"
        const val FIGMA_V3_DIR = "figma-v3-implementation"
        const val STITCH_FAMILY_DIR = "stitch-family-1"
        const val STITCH_TABLET_DIR = "stitch-tablet"
        const val COLOUR_MODES_DIR = "colour-modes"
    }
}
