package com.dot.gallery.editor.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoInfo
import com.dot.gallery.editor.render.photo.PhotoRaster
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [PhotoRaster] over a platform [BitmapRegionDecoder]. [readRegion] serves
 * exact raw (un-oriented) rectangles; [readPreview] decodes subsampled to
 * at most `maxEdge` on either axis and rotates to upright through the shared
 * [PhotoMapping] so all eight EXIF orientations — mirrors included — resolve
 * with the same arithmetic the exporter uses. All decoder access is
 * serialized on [mutex] and runs on [ioDispatcher]; decoded [Bitmap]s never
 * escape a call. [importFile] is a privately owned staging copy of a
 * non-seekable source — deleted by [close], never the user's original.
 */
internal class AndroidPhotoRaster(
    override val info: PhotoInfo,
    /** Magic-byte sniffer result — the source container, never the declared MIME. */
    val mimeType: String,
    private val descriptor: ParcelFileDescriptor,
    private val decoder: BitmapRegionDecoder,
    private val ioDispatcher: CoroutineDispatcher,
    private val importFile: File? = null
) : PhotoRaster {

    private val mutex = Mutex()
    private var closed = false

    override suspend fun readRegion(rect: PixelRect): SrgbPixels =
        withContext(ioDispatcher) { readRegionOnIo(rect) }

    private suspend fun readRegionOnIo(rect: PixelRect): SrgbPixels {
        check(rect.isInside(info.rawSize)) {
            "region $rect outside raw ${info.rawSize.width}x${info.rawSize.height}"
        }
        val bitmap = mutex.withLock {
            check(!closed) { "raster is closed" }
            val options = decodeOptions()
            decoder.decodeRegion(
                Rect(rect.left, rect.top, rect.rightExclusive, rect.bottomExclusive),
                options
            )
        } ?: throw IOException("region decode returned no bitmap")
        try {
            check(bitmap.width == rect.width && bitmap.height == rect.height) {
                "region decode returned ${bitmap.width}x${bitmap.height} " +
                    "for ${rect.width}x${rect.height}"
            }
            val pixels = IntArray(rect.width * rect.height)
            bitmap.getPixels(pixels, 0, rect.width, 0, 0, rect.width, rect.height)
            return SrgbPixels(PixelSize(rect.width, rect.height), pixels)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * The first up-to-[length] bytes of the source, read position-independently
     * so the shared descriptor's offset (used by the region decoder) is never
     * disturbed. Used to probe the JPEG quantization tables before export.
     * Unreadable sources return an empty array rather than failing the export.
     */
    internal suspend fun readPrefix(length: Int): ByteArray = withContext(ioDispatcher) {
        if (length <= 0) return@withContext ByteArray(0)
        val buffer = ByteArray(length)
        val read = runCatching {
            Os.pread(descriptor.fileDescriptor, buffer, 0, length, 0L)
        }.getOrDefault(0)
        if (read > 0) buffer.copyOf(read) else ByteArray(0)
    }

    override suspend fun readPreview(maxEdge: Int): SrgbPixels =
        withContext(ioDispatcher) { readPreviewOnIo(maxEdge) }

    private suspend fun readPreviewOnIo(maxEdge: Int): SrgbPixels {
        require(maxEdge > 0) { "maxEdge must be positive: $maxEdge" }
        val rawWidth = info.rawSize.width
        val rawHeight = info.rawSize.height
        // Largest-axis guard: ceil(maxDim / sample) must fit maxEdge, so a
        // panoramic source is still subsampled rather than fully decoded.
        var sample = 1
        while ((maxOf(rawWidth, rawHeight).toLong() + sample - 1) / sample > maxEdge) {
            sample *= 2
        }
        val decoded = mutex.withLock {
            check(!closed) { "raster is closed" }
            val options = decodeOptions()
            options.inSampleSize = sample
            // The fd position is shared with the region decoder and the EXIF
            // probe — rewind before a whole-file decode.
            Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_SET)
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
        } ?: throw IOException("preview decode returned no bitmap")
        try {
            var width = decoded.width
            var height = decoded.height
            var pixels = IntArray(width * height)
            decoded.getPixels(pixels, 0, width, 0, 0, width, height)
            if (width > maxEdge || height > maxEdge) {
                // Platform subsampling overshot; nearest-neighbour on packed
                // pixels — never Bitmap.createScaledBitmap, which would
                // premultiply semi-transparent colour away.
                val scale = maxEdge.toDouble() / maxOf(width, height)
                val outWidth = maxOf(1, (width * scale).toInt())
                val outHeight = maxOf(1, (height * scale).toInt())
                val resampled = IntArray(outWidth * outHeight)
                for (y in 0 until outHeight) {
                    coroutineContext.ensureActive()
                    for (x in 0 until outWidth) {
                        resampled[y * outWidth + x] =
                            pixels[(y * height / outHeight) * width + (x * width / outWidth)]
                    }
                }
                pixels = resampled
                width = outWidth
                height = outHeight
            }
            val frame = SrgbPixels(PixelSize(width, height), pixels)
            if (info.orientation == 1) return frame
            // Reuse the shared mapping instead of duplicating EXIF math: an
            // identity-geometry mapping un-orients the decoded raster.
            val mapping = PhotoMapping(frame.size, info.orientation, PhotoGeometry())
            val source = frame.copyPixels()
            val upright = IntArray(mapping.outputSize.pixelCount.toInt())
            for (y in 0 until mapping.outputSize.height) {
                coroutineContext.ensureActive()
                for (x in 0 until mapping.outputSize.width) {
                    val packed = mapping.sourcePointPacked(x, y)
                    upright[y * mapping.outputSize.width + x] =
                        source[packed.toInt() * width + (packed shr 32).toInt()]
                }
            }
            return SrgbPixels(mapping.outputSize, upright)
        } finally {
            decoded.recycle()
        }
    }

    override fun close() {
        closed = true
        decoder.recycle()
        descriptor.close()
        // Only a privately staged import copy is removed — never the source.
        importFile?.delete()
    }

    private fun decodeOptions(): BitmapFactory.Options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        // Keep straight (unpremultiplied) alpha so semi-transparent RGB
        // survives the sRGB pipeline unchanged.
        inPremultiplied = false
    }
}
