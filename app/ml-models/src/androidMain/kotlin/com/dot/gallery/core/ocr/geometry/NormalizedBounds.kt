/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.geometry

data class NormalizedBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f)
        require(left <= right && top <= bottom)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    fun intersection(other: NormalizedBounds): NormalizedBounds? {
        val l = maxOf(left, other.left)
        val t = maxOf(top, other.top)
        val r = minOf(right, other.right)
        val b = minOf(bottom, other.bottom)
        return if (l < r && t < b) NormalizedBounds(l, t, r, b) else null
    }
}
