package com.dot.gallery.editor.core.geometry

import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize

/**
 * The bridge between the stored crop ([NormalizedCrop], upright-frame
 * fractions) and the crop tool's working space: an axis-aligned rectangle
 * in warp-plane pixels ([PlaneRect]). The plane's axes are the upright
 * axes after the quarter turns and flips, so the conversion is a pure axis
 * permutation/mirror — no warp is involved, which keeps a neutral warp
 * bit-identical to the rigid crop.
 */
class CropPlane(upright: PixelSize, private val quarterTurns: Int,
    private val flipHorizontal: Boolean, private val flipVertical: Boolean) {

    constructor(upright: PixelSize, geometry: PhotoGeometry) :
        this(upright, geometry.quarterTurns, geometry.flipHorizontal, geometry.flipVertical)

    private val turns = ((quarterTurns % 4) + 4) % 4

    /** Plane size in pixels: the upright size with axes swapped for odd turns. */
    val width: Double = (if (turns % 2 == 1) upright.height else upright.width).toDouble()
    val height: Double = (if (turns % 2 == 1) upright.width else upright.height).toDouble()

    /** [crop] as plane-normalized `[x0, y0, x1, y1]`. */
    fun normalized(crop: NormalizedCrop): DoubleArray {
        var dx0: Double
        var dy0: Double
        var dx1: Double
        var dy1: Double
        when (turns) {
            1 -> { dx0 = 1.0 - crop.bottom; dx1 = 1.0 - crop.top; dy0 = crop.left; dy1 = crop.right }
            2 -> {
                dx0 = 1.0 - crop.right; dx1 = 1.0 - crop.left
                dy0 = 1.0 - crop.bottom; dy1 = 1.0 - crop.top
            }
            3 -> { dx0 = crop.top; dx1 = crop.bottom; dy0 = 1.0 - crop.right; dy1 = 1.0 - crop.left }
            else -> { dx0 = crop.left; dx1 = crop.right; dy0 = crop.top; dy1 = crop.bottom }
        }
        if (flipHorizontal) { val t = dx0; dx0 = 1.0 - dx1; dx1 = 1.0 - t }
        if (flipVertical) { val t = dy0; dy0 = 1.0 - dy1; dy1 = 1.0 - t }
        return doubleArrayOf(dx0, dy0, dx1, dy1)
    }

    fun rectOf(crop: NormalizedCrop): PlaneRect {
        val n = normalized(crop)
        return PlaneRect(n[0] * width, n[1] * height, n[2] * width, n[3] * height)
    }

    /**
     * The stored crop for a plane rectangle. The rectangle is clamped to
     * the plane and to a minimum extent of [minSidePx] so the result is
     * always a valid [NormalizedCrop].
     */
    fun cropOf(rect: PlaneRect, minSidePx: Double = 1.0): NormalizedCrop {
        val minX = (minSidePx / width).coerceAtMost(1.0)
        val minY = (minSidePx / height).coerceAtMost(1.0)
        var x0 = (rect.left / width).coerceIn(0.0, 1.0)
        var x1 = (rect.right / width).coerceIn(0.0, 1.0)
        var y0 = (rect.top / height).coerceIn(0.0, 1.0)
        var y1 = (rect.bottom / height).coerceIn(0.0, 1.0)
        if (x1 - x0 < minX) { val c = ((x0 + x1) / 2).coerceIn(minX / 2, 1 - minX / 2); x0 = c - minX / 2; x1 = c + minX / 2 }
        if (y1 - y0 < minY) { val c = ((y0 + y1) / 2).coerceIn(minY / 2, 1 - minY / 2); y0 = c - minY / 2; y1 = c + minY / 2 }
        x0 = snap(x0); x1 = snap(x1); y0 = snap(y0); y1 = snap(y1)
        if (flipHorizontal) { val t = x0; x0 = 1.0 - x1; x1 = 1.0 - t }
        if (flipVertical) { val t = y0; y0 = 1.0 - y1; y1 = 1.0 - t }
        return when (turns) {
            1 -> NormalizedCrop(left = y0, right = y1, top = 1.0 - x1, bottom = 1.0 - x0)
            2 -> NormalizedCrop(left = 1.0 - x1, right = 1.0 - x0, top = 1.0 - y1, bottom = 1.0 - y0)
            3 -> NormalizedCrop(left = 1.0 - y1, right = 1.0 - y0, top = x0, bottom = x1)
            else -> NormalizedCrop(left = x0, top = y0, right = x1, bottom = y1)
        }
    }

    fun plane(warp: PhotoWarp) = WarpPlane(width, height, warp)

    // Removes sub-ULP noise (1 - 0.8 == 0.19999999999999996) that would
    // floor-map to one pixel too many at rasterisation time.
    private fun snap(v: Double): Double = kotlin.math.round(v * 1e12) / 1e12
}
