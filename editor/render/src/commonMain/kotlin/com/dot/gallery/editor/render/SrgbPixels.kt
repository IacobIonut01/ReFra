package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * An immutable bitmap in packed sRGB ARGB (`0xAARRGGBB`, straight — never
 * premultiplied) integer form. This is the platform-facing format: decode
 * sources and display/export sinks use it, while the render pipeline works
 * in [LinearRgbaFrame] linear floats.
 *
 * The public constructor copies the array; [SrgbConversion] hosts the
 * suspendable, row-cancellable conversions between this format and linear
 * RGBA floats.
 */
class SrgbPixels private constructor(
    val size: PixelSize,
    private val storage: IntArray,
    @Suppress("unused") ownership: Ownership
) {

    /** Distinguishes the trusted no-copy constructor from the copying public one. */
    private enum class Ownership { Owned }

    /** Copies [pixels] into a validated frame. */
    constructor(size: PixelSize, pixels: IntArray) : this(size, pixels.copyOf(), Ownership.Owned)

    init {
        val expected = size.pixelCount
        require(expected <= Int.MAX_VALUE.toLong()) {
            "frame too large: ${size.width}x${size.height} exceeds an IntArray"
        }
        require(storage.size.toLong() == expected) {
            "pixel buffer holds ${storage.size} but " +
                "${size.width}x${size.height} needs $expected"
        }
    }

    /** Defensive copy of the pixel buffer. */
    fun copyPixels(): IntArray = storage.copyOf()

    /** Copies the pixels into [target], so a per-frame caller can reuse it. */
    fun copyPixelsInto(target: IntArray) {
        require(target.size >= storage.size) {
            "target holds ${target.size} but ${storage.size} pixels are needed"
        }
        storage.copyInto(target)
    }

    /** Whether every pixel has full alpha. */
    val isOpaque: Boolean
        get() = storage.all { (it ushr 24) == 0xFF }

    /** Read one packed ARGB pixel without exposing mutable storage. */
    fun argbAt(x: Int, y: Int): Int {
        require(x in 0 until size.width && y in 0 until size.height)
        return storage[y * size.width + x]
    }

    internal fun component(index: Int): Int = storage[index]

    internal val componentCount: Int get() = storage.size

    internal companion object {

        /**
         * Wraps [pixels] without copying — the caller transfers ownership and
         * must not mutate the array afterwards.
         */
        internal fun fromOwned(size: PixelSize, pixels: IntArray): SrgbPixels =
            SrgbPixels(size, pixels, Ownership.Owned)
    }
}

/**
 * A cheap nearest-neighbour copy no larger than [maxEdge] on its long side
 * (the receiver itself when it already fits). Tile previews run a full
 * colour grade per look; sampling the session preview once keeps a large
 * catalogue cheap without touching the export path.
 */
fun SrgbPixels.sampledTo(maxEdge: Int): SrgbPixels {
    require(maxEdge > 0) { "maxEdge must be positive: $maxEdge" }
    val longest = max(size.width, size.height)
    if (longest <= maxEdge) return this
    val scale = maxEdge.toFloat() / longest
    val width = max(1, (size.width * scale).roundToInt())
    val height = max(1, (size.height * scale).roundToInt())
    val src = copyPixels()
    val srcWidth = size.width
    val out = IntArray(width * height)
    for (y in 0 until height) {
        val srcRow = (y.toLong() * size.height / height).toInt() * srcWidth
        val outRow = y * width
        for (x in 0 until width) {
            out[outRow + x] = src[srcRow + (x.toLong() * srcWidth / width).toInt()]
        }
    }
    return SrgbPixels(PixelSize(width, height), out)
}
