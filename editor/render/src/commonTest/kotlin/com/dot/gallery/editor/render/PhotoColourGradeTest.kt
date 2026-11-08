package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PhotoColourGrade
import com.dot.gallery.editor.core.model.PhotoColourWheel
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoHslAdjustment
import com.dot.gallery.editor.core.model.PhotoHslBand
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The colour grade's behaviour on single pixels: each control moves only
 * what it claims to, and a grade with nothing set keeps the finishing
 * stage's bit-preserving neutral path.
 */
class PhotoColourGradeTest {

    private suspend fun graded(
        grade: PhotoColourGrade,
        r: Float,
        g: Float,
        b: Float
    ): List<Float> {
        val frame = LinearRgbaFrame.fromOwned(
            PixelSize(1, 1),
            floatArrayOf(r, g, b, 1f)
        )
        val out = processPhotoFinishing(
            frame,
            PhotoFinishing(grade = grade),
            0,
            PixelSize(1, 1)
        )
        return List(3) { out.component(it) }
    }

    private fun saturationOf(rgb: List<Float>) = rgb.max() - rgb.min()

    @Test
    fun `protect skin alone is neutral and returns the identical frame`() = runTest {
        val grade = PhotoColourGrade(protectSkin = true)
        assertTrue(grade.isNeutral)
        val frame = LinearRgbaFrame.fromOwned(
            PixelSize(1, 1),
            floatArrayOf(0.5f, 0.3f, 0.2f, 1f)
        )
        val out = processPhotoFinishing(
            frame,
            PhotoFinishing(grade = grade),
            0,
            PixelSize(1, 1)
        )
        assertTrue(out === frame)
    }

    @Test
    fun `vibrance lifts muted colours more than saturated ones`() = runTest {
        val grade = PhotoColourGrade(vibrance = 0.5f)
        val muted = listOf(0.5f, 0.45f, 0.4f)
        val vivid = listOf(0.9f, 0.1f, 0.1f)
        val mutedOut = graded(grade, muted[0], muted[1], muted[2])
        val vividOut = graded(grade, vivid[0], vivid[1], vivid[2])
        val mutedGain = saturationOf(mutedOut) / saturationOf(muted)
        val vividGain = saturationOf(vividOut) / saturationOf(vivid)
        assertTrue(mutedGain > vividGain, "$mutedGain <= $vividGain")
    }

    @Test
    fun `protect skin damps vibrance on skin hues only`() = runTest {
        val open = PhotoColourGrade(vibrance = 0.8f)
        val guarded = open.copy(protectSkin = true)
        val skin = listOf(0.7f, 0.5f, 0.4f)
        val sky = listOf(0.3f, 0.45f, 0.7f)
        assertTrue(
            saturationOf(graded(guarded, skin[0], skin[1], skin[2])) <
                saturationOf(graded(open, skin[0], skin[1], skin[2]))
        )
        assertEquals(
            graded(open, sky[0], sky[1], sky[2]),
            graded(guarded, sky[0], sky[1], sky[2])
        )
    }

    @Test
    fun `an hsl band moves its own hue and leaves distant hues and greys alone`() =
        runTest {
            val grade = PhotoColourGrade().withAdjustment(
                PhotoHslBand.Blue,
                PhotoHslAdjustment(saturation = -1f)
            )
            // Hue 220, the band's exact centre.
            val g = 0.36666667f
            val blue = graded(grade, 0.1f, g, 0.9f)
            val luma = 0.2126f * 0.1f + 0.7152f * g + 0.0722f * 0.9f
            blue.forEach { assertEquals(luma, it, 1e-3f) }
            val red = graded(grade, 0.9f, 0.1f, 0.1f)
            assertEquals(0.9f, red[0], 1e-5f)
            assertEquals(0.1f, red[1], 1e-5f)
            assertEquals(0.1f, red[2], 1e-5f)
            assertEquals(
                listOf(0.5f, 0.5f, 0.5f),
                graded(grade, 0.5f, 0.5f, 0.5f)
            )
        }

    @Test
    fun `hsl hue rotates toward the next band`() = runTest {
        val grade = PhotoColourGrade().withAdjustment(
            PhotoHslBand.Red,
            PhotoHslAdjustment(hue = 1f)
        )
        val (r, g, b) = graded(grade, 0.9f, 0.05f, 0.05f)
        assertTrue(g > b, "red should turn toward orange: $r $g $b")
    }

    @Test
    fun `hsl luminance is a one stop gain at full strength`() = runTest {
        val grade = PhotoColourGrade().withAdjustment(
            PhotoHslBand.Red,
            PhotoHslAdjustment(luminance = 1f)
        )
        assertEquals(
            listOf(1.6f, 0f, 0f),
            graded(grade, 0.8f, 0f, 0f)
        )
    }

    @Test
    fun `wheels act by tone and the push follows the wheel hue`() = runTest {
        val shadows = PhotoColourGrade(
            shadows = PhotoColourWheel(hueDegrees = 240f, amount = 1f)
        )
        val dark = graded(shadows, 0.01f, 0.01f, 0.01f)
        assertTrue(dark[2] > dark[0] && dark[2] > dark[1], "$dark")
        val bright = graded(shadows, 1f, 1f, 1f)
        bright.forEach { assertEquals(1f, it, 1e-5f) }

        val highlights = PhotoColourGrade(
            highlights = PhotoColourWheel(hueDegrees = 0f, amount = 1f)
        )
        val warm = graded(highlights, 1f, 1f, 1f)
        assertTrue(warm[0] > warm[1] && warm[0] > warm[2], "$warm")
    }

    @Test
    fun `wheel luminance is a gain on its tonal region`() = runTest {
        val grade = PhotoColourGrade(
            highlights = PhotoColourWheel(luminance = 1f)
        )
        graded(grade, 1f, 1f, 1f).forEach { assertEquals(2f, it, 1e-5f) }
    }

    @Test
    fun `out of range input stays finite and is not clamped`() = runTest {
        val grade = PhotoColourGrade(
            vibrance = 1f,
            shadows = PhotoColourWheel(hueDegrees = 90f, amount = 1f)
        ).withAdjustment(PhotoHslBand.Green, PhotoHslAdjustment(0.5f, 0.5f, 0.5f))
        val out = graded(grade, -0.2f, 3f, 0.1f)
        out.forEach { assertTrue(it.isFinite()) }
        assertTrue(out[1] > 1f)
    }
}
