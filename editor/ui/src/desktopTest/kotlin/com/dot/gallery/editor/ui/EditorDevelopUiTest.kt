package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PhotoMarkup
import com.dot.gallery.editor.core.model.PhotoMarkupStroke
import com.dot.gallery.editor.core.model.PhotoMarkupType
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.core.model.PhotoRawWhiteBalance
import com.dot.gallery.editor.core.model.PhotoRetouch
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.photo.PhotoInfo
import com.dot.gallery.editor.render.photo.PhotoMediaAccess
import com.dot.gallery.editor.render.photo.PhotoRaster
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * A stand-in RAW source: each develop paints a flat colour derived from the
 * recipe, so a test can see the pixels follow the develop stage.
 */
private class FakeRawMedia : PhotoMediaAccess {
    val size = PixelSize(120, 90)
    var exportedFormat: PhotoExportFormat? = null
    var exportedDevelop: PhotoRawDevelop? = null

    fun pixelsFor(develop: PhotoRawDevelop, target: PixelSize = size): SrgbPixels {
        val grey = (120 + develop.exposure * 30f).toInt().coerceIn(0, 255)
        val blue = if (develop.whiteBalance == PhotoRawWhiteBalance.Daylight) 220 else grey
        val argb = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or blue
        return SrgbPixels(target, IntArray(target.width * target.height) { argb })
    }

    private inner class Raster(val develop: PhotoRawDevelop, reference: String) : PhotoRaster {
        override val info = PhotoInfo(reference, "sample.dng", size, isRaw = true)
        override suspend fun readRegion(rect: PixelRect) =
            pixelsFor(develop, PixelSize(rect.width, rect.height))
        override suspend fun readPreview(maxEdge: Int) = pixelsFor(develop)
        override fun close() = Unit
    }

    override suspend fun open(reference: String): PhotoRaster = Raster(PhotoRawDevelop(), reference)

    override suspend fun openDeveloped(reference: String, develop: PhotoRawDevelop): PhotoRaster =
        Raster(develop, reference)

    override suspend fun developPreview(
        reference: String,
        develop: PhotoRawDevelop,
        maxEdge: Int,
        reuseBase: Boolean
    ) = pixelsFor(develop)

