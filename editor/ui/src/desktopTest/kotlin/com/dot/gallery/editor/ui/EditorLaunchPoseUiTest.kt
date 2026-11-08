package com.dot.gallery.editor.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A host-provided [EditorLaunchPose] opens the workspace on the requested
 * surface through the real selection path, once the document has opened,
 * and a screen without a pose still opens on its home state.
 */
@OptIn(ExperimentalTestApi::class)
class EditorLaunchPoseUiTest {

    private fun poseTest(
        pose: EditorLaunchPose?,
        body: ComposeUiTest.(PhotoEditorSession) -> Unit
    ) = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = openedSession(samplePixels())
        try {
            setContent {
                TestEditorRoot {
                    CompositionLocalProvider(LocalEditorLaunchPose provides pose) {
                        PhotoEditorScreen(
                            session, onClose = {}, onRetryOpen = {},
                            renderPreference = PhotoRenderPreference.Cpu
                        )
                    }
                }
            }
            awaitImage(session)
            waitForIdle()
            body(session)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun noPoseOpensTheHomeState() = poseTest(null) { session ->
        assertFalse(session.state.value.toolEditing)
        assertTrue(onAllNodesWithTag(TAG_EDITOR_SETTINGS).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun toolPoseBeginsTheToolEdit() = poseTest(
        EditorLaunchPose(tool = EditorToolIds.PhotoCurves.value)
    ) { session ->
        waitUntil(timeoutMillis = 15_000) { session.state.value.toolEditing }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag("editor.curves.editor").fetchSemanticsNodes().isNotEmpty()
        }
        // Opening a tool records nothing until it is applied.
        assertFalse(session.state.value.canUndo)
    }

    @Test
    fun settingsPoseOpensTheSettingsPage() = poseTest(
        EditorLaunchPose(settingsOpen = true)
    ) { session ->
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_EDITOR_SETTINGS).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(false, session.state.value.toolEditing)
    }

    @Test
    fun cataloguePoseOpensTheToolGrid() = poseTest(
        EditorLaunchPose(catalogueOpen = true)
    ) { _ ->
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_CATALOGUE_GRID).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
