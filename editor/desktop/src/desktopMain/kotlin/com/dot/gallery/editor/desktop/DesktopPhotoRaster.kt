package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoInfo
import com.dot.gallery.editor.render.photo.PhotoRaster
import java.awt.Rectangle
import java.io.IOException
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [PhotoRaster] over a cached ImageIO [ImageReader] + [ImageInputStream].
 * [readRegion] decodes only the requested raw rectangle via
 * `ImageReadParam.sourceRegion`; [readPreview] subsamples to fit `maxEdge`
 * and rotates to upright through the shared [PhotoMapping]. All reader use
 * is serialized on [mutex] and runs on [ioDispatcher]; decoded
 * [java.awt.image.BufferedImage]s stay local.
 */
internal class DesktopPhotoRaster(
    override val info: PhotoInfo,
    private val reader: ImageReader,
    private val input: ImageInputStream,
    private val ioDispatcher: CoroutineDispatcher
) : PhotoRaster {

    private val mutex = Mutex()
    private var closed = false

    override suspend fun readRegion(rect: PixelRect): SrgbPixels =
        withContext(ioDispatcher) { readRegionOnIo(rect) }

    private suspend fun readRegionOnIo(rect: PixelRect): SrgbPixels {
        check(rect.isInside(info.rawSize)) {
            "region $rect outside raw ${info.rawSize.width}x${info.rawSize.height}"
        }
        val image = mutex.withLock {
            check(!closed) { "raster is closed" }
            val param = reader.defaultReadParam
            param.sourceRegion =
                Rectangle(rect.left, rect.top, rect.width, rect.height)
            reader.read(0, param)
        } ?: throw IOException("region decode returned no image")
        try {
            check(image.width == rect.width && image.height == rect.height) {
                "region decode returned ${image.width}x${image.height} " +
                    "for ${rect.width}x${rect.height}"
            }
            val pixels = IntArray(rect.width * rect.height)
            image.getRGB(0, 0, rect.width, rect.height, pixels, 0, rect.width)
            return SrgbPixels(PixelSize(rect.width, rect.height), pixels)
        } finally {
            image.flush()
        }
    }

    override suspend fun readPreview(maxEdge: Int): SrgbPixels =
        withContext(ioDispatcher) { readPreviewOnIo(maxEdge) }

    private suspend fun readPreviewOnIo(maxEdge: Int): SrgbPixels {
        require(maxEdge > 0) { "maxEdge must be positive: $maxEdge" }
        val rawWidth = info.rawSize.width
        val rawHeight = info.rawSize.height
        // ceil(maxDim / maxEdge) so the subsampled image fits both axes.
        val sample = maxOf(1, (maxOf(rawWidth, rawHeight) + maxEdge - 1) / maxEdge)
        val image = mutex.withLock {
            check(!closed) { "raster is closed" }
            val param = reader.defaultReadParam
            param.setSourceSubsampling(sample, sample, 0, 0)
            reader.read(0, param)
        } ?: throw IOException("preview decode returned no image")
        try {
            check(image.width <= maxEdge && image.height <= maxEdge) {
                "subsampled preview ${image.width}x${image.height} exceeds $maxEdge"
            }
            val pixels = IntArray(image.width * image.height)
            image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
            val frame = SrgbPixels(PixelSize(image.width, image.height), pixels)
            if (info.orientation == 1) return frame
            // Shared EXIF math — same inverse mapping the exporter uses.
            val mapping = PhotoMapping(frame.size, info.orientation, PhotoGeometry())
            val source = frame.copyPixels()
            val upright = IntArray(mapping.outputSize.pixelCount.toInt())
            for (y in 0 until mapping.outputSize.height) {
                coroutineContext.ensureActive()
                for (x in 0 until mapping.outputSize.width) {
                    val packed = mapping.sourcePointPacked(x, y)
                    upright[y * mapping.outputSize.width + x] =
                        source[packed.toInt() * image.width + (packed shr 32).toInt()]
                }
            }
            return SrgbPixels(mapping.outputSize, upright)
        } finally {
            image.flush()
        }
    }

    override fun close() {
        closed = true
        reader.dispose()
        input.close()
    }
}
