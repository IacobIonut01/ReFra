/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.domain

import kotlinx.serialization.Serializable

@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "OCR point coordinates must be finite" }
        require(x in 0f..1f && y in 0f..1f) { "OCR point coordinates must be normalized" }
    }
}
