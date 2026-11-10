/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.geometry

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.NormalizedQuad
import kotlin.math.ceil
import kotlin.math.max

/** How a detected line is cut out and fed to the recognizer, following PaddleOCR. */
object OcrCropGeometry {
    /** PaddleOCR turns a crop at least this many times taller than wide a quarter turn anticlockwise. */
    const val ROTATE_RATIO = 1.5f

    /** Widest recognizer input; longer lines are squeezed to fit (Paddle has no cap, this bounds memory). */
    const val MAX_INPUT_WIDTH = 3_200

    /** Whether a crop of this size is a vertical line to rotate (`np.rot90`, anticlockwise) before reading. */
    fun rotatesCrop(width: Int, height: Int): Boolean = width > 0 && height >= width * ROTATE_RATIO

    /**
     * The recognizer input for a crop: the text is scaled to [height] keeping its aspect ratio, and the
     * input is at least [minimumWidth] wide (padded on the right), as in PaddleOCR's `resize_norm_img`
     * with `max_wh_ratio`. A fixed [minimumWidth] used to squeeze every line longer than about 6.7:1.
     */
    fun recognizerWidths(cropWidth: Int, cropHeight: Int, height: Int, minimumWidth: Int): RecognizerWidths {
        require(cropWidth > 0 && cropHeight > 0 && height > 0 && minimumWidth > 0)
        val text = ceil(cropWidth.toDouble() * height / cropHeight).toInt().coerceIn(1, MAX_INPUT_WIDTH)
        return RecognizerWidths(textWidth = text, inputWidth = max(minimumWidth, text))
    }

    /**
     * The part of [line] between [start] and [end] (fractions of the text along the reading direction).
     * Horizontal crops read from the left edge to the right; a rotated crop reads the line from its top
     * edge to its bottom, because the anticlockwise turn puts the top edge on the left.
     */
    fun span(line: NormalizedQuad, start: Float, end: Float, rotated: Boolean): NormalizedQuad = if (rotated) {
        NormalizedQuad(
            topLeft = interpolate(line.topLeft, line.bottomLeft, start),
            topRight = interpolate(line.topRight, line.bottomRight, start),
            bottomRight = interpolate(line.topRight, line.bottomRight, end),
            bottomLeft = interpolate(line.topLeft, line.bottomLeft, end),
        )
    } else {
        NormalizedQuad(
            topLeft = interpolate(line.topLeft, line.topRight, start),
            topRight = interpolate(line.topLeft, line.topRight, end),
            bottomRight = interpolate(line.bottomLeft, line.bottomRight, end),
            bottomLeft = interpolate(line.bottomLeft, line.bottomRight, start),
        )
    }

    private fun interpolate(from: NormalizedPoint, to: NormalizedPoint, fraction: Float): NormalizedPoint {
        val value = fraction.coerceIn(0f, 1f)
        return NormalizedPoint(
            (from.x + (to.x - from.x) * value).coerceIn(0f, 1f),
            (from.y + (to.y - from.y) * value).coerceIn(0f, 1f),
        )
    }

    data class RecognizerWidths(val textWidth: Int, val inputWidth: Int) {
        /** Share of the input the text fills before the padding. */
        val contentFraction: Float get() = textWidth.toFloat() / inputWidth
    }
}
