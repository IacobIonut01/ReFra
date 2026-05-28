package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.util.Size as AndroidSize
import androidx.annotation.RequiresApi
import com.github.panpf.sketch.asImage
import com.github.panpf.sketch.request.ImageData
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.internal.createScaledTransformed
import com.github.panpf.sketch.drawable.ScaledAnimatableDrawable
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.animationEndCallback
import com.github.panpf.sketch.request.animationStartCallback
import com.github.panpf.sketch.request.repeatCount
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.animatable2CompatCallbackOf
import com.github.panpf.sketch.util.calculateScaleMultiplierWithOneSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okio.buffer
import java.nio.ByteBuffer
import kotlin.math.roundToInt

inline fun DataSource.getImageInfo(
    requestContext: RequestContext,
    mimeType: String,
    getSize: (ByteArray) -> AndroidSize?
): ImageInfo {
    openSource().use { src ->
        val sourceData = src.buffer().readByteArray()
        val originalSizeDecoded = getSize(sourceData) ?: AndroidSize(0, 0)
        val size = if (requestContext.size == Size.Origin) {
            Size(originalSizeDecoded.width, originalSizeDecoded.height)
        } else {
            val scale = calculateScaleMultiplierWithOneSide(
                sourceSize = Size(originalSizeDecoded.width, originalSizeDecoded.height),
                targetSize = requestContext.size
            )
            Size(
                width = (originalSizeDecoded.width * scale).roundToInt(),
                height = (originalSizeDecoded.height * scale).roundToInt()
            )
        }
        return ImageInfo(
            width = size.width,
            height = size.height,
            mimeType = mimeType,
        )
    }
}

inline fun DataSource.withCustomDecoder(
    requestContext: RequestContext,
    mimeType: String,
    getSize: (ByteArray) -> AndroidSize?,
    decodeSampled: (ByteArray, Int, Int) -> Bitmap
): ImageData = openSource().use { src ->
    val sourceData = src.buffer().readByteArray()

    var transformeds: List<String>? = null
    val originalSizeDecoded = getSize(sourceData) ?: AndroidSize(0, 0)
    val originalSize = Size(originalSizeDecoded.width, originalSizeDecoded.height)
    val targetSize = requestContext.size
    val scale = calculateScaleMultiplierWithOneSide(
        sourceSize = originalSize,
        targetSize = targetSize
    )
    if (scale != 1f) {
        transformeds = listOf(createScaledTransformed(scale))
    }

    val imageInfo = getImageInfo(
        requestContext = requestContext,
        mimeType = mimeType,
        getSize = getSize
    )

    val decodedImage = if (requestContext.size == Size.Origin) {
        decodeSampled(
            sourceData,
            originalSize.width,
            originalSize.height
        )
    } else {
        val dstSize = Size(
            width = (originalSize.width * scale).roundToInt(),
            height = (originalSize.height * scale).roundToInt()
        )
        decodeSampled(
            sourceData,
            dstSize.width,
            dstSize.height
        )
    }

    val resize = requestContext.computeResize(imageInfo.size)
    ImageData(
        image = decodedImage.asImage(),
        imageInfo = imageInfo,
        dataFrom = dataFrom,
        resize = resize,
        transformeds = transformeds,
        extras = null
    )
}

/**
 * Decodes a static image from pre-read bytes using the provided [getSize] and [decodeSampled]
 * callbacks (typically from HeifCoder). Replicates [withCustomDecoder] logic without re-reading
 * from the DataSource.
 */
