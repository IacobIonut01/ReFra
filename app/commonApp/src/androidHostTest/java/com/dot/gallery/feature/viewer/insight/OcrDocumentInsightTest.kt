/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.domain.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrDocumentInsightTest {
    @Test
    fun eachLineKeepsThePackThatReadIt() {
        val document = OcrDocument(
            sourceWidth = 100,
            sourceHeight = 100,
            fullText = "Hello\nمرحبا\nold",
            lines = listOf(
                line("a", 0, "Hello", 0, "latin"),
                line("b", 1, "مرحبا", 6, "arabic"),
                // Stored before lines recorded their pack.
                line("c", 2, "old", 12, null),
            ),
        )
        val insight = document.toInsight(packId = "latin")!!
        assertEquals(listOf("latin", "arabic", "latin"), insight.lines.map { it.packId })
    }

    private fun line(id: String, index: Int, text: String, start: Int, packId: String?) = OcrLine(
        id = id,
        readingIndex = index,
        text = text,
        startOffset = start,
        endOffset = start + text.length,
        quad = NormalizedQuad.axisAligned(0.1f, 0.1f * (index + 1), 0.5f, 0.1f * (index + 1) + 0.05f),
        confidence = 0.9f,
        packId = packId,
    )
}
