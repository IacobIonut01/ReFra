package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Rasterizes a [PhotoLayerMask] into an sRGB ARGB frame the GPU pipeline
 * binds as a mask texture: RGB is white, the alpha channel is
 * [photoMaskAlpha] evaluated at each pixel centre `((x+.5)/w, (y+.5)/h)`.
 *
 * The naive route — evaluating every stroke at every pixel — is
 * O(pixels x points); this rasterizes each stroke only inside its
 * radius-expanded bounding box: one reusable coverage plane accumulates
 * the stroke's per-pixel minimum distance as max coverage (the same
 * whole-stroke semantics as [photoMaskAlpha]), then the Paint/Erase blend
 * applies once per covered pixel. Pixels a stroke cannot reach keep the
 * running alpha untouched. A neutral mask fast-fills [PhotoLayerMask.baseAlpha].
 *
 * [sourceSize] is the layer's upright preview size — the same pixel space
 * the brush's normalized coordinates and diameters map onto.
 */
fun rasterizePhotoMask(mask: PhotoLayerMask, sourceSize: PixelSize): SrgbPixels {
    val w = sourceSize.width
    val h = sourceSize.height
    val count = w * h
    if (mask.strokes.isEmpty() && !mask.inverted) {
        return SrgbPixels(
            sourceSize, IntArray(count) { argb(mask.baseAlpha) }
        )
    }
    // Running mask alpha — doubles, mirroring photoMaskAlpha's arithmetic.
    val alpha = DoubleArray(count) { mask.baseAlpha.toDouble() }
    // Per-stroke coverage plane, reset only inside the bbox it touched.
    val coverage = FloatArray(count)
    val minDim = min(w, h).toDouble()
    for (stroke in mask.strokes) {
        val radius = stroke.diameter.toDouble() * minDim / 2.0
        val core = radius * stroke.hardness
        // The stroke's reach: every point's disc merged into one bbox —
        // only pixels inside can carry coverage.
        var dirtyMinX = Int.MAX_VALUE
        var dirtyMinY = Int.MAX_VALUE
        var dirtyMaxX = Int.MIN_VALUE
        var dirtyMaxY = Int.MIN_VALUE
        val points = stroke.points
        // Dot: the single point's disc. Segments add their swept discs.
        var prevX = points[0].x.toDouble() * w
        var prevY = points[0].y.toDouble() * h
        for (segment in 0 until points.size) {
            val x1: Double
            val y1: Double
            if (segment == 0) {
                x1 = prevX; y1 = prevY
            } else {
                x1 = points[segment].x.toDouble() * w
                y1 = points[segment].y.toDouble() * h
            }
            // Pixel centres are (px+.5, py+.5): a centre lands within the
            // disc when its pixel intersects [point-radius, point+radius].
            val loX = (floor(min(prevX, x1) - radius + .5)).toInt()
                .coerceIn(0, w - 1)
            val hiX = (floor(max(prevX, x1) + radius - .5)).toInt()
                .coerceIn(0, w - 1)
            val loY = (floor(min(prevY, y1) - radius + .5)).toInt()
                .coerceIn(0, h - 1)
            val hiY = (floor(max(prevY, y1) + radius - .5)).toInt()
                .coerceIn(0, h - 1)
            for (py in loY..hiY) {
                val cy = py + .5
                val rowStart = py * w
                for (px in loX..hiX) {
                    val distance = segmentDistance(
                        px + .5, cy, prevX, prevY, x1, y1
                    )
                    val cov = when {
                        distance >= radius -> continue
                        distance <= core -> 1f
                        else -> ((radius - distance) / (radius - core)).toFloat()
                    }
                    val index = rowStart + px
                    if (cov > coverage[index]) coverage[index] = cov
                }
            }
            if (loX < dirtyMinX) dirtyMinX = loX
            if (hiX > dirtyMaxX) dirtyMaxX = hiX
            if (loY < dirtyMinY) dirtyMinY = loY
            if (hiY > dirtyMaxY) dirtyMaxY = hiY
            prevX = x1; prevY = y1
        }
        if (dirtyMinX == Int.MAX_VALUE) continue
        val flow = stroke.flow.toDouble()
        for (py in dirtyMinY..dirtyMaxY) {
            val rowStart = py * w
            for (px in dirtyMinX..dirtyMaxX) {
                val index = rowStart + px
                val gain = coverage[index].toDouble() * flow
                if (gain > 0.0) {
                    alpha[index] = when (stroke.mode) {
                        PhotoMaskMode.Paint ->
                            alpha[index] + (1.0 - alpha[index]) * gain
                        PhotoMaskMode.Erase -> alpha[index] * (1.0 - gain)
                    }
                }
                coverage[index] = 0f
            }
        }
    }
    val pixels = IntArray(count)
    for (i in 0 until count) {
        var a = alpha[i]
        if (mask.inverted) a = 1.0 - a
        pixels[i] = argb(a.coerceIn(0.0, 1.0).toFloat())
    }
    return SrgbPixels(sourceSize, pixels)
}

/** Distance from `(px, py)` to the segment `(ax, ay)`-`(bx, by)`. */
private fun segmentDistance(
    px: Double, py: Double,
    ax: Double, ay: Double,
    bx: Double, by: Double
): Double {
    val abx = bx - ax
    val aby = by - ay
    val lengthSquared = abx * abx + aby * aby
    val t = if (lengthSquared == 0.0) {
        0.0
    } else {
        (((px - ax) * abx + (py - ay) * aby) / lengthSquared).coerceIn(0.0, 1.0)
    }
    return hypot(px - (ax + t * abx), py - (ay + t * aby))
}

/** White RGB with the mask alpha quantized to the sRGB byte channel. */
private fun argb(alpha: Float): Int =
    (alpha.coerceIn(0f, 1f) * 255f + .5f).toInt().shl(24) or 0x00FFFFFF