fun decodeStaticFromBytes(
    sourceData: ByteArray,
    requestContext: RequestContext,
    dataFrom: com.github.panpf.sketch.source.DataFrom,
    mimeType: String,
    getSize: (ByteArray) -> AndroidSize?,
    decodeSampled: (ByteArray, Int, Int) -> Bitmap
): ImageData {
    var transformeds: List<String>? = null
    val originalSizeDecoded = getSize(sourceData) ?: AndroidSize(0, 0)
    val originalSize = Size(originalSizeDecoded.width, originalSizeDecoded.height)
    val targetSize = requestContext.size
    val scale = calculateScaleMultiplierWithOneSide(
        sourceSize = originalSize,
        targetSize = targetSize
    )
    if (scale != 1f) {
        transformeds = listOf(createScaledTransformed(scale))
    }

    val imageInfo = ImageInfo(
        width = (originalSizeDecoded.width * scale).roundToInt(),
        height = (originalSizeDecoded.height * scale).roundToInt(),
        mimeType = mimeType,
    )

    val decodedImage = if (requestContext.size == Size.Origin) {
        decodeSampled(sourceData, originalSize.width, originalSize.height)
    } else {
        decodeSampled(
            sourceData,
            (originalSize.width * scale).roundToInt(),
            (originalSize.height * scale).roundToInt()
        )
    }

    val resize = requestContext.computeResize(imageInfo.size)
    return ImageData(
        image = decodedImage.asImage(),
        imageInfo = imageInfo,
        dataFrom = dataFrom,
        resize = resize,
        transformeds = transformeds,
        extras = null
    )
}

/**
 * Decodes animated AVIF from pre-read bytes using Android's [ImageDecoder] (API 31+).
 * Returns null if the image is not actually animated or decoding fails.
 */
@Suppress("OPT_IN_USAGE")
@RequiresApi(Build.VERSION_CODES.S)
fun decodeAnimatedAvif(
    bytes: ByteArray,
    requestContext: RequestContext,
    dataFrom: com.github.panpf.sketch.source.DataFrom,
    mimeType: String,
    getSize: (ByteArray) -> AndroidSize?
): ImageData? {
    val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
    val drawable = try {
        ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: Exception) {
        return null
    }

    if (drawable !is AnimatedImageDrawable) return null

    val request = requestContext.request
    drawable.repeatCount = request.repeatCount
        ?.takeIf { it != 0 }
        ?: AnimatedImageDrawable.REPEAT_INFINITE

    val scaledDrawable = ScaledAnimatableDrawable(drawable).apply {
        val onStart = request.animationStartCallback
        val onEnd = request.animationEndCallback
        if (onStart != null || onEnd != null) {
            GlobalScope.launch(Dispatchers.Main) {
                registerAnimationCallback(animatable2CompatCallbackOf(onStart, onEnd))
            }
        }
    }

    val originalSize = getSize(bytes) ?: AndroidSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
    val imageInfo = ImageInfo(originalSize.width, originalSize.height, mimeType)
    val resize = requestContext.computeResize(imageInfo.size)

    return ImageData(
        image = scaledDrawable.asImage(),
        imageInfo = imageInfo,
        dataFrom = dataFrom,
        resize = resize,
        transformeds = null,
        extras = null,
    )
}

/**
 * Checks if the given bytes represent an animated AVIF (AVIF sequence) by scanning
 * the ISO BMFF ftyp box for the 'avis' brand (major or compatible).
 */
fun isAnimatedAvif(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    val max = minOf(bytes.size - 8, 256)
    var i = 0
    while (i < max) {
        if (bytes[i] == 'f'.code.toByte() &&
            bytes[i + 1] == 't'.code.toByte() &&
            bytes[i + 2] == 'y'.code.toByte() &&
            bytes[i + 3] == 'p'.code.toByte()
        ) {
            // ftyp keyword found; box size is in the 4 bytes preceding it
            val boxStart = i - 4
            if (boxStart < 0) return false
            val boxSize = ((bytes[boxStart].toInt() and 0xFF) shl 24) or
                    ((bytes[boxStart + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[boxStart + 2].toInt() and 0xFF) shl 8) or
                    (bytes[boxStart + 3].toInt() and 0xFF)

            // Major brand: 4 bytes immediately after 'ftyp'
            if (i + 8 <= bytes.size) {
                val major = String(bytes, i + 4, 4)
                if (major.equals("avis", ignoreCase = true)) return true
            }

            // Compatible brands start 12 bytes after 'ftyp' (skip major 4 + version 4)
            val brandStart = i + 12
            val boxEnd = if (boxSize > 0) minOf(boxStart + boxSize, bytes.size) else bytes.size
            var j = brandStart
            while (j + 4 <= boxEnd) {
                val brand = String(bytes, j, 4)
                if (brand.equals("avis", ignoreCase = true)) return true
                j += 4
            }
            return false
        }
        i++
    }
    return false
}