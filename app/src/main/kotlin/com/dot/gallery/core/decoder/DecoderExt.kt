package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.util.Size as AndroidSize
import androidx.annotation.RequiresApi
import com.dot.gallery.core.decoder.format.HeifBitstreamProbe
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
    val originalSizeDecoded = getSize(sourceData)
    if (originalSizeDecoded == null ||
        originalSizeDecoded.width <= 0 ||
        originalSizeDecoded.height <= 0
    ) {
        // getSize failed (e.g. corrupt/unsupported HEIF the native decoder can't read). Fail with a
        // proper decode exception so Sketch surfaces an error state, instead of proceeding into
        // NaN scale math (0-size source) which crashes on roundToInt().
        throw IllegalStateException("Unable to read image dimensions for $mimeType")
    }
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
 * Wraps an already-decoded [Bitmap] into Sketch [ImageData], applying the request's resize.
 */
fun imageDataFromBitmap(
    bitmap: Bitmap,
    requestContext: RequestContext,
    dataFrom: com.github.panpf.sketch.source.DataFrom,
    mimeType: String,
): ImageData {
    val imageInfo = ImageInfo(bitmap.width, bitmap.height, mimeType)
    val resize = requestContext.computeResize(imageInfo.size)
    return ImageData(
        image = bitmap.asImage(),
        imageInfo = imageInfo,
        dataFrom = dataFrom,
        resize = resize,
        transformeds = null,
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
    val originalSizeDecoded = getSize(sourceData)
    if (originalSizeDecoded == null ||
        originalSizeDecoded.width <= 0 ||
        originalSizeDecoded.height <= 0
    ) {
        // getSize failed (e.g. corrupt/unsupported HEIF the native decoder can't read). Fail with a
        // proper decode exception so Sketch surfaces an error state, instead of proceeding into
        // NaN scale math (0-size source) which crashes on roundToInt().
        throw IllegalStateException("Unable to read image dimensions for $mimeType")
    }
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
 * Decodes an animated HEIF/AVIF image sequence from pre-read bytes via Android's [ImageDecoder]
 * (API 31+). Returns null when the platform does not produce an [AnimatedImageDrawable] (i.e. the
 * image is not animated, or the OS lacks animated-sequence support for this container), so the
 * caller falls back to a static decode. Same implementation as [decodeAnimatedAvif]; the AVIF and
 * HEIC sequence containers share the ISO-BMFF path.
 */
@Suppress("OPT_IN_USAGE")
@RequiresApi(Build.VERSION_CODES.S)
fun decodeAnimatedHeif(
    bytes: ByteArray,
    requestContext: RequestContext,
    dataFrom: com.github.panpf.sketch.source.DataFrom,
    mimeType: String,
    getSize: (ByteArray) -> AndroidSize?
): ImageData? = decodeAnimatedAvif(bytes, requestContext, dataFrom, mimeType, getSize)

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
    // >10-bit (12-bit) sequences would be corrupted by the platform codec just like stills.
    // Returning null lets the caller fall back to a correct static software decode.
    if (HeifBitstreamProbe.needsSoftwareDecode(bytes)) return null
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
fun isAnimatedAvif(bytes: ByteArray): Boolean = hasFtypBrand(bytes, setOf("avis"))

/**
 * Checks if the given bytes represent an animated HEIC/HEIF image sequence by scanning the ISO
 * BMFF ftyp box for a sequence brand: 'msf1' (HEIF image sequence), 'hevc'/'hevx' (HEVC sequence),
 * or 'avis' (AVIF sequence, shares the container).
 */
fun isAnimatedHeif(bytes: ByteArray): Boolean =
    hasFtypBrand(bytes, setOf("msf1", "hevc", "hevx", "avis"))

/**
 * Checks if the given RIFF/WEBP bytes represent an animated WebP: an extended (VP8X) header with
 * the animation flag set, or (fallback) the presence of an `ANIM` chunk. Bounded to a cheap header
 * scan. Static WebP returns false.
 */
fun isAnimatedWebp(bytes: ByteArray): Boolean {
    if (bytes.size < 21) return false
    // RIFF....WEBP
    if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
        bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte() ||
        bytes[8] != 'W'.code.toByte() || bytes[9] != 'E'.code.toByte() ||
        bytes[10] != 'B'.code.toByte() || bytes[11] != 'P'.code.toByte()
    ) return false
    // Extended format (VP8X): animation flag is bit 1 (0x02) of the flags byte at offset 20.
    if (bytes[12] == 'V'.code.toByte() && bytes[13] == 'P'.code.toByte() &&
        bytes[14] == '8'.code.toByte() && bytes[15] == 'X'.code.toByte()
    ) {
        if ((bytes[20].toInt() and 0x02) != 0) return true
    }
    // Fallback: scan the first bytes for an "ANIM" chunk.
    val max = minOf(bytes.size - 4, 256)
    var i = 12
    while (i < max) {
        if (bytes[i] == 'A'.code.toByte() && bytes[i + 1] == 'N'.code.toByte() &&
            bytes[i + 2] == 'I'.code.toByte() && bytes[i + 3] == 'M'.code.toByte()
        ) return true
        i++
    }
    return false
}

/**
 * Scans the ISO-BMFF `ftyp` box (major + compatible brands) for any of [brands]
 * (case-insensitive). Bounded to the first 256 bytes to stay cheap.
 */
private fun hasFtypBrand(bytes: ByteArray, brands: Set<String>): Boolean {
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
                if (brands.any { it.equals(major, ignoreCase = true) }) return true
            }

            // Compatible brands start 12 bytes after 'ftyp' (skip major 4 + version 4)
            val brandStart = i + 12
            val boxEnd = if (boxSize > 0) minOf(boxStart + boxSize, bytes.size) else bytes.size
            var j = brandStart
            while (j + 4 <= boxEnd) {
                val brand = String(bytes, j, 4)
                if (brands.any { it.equals(brand, ignoreCase = true) }) return true
                j += 4
            }
            return false
        }
        i++
    }
    return false
}