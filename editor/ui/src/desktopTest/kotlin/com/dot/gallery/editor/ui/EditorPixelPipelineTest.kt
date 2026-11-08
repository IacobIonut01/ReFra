package com.dot.gallery.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.photo.PhotoImageReader
import com.dot.gallery.editor.render.photo.PhotoMediaAccess
import com.dot.gallery.editor.render.photo.PhotoRaster
import com.github.panpf.sketch.BitmapImage
import com.github.panpf.zoomimage.util.IntRectCompat
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorPixelPipelineTest {
    private val pixels = SrgbPixels(
        PixelSize(8, 6),
        IntArray(48) { i -> 0xFF000000.toInt() or ((i * 17 and 255) shl 16) or (i * 3 and 255) }
    )
    private val document = PhotoDocument(MediaAsset.Image("base", "fixture://base", pixels.size))

    @Test
    fun sketchEngineUsesThePixelFetcherAndDecoderWithNoFallbackBitmap() =
        runDesktopComposeUiTest(width = 80, height = 60) {
            val color = 0xFF5599CC.toInt()
            val pixels = SrgbPixels(PixelSize(8, 6), IntArray(48) { color })
            setContent {
                TestEditorRoot {
                    val painter = rememberEditorPixelPainter(pixels, fallback = null)!!
                    Box(
                        Modifier.fillMaxSize().testTag("sketch.pixels").semantics {
                            stateDescription = if (painter.intrinsicSize.width > 0f) "ready" else "pending"
                        }
                    ) {
                        Image(painter, "Photo", Modifier.fillMaxSize())
                    }
                }
            }
            waitUntil(timeoutMillis = 15_000) {
                onNodeWithTag("sketch.pixels").fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription] == "ready"
            }
            val image = onNodeWithTag("sketch.pixels").captureToImage().toPixelMap()
            assertEquals(color, image[image.width / 2, image.height / 2].toArgb())
        }

    private class Media(private val pixels: SrgbPixels) : PhotoMediaAccess {
        val opened = CopyOnWriteArrayList<TrackingRaster>()
        override suspend fun open(reference: String): PhotoRaster =
            TrackingRaster(FixturePhotoRaster(pixels, reference)).also(opened::add)
        override suspend fun export(
            document: PhotoDocument,
            format: com.dot.gallery.editor.core.model.PhotoExportFormat?,
            onPublished: (PhotoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): PhotoExportResult = error("not used")
    }

    private class TrackingRaster(private val raster: PhotoRaster) : PhotoRaster by raster {
        val closed = CompletableDeferred<Unit>()
        override fun close() {
            raster.close()
            closed.complete(Unit)
        }
    }

    @Test
    fun sketchDecoderTranslatesEditorPixelsWithoutAnEncodedImage() = runTest {
        val source = EditorPixelDataSource(pixels)
        val fetched = EditorPixelFetcher(source).fetch().getOrThrow()
        assertSame(source, fetched.dataSource)
        val result = EditorPixelDecoder(source).decode()
        val image = assertIs<BitmapImage>(result.image)
        val rendered = image.bitmap.asComposeImageBitmap().toPixelMap()
        val actual = IntArray(48) { i -> rendered[i % 8, i / 8].toArgb() }
        assertContentEquals(pixels.copyPixels(), actual)
        assertEquals(8, result.imageInfo.width)
        assertEquals(6, result.imageInfo.height)
    }

    @Test
    fun independentZoneDecoderCopiesRenderEditedOutputAndCloseTheirOwnHandles() = runTest {
        withContext(Dispatchers.Default) {
            val media = Media(pixels)
            val doc = document.copy(geometry = PhotoGeometry(quarterTurns = 1, flipHorizontal = true))
            val errors = CopyOnWriteArrayList<Throwable>()
            val image = EditorPixelImage(doc, null, { PhotoImageReader.open(media, doc) }, errors::add)
            val first = EditorPixelRegionDecoder(image)
            val second = first.copy()
            try {
                val region = IntRectCompat(1, 2, 4, 4)
                val one = first.decodeRegion(region, 1).asComposeImageBitmap().toPixelMap()
                val two = second.decodeRegion(region, 2).asComposeImageBitmap().toPixelMap()
                val mapping = PhotoMapping(pixels.size, 1, doc.geometry)
                for (y in 0 until 2) {
                    for (x in 0 until 3) {
                        val (sx, sy) = mapping.sourcePoint(x + 1, y + 2)
                        assertEquals(pixels.argbAt(sx, sy), one[x, y].toArgb())
                    }
                }
                val (sx, sy) = mapping.sourcePoint(3, 2)
                assertEquals(pixels.argbAt(sx, sy), two[1, 0].toArgb())
                assertEquals(2, media.opened.size)
                first.close()
                withTimeout(5_000) { media.opened[0].closed.await() }
                assertFalse(media.opened[1].closed.isCompleted)
                second.decodeRegion(region, 1)
                assertEquals(emptyList(), errors.toList())
            } finally {
                first.close()
                second.close()
                withTimeout(5_000) { media.opened.forEach { it.closed.await() } }
            }
        }
    }

    @Test
    fun snapshotsHaveDistinctTileKeysAndCoarseLevelsUseOnlySufficientPreviewDensity() {
        val preview = SrgbPixels(PixelSize(2, 2), intArrayOf(-1, -2, -3, -4))
        val full = document.copy(source = document.source.copy(size = PixelSize(8, 8)))
        fun image(doc: PhotoDocument) = EditorPixelImage(
            doc, preview, { error("coarse preview must not open a reader") }, { throw it }
        )
        val first = image(full)
        val changed = image(full.copy(adjustments = ColorAdjustments(exposureStops = 0.7f)))
        assertNotEquals(first.key, changed.key)
        val zone = PixelRect(1, 1, 7, 7)
        assertNull(first.previewRegion(zone, 2))
        assertContentEquals(intArrayOf(-1, -2, -3, -4), first.previewRegion(zone, 4)!!.copyPixels())
    }
}
