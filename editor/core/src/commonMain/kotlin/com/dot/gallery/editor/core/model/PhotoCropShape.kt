package com.dot.gallery.editor.core.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The outline the crop keeps. [Rectangle] keeps every output pixel; the
 * other shapes clear the pixels outside the outline, so a shaped crop
 * carries transparency and saves as PNG or WebP. The outline is inscribed
 * in the output frame: [Circle] is the frame's inscribed ellipse (a circle
 * at the 1:1 aspect its preset locks), and [RoundedSquare] keeps the frame
 * with corners rounded by [ROUNDED_CORNER_FRACTION] of its shorter side.
 */
enum class PhotoCropShape {
    Rectangle,
    Circle,
    RoundedSquare;

    val isShaped: Boolean get() = this != Rectangle

    /**
     * The anti-aliased coverage (0..1) of the output pixel at ([x], [y]) in a
     * [width] x [height] frame: the pixel's centre is measured against the
     * outline and the edge is smoothed over about one pixel.
     */
    fun coverage(x: Int, y: Int, width: Int, height: Int): Float {
        if (this == Rectangle || width <= 0 || height <= 0) return 1f
        val px = x + 0.5
        val py = y + 0.5
        val distance = when (this) {
            Rectangle -> return 1f
            Circle -> {
                val rx = width / 2.0
                val ry = height / 2.0
                val dx = (px - rx) / rx
                val dy = (py - ry) / ry
                // Signed distance in pixels, scaled by the shorter radius
                // (exact for a circle, close enough at an ellipse edge).
                (sqrt(dx * dx + dy * dy) - 1.0) * min(rx, ry)
            }
            RoundedSquare -> {
                val radius = min(width, height) * ROUNDED_CORNER_FRACTION
                val hx = width / 2.0
                val hy = height / 2.0
                val qx = kotlin.math.abs(px - hx) - (hx - radius)
                val qy = kotlin.math.abs(py - hy) - (hy - radius)
                val ox = max(qx, 0.0)
                val oy = max(qy, 0.0)
                sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0.0) - radius
            }
        }
        return (0.5 - distance).coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        /** Corner radius of [RoundedSquare] as a fraction of the shorter side. */
        const val ROUNDED_CORNER_FRACTION = 0.2

        /** The shape named [name], or [Rectangle] for an unknown or absent name. */
        fun fromName(name: String?): PhotoCropShape =
            entries.firstOrNull { it.name == name } ?: Rectangle
    }
}
