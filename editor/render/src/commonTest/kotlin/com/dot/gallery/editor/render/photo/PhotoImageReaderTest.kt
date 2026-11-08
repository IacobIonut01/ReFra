package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoImageReaderTest {
    private val size = PixelSize(8, 6)
    private val source = MediaAsset.Image("base", "fixture://base", size)
    private val document = PhotoDocument(source)

    private class Raster(override val info: PhotoInfo) : PhotoRaster {
        var closes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun readRegion(rect: PixelRect): SrgbPixels {
            gate?.await()
            return SrgbPixels(PixelSize(rect.width, rect.height), IntArray(rect.width * rect.height) { -1 })
        }
        override suspend fun readPreview(maxEdge: Int) = error("zone reader must not decode the whole preview")
        override fun close() { closes++ }
    }

    private class Media(val raster: Raster) : PhotoMediaAccess {
        override suspend fun open(reference: String): PhotoRaster {
            check(reference == raster.info.reference) { "missing source" }
            return raster
        }
        override suspend fun export(
            document: PhotoDocument,
            format: com.dot.gallery.editor.core.model.PhotoExportFormat?,
            onPublished: (PhotoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): PhotoExportResult = error("not used")
    }

    @Test
    fun closeIsIdempotentAndPreventsLaterReads() = runTest {
        val raster = Raster(PhotoInfo(source.reference, "base", size))
        val reader = PhotoImageReader.open(Media(raster), document, StandardTestDispatcher(testScheduler))
        assertEquals(size, reader.size)
        assertEquals(PixelSize(2, 3), reader.readRegion(PixelRect(1, 2, 2, 3)).size)
        reader.close()
        reader.close()
        assertEquals(1, raster.closes)
        assertFailsWith<IllegalStateException> {
            reader.readRegion(PixelRect(0, 0, 1, 1))
        }
    }

    @Test
    fun failedLayerOpenClosesPreviouslyAcquiredBase() = runTest {
        val raster = Raster(PhotoInfo(source.reference, "base", size))
        val layer = PhotoImageLayer(
            "layer", "Missing", MediaAsset.Image("overlay", "fixture://missing", size)
        )
        assertFailsWith<IllegalStateException> {
            PhotoImageReader.open(
                Media(raster), document.copy(layers = persistentListOf(layer)),
                StandardTestDispatcher(testScheduler)
            )
        }
        assertEquals(1, raster.closes)
    }

    @Test
    fun changedSourceDimensionsCloseTheAcquiredHandle() = runTest {
        val raster = Raster(PhotoInfo(source.reference, "changed", PixelSize(9, 6)))
        assertFailsWith<IllegalArgumentException> {
            PhotoImageReader.open(Media(raster), document, StandardTestDispatcher(testScheduler))
        }
        assertEquals(1, raster.closes)
    }

    @Test
    fun cancellationReleasesTheReadLockBeforeClose() = runTest {
        val raster = Raster(PhotoInfo(source.reference, "base", size)).apply {
            gate = CompletableDeferred()
        }
        val reader = PhotoImageReader.open(Media(raster), document, StandardTestDispatcher(testScheduler))
        val read = async { reader.readRegion(PixelRect(1, 1, 2, 2)) }
        runCurrent()
        val closing = async { reader.close() }
        runCurrent()
        assertFalse(closing.isCompleted)
        assertEquals(0, raster.closes)
        read.cancelAndJoin()
        closing.await()
        assertEquals(1, raster.closes)
    }
}
