package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The tablet master-detail composition: a canvas with one floating
 * document capsule and a 380 dp inspector whose strip lists Layers plus
 * every catalogued tool in registry order. The inspector's foot owns the
 * open tool's Cancel/Apply; the capsule's Save waits for the draft to
 * settle.
 */
@OptIn(ExperimentalTestApi::class)
class EditorTabletWorkspaceUiTest {

    private var session: PhotoEditorSession? = null

    private fun ComposeUiTest.liveSession(
        widthDp: Float = 1280f,
        heightDp: Float = 1024f
    ): PhotoEditorSession {
        val s = openedSession(samplePixels())
        session = s
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(LocalDensity.current.density, 1f)
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

    @Test
    fun masterDetailKeepsTheCanvasAndA380Inspector() =
        runDesktopComposeUiTest(width = 1280, height = 1024) {
        liveSession()

        // The master field and the fixed inspector — no rail, no
        // separate layers pane: those belong to the four-pane Desktop.
        onNodeWithTag(TAG_PHOTO_HEADER).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_CANVAS).assertIsDisplayed()
        onNodeWithTag(TAG_TABLET_INSPECTOR)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(380.dp)
            .assertHeightIsEqualTo(1024.dp)
        onNodeWithTag(TAG_DESKTOP_RAIL).assertDoesNotExist()
        onNodeWithTag(TAG_DESKTOP_LAYERS_PANE).assertDoesNotExist()
        onNodeWithTag(TAG_DESKTOP_PROPS_PANE).assertDoesNotExist()

        // The canvas keeps its zoom field and the focus destination; the
        // inspector opens on the Edit surface with the real histogram.
        onNodeWithTag(TAG_TABLET_ZOOM).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS).assertIsDisplayed()
        onNodeWithTag(TAG_TABLET_HISTOGRAM).assertIsDisplayed()
    }

    @Test
    fun toolStripListsLayersThenEveryCataloguedTool() =
        runDesktopComposeUiTest(width = 1280, height = 1024) {
        liveSession()
        val catalogued = photoToolEntries().filter { it.catalogued }

        // The strip is data-driven: Layers plus one cell per catalogued
        // registry entry, in registry order — no invented category tabs.
        onNodeWithTag(TAG_TABLET_TOOL_STRIP)
            .assertIsDisplayed()
            .onChildren()
            .assertCountEquals(catalogued.size + 1)
        onNodeWithTag(TAG_LAYERS).assertIsDisplayed()
        catalogued.forEach { entry ->
            onNodeWithTag(entry.tag).assertExists(
                "missing strip cell for ${entry.descriptor.id}"
            )
        }
    }

    @Test
    fun lightTransactionPinsCancelApplyAndLocksSaveUntilApplied() =
        runDesktopComposeUiTest(width = 1280, height = 1024) {
        val s = liveSession()

        onNodeWithTag(TAG_CATEGORY_LIGHT).performClick()
        awaitState { it.state.value.toolEditing }

        // The inspector foot owns the open transaction, and Save waits
        // for the draft to settle (the capsule has no room for both).
        onNodeWithTag(TAG_TOOL_CANCEL).assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertIsNotEnabled()

        onNodeWithTag(TAG_SLIDER_EXPOSURE).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(2.0f) }
        awaitState { !it.state.value.interactionActive }
        assertTrue(
            abs(s.state.value.document!!.adjustments.exposureStops - 2.0f) < 0.01f
        )

        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitState { !it.state.value.toolEditing }
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertDoesNotExist()
        onNodeWithTag(TAG_SAVE).assertIsEnabled()
    }

    @Test
    fun compareToggleSplitsTheCanvas() =
        runDesktopComposeUiTest(width = 1280, height = 1024) {
        liveSession()

        onNodeWithTag(TAG_SPLIT_HANDLE).assertDoesNotExist()
        onNodeWithTag(TAG_BEFORE_CHIP).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_SPLIT_HANDLE).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(TAG_SPLIT_ORIGINAL).assertExists()

        onNodeWithTag(TAG_BEFORE_CHIP).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_SPLIT_HANDLE).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun canvasFocusButtonEntersAndLeavesFocusMode() =
        runDesktopComposeUiTest(width = 1280, height = 1024) {
        liveSession()

        onNodeWithTag(TAG_FOCUS).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_FOCUS_EXIT).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(TAG_TABLET_INSPECTOR).assertDoesNotExist()

        onNodeWithTag(TAG_FOCUS_EXIT).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_TABLET_INSPECTOR).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(TAG_FOCUS).assertIsDisplayed()
    }

    @Test
    fun capsuleMoreMenuCarriesTheRealSecondarySurfaces() =
        runDesktopComposeUiTest(width = 1280, height = 1024) {
        liveSession()

        onNodeWithTag(TAG_OVERFLOW).performClick()
        onNodeWithTag(TAG_PANEL_SOURCE).assertIsDisplayed()
        onNodeWithTag(TAG_PANEL_EDITS).assertIsDisplayed()
        onNodeWithTag(TAG_RESET_ALL).assertIsDisplayed()
    }
}
