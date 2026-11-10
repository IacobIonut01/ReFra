/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.geometry

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import kotlin.math.abs

/** Projective mapping represented by a 3x3 matrix whose final coefficient is one. */
class PerspectiveTransform internal constructor(
    private val coefficients: DoubleArray,
) {
    fun map(x: Double, y: Double): Pair<Double, Double>? {
        val denominator = coefficients[6] * x + coefficients[7] * y + 1.0
        if (!denominator.isFinite() || abs(denominator) < 1e-12) return null
        val mappedX = (coefficients[0] * x + coefficients[1] * y + coefficients[2]) / denominator
        val mappedY = (coefficients[3] * x + coefficients[4] * y + coefficients[5]) / denominator
        return if (mappedX.isFinite() && mappedY.isFinite()) mappedX to mappedY else null
    }

    fun map(point: NormalizedPoint): NormalizedPoint? {
        val mapped = map(point.x.toDouble(), point.y.toDouble()) ?: return null
        if (mapped.first !in -GEOMETRY_EPSILON..1.0 + GEOMETRY_EPSILON) return null
        if (mapped.second !in -GEOMETRY_EPSILON..1.0 + GEOMETRY_EPSILON) return null
        return NormalizedPoint(
            mapped.first.coerceIn(0.0, 1.0).toFloat(),
            mapped.second.coerceIn(0.0, 1.0).toFloat(),
        )
    }
}
