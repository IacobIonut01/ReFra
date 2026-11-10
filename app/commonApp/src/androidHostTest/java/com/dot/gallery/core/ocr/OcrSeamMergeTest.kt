/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.geometry.OcrSeamMerge
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrSeamMergeTest {
    @Test
    fun mergesNormalizedTextAtOverlappingTileSeams() {
        val weaker = line("a", "ＦＯＯ", 0.6f, quad(0.4f, 0.2f, 0.6f, 0.3f))
        val stronger = line("b", "foo", 0.9f, quad(0.41f, 0.2f, 0.61f, 0.3f))

        val merged = OcrSeamMerge.mergeLines(listOf(weaker, stronger), iouThreshold = 0.5f)
        assertEquals(1, merged.size)
        assertEquals("b", merged.single().id)
    }

    @Test
    fun mergesContainedDuplicateWhenOneTileClipsTheLineAtASeam() {
        val complete = line("a", "seam text", 0.8f, quad(0.35f, 0.2f, 0.65f, 0.3f))
        val clipped = line("b", "SEAM TEXT", 0.9f, quad(0.48f, 0.2f, 0.65f, 0.3f))

        val merged = OcrSeamMerge.mergeLines(listOf(complete, clipped), iouThreshold = 0.5f)

        assertEquals(1, merged.size)
        assertEquals("b", merged.single().id)
    }

    @Test
    fun retainsEqualTextWhenGeometryDoesNotOverlap() {
        val lines = listOf(
            line("a", "same", 0.8f, quad(0.1f, 0.1f, 0.2f, 0.2f)),
            line("b", "SAME", 0.9f, quad(0.7f, 0.7f, 0.8f, 0.8f)),
        )
        assertEquals(2, OcrSeamMerge.mergeLines(lines).size)
    }

    private fun line(id: String, text: String, confidence: Float, quad: NormalizedQuad) =
        OcrLine(id, if (id == "a") 0 else 1, text, 0, text.length, quad, confidence)

    private fun quad(left: Float, top: Float, right: Float, bottom: Float) =
        NormalizedQuad.axisAligned(left, top, right, bottom)
}
