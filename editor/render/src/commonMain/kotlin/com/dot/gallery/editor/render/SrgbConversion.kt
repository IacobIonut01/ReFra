package com.dot.gallery.editor.render

import kotlin.coroutines.coroutineContext
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

/**
 * Standard sRGB transfer conversions between the packed 8-bit
 * [SrgbPixels] format and linear-light [LinearRgbaFrame] floats.
 *
 * Decode (sRGB -> linear): `c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055)^2.4`
 * applied through a 256-entry table.
 * Encode (linear -> sRGB): components are clamped to the displayable `[0,1]`
 * range only here, at the display/export boundary — the pipeline itself never
 * clamps. `c <= 0.0031308 ? 12.92 c : 1.055 c^(1/2.4) - 0.055`, rounded to the
 * nearest 8-bit step. Alpha is preserved in float and rounded to the nearest
 * step on encode; RGB under transparent alpha is kept.
 *
 * Both are suspendable and check cancellation per row.
 */

private val SRGB_DECODE_TABLE = FloatArray(256) { index ->
    val c = index / 255f
    if (c <= 0.04045f) c / 12.92f
    else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
}

private const val ENCODE_PIVOT = 0.0031308f

/** The exact transfer function; the tables below reproduce it step for step. */
internal fun encodeChannelExact(linear: Float): Int {
    val c = linear.coerceIn(0f, 1f)
    val srgb = if (c <= ENCODE_PIVOT) {
        c * 12.92f
    } else {
        (1.055 * c.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()
    }
    return (srgb * 255f).roundToInt().coerceIn(0, 255)
}

/** The smallest linear value that encodes to each 8-bit level. */
private val ENCODE_LEVEL_START = FloatArray(256) { level ->
    if (level == 0) {
        0f
    } else {
        var low = 0f.toRawBits()
        var high = 1f.toRawBits()
        while (high - low > 1) {
            val middle = low + (high - low) / 2
            if (encodeChannelExact(Float.fromBits(middle)) >= level) high = middle else low = middle
        }
        Float.fromBits(high)
    }
}

private const val ENCODE_COARSE_LAST = 4095

/** A nearby level for each coarse bucket, so the walk below is a step or two. */
private val ENCODE_COARSE = ShortArray(ENCODE_COARSE_LAST + 1) { index ->
    encodeChannelExact(index / ENCODE_COARSE_LAST.toFloat()).toShort()
}

/** [encodeChannelExact] without a `pow` for every channel of every pixel. */
internal fun encodeChannel(linear: Float): Int {
    val c = linear.coerceIn(0f, 1f)
    var level = ENCODE_COARSE[(c * ENCODE_COARSE_LAST).toInt()].toInt()
    while (level > 0 && c < ENCODE_LEVEL_START[level]) level--
    while (level < 255 && c >= ENCODE_LEVEL_START[level + 1]) level++
    return level
}

internal fun encodeAlpha(linear: Float): Int =
    (linear.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)

/** One packed ARGB pixel's worth of [SRGB_DECODE_TABLE] into [output] at [index]. */
internal fun decodeSrgbPixel(argb: Int, output: FloatArray, index: Int) {
    output[index] = SRGB_DECODE_TABLE[(argb shr 16) and 0xFF]
    output[index + 1] = SRGB_DECODE_TABLE[(argb shr 8) and 0xFF]
    output[index + 2] = SRGB_DECODE_TABLE[argb and 0xFF]
    output[index + 3] = ((argb shr 24) and 0xFF) / 255f
}

suspend fun SrgbPixels.toLinearFrame(): LinearRgbaFrame {
    // componentCount * 4 can overflow Int on huge frames; reject first.
    require(componentCount.toLong() * 4 <= Int.MAX_VALUE.toLong()) {
        "frame too large to decode: ${size.width}x${size.height} " +
            "exceeds a FloatArray"
    }
    val output = FloatArray(componentCount * 4)
    val width = size.width
    for (y in 0 until size.height) {
        coroutineContext.ensureActive()
        val rowStart = y * width
        for (x in 0 until width) {
            val argb = component(rowStart + x)
            decodeSrgbPixel(argb, output, (rowStart + x) * 4)
        }
    }
    return LinearRgbaFrame.fromOwned(size, output)
}

suspend fun LinearRgbaFrame.toSrgbPixels(): SrgbPixels {
    val output = IntArray(componentCount / 4)
    val width = size.width
    for (y in 0 until size.height) {
        coroutineContext.ensureActive()
        val rowStart = y * width
        for (x in 0 until width) {
            val i = (rowStart + x) * 4
            val a = encodeAlpha(component(i + 3))
            val r = encodeChannel(component(i))
            val g = encodeChannel(component(i + 1))
            val b = encodeChannel(component(i + 2))
            output[rowStart + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return SrgbPixels.fromOwned(size, output)
}
