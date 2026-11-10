/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.selection

/** Source-to-viewport mapping shared by the smooth and pixel-perfect viewer paths. */
data class OcrViewportRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(right >= left && bottom >= top)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
