/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.domain

import kotlinx.serialization.Serializable

@Serializable
data class OcrLine(
    val id: String,
    val readingIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val quad: NormalizedQuad,
    val confidence: Float,
    val words: List<OcrWord> = emptyList(),
    /** The pack whose recognizer read this line (null in documents stored before it was recorded). */
    val packId: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(readingIndex >= 0)
        require(startOffset >= 0 && endOffset >= startOffset)
        require(endOffset - startOffset == text.length) { "OCR offsets use UTF-16 string indices" }
        require(confidence.isFinite() && confidence in 0f..1f)
        require(words.size <= OcrLimits.MAX_TOKENS)
        require(words.map(OcrWord::id).distinct().size == words.size)
        require(words.map(OcrWord::readingIndex).distinct().size == words.size)
        require(words.all { it.startOffset >= startOffset && it.endOffset <= endOffset })
    }
}
