/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.domain

import kotlinx.serialization.Serializable

@Serializable
data class OcrWord(
    val id: String,
    val readingIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val quad: NormalizedQuad,
    val confidence: Float,
) {
    init {
        require(id.isNotBlank())
        require(readingIndex >= 0)
        require(startOffset >= 0 && endOffset >= startOffset)
        require(endOffset - startOffset == text.length) { "OCR offsets use UTF-16 string indices" }
        require(confidence.isFinite() && confidence in 0f..1f)
    }
}
