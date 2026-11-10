/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.domain

import kotlinx.serialization.Serializable

/** Four source-oriented normalized points in perimeter order. */
@Serializable
data class NormalizedQuad(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint,
) {
    val points: List<NormalizedPoint>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        fun axisAligned(left: Float, top: Float, right: Float, bottom: Float): NormalizedQuad {
            require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
            require(left <= right && top <= bottom)
            return NormalizedQuad(
                topLeft = NormalizedPoint(left, top),
                topRight = NormalizedPoint(right, top),
                bottomRight = NormalizedPoint(right, bottom),
                bottomLeft = NormalizedPoint(left, bottom),
            )
        }
    }
}
