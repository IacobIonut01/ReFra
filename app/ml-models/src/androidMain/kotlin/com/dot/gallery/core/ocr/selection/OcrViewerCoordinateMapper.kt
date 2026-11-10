/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.selection

import com.dot.gallery.core.ocr.domain.NormalizedPoint

object OcrViewerCoordinateMapper {
    fun sourceToViewport(
        point: NormalizedPoint,
        viewport: OcrViewportRect,
        viewerRotationDegrees: Int,
    ): Pair<Float, Float> {
        val rotated = rotateSource(point, normalizedQuarterTurns(viewerRotationDegrees))
        return viewport.left + rotated.x * viewport.width to viewport.top + rotated.y * viewport.height
    }

    fun viewportToSource(
        x: Float,
        y: Float,
        viewport: OcrViewportRect,
        viewerRotationDegrees: Int,
    ): NormalizedPoint? {
        if (viewport.width <= 0f || viewport.height <= 0f ||
            x !in viewport.left..viewport.right || y !in viewport.top..viewport.bottom
        ) return null
        val rotated = NormalizedPoint(
            x = ((x - viewport.left) / viewport.width).coerceIn(0f, 1f),
            y = ((y - viewport.top) / viewport.height).coerceIn(0f, 1f),
        )
        return unrotateSource(rotated, normalizedQuarterTurns(viewerRotationDegrees))
    }

    private fun rotateSource(point: NormalizedPoint, quarterTurns: Int): NormalizedPoint = when (quarterTurns) {
        0 -> point
        1 -> NormalizedPoint(1f - point.y, point.x)
        2 -> NormalizedPoint(1f - point.x, 1f - point.y)
        else -> NormalizedPoint(point.y, 1f - point.x)
    }

    private fun unrotateSource(point: NormalizedPoint, quarterTurns: Int): NormalizedPoint = when (quarterTurns) {
        0 -> point
        1 -> NormalizedPoint(point.y, 1f - point.x)
        2 -> NormalizedPoint(1f - point.x, 1f - point.y)
        else -> NormalizedPoint(1f - point.y, point.x)
    }

    private fun normalizedQuarterTurns(degrees: Int): Int =
        (((degrees % 360) + 360) % 360 / 90).coerceIn(0, 3)
}
