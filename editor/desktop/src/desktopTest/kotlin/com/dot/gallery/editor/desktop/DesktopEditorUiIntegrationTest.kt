package com.dot.gallery.editor.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.ui.EditorTheme
import com.dot.gallery.editor.ui.PhotoEditorScreen
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Full-path check through the real [DesktopPhotoMediaAccess]: a UI-driven
 * rotate, a UI click on Save copy, the exported PNG re-decoded from disk,
 * and the source file verified byte-identical afterwards. Tags are the
 * `editor.*` literals — they are :editor:ui internals.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopEditorUiIntegrationTest {

    private fun writeFixture(dir: Path): Path {
        // Non-square so the quarter-turn is visible in the output size.
        val image = BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB)
        val rgb = image.getRGB(0, 0, 320, 200, null, 0, 320)
        for (i in rgb.indices) rgb[i] = (0xFF shl 24) or (i % 0x00FFFF)
        image.setRGB(0, 0, 320, 200, rgb, 0, 320)
        val file = dir.resolve("ui-integration.png")
        ImageIO.write(image, "png", file.toFile())
        return file
    }

    @Test
    fun uiDrivenEditSavesRealPngCopyAndLeavesSourceAlone() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val dir = Files.createTempDirectory("editor-ui-it")
            val source = writeFixture(dir)
            val sourceBytes = Files.readAllBytes(source)
            val errors = mutableListOf<Throwable>()

            var session: PhotoEditorSession? = null
            runOnUiThread {
                session = PhotoEditorSession(
                    scope = CoroutineScope(Dispatchers.Main),
                    media = DesktopPhotoMediaAccess(
                        outputDirectory = dir,
                        onError = { errors += it }
                    ),
                    onError = { errors += it }
                )
                session!!.open(source.toString())
            }
            try {
                setContent {
                    EditorTheme {
                        PhotoEditorScreen(
                            session = session!!,
                            onClose = {},
                            onRetryOpen = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // Wait for the real decode: document + a landed bitmap.
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.let { it.document != null && !it.opening } &&
                        onAllNodesWithTag("editor.image")
                            .fetchSemanticsNodes().isNotEmpty()
                }

                // Rotate via the UI inside the Crop tool, Apply the tool
                // transaction (one committed step), then save via the UI —
                // an open tool draft intentionally blocks export.
                onNodeWithTag("editor.category.crop").performClick()
                onNodeWithTag("editor.rotateRight").performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.document?.geometry?.quarterTurns == 1
                }
                onNodeWithTag("editor.tool.apply").performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.let { !it.toolEditing && !it.isDrafting }
                }
                onNodeWithTag("editor.save").performClick()
                waitUntil(timeoutMillis = 30_000) {
                    session!!.state.value.let { it.exported != null && !it.exporting }
                }

                val result = session!!.state.value.exported!!
                val output = Path.of(URI.create(result.reference))
                assertTrue(Files.exists(output), "exported copy must exist")
                val decoded = output.toFile().inputStream().use {
                    ImageIO.read(it)
                }
                assertNotNull(decoded)
                // 320x200 rotated a quarter turn upright -> 200x320.
                assertEquals(200, decoded.width)
                assertEquals(320, decoded.height)
                assertTrue(
                    Files.readAllBytes(source).contentEquals(sourceBytes),
                    "source file must be untouched"
                )
                assertTrue(errors.isEmpty(), "unexpected errors: $errors")
            } finally {
                runOnUiThread { session?.close() }
            }
        }

    /**
     * The design-review capture on the real decode path: the fixture PNG is
     * probed and previewed by the packaged [DesktopPhotoMediaAccess], shown
     * in the redesigned wide chrome at 1280x800 — nothing here is a
     * stand-in. Saved as `build/review/redesign-photo-realfile.png`.
     */
    @Test
    fun realFilePhotoWideCapture() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val dir = Files.createTempDirectory("editor-photo-capture")
            val source = writeFixture(dir)
            val errors = mutableListOf<Throwable>()

            var session: PhotoEditorSession? = null
            runOnUiThread {
                session = PhotoEditorSession(
                    scope = CoroutineScope(Dispatchers.Main),
                    media = DesktopPhotoMediaAccess(
                        outputDirectory = dir,
                        onError = { errors += it }
                    ),
                    onError = { errors += it }
                )
                session!!.open(source.toString())
            }
            try {
                setContent {
                    EditorTheme {
                        PhotoEditorScreen(
                            session = session!!,
                            onClose = {},
                            onRetryOpen = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // Wait for the real decode: document + a landed bitmap.
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.let {
                        it.document != null && !it.opening &&
                            it.preview != null
                    }
                }
                waitForIdle()

                val bitmap = onRoot().captureToImage()
                val data = org.jetbrains.skia.Image
                    .makeFromBitmap(bitmap.asSkiaBitmap())
                    .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
                    ?: error("PNG encode failed")
                val out = File("build/review").apply { mkdirs() }
                File(out, "redesign-photo-realfile.png").writeBytes(data.bytes)
                assertTrue(errors.isEmpty(), "unexpected errors: $errors")
            } finally {
                runOnUiThread { session?.close() }
            }
        }
}