    override suspend fun export(
        document: PhotoDocument,
        format: PhotoExportFormat?,
        onPublished: (PhotoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): PhotoExportResult {
        exportedFormat = format
        exportedDevelop = document.develop
        val result = PhotoExportResult("file:///developed", "image/tiff", size)
        onPublished(result)
        return result
    }
}

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorDevelopUiTest {

    private fun ComposeUiTest.stagedRaw(media: FakeRawMedia): PhotoEditorSession {
        lateinit var session: PhotoEditorSession
        runOnUiThread {
            session = PhotoEditorSession(
                scope = CoroutineScope(Dispatchers.Main),
                media = media,
                onError = {}
            )
            session.open("fixture://sample.dng")
        }
        setContent {
            TestEditorRoot(darkTheme = true) {
                PhotoEditorScreen(
                    session = session,
                    onClose = {},
                    onRetryOpen = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        awaitImage(session)
        return session
    }

    private fun ComposeUiTest.save(name: String) {
        waitForIdle()
        val directory = File("build/review/retouch").apply { mkdirs() }
        val image = Image.makeFromBitmap(
            onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap()
        )
        File(directory, "$name.png").writeBytes(
            image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        )
    }

    private fun ComposeUiTest.openDevelop() {
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoDevelop.value)).performClick()
        onNodeWithTag("editor.develop").assertIsDisplayed()
        waitForIdle()
    }

    @Test
    fun aRawOpensOnItsOwnDock() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedRaw(FakeRawMedia())
        try {
            save("raw-home")
            onNodeWithTag(photoPinTag(EditorToolIds.PhotoCrop.value)).assertIsDisplayed()
            onNodeWithTag(photoPinTag(EditorToolIds.PhotoDevelop.value)).assertIsDisplayed()
            onNodeWithTag(photoPinTag(EditorToolIds.PhotoLooks.value)).assertIsDisplayed()
            assertTrue(
                onAllNodesWithTag(photoPinTag(EditorToolIds.PhotoColour.value))
                    .fetchSemanticsNodes().isEmpty()
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun ordinaryPhotosNeverListDevelop() {
        assertTrue(photoToolEntries().none { it.catalogued && it.descriptor.id == EditorToolIds.PhotoDevelop })
        assertTrue(
            photoToolEntries(rawSource = true)
                .any { it.catalogued && it.descriptor.id == EditorToolIds.PhotoDevelop }
        )
    }

    @Test
    fun theDevelopRedecodesTheSourceAndUndoRestoresIt() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = stagedRaw(FakeRawMedia())
            try {
                openDevelop()
                save("develop-wb")
                val before = session.state.value.preview!!.argbAt(10, 10)
                onNodeWithTag("editor.develop.tile.Daylight").performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.document?.develop?.whiteBalance ==
                        PhotoRawWhiteBalance.Daylight
                }
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.preview!!.argbAt(10, 10) != before
                }
                assertEquals(220, session.state.value.preview!!.argbAt(10, 10) and 0xFF)
                runOnUiThread { session.undo() }
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.preview!!.argbAt(10, 10) == before
                }
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun everyTabHasItsControls() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedRaw(FakeRawMedia())
        try {
            openDevelop()
            onNodeWithTag("editor.develop.tile.Custom").performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.document?.develop?.whiteBalance == PhotoRawWhiteBalance.Custom
            }
            onNodeWithTag("editor.develop.param.developTemperature").assertIsDisplayed()
            save("develop-custom")
            onNodeWithTag("editor.develop.tab.Tone").performClick()
            onNodeWithTag("editor.develop.param.developExposure").assertIsDisplayed()
            save("develop-tone")
            onNodeWithTag("editor.develop.tab.Detail").performClick()
            onNodeWithTag("editor.develop.option.Ahd").assertIsDisplayed()
            onNodeWithTag("editor.develop.half").assertIsDisplayed()
            save("develop-detail")
            onNodeWithTag("editor.develop.tab.Colour").performClick()
            onNodeWithTag("editor.develop.option.AdobeRgb").assertIsDisplayed()
            save("develop-colour")
            onNodeWithTag("editor.develop.tab.Output").performClick()
            onNodeWithTag("editor.develop.tile.Rebuild").assertIsDisplayed()
            save("develop-output")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun aDevelopOnlyRawOffersTiffAndSavesTheRecipe() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val media = FakeRawMedia()
            val session = stagedRaw(media)
            try {
                val develop = PhotoRawDevelop(exposure = 1f)
                runOnUiThread {
                    session.applyEdits(
                        "Develop", session.state.value.document!!.toEdits().copy(develop = develop)
                    )
                }
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.document?.develop == develop && !session.state.value.isDrafting
                }
                onNodeWithTag(TAG_SAVE).performClick()
                waitForIdle()
                val menuItem = onAllNodesWithTag(TAG_SAVE_MENU_COPY).fetchSemanticsNodes()
                if (menuItem.isNotEmpty()) onNodeWithTag(TAG_SAVE_MENU_COPY).performClick()
                onNodeWithTag(TAG_RAW_EXPORT_SHEET).assertIsDisplayed()
                save("raw-export")
                onNodeWithTag("$TAG_RAW_EXPORT_SHEET.TIFF_16").performClick()
                waitUntil(timeoutMillis = 15_000) { media.exportedFormat != null }
                assertEquals(PhotoExportFormat.TIFF_16, media.exportedFormat)
                assertEquals(develop, media.exportedDevelop)
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun anyOtherEditTakesTiffAwayAndSaysWhy() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val media = FakeRawMedia()
            val session = stagedRaw(media)
            try {
                val markup = PhotoMarkup(
                    persistentListOf(
                        PhotoMarkupStroke(
                            PhotoMarkupType.Pen,
                            persistentListOf(NormalizedPoint(0.5f, 0.5f)),
                            size = 30f
                        )
                    )
                )
                runOnUiThread {
                    session.applyEdits(
                        "Markup",
                        session.state.value.document!!.toEdits()
                            .copy(retouch = PhotoRetouch(markup = markup))
                    )
                }
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.document?.retouch?.markup == markup
                }
                onNodeWithTag(TAG_SAVE).performClick()
                waitForIdle()
                val menuItem = onAllNodesWithTag(TAG_SAVE_MENU_COPY).fetchSemanticsNodes()
                if (menuItem.isNotEmpty()) onNodeWithTag(TAG_SAVE_MENU_COPY).performClick()
                onNodeWithTag("$TAG_RAW_EXPORT_SHEET.note").assertIsDisplayed()
                onNodeWithTag("$TAG_RAW_EXPORT_SHEET.JPEG").assertIsDisplayed()
                assertTrue(
                    onAllNodesWithTag("$TAG_RAW_EXPORT_SHEET.TIFF_8").fetchSemanticsNodes().isEmpty()
                )
                save("raw-export-no-tiff")
            } finally {
                runOnUiThread { session.close() }
            }
        }
}
