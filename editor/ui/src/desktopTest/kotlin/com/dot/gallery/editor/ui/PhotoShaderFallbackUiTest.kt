package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The fallback path end to end: a renderer whose draw rejects every frame
 * must hand the canvas back to the CPU pipeline — edits keep applying
 * through the real session, the preview re-renders the latest recipe and no
 * user-facing error strip appears.
 */
@OptIn(ExperimentalTestApi::class)
class PhotoShaderFallbackUiTest {

    private var session: PhotoEditorSession? = null
    // Session errors arrive on the owner main thread while waitUntil
    // polls from the test scheduler — use a concurrent list.
    private val errors = CopyOnWriteArrayList<Throwable>()

    @BeforeTest
    fun installFailingFactory() {
        photoShaderRendererFactory = { _: ImageBitmap, _: (Throwable) -> Unit ->
            object : PhotoShaderRenderer {
                override fun draw(scope: DrawScope, edits: PhotoEdits): Boolean =
                    false

                override fun close() {}
            }
        }
    }

    @AfterTest
    fun tearDown() {
        photoShaderRendererFactory =
            { source, onFailure -> createPhotoShaderRenderer(source, onFailure) }
        session?.close()
        session = null
    }

    @Test
    fun drawRejectionFallsBackToCpuPreview() = runComposeUiTest {
        val pixels = samplePixels()
        lateinit var s: PhotoEditorSession
        runOnUiThread {
            s = PhotoEditorSession(
                scope = CoroutineScope(Dispatchers.Main),
                media = FixturePhotoMediaAccess { pixels },
                onError = { errors += it }
            )
            s.open("fixture://editor_sample.jpg")
        }
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp).fillMaxSize()) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                    )
                }
            }
        }
        awaitImage(s)

        // The failed shader frame is reported exactly once to diagnostics
        // and the session re-enabled the CPU pipeline for it.
        waitUntil(timeoutMillis = 15_000) {
            errors.any { it is ShaderCanvasUnavailableException }
        }
        assertNull(s.state.value.error, "fallback must not surface a user error")

        // Editing still works and the CPU preview re-renders the recipe —
        // inside the open tool the gesture is a local step, so watch the
        // interaction flag rather than the tool-wide draft.
        val stale = s.state.value.preview
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.5f, 0f))
            moveTo(centerRight)
            up()
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.let {
                !it.interactionActive &&
                    it.document?.adjustments?.exposureStops != 0f &&
                    it.preview !== stale
            }
        }
        onNodeWithTag(TAG_IMAGE).assertIsDisplayed()
    }
}
