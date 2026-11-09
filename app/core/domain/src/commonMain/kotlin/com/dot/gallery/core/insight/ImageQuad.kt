/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import androidx.compose.runtime.Immutable

/**
 * Four corners in the displayed (EXIF-oriented) image, each normalised to 0..1: top-left,
 * top-right, bottom-right, bottom-left as the reader reported them. The code may be rotated or
 * seen in perspective, so the quad is not necessarily axis-aligned.
 */
@Immutable
data class ImageQuad(
    val x0: Float, val y0: Float,
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val x3: Float, val y3: Float,
) {
    fun x(index: Int): Float = when (index) {
        0 -> x0; 1 -> x1; 2 -> x2; else -> x3
    }

    fun y(index: Int): Float = when (index) {
        0 -> y0; 1 -> y1; 2 -> y2; else -> y3
    }

    val left: Float get() = minOf(x0, x1, x2, x3)
    val top: Float get() = minOf(y0, y1, y2, y3)
    val right: Float get() = maxOf(x0, x1, x2, x3)
    val bottom: Float get() = maxOf(y0, y1, y2, y3)
    val centerX: Float get() = (x0 + x1 + x2 + x3) / 4f
    val centerY: Float get() = (y0 + y1 + y2 + y3) / 4f

    /** True when ([x], [y]) is inside the quad (any winding), using the even-odd rule. */
    fun contains(x: Float, y: Float): Boolean {
        var inside = false
        var j = 3
        for (i in 0..3) {
            val xi = x(i)
            val yi = y(i)
            val xj = x(j)
            val yj = y(j)
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        /** Builds a quad from frame pixel corners, normalising by the frame size. */
        fun fromPixels(points: IntArray, offset: Int, width: Int, height: Int): ImageQuad {
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            fun nx(i: Int) = (points[offset + i * 2] / w).coerceIn(0f, 1f)
            fun ny(i: Int) = (points[offset + i * 2 + 1] / h).coerceIn(0f, 1f)
            return ImageQuad(nx(0), ny(0), nx(1), ny(1), nx(2), ny(2), nx(3), ny(3))
        }
    }
}
