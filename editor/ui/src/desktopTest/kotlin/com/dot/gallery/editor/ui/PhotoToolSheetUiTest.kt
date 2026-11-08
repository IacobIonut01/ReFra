package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.render.SrgbPixels
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class PhotoToolSheetUiTest {
    private fun actions() = PhotoEditorActions(
        beginEdit = {}, updateEdits = {}, commitEdit = {}, cancelEdit = {},
        applyEdits = { _, _ -> }, undo = {}, redo = {}, reset = {},
        saveCopy = {}, cancelExport = {}, retryOpen = {}, retryPreview = {},
        clearError = {}, close = {}
    )

    @Test
    fun cardMaterialIsAlwaysPaintedAndExpansionNeverFadesTheControls() =
        runDesktopComposeUiTest(width = 320, height = 240) {
            var expansion by mutableStateOf(0f)
            var blur by mutableStateOf(true)
            val backdrop = Color(0xFF376391)
            setContent {
                TestEditorRoot {
                    CompositionLocalProvider(
                        LocalEditorUiPreferences provides EditorUiPreferences(blurEnabled = blur)
                    ) {
                        Box(Modifier.fillMaxSize().background(backdrop)) {
                            PhotoToolSheetSurface(
                                expansion, Modifier.size(220.dp, 180.dp).testTag("surface")
                            ) {
                                Box(
                                    Modifier.size(20.dp).align(Alignment.Center)
                                        .background(Color.White).testTag("control")
                                )
                            }
                        }
                    }
                }
            }
            for (enabled in listOf(true, false)) {
                // The approved card is a floating surface at rest — the
                // material never waits for an expansion to appear.
                runOnIdle { blur = enabled; expansion = 0f }
                onNodeWithTag(TAG_PHOTO_SHEET_MATERIAL).assertIsDisplayed()
                val collapsed = onNodeWithTag("surface").captureToImage().toPixelMap()
                assertNotEquals(backdrop, collapsed[10, collapsed.height / 2])
                runOnIdle { expansion = 0.5f }
                onNodeWithTag(TAG_PHOTO_SHEET_MATERIAL).assertIsDisplayed()
                val halfway = onNodeWithTag("surface").captureToImage().toPixelMap()
                val control = onNodeWithTag("control").captureToImage().toPixelMap()
                assertEquals(Color.White, control[control.width / 2, control.height / 2])
                runOnIdle { expansion = 1f }
                val expanded = onNodeWithTag("surface").captureToImage().toPixelMap()
                // Expansion never fades the card material nor its controls.
                assertEquals(
                    halfway[10, halfway.height / 2],
                    expanded[10, expanded.height / 2]
                )
                val expandedControl = onNodeWithTag("control")
                    .captureToImage().toPixelMap()
                assertEquals(
                    Color.White,
                    expandedControl[expandedControl.width / 2, expandedControl.height / 2]
                )
                onNodeWithTag(TAG_PHOTO_SHEET_MATERIAL).assertIsDisplayed()
            }
        }

    @Test
    fun everyRegisteredPhotoToolUsesTheSameCollapsedAndExpandedShell() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val base = fixtureState()
            val size = base.preview!!.size
            val pixels = SrgbPixels(
                size, IntArray(size.pixelCount.toInt()) { 0xFF334455.toInt() }
            )
            val layer = PhotoImageLayer(
                "overlay", "Overlay", MediaAsset.Image("overlay-source", "fixture://overlay", pixels.size)
            )
            val document = base.document!!.copy(layers = persistentListOf(layer))
            val state = base.copy(
                document = document, preview = pixels, originalPreview = pixels,
                previewDocument = document, layerPreviews = persistentMapOf(layer.id to pixels)
            )
            val entries = photoToolEntries()
            assertTrue(entries.all { it.presentation == EditorToolPresentation.AdjustmentSheet })
            setContent {
                TestEditorRoot {
                    CompositionLocalProvider(
                        LocalEditorUiPreferences provides EditorUiPreferences(blurEnabled = false)
                    ) {
                        PhotoEditorContent(state, actions(), renderPreference = PhotoRenderPreference.Cpu)
                    }
                }
            }
            for (entry in entries.filter { it.catalogued }) {
                openPhotoTool(entry.tag)
                assertAndCaptureStates(entry.descriptor.id.value)
                onNodeWithTag(TAG_BACK).performClick()
            }
            openLayersWorkspace()
            onNodeWithTag(TAG_LAYER_MORE_PREFIX + layer.id).performClick()
            onNodeWithTag(TAG_LAYER_ROUTE_MASK).performClick()
            assertAndCaptureStates(EditorToolIds.PhotoLayerMask.value)
        }

    @Test
    fun colourRulerKeepsItsModelRangeAndCommitsOneUndoableToolEdit() =
        runDesktopComposeUiTest(width = 412, height = 832) {
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
                onNodeWithTag(TAG_SLIDER_SATURATION).assertRangeInfoEquals(
                    ProgressBarRangeInfo(1f, 0f..4f, steps = 0)
                )
                onNodeWithTag(TAG_SLIDER_SATURATION).performSemanticsAction(
                    SemanticsActions.SetProgress
                ) { it(0.7f) }
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.document?.adjustments?.saturation == 0.7f
                }
                onNodeWithTag(TAG_TOOL_APPLY).performClick()
                onNodeWithTag(TAG_UNDO).performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.document?.adjustments?.saturation == 1f
                }
            } finally {
                runOnUiThread { session.close() }
            }
        }

    private fun ComposeUiTest.assertAndCaptureStates(name: String) {
        onNodeWithTag(TAG_V3_ADJUSTMENT_SHEET).assertIsDisplayed()
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()
        // The floating card is always painted; the Apply row lives in its
        // foot, above the six-slot dock.
        onNodeWithTag(TAG_PHOTO_SHEET_MATERIAL).assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        val card = onNodeWithTag(TAG_V3_SHEET_BODY).getUnclippedBoundsInRoot()
        val dock = onNodeWithTag(TAG_EDITOR_PINS).getUnclippedBoundsInRoot()
        assertTrue(
            card.bottom <= dock.top,
            "$name card foot ${card.bottom} overlaps the dock at ${dock.top}"
        )
        val lastControl = when (name) {
            // The catalogue chips are the bottom-most row now.
            EditorToolIds.PhotoLooks.value -> "editor.look.category.all"
            EditorToolIds.PhotoBlend.value -> TAG_LAYER_CLIP
            EditorToolIds.PhotoCrop.value -> TAG_STRAIGHTEN
            else -> null
        }
        if (lastControl != null) {
            val control = onNodeWithTag(lastControl).getUnclippedBoundsInRoot()
            assertTrue(
                control.bottom <= card.bottom,
                "$name clips its last collapsed control"
            )
        }
        saveCapture("$name-collapsed")
        // Light and Looks have no expanded state: their resting card is
        // the whole card and paints no grab strip (D47, D51).
        val nonExpanding = name == EditorToolIds.PhotoLight.value ||
            name == EditorToolIds.PhotoLooks.value ||
            name == EditorToolIds.PhotoColour.value
        if (nonExpanding) {
            onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
                .assertDoesNotExist()
            return
        }
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, -440f)); up()
        }
        waitForIdle()
        onNodeWithTag(TAG_PHOTO_SHEET_MATERIAL).assertIsDisplayed()
        // Expansion grows the card upward; its foot stays above the dock.
        val expandedCard = onNodeWithTag(TAG_V3_SHEET_BODY).getUnclippedBoundsInRoot()
        assertTrue(
            expandedCard.top < card.top,
            "$name did not expand (top stayed at ${expandedCard.top})"
        )
        assertTrue(
            expandedCard.bottom <= dock.top,
            "$name expanded card foot ${expandedCard.bottom} overlaps ${dock.top}"
        )
        saveCapture("$name-expanded")
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, 480f)); up()
        }
        waitForIdle()
        val collapsedAgain = onNodeWithTag(TAG_V3_SHEET_BODY).getUnclippedBoundsInRoot()
        assertEquals(card.top.value, collapsedAgain.top.value, 1f)
    }

    private fun ComposeUiTest.saveCapture(name: String) {
        val dir = File("build/review/v4-all-tools").apply { mkdirs() }
        val bitmap = captureWindowImage()
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encode failed")
            data.use { File(dir, "$name.png").writeBytes(it.bytes) }
        }
    }
}
