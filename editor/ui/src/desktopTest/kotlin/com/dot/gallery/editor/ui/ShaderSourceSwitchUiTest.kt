package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Source-switch guard: when the session moves from photo A to photo B, the
 * bitmap decoded for A must never be drawn or handed to the shader factory
 * under B's reference. A recording factory tracks which pixels each renderer
 * was compiled for; a recording renderer proves the stale one is retired
 * before the new source's frames can draw.
 */
@OptIn(ExperimentalTestApi::class)
class ShaderSourceSwitchUiTest {

    private var session: PhotoEditorSession? = null
    private val errors = CopyOnWriteArrayList<Throwable>()
    private val factorySources = CopyOnWriteArrayList<ImageBitmap>()
    private val renderers = CopyOnWriteArrayList<RecordingRenderer>()

    private class RecordingRenderer : PhotoShaderRenderer {
        val draws = AtomicInteger()
        val closed = AtomicBoolean()

        override fun draw(scope: DrawScope, edits: PhotoEdits): Boolean {
            draws.incrementAndGet()
            return true
        }

        override fun close() {
            closed.set(true)
        }
    }

    @BeforeTest
    fun installRecordingFactory() {
        photoShaderRendererFactory = { source: ImageBitmap, _: (Throwable) -> Unit ->
            factorySources += source
            RecordingRenderer().also(renderers::add)
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
    fun newSourceDropsStaleBitmapAndRenderer() = runComposeUiTest {
        val red = SrgbPixels(PixelSize(4, 2), IntArray(8) { 0xFFFF0000.toInt() })
        val blue = SrgbPixels(PixelSize(4, 2), IntArray(8) { 0xFF0000FF.toInt() })
        val opens = AtomicInteger()
        lateinit var s: PhotoEditorSession
        runOnUiThread {
            s = PhotoEditorSession(
                scope = CoroutineScope(Dispatchers.Main),
                media = FixturePhotoMediaAccess {
                    if (opens.incrementAndGet() == 1) red else blue
                },
                onError = { errors += it }
            )
            s.open("fixture://a.png")
        }
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp).fillMaxSize()) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://a.png") }
                    )
                }
            }
        }
        awaitImage(s)
        waitUntil(timeoutMillis = 15_000) { renderers.isNotEmpty() }
        val aRenderer = renderers.first()

        runOnUiThread { s.open("fixture://b.png") }

        // Once B is the live source it must have a renderer built from B's
        // pixels — and the A renderer must already be retired.
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.info?.reference == "fixture://b.png" &&
                renderers.size >= 2
        }
        val bRenderer = renderers.last()
        assertTrue(
            aRenderer.closed.get(),
            "renderer for the previous source was still live under the new one"
        )
        assertTrue(
            factorySources.last().cornerIsBlue(),
            "latest shader source was not decoded from the new photo"
        )

        // Force real frames under B: the new renderer draws, the stale one
        // stays frozen at whatever it accumulated under A.
        val aDraws = aRenderer.draws.get()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(centerRight)
            up()
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.adjustments?.exposureStops != 0f &&
                bRenderer.draws.get() > 0
        }
        assertEquals(
            aDraws, aRenderer.draws.get(),
            "stale renderer drew a frame for the new source"
        )
        onNodeWithTag(TAG_IMAGE).assertIsDisplayed()
    }

    private fun ImageBitmap.cornerIsBlue(): Boolean {
        val c = toPixelMap()[0, 0]
        return c.blue > 0.9f && c.red < 0.1f
    }
}
