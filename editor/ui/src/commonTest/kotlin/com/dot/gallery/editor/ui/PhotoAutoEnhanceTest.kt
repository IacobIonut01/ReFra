package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoColourGrade
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Auto enhance's pure pixel maths: tone keys off the luma
 * distribution, white balance off the neutral mid-tones' mean cast, and
 * only the analysed fields of the recipe are replaced.
 */
class PhotoAutoEnhanceTest {

    private fun pixels(width: Int, height: Int, argb: (Int, Int) -> Int): SrgbPixels {
        val data = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                data[y * width + x] = argb(x, y)
            }
        }
        return SrgbPixels(PixelSize(width, height), data)
    }

    private fun argb(r: Int, g: Int, b: Int) =
        (-0x1000000) or (r shl 16) or (g shl 8) or b

    private fun grey(value: Int) = argb(value, value, value)

    private fun srgb(luma: Float) =
        (luma.coerceIn(0f, 1f) * 255f).roundToInt()

    @Test
    fun darkImageLiftsExposure() {
        val source = pixels(64, 64) { _, _ -> grey(srgb(0.04f)) }
        val result = autoEnhance(source, PhotoEdits())
        assertTrue(
            result.color.exposureStops > 0.3f,
            "expected exposure > 0.3, got ${result.color.exposureStops}"
        )
    }

    @Test
    fun clippedMassPullsHighlightsDown() {
        // 5 % pure white over a mid grey field.
        val white = 205
        val source = pixels(64, 64) { x, y ->
            if (y * 64 + x < white) argb(255, 255, 255) else grey(128)
        }
        val result = autoEnhance(source, PhotoEdits())
        assertTrue(
            result.color.highlights < 0f,
            "expected highlights < 0, got ${result.color.highlights}"
        )
    }

    @Test
    fun blueCastWarmsTheWhiteBalance() {
        val source = pixels(64, 64) { _, _ -> argb(150, 150, 200) }
        val result = autoEnhance(source, PhotoEdits())
        assertTrue(
            result.finishing.whiteBalance.temperatureKelvin > 6500f,
            "expected kelvin > 6500, got " +
                result.finishing.whiteBalance.temperatureKelvin
        )
    }

    @Test
    fun wellExposedNeutralRampStaysCloseToNeutral() {
        // A wide grey ramp centred so its median lands on the 0.18
        // linear middle grey: exposure ~0, IQR above the flat gate.
        val source = pixels(64, 64) { x, _ ->
            grey(srgb(0.06f + 0.84f * x / 63f))
        }
        val result = autoEnhance(source, PhotoEdits())
        assertTrue(
            kotlin.math.abs(result.color.exposureStops) < 0.15f,
            "exposure ${result.color.exposureStops}"
        )
        assertEquals(1f, result.color.contrast)
        assertEquals(PhotoWhiteBalance(), result.finishing.whiteBalance)
    }

    @Test
    fun untouchedRecipeFieldsCarryOver() {
        val source = pixels(64, 64) { x, _ ->
            grey(srgb(0.06f + 0.84f * x / 63f))
        }
        val base = PhotoEdits(
            color = ColorAdjustments(
                saturation = 0.5f,
                whites = 0.3f,
                blacks = -0.2f
            ),
            finishing = PhotoFinishing(
                whiteBalance = PhotoWhiteBalance(7000f, 10f),
                grade = PhotoColourGrade(
                    vibrance = -0.4f,
                    protectSkin = true
                )
            )
        )
        val result = autoEnhance(source, base)
        assertEquals(base.color.saturation, result.color.saturation)
        assertEquals(base.color.whites, result.color.whites)
        assertEquals(base.color.blacks, result.color.blacks)
        assertEquals(base.geometry, result.geometry)
        assertEquals(base.layers, result.layers)
        assertEquals(base.spatial, result.spatial)
        assertEquals(base.retouch, result.retouch)
        assertEquals(base.develop, result.develop)
        assertEquals(
            base.finishing.grade.protectSkin,
            result.finishing.grade.protectSkin
        )
        assertEquals(base.finishing.curves, result.finishing.curves)
        assertEquals(base.finishing.vignette, result.finishing.vignette)
        assertEquals(base.finishing.grain, result.finishing.grain)
        assertEquals(base.finishing.look, result.finishing.look)
        // A neutral estimate keeps the base white balance; vibrance is
        // always re-graded.
        assertEquals(
            base.finishing.whiteBalance,
            result.finishing.whiteBalance
        )
        assertNotEquals(base.finishing.grade.vibrance, result.finishing.grade.vibrance)
    }
}
