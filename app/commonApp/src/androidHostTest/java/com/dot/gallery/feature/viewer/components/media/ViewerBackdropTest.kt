/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.components.media

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The viewer's blurred backdrop is a pre-blurred, media-width bitmap drawn as a stretched quad.
 * Its appearance is decided entirely by these two pure functions, so they are what is pinned
 * here — the composable around them only places the result.
 */
class ViewerBackdropTest {

    @Test
    fun targetSizeKeepsTheSourceAspect() {
        assertEquals(IntSize(48, 48), backdropTargetSize(600, 600, 48))
        assertEquals(IntSize(48, 64), backdropTargetSize(600, 800, 48))
        assertEquals(IntSize(48, 24), backdropTargetSize(1200, 600, 48))
    }

    @Test
    fun targetSizeNeverUpscalesAPreviewSmallerThanTheTarget() {
        assertEquals(IntSize(32, 32), backdropTargetSize(32, 32, 48))
        assertEquals(IntSize(20, 10), backdropTargetSize(20, 10, 48))
    }

    @Test
    fun targetSizeDegradesToZeroInsteadOfThrowing() {
        assertEquals(IntSize.Zero, backdropTargetSize(0, 600, 48))
        assertEquals(IntSize.Zero, backdropTargetSize(600, 0, 48))
        assertEquals(IntSize.Zero, backdropTargetSize(600, 600, 0))
        assertEquals(IntSize.Zero, backdropTargetSize(-1, 600, 48))
    }

    @Test
    fun targetSizeKeepsAtLeastOnePixelOfHeightForAPanoramaStrip() {
        assertEquals(IntSize(48, 1), backdropTargetSize(6000, 20, 48))
    }

    @Test
    fun aFlatColourIsUnchangedByTheBlur() {
        val pixels = IntArray(16 * 16) { argb(alpha = 0xFF, red = 40, green = 80, blue = 120) }

        boxBlurArgb(pixels, width = 16, height = 16, radius = 4)

        assertTrue(pixels.all { it == argb(0xFF, 40, 80, 120) })
    }

    @Test
    fun alphaSurvivesTheBlurUntouched() {
        // Edge clamping exists so the border keeps the image's own colour instead of fading to
        // transparent: a drawImage of a half-transparent quad would show the black viewer through
        // it as a dark rim.
        val pixels = IntArray(8 * 8) { argb(alpha = 0xFF, red = 255, green = 255, blue = 255) }

        boxBlurArgb(pixels, width = 8, height = 8, radius = 2)

        assertTrue(pixels.all { (it ushr 24) and 0xFF == 0xFF })
    }

    @Test
    fun aHardEdgeIsSoftenedIntoTheBlurredSide() {
        // Left half black, right half white: after the blur the seam must hold intermediate
        // values, otherwise the "blur" is just a downscale and would still show a visible edge
        // upscaled over the whole screen.
        val width = 16
        val height = 8
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            argb(alpha = 0xFF, red = if (x < width / 2) 0 else 255, green = 0, blue = 0)
        }

        boxBlurArgb(pixels, width, height, radius = 4)

        val seam = (width / 2 - 1 until width / 2 + 1).map { x ->
            (pixels[x] shr 16) and 0xFF
        }
        assertTrue("seam should be grey, was $seam", seam.all { it in 1..254 })
    }

    @Test
    fun theBorderKeepsTheEdgeColourInsteadOfFadingToBlack() {
        val width = 12
        val height = 12
        val pixels = IntArray(width * height) { argb(alpha = 0xFF, red = 200, green = 200, blue = 200) }

        boxBlurArgb(pixels, width, height, radius = 3)

        assertEquals(argb(0xFF, 200, 200, 200), pixels[0])
        assertEquals(argb(0xFF, 200, 200, 200), pixels[width * height - 1])
    }

    @Test
    fun zeroRadiusLeavesThePixelsAlone() {
        val pixels = IntArray(9) { argb(alpha = 0xFF, red = it * 10, green = 0, blue = 0) }
        val before = pixels.copyOf()

        boxBlurArgb(pixels, width = 3, height = 3, radius = 0)

        assertEquals(before.toList(), pixels.toList())
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}
