package com.dot.gallery.editor.android

import android.graphics.Bitmap
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoInfo
import com.dot.gallery.editor.render.photo.PhotoRaster
import com.dot.gallery.editor.render.photo.PhotoTooLargeException
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A bitmap's pixels as editor pixels; the bitmap is left untouched. */
internal fun Bitmap.toSrgbPixels(): SrgbPixels {
    val out = IntArray(width * height)
    getPixels(out, 0, width, 0, 0, width, height)
    return SrgbPixels(PixelSize(width, height), out)
}

/** [this], or a smaller copy fitting [maxEdge], never upscaled. */
internal fun Bitmap.boundedTo(maxEdge: Int): Bitmap {
    val edge = max(width, height)
    if (edge <= maxEdge) return this
    val scale = maxEdge.toFloat() / edge
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

/**
 * A developed RAW as a raster. Pixels are already upright, so the orientation is
 * always 1. The full decode happens on the first region read and stays held until
 * [close], since LibRaw has no region access.
 */
internal class AndroidRawPhotoRaster(
    reference: String,
    displayName: String,
    size: PixelSize,
    private val data: ByteArray,
    private val develop: PhotoRawDevelop,
    private val decoder: EditorRawDecoder,
    private val dispatcher: CoroutineDispatcher,
    decoded: Bitmap? = null
) : PhotoRaster {

    override val info = PhotoInfo(reference, displayName, size, orientation = 1, isRaw = true)

    private val lock = Mutex()
    private var full: Bitmap? = decoded

    private suspend fun fullBitmap(): Bitmap = lock.withLock {
        full ?: decodeRawOrThrow(decoder, data, develop, develop.halfSize).also { full = it }
    }

    override suspend fun readRegion(rect: PixelRect): SrgbPixels = withContext(dispatcher) {
        val bitmap = fullBitmap()
        // LibRaw's reported size can miss the decode by a few pixels at the edge.
        val out = IntArray(rect.width * rect.height)
        val copyWidth = minOf(rect.width, bitmap.width - rect.left)
        val copyHeight = minOf(rect.height, bitmap.height - rect.top)
        if (copyWidth > 0 && copyHeight > 0) {
            bitmap.getPixels(out, 0, rect.width, rect.left, rect.top, copyWidth, copyHeight)
        }
        SrgbPixels(PixelSize(rect.width, rect.height), out)
    }

    override suspend fun readPreview(maxEdge: Int): SrgbPixels = withContext(dispatcher) {
        val half = max(info.rawSize.width, info.rawSize.height) >= maxEdge * 2
        val decoded = decodeRawOrThrow(decoder, data, develop, half)
        val bounded = decoded.boundedTo(maxEdge)
        try {
            bounded.toSrgbPixels()
        } finally {
            if (bounded !== decoded) bounded.recycle()
            decoded.recycle()
        }
    }

    override fun close() {
        full?.recycle()
        full = null
    }
}

internal fun decodeRawOrThrow(
    decoder: EditorRawDecoder,
    data: ByteArray,
    develop: PhotoRawDevelop,
    halfSize: Boolean
): Bitmap = try {
    decoder.demosaic(data, develop, halfSize)
        ?: throw IOException("the RAW decoder could not develop this photo")
} catch (memory: OutOfMemoryError) {
    throw PhotoTooLargeException("not enough memory to develop this RAW photo")
}
