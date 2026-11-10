/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.domain.OcrWord
import com.dot.gallery.core.ocr.repository.OcrRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrSearchMatchTest {
    private val pier = OcrWord("w1", 0, "Pier", 0, 4, NormalizedQuad.axisAligned(0.1f, 0.5f, 0.2f, 0.55f), 1f)
    private val harbour = OcrWord("w2", 1, "Harbour", 8, 15, NormalizedQuad.axisAligned(0.3f, 0.5f, 0.5f, 0.55f), 1f)
    private val document = OcrDocument(
        sourceWidth = 200,
        sourceHeight = 100,
        fullText = "Canal Nights\nPier 4, Harbour Road",
        lines = listOf(
            OcrLine("l0", 0, "Canal Nights", 0, 12, NormalizedQuad.axisAligned(0.1f, 0.1f, 0.6f, 0.2f), 1f),
            OcrLine("l1", 1, "Pier 4, Harbour Road", 13, 33, NormalizedQuad.axisAligned(0.1f, 0.5f, 0.7f, 0.55f), 1f,
                listOf(pier.copy(startOffset = 13, endOffset = 17), harbour.copy(startOffset = 21, endOffset = 28))),
        ),
    )

    @Test
    fun matchPicksTheLineAndOnlyTheMatchingWords() {
        val region = OcrRepository.matchRegion(document, listOf("harbour"))!!
        assertEquals(document.lines[1].quad, region.line)
        assertEquals(listOf(harbour.quad), region.words)
        assertEquals(2f, region.aspectRatio)
    }

    @Test
    fun caseAndPrefixMatchButUnrelatedQueriesDoNot() {
        assertEquals(document.lines[0].quad, OcrRepository.matchRegion(document, listOf("canal"))!!.line)
        assertEquals(listOf(harbour.quad), OcrRepository.matchRegion(document, listOf("harb"))!!.words)
        assertNull(OcrRepository.matchRegion(document, listOf("museum")))
    }
}
