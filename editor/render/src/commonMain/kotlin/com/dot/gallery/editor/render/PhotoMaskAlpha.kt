package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.hypot
import kotlin.math.min

/**
 * Evaluates a layer mask at one normalized layer-space point. The mask
 * starts at [PhotoLayerMask.baseAlpha], replays each stroke in order,
 * then applies inversion last.
 *
 * Stroke geometry lives in pixel space over the layer's *upright* source
 * size: normalized coordinates map to `(nx*w, ny*h)` and the brush
 * radius is `diameter * min(w, h) / 2`. Distance is the minimum distance
 * to every segment of the polyline (a single-point stroke is a dot);
 * coverage is 1 inside `radius * hardness` and feathers linearly to 0 at
 * the radius. Paint performs `a += (1-a)*coverage*flow`, Erase
 * `a *= 1-coverage*flow`.
 *
 * A neutral mask (no strokes, not inverted) returns [baseAlpha] without
 * touching the stroke math.
 */
internal fun photoMaskAlpha(
    mask: PhotoLayerMask,
    normalizedX: Double,
    normalizedY: Double,
    sourceSize: PixelSize
): Float {
    if (mask.strokes.isEmpty() && !mask.inverted) return mask.baseAlpha
    var alpha = mask.baseAlpha.toDouble()
    val w = sourceSize.width.toDouble()
    val h = sourceSize.height.toDouble()
    val px = normalizedX * w
    val py = normalizedY * h
    val minDim = min(w, h)
    for (stroke in mask.strokes) {
        val radius = stroke.diameter.toDouble() * minDim / 2.0
        val core = radius * stroke.hardness
        var distance = Double.MAX_VALUE
        val points = stroke.points
        var prevX = points[0].x.toDouble() * w
        var prevY = points[0].y.toDouble() * h
        distance = hypot(px - prevX, py - prevY)
        for (i in 1 until points.size) {
            val x = points[i].x.toDouble() * w
            val y = points[i].y.toDouble() * h
            distance = min(
                distance,
                segmentDistance(px, py, prevX, prevY, x, y)
            )
            prevX = x
            prevY = y
        }
        val coverage = when {
            distance >= radius -> 0.0
            distance <= core -> 1.0
            else -> (radius - distance) / (radius - core)
        }
        val gain = coverage * stroke.flow
        alpha = when (stroke.mode) {
            PhotoMaskMode.Paint -> alpha + (1.0 - alpha) * gain
            PhotoMaskMode.Erase -> alpha * (1.0 - gain)
        }
    }
    if (mask.inverted) alpha = 1.0 - alpha
    return alpha.toFloat().coerceIn(0f, 1f)
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
