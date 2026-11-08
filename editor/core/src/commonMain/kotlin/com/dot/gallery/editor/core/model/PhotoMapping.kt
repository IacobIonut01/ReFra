package com.dot.gallery.editor.core.model

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Exact integer coordinate mapping between an edited output frame and the
 * native (un-oriented) source pixels.
 *
 * The pipeline is applied in this order:
 * 1. the EXIF orientation turns the stored raster into the upright frame
 *    ([uprightSize], width/height swapped for orientations 5–8);
 * 2. [PhotoGeometry.crop] is resolved to pixels on the upright frame —
 *    `floor(left * w)` / `ceil(right * w)` clamped inside the bounds;
 * 3. `quarterTurns` rotates the crop by 90-degree steps (1 = clockwise,
 *    swapping width/height for odd counts);
 * 4. the flips mirror the rotated output.
 *
 * [sourcePoint] inverts that chain: an output pixel maps to exactly one raw
 * pixel, so raster reads stay rectangular ([sourceBounds]) and no full-image
 * decode is needed.
 */
class PhotoMapping(
    val rawSize: PixelSize,
    val orientation: Int,
    val geometry: PhotoGeometry = PhotoGeometry()
) {
    init {
        require(orientation in 1..8) {
            "EXIF orientation must be in 1..8: $orientation"
        }
    }

    /** Dimensions of the source after EXIF orientation is applied. */
    val uprightSize: PixelSize =
        if (orientation in 5..8) PixelSize(rawSize.height, rawSize.width) else rawSize

    /** Pixel crop on the upright frame, half-open [left,right)x[top,bottom). */
    val cropRect: PixelRect = run {
        val w = uprightSize.width
        val h = uprightSize.height
        val left = floor(geometry.crop.left * w).toInt().coerceIn(0, w - 1)
        val right = ceil(geometry.crop.right * w).toInt().coerceIn(left + 1, w)
        val top = floor(geometry.crop.top * h).toInt().coerceIn(0, h - 1)
        val bottom = ceil(geometry.crop.bottom * h).toInt().coerceIn(top + 1, h)
        PixelRect(left, top, right - left, bottom - top)
    }

    /** Dimensions of the fully processed output frame. */
    val outputSize: PixelSize =
        if (geometry.quarterTurns % 2 == 1) {
            PixelSize(cropRect.height, cropRect.width)
        } else {
            PixelSize(cropRect.width, cropRect.height)
        }

    /**
     * True when the mapping is a full pass-through: whole-frame crop, no
     * rotation or flips, a neutral warp, and orientation 1 (raw is already
     * upright).
     */
    val isIdentity: Boolean
        get() = orientation == 1 &&
            cropRect.left == 0 && cropRect.top == 0 &&
            cropRect.rightExclusive == uprightSize.width &&
            cropRect.bottomExclusive == uprightSize.height &&
            geometry.quarterTurns == 0 &&
            !geometry.flipHorizontal && !geometry.flipVertical &&
            geometry.warp.isNeutral

    /**
     * Maps an output pixel `[x, y)` to the raw source pixel it samples.
     * Throws [IllegalArgumentException] when outside [outputSize].
     */
    fun sourcePoint(x: Int, y: Int): Pair<Int, Int> {
        val packed = sourcePointPacked(x, y)
        return (packed shr 32).toInt() to packed.toInt()
    }

    /**
     * Allocation-free variant of [sourcePoint]: raw `x` in the high 32 bits,
     * raw `y` in the low 32 bits.
     */
    fun sourcePointPacked(x: Int, y: Int): Long {
        require(x in 0 until outputSize.width && y in 0 until outputSize.height) {
            "output point ($x, $y) outside ${outputSize.width}x${outputSize.height}"
        }
        // 1. undo the output flips -> rotated crop coordinates.
        val rx = if (geometry.flipHorizontal) outputSize.width - 1 - x else x
        val ry = if (geometry.flipVertical) outputSize.height - 1 - y else y
        // 2. undo the quarter-turn rotation -> coordinates inside the crop.
        val cx: Int
        val cy: Int
        when (geometry.quarterTurns) {
            0 -> { cx = rx; cy = ry }
            1 -> { cx = ry; cy = cropRect.height - 1 - rx }
            2 -> { cx = cropRect.width - 1 - rx; cy = cropRect.height - 1 - ry }
            else -> { cx = cropRect.width - 1 - ry; cy = rx }
        }
        // 3. crop offset -> upright frame coordinates.
        val u = cropRect.left + cx
        val v = cropRect.top + cy
        // 4. undo EXIF orientation -> raw stored coordinates.
        val w = rawSize.width
        val h = rawSize.height
        val rawX: Int
        val rawY: Int
        when (orientation) {
            1 -> { rawX = u; rawY = v }
            2 -> { rawX = w - 1 - u; rawY = v }
            3 -> { rawX = w - 1 - u; rawY = h - 1 - v }
            4 -> { rawX = u; rawY = h - 1 - v }
            5 -> { rawX = v; rawY = u }
            6 -> { rawX = v; rawY = h - 1 - u }
            7 -> { rawX = w - 1 - v; rawY = h - 1 - u }
            else -> { rawX = w - 1 - v; rawY = u }
        }
        return (rawX.toLong() shl 32) or (rawY.toLong() and 0xFFFF_FFFFL)
    }

    /**
     * The raw rectangle covering [outputRect]. Because the mapping is rigid
     * (90-degree steps and mirrors only), the four output corner pixels bound
     * the read exactly. [outputRect] must lie inside [outputSize].
     */
    fun sourceBounds(outputRect: PixelRect): PixelRect {
        require(outputRect.isInside(outputSize)) {
            "output rect $outputRect outside " +
                "${outputSize.width}x${outputSize.height}"
        }
        val points = intArrayOf(
            outputRect.left, outputRect.top,
            outputRect.rightExclusive - 1, outputRect.top,
            outputRect.left, outputRect.bottomExclusive - 1,
            outputRect.rightExclusive - 1, outputRect.bottomExclusive - 1
        )
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (i in points.indices step 2) {
            val packed = sourcePointPacked(points[i], points[i + 1])
            val x = (packed shr 32).toInt()
            val y = packed.toInt()
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return PixelRect(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }
}
