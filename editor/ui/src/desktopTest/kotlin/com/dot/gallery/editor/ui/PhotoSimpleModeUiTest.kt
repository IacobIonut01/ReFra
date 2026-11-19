package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Simple photo composition: the labelled dock (Auto, pins, More)
 * drives both the compact sheet layout and the large window's rail +
 * tool card, and the header's mode switch flips back to the Studio
 * layouts without losing the document or the open tool.
 */
@OptIn(ExperimentalTestApi::class)
class PhotoSimpleModeUiTest {

    private var session: PhotoEditorSession? = null

    private fun ComposeUiTest.simpleSession(
        widthDp: Float,
        heightDp: Float,
        pixels: SrgbPixels = samplePixels(),
        fontScale: () -> Float = { 1f }
    ): PhotoEditorSession {
        val s = openedSession(pixels)
        session = s
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(LocalDensity.current.density, fontScale())
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

    private fun ComposeUiTest.awaitState(condition: (PhotoEditorSession) -> Boolean) {
        val s = session ?: error("no session")
        waitUntil(timeoutMillis = 15_000) { condition(s) }
    }

    private fun darkPixels(): SrgbPixels {
        val size = PixelSize(64, 64)
        val argb = (0xFF shl 24) or (0x14 shl 16) or (0x14 shl 8) or 0x14
        return SrgbPixels(size, IntArray(size.width * size.height) { argb })
    }

    @Test
    fun largeWindowShowsRailHeaderAndCanvasOnly() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
        simpleSession(1280f, 800f)

        onNodeWithTag(TAG_SIMPLE_HEADER).assertIsDisplayed()
        onNodeWithTag(TAG_SIMPLE_RAIL).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_CANVAS).assertIsDisplayed()
        onNodeWithTag(TAG_SIMPLE_CARD).assertDoesNotExist()
    }

    @Test
    fun dockPinOpensToolCardAndDoneApplies() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
        simpleSession(1280f, 800f)

        onNodeWithTag("editor.pin.photo.light").performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_SIMPLE_CARD)
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()

        onNodeWithTag(TAG_SLIDER_EXPOSURE).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(2.0f) }
        onNodeWithTag(TAG_TOOL_APPLY).performClick()

        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_SIMPLE_CARD)
                .fetchSemanticsNodes().isEmpty()
        }
        val s = session ?: error("no session")
        assertTrue(
            kotlin.math.abs(
                s.state.value.document!!.adjustments.exposureStops - 2.0f
            ) < 0.01f
        )
        awaitState { !it.state.value.toolEditing }
    }

    @Test
    fun autoDockAppliesOneStepAndSecondTapUndoes() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
        simpleSession(1280f, 800f, pixels = darkPixels())

        onNodeWithTag(TAG_DOCK_AUTO).performClick()
        awaitState {
            it.state.value.canUndo &&
                it.state.value.document!!.adjustments.exposureStops > 0.5f
        }
        onNodeWithTag(TAG_DOCK_AUTO).assertIsSelected()

        onNodeWithTag(TAG_DOCK_AUTO).performClick()
        awaitState {
            !it.state.value.canUndo &&
                it.state.value.document!!.adjustments.exposureStops == 0f
        }
        onNodeWithTag(TAG_DOCK_AUTO).assertIsNotSelected()
    }

    @Test
    fun compactHeaderKeepsCommandsAndSave() =
        runDesktopComposeUiTest(width = 360, height = 640) {
        var scale by mutableStateOf(1f)
        simpleSession(360f, 640f, fontScale = { scale })

        fun assertHeaderCommands() {
            onNodeWithTag(TAG_BACK).assertIsDisplayed()
            onNodeWithTag(TAG_UNDO).assertIsDisplayed()
            onNodeWithTag(TAG_REDO).assertIsDisplayed()
            onNodeWithTag(TAG_BEFORE_CHIP).assertIsDisplayed()
            onNodeWithTag(TAG_SAVE).assertIsDisplayed()
            onNodeWithTag(TAG_SAVE_CHEVRON).assertIsDisplayed()

            // Every control stays inside the window and none overlaps.
            val root = onRoot().fetchSemanticsNode().boundsInRoot
            val rects = listOf(
                TAG_BACK, TAG_UNDO, TAG_REDO,
                TAG_BEFORE_CHIP, TAG_SAVE, TAG_SAVE_CHEVRON
            ).associateWith {
                onNodeWithTag(it).fetchSemanticsNode().boundsInRoot
            }.values.toList()
            rects.forEach { r ->
                assertTrue(
                    r.left >= root.left && r.right <= root.right &&
                        r.top >= root.top && r.bottom <= root.bottom,
                    "header control outside the window: $r"
                )
            }
            for (i in rects.indices) {
                for (j in i + 1 until rects.size) {
                    assertFalse(
                        rects[i].overlaps(rects[j]),
                        "header controls overlap: ${rects[i]} vs ${rects[j]}"
                    )
                }
            }
        }

        assertHeaderCommands()
        scale = 2f
        waitForIdle()
        assertHeaderCommands()
    }

    @Test
    fun compactKeepsTheDockWithTheOpenToolSelected() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        simpleSession(412f, 832f)

        onNodeWithTag(TAG_EDITOR_PINS).assertIsDisplayed()
        onNodeWithTag(TAG_DOCK_AUTO).assertIsDisplayed()

        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_EDITOR_PINS).assertIsDisplayed()
        onNodeWithTag("editor.pin.photo.light").assertIsSelected()

        // More opens the catalogue over the tool surface.
        onNodeWithTag(TAG_CATALOGUE).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_CATALOGUE_GRID)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
