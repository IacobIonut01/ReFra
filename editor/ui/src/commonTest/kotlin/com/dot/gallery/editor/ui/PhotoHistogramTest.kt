package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoHistogramTest {

    @Test
    fun transparentAndPartialPixelsUseDeterministicAlphaWeight() {
        // Given transparent red, half-alpha red, opaque green and opaque blue.
        val pixels = SrgbPixels(
            PixelSize(4, 1),
            intArrayOf(
                0x00FF0000,
                0x80FF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt()
            )
        )

        // When calculating four shared-normalization bins.
        val histogram = calculatePhotoHistogram(pixels, bins = 4)

        // Then transparency is ignored, partial alpha is weighted, zero
        // channel values remain in bin zero, and all channels share the
        // largest peak (the red channel's two zero samples: 255 + 255).
        assertEquals(638L, histogram.alphaWeight)
        assertEquals(1f, histogram.red[0], 0.0001f)
        assertEquals(128f / 510f, histogram.red[3], 0.0001f)
        assertEquals(383f / 510f, histogram.green[0], 0.0001f)
        assertEquals(255f / 510f, histogram.green[3], 0.0001f)
        assertEquals(383f / 510f, histogram.blue[0], 0.0001f)
        assertEquals(255f / 510f, histogram.blue[3], 0.0001f)
        assertEquals(383f / 510f, histogram.luma[0], 0.0001f)
        assertEquals(255f / 510f, histogram.luma[2], 0.0001f)
    }

    @Test
    fun emptyVisiblePopulationProducesZeroChannels() {
        // Given only transparent pixels.
        val pixels = SrgbPixels(
            PixelSize(2, 1),
            intArrayOf(0x00FFFFFF, 0x00000000)
        )

        // When calculating the histogram.
        val histogram = calculatePhotoHistogram(pixels)

        // Then no divide-by-zero or fabricated distribution appears.
        assertEquals(0L, histogram.alphaWeight)
        listOf(
            histogram.red, histogram.green,
            histogram.blue, histogram.luma
        ).forEach { channel ->
            channel.forEach { assertEquals(0f, it) }
        }
    }

    @Test
    fun statsAreExactAlphaWeightedEightBitFigures() {
        // Given black, white, a half-alpha mid grey and pure red.
        val pixels = SrgbPixels(
            PixelSize(4, 1),
            intArrayOf(
                0xFF000000.toInt(),
                0xFFFFFFFF.toInt(),
                0x80808080.toInt(),
                0xFFFF0000.toInt()
            )
        )

        val stats = calculatePhotoHistogram(pixels).stats

        // Then weights are 255 + 255 + 128 + 255 = 893.
        // Red: 0×255, 255×255, 128×128, 255×255.
        assertEquals((255.0 * 255 * 2 + 128.0 * 128) / 893.0, stats.red.mean.toDouble(), 0.001)
        assertEquals(255L, stats.red.clippedLow)
        assertEquals(510L, stats.red.clippedHigh)
        // Green: 0 (black), 255 (white), 128, 0 (red) — the weight at 0
        // (510) already passes half of 893, so the median is 0; red's
        // cumulative weight only passes half at 255.
        assertEquals(510L, stats.green.clippedLow)
        assertEquals(0, stats.green.median)
        assertEquals(255, stats.red.median)
        // Luma of the grey is 128.
        assertEquals(255L, stats.luma.clippedLow)
        // RGB clipping counts a pixel once when any channel is at the limit:
        // black and red have a zero channel; white and red have a 255.
        assertEquals(510L, stats.anyClippedLow)
        assertEquals(510L, stats.anyClippedHigh)
        assertEquals(510L to 510L, stats.clipping(PhotoHistogramMode.Rgb))
        assertEquals(255L to 510L, stats.clipping(PhotoHistogramMode.Red))
    }

    @Test
    fun emptyPopulationHasEmptyStats() {
        val pixels = SrgbPixels(PixelSize(1, 1), intArrayOf(0x00FFFFFF))
        assertEquals(PhotoHistogramStats.Empty, calculatePhotoHistogram(pixels).stats)
    }

    @Test
    fun clipShareNeverRoundsRealPixelsToNone() {
        assertEquals("0.0%", formatClipShare(0L, 1_000L))
        assertEquals("<0.1%", formatClipShare(1L, 100_000L))
        assertEquals("12.5%", formatClipShare(125L, 1_000L))
        assertEquals("100.0%", formatClipShare(7L, 7L))
        assertEquals("0.0%", formatClipShare(3L, 0L))
    }
}
