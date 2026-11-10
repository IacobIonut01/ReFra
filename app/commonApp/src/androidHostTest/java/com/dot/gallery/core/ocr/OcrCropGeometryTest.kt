/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.geometry.OcrCropGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCropGeometryTest {
    @Test
    fun longLinesKeepTheirAspectRatioInsteadOfBeingSqueezed() {
        // A 20:1 line at height 48 is 960 wide; it used to be squeezed into 320.
        val widths = OcrCropGeometry.recognizerWidths(cropWidth = 1_000, cropHeight = 50, height = 48, minimumWidth = 320)
        assertEquals(960, widths.textWidth)
        assertEquals(960, widths.inputWidth)
        assertEquals(1f, widths.contentFraction, 0f)
    }

    @Test
    fun shortLinesArePaddedToTheMinimumWidth() {
        val widths = OcrCropGeometry.recognizerWidths(cropWidth = 100, cropHeight = 50, height = 48, minimumWidth = 320)
        assertEquals(96, widths.textWidth)
        assertEquals(320, widths.inputWidth)
        assertEquals(0.3f, widths.contentFraction, 0.0001f)
    }

    @Test
    fun extremeLinesAreCapped() {
        val widths = OcrCropGeometry.recognizerWidths(cropWidth = 100_000, cropHeight = 10, height = 48, minimumWidth = 320)
        assertEquals(OcrCropGeometry.MAX_INPUT_WIDTH, widths.inputWidth)
    }

    @Test
    fun cropsAreRotatedAtPaddlesRatio() {
        assertTrue(OcrCropGeometry.rotatesCrop(width = 30, height = 45))
        assertFalse(OcrCropGeometry.rotatesCrop(width = 30, height = 44))
    }

    @Test
    fun rotatedLinesPlaceWordsFromTopToBottom() {
        val column = NormalizedQuad.axisAligned(0.4f, 0.1f, 0.5f, 0.9f)
        val word = OcrCropGeometry.span(column, start = 0.5f, end = 1f, rotated = true)
        val expected = NormalizedQuad.axisAligned(0.4f, 0.5f, 0.5f, 0.9f).points
        expected.zip(word.points).forEach { (want, got) ->
            assertEquals(want.x, got.x, 0.0001f)
            assertEquals(want.y, got.y, 0.0001f)
        }

        val line = NormalizedQuad.axisAligned(0.1f, 0.4f, 0.9f, 0.5f)
        val horizontal = OcrCropGeometry.span(line, start = 0.5f, end = 1f, rotated = false)
        assertEquals(0.5f, horizontal.topLeft.x, 0.0001f)
        assertEquals(0.9f, horizontal.topRight.x, 0.0001f)
    }
}
