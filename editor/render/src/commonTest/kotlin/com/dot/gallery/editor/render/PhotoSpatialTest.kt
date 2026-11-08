package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The settled spatial math: the Details 3x3 decomposition (edge-clamped
 * box blur, high-band gain, mid-tone clarity, lerp denoise), the
 * lens-blur focus mask and 13-tap disk in absolute output coordinates,
 * highlight bokeh, straight alpha preservation, and the halo-expanded
 * band parity the strip exporter depends on.
 */
class PhotoSpatialTest {

    private fun frameOf(
        width: Int,
        height: Int,
        pixels: FloatArray
    ): LinearRgbaFrame =
        LinearRgbaFrame.fromOwned(PixelSize(width, height), pixels)

    private fun px(r: Float, g: Float, b: Float, a: Float = 1f) =
        floatArrayOf(r, g, b, a)

    private fun component(frame: LinearRgbaFrame, x: Int, y: Int, c: Int) =
        frame.component(
            (y * frame.size.width + x) * LinearRgbaFrame.COMPONENTS_PER_PIXEL + c
        )

    private fun flatFrame(
        width: Int, height: Int, r: Float, g: Float, b: Float,
        a: Float = 1f
    ): LinearRgbaFrame = frameOf(
        width, height,
        FloatArray(width * height * 4) { i ->
            when (i % 4) {
                0 -> r
                1 -> g
                2 -> b
                else -> a
            }
        }
    )

    // ---- neutral ---------------------------------------------------------

    @Test
    fun `a neutral spatial stage returns the identical frame`() = runTest {
        val frame = frameOf(2, 2,
            FloatArray(16) { if (it % 4 == 3) 1f else it * 0.13f })
        val out = processPhotoSpatial(
            frame, PhotoSpatialEffects(), 0, PixelSize(2, 2)
        )
        assertTrue(out === frame, "neutral spatial must not copy")
        // A shaped-but-zero lens blur is still neutral — no arithmetic.
        val shaped = PhotoSpatialEffects(
            lensBlur = PhotoLensBlur(
                mode = LensBlurMode.Linear, transition = 0.9f,
                centerX = 0.1f, angleDegrees = 45f
            )
        )
        assertTrue(
            processPhotoSpatial(
                frame, shaped, 0, PixelSize(2, 2)
            ) === frame
        )
    }

    // ---- details ---------------------------------------------------------

    @Test
    fun `details leaves a flat field unchanged at any strength`() =
        runTest {
            // On a flat field blur == rgb, high == 0 and local == 0, and
            // lerp(rgb, blur) == rgb — every Details control collapses to
            // a no-op within float summation noise; alpha stays exact.
            val frame = flatFrame(7, 5, 0.3f, 0.5f, 0.7f, 0.6f)
            val out = processPhotoSpatial(
                frame,
                PhotoSpatialEffects(
                    details = PhotoDetails(
                        texture = 1f, clarity = -1f,
                        sharpening = 1f, noiseReduction = 1f
                    )
                ),
                0, PixelSize(7, 5)
            )
            for (i in 0 until 7 * 5 * 4) {
                if (i % 4 == 3) {
                    assertEquals(frame.component(i), out.component(i))
                } else {
                    assertEquals(frame.component(i), out.component(i), 1e-5f)
                }
            }
        }

    @Test
    fun `sharpening boosts the impulse centre and digs the ring exactly`() =
        runTest {
            // 5x5 impulse: centre 1, zeros elsewhere. The 3x3 mean at the
            // centre and its eight neighbours is 1/9; the high band is
            // value - mean. sharpening=1 -> high * 0.9.
            val pixels = FloatArray(5 * 5 * 4)
            val centre = (2 * 5 + 2) * 4
            pixels[centre] = 1f; pixels[centre + 1] = 1f
            pixels[centre + 2] = 1f; pixels[centre + 3] = 1f
            for (i in 3 until pixels.size step 4) {
                if (pixels[i] == 0f && i != centre + 3) pixels[i] = 1f
            }
            val out = processPhotoSpatial(
                frameOf(5, 5, pixels),
                PhotoSpatialEffects(
                    details = PhotoDetails(sharpening = 1f)
                ),
                0, PixelSize(5, 5)
            )
            // Centre: 1 + (1 - 1/9) * 0.9 = 1.8
            assertEquals(1.8f, component(out, 2, 2, 0), 1e-5f)
            // Neighbour: 0 + (0 - 1/9) * 0.9 = -0.1 (halo digs, unclamped)
            assertEquals(-0.1f, component(out, 2, 1, 0), 1e-5f)
            // Corner sees no impulse: untouched.
            assertEquals(0f, component(out, 0, 0, 0))
        }

    @Test
    fun `noise reduction lerps the pixel toward its 3x3 mean`() = runTest {
        // Isolated impulse: centre 1, neighbours 0. nr=1 lerps 72% of
        // the way to the mean 1/9: 1*(1-.72) + (1/9)*.72 = .36.
        val pixels = FloatArray(5 * 5 * 4)
        val centre = (2 * 5 + 2) * 4
        pixels[centre] = 1f; pixels[centre + 1] = 1f
        pixels[centre + 2] = 1f
        for (i in 3 until pixels.size step 4) pixels[i] = 1f
        val out = processPhotoSpatial(
            frameOf(5, 5, pixels),
            PhotoSpatialEffects(
                details = PhotoDetails(noiseReduction = 1f)
            ),
            0, PixelSize(5, 5)
        )
        val expected = 1f * 0.28f + (1f / 9f) * 0.72f
        assertEquals(expected, component(out, 2, 2, 0), 1e-5f)
        // A far pixel whose 3x3 is all zero stays zero.
        assertEquals(0f, component(out, 0, 0, 0))
    }

    @Test
    fun `negative texture softens where positive texture sharpens`() =
        runTest {
            // A single high-contrast edge column: the 3x3 mean at an edge
            // pixel differs from its value, so the high band is nonzero.
            val w = 9; val h = 9
            val pixels = FloatArray(w * h * 4)
            for (y in 0 until h) for (x in 0 until w) {
                val i = (y * w + x) * 4
                val v = if (x < w / 2) 0.2f else 0.8f
                pixels[i] = v; pixels[i + 1] = v
                pixels[i + 2] = v; pixels[i + 3] = 1f
            }
            suspend fun edgeDelta(texture: Float): Float {
                val out = processPhotoSpatial(
                    frameOf(w, h, pixels.copyOf()),
                    PhotoSpatialEffects(
                        details = PhotoDetails(texture = texture)
                    ),
                    0, PixelSize(w, h)
                )
                // Pixel just right of the edge starts at 0.8.
                return component(out, w / 2, h / 2, 0) - 0.8f
            }
            val positive = edgeDelta(1f)
            val negative = edgeDelta(-1f)
            assertTrue(positive > 0f, "positive texture must lift the edge")
            assertEquals(-positive, negative, 1e-5f)
        }

    @Test
    fun `edge reads are clamped, not wrapped or skipped`() = runTest {
        // A bright stripe on the very first row: the 3x3 at row 0 reads
        // row 0 twice (clamp), so its mean is 2/3 stripe + 1/3 dark —
        // different from an interior stripe's 1/3.
        val w = 9; val h = 9
        val pixels = FloatArray(w * h * 4)
        for (x in 0 until w) {
            val i = x * 4
            pixels[i] = 1f; pixels[i + 1] = 1f
            pixels[i + 2] = 1f; pixels[i + 3] = 1f
        }
        for (i in 3 until pixels.size step 4) {
            if (pixels[i] == 0f) pixels[i] = 1f
        }
        val out = processPhotoSpatial(
            frameOf(w, h, pixels),
            PhotoSpatialEffects(details = PhotoDetails(texture = 1f)),
            0, PixelSize(w, h)
        )
        // Row 0 pixel: blur = (2 rows of 1.0 over 3) -> high = 1 - 2/3.
        val topExpected = 1f + (1f - 2f / 3f) * 0.38f
        assertEquals(topExpected, component(out, 4, 0, 0), 1e-4f)
        // A wrapped read would drag row 8's dark into the mean.
        assertTrue(
            component(out, 4, 0, 0) > 1f,
            "the edge row must clamp, not wrap"
        )
    }

    // ---- lens blur ---------------------------------------------------------

    private fun verticalSplitFrame(w: Int, h: Int): LinearRgbaFrame {
        // Black left half, white right half — blur taps count white
        // fractions deterministically.
        val pixels = FloatArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            val v = if (x >= w / 2) 1f else 0f
            pixels[i] = v; pixels[i + 1] = v
            pixels[i + 2] = v; pixels[i + 3] = 1f
        }
        return frameOf(w, h, pixels)
    }

    @Test
    fun `radial blur leaves the focus point untouched and blurs far`() =
        runTest {
            val w = 33; val h = 33
            val spatial = PhotoSpatialEffects(
                lensBlur = PhotoLensBlur(
                    strength = 1f, transition = 0.05f,
                    highlightBokeh = 0f, centerX = 0f, centerY = 0.5f
                )
            )
            val out = processPhotoSpatial(
                verticalSplitFrame(w, h), spatial, 0, PixelSize(w, h)
            )
            // Focus centre (px ~0, py .5): mask 0 -> the source value.
            assertEquals(0f, component(out, 0, 16, 0))
            // x=24 deep in the blur zone, radius 16. Tap x offsets:
            // {-16, +16, -11.3, +11.3, -8, +8} -> positions
            // {8, 40->32, 12.7->13, 35.3->35, 16, 32}; white half is
            // x >= 16, so taps land 3 black + 10 white -> mean 10/13.
            assertEquals(10f / 13f, component(out, 24, 16, 0), 1e-4f)
        }

    @Test
    fun `linear blur blurs across the axis and not along the line`() =
        runTest {
            val w = 33; val h = 33
            // Angle 0: focus runs vertically through centerX; distance
            // is |px - centerX| — the right half is uniformly far.
            val spatial = PhotoSpatialEffects(
                lensBlur = PhotoLensBlur(
                    mode = LensBlurMode.Linear,
                    strength = 1f, transition = 0.05f,
                    highlightBokeh = 0f,
                    centerX = 0.5f, centerY = 0.5f, angleDegrees = 0f
                )
            )
            val out = processPhotoSpatial(
                verticalSplitFrame(w, h), spatial, 0, PixelSize(w, h)
            )
            // On the focus line (x=16 -> px .5): radius 0, source value.
            assertEquals(1f, component(out, 16, 16, 0))
            // Far right (x=32): the disk reads the same row plus columns
            // clamped at the edge — still all-white. Check x=24 instead:
            // same 10/13 white fraction as the radial case.
            assertEquals(10f / 13f, component(out, 24, 16, 0), 1e-4f)
        }

    @Test
    fun `lens blur preserves the source alpha exactly`() = runTest {
        val w = 33; val h = 33
        val pixels = FloatArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            pixels[i] = if (x >= w / 2) 1f else 0f
            pixels[i + 1] = pixels[i]
            pixels[i + 2] = pixels[i]
            // Varied straight alpha — must survive untouched, never
            // averaged into neighbours.
            pixels[i + 3] = if ((x + y) % 3 == 0) 0.5f else 1f
        }
        val out = processPhotoSpatial(
            frameOf(w, h, pixels),
            PhotoSpatialEffects(
                lensBlur = PhotoLensBlur(
                    strength = 1f, transition = 0.05f, centerX = 0f
                )
            ),
            0, PixelSize(w, h)
        )
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            assertEquals(
                pixels[i + 3], component(out, x, y, 3),
                "alpha at ($x,$y) must equal the source alpha"
            )
        }
    }

    @Test
    fun `highlight bokeh lifts blurred highlights only`() = runTest {
        val w = 33; val h = 33
        // Flat luma .9 field: the blur keeps it flat, so the whole
        // out-of-focus area gains (0.9 - 0.7) * bokeh * mask.
        val spatial = PhotoSpatialEffects(
            lensBlur = PhotoLensBlur(
                strength = 1f, transition = 0.05f,
                highlightBokeh = 1f, centerX = 0f, centerY = 0.5f
            )
        )
        val out = processPhotoSpatial(
            flatFrame(w, h, 0.9f, 0.9f, 0.9f), spatial, 0, PixelSize(w, h)
        )
        // Focus point: mask 0 -> untouched .9.
        assertEquals(0.9f, component(out, 0, 16, 0), 1e-4f)
        // Far field: mask 1 -> 0.9 + 0.2.
        assertEquals(1.1f, component(out, 32, 16, 0), 1e-3f)
        // A dark field gains nothing — max(0, luma - .7) is zero.
        val dark = processPhotoSpatial(
            flatFrame(w, h, 0.3f, 0.3f, 0.3f), spatial, 0, PixelSize(w, h)
        )
        assertEquals(0.3f, component(dark, 32, 16, 0), 1e-4f)
    }

    @Test
    fun `blur applies to the details output, not the raw input`() = runTest {
        // A sharpened impulse spreads through the disk: without the
        // ordering the blurred far field would equal the unsharpened
        // input. Compare against details-only to show the ordering
        // matters rather than re-deriving the 13-tap sum.
        val w = 33; val h = 33
        val pixels = FloatArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            val v = if (x >= w / 2) 1f else 0f
            pixels[i] = v; pixels[i + 1] = v
            pixels[i + 2] = v; pixels[i + 3] = 1f
        }
        val details = PhotoDetails(sharpening = 1f)
        val blurOnly = processPhotoSpatial(
            frameOf(w, h, pixels.copyOf()),
            PhotoSpatialEffects(
                lensBlur = PhotoLensBlur(
                    strength = 1f, transition = 0.05f,
                    centerX = 0f, centerY = 0.5f
                )
            ),
            0, PixelSize(w, h)
        )
        val both = processPhotoSpatial(
            frameOf(w, h, pixels.copyOf()),
            PhotoSpatialEffects(
                details = details,
                lensBlur = PhotoLensBlur(
                    strength = 1f, transition = 0.05f,
                    centerX = 0f, centerY = 0.5f
                )
            ),
            0, PixelSize(w, h)
        )
        // The sharpened edge digs a halo the blur then spreads — the
        // combined value at a mid-blur pixel must differ from blur-only.
        assertTrue(
            abs(component(both, 24, 16, 0) - component(blurOnly, 24, 16, 0)) >
                1e-4f,
            "details before blur must change the blurred result"
        )
    }

    // ---- band parity -------------------------------------------------------

    /**
     * Mirrors the strip exporter's halo expansion: the full frame is
     * processed once at origin 0; each band processes the expanded slice
     * [y - halo, y + rows + halo) at its absolute origin and the centre
     * rows must match the full frame bit for bit.
     */
    private suspend fun assertBandParity(
        width: Int,
        height: Int,
        source: FloatArray,
        spatial: PhotoSpatialEffects
    ) {
        val size = PixelSize(width, height)
        val full = processPhotoSpatial(
            frameOf(width, height, source.copyOf()), spatial, 0, size
        )
        val halo = spatial.requiredHalo
        for (bandHeight in intArrayOf(1, 7)) {
            var y = 0
            while (y < height) {
                val rows = minOf(bandHeight, height - y)
                val top = maxOf(0, y - halo)
                val bottom = minOf(height, y + rows + halo)
                val expanded = FloatArray(width * (bottom - top) * 4)
                for (r in top until bottom) {
                    source.copyInto(
                        expanded, (r - top) * width * 4,
                        r * width * 4, (r + 1) * width * 4
                    )
                }
                val band = processPhotoSpatial(
                    frameOf(width, bottom - top, expanded),
                    spatial, top, size
                )
                for (r in 0 until rows) for (x in 0 until width) {
                    for (c in 0 until 4) {
                        assertEquals(
                            component(full, x, y + r, c),
                            component(band, x, y - top + r, c),
                            "band top=$top row ${y + r} pixel ($x) " +
                                "channel $c must equal the full frame"
                        )
                    }
                }
                y += rows
            }
        }
    }

    @Test
    fun `details is identical across halo-expanded bands`() = runTest {
        val w = 17; val h = 23
        val pixels = FloatArray(w * h * 4) { i ->
            if (i % 4 == 3) 1f else ((i * 31) % 97) / 97f
        }
        assertBandParity(
            w, h, pixels,
            PhotoSpatialEffects(
                details = PhotoDetails(
                    texture = 0.8f, clarity = -0.4f,
                    sharpening = 1f, noiseReduction = 0.6f
                )
            )
        )
    }

    @Test
    fun `lens blur is identical across halo-expanded bands`() = runTest {
        val w = 21; val h = 45
        val pixels = FloatArray(w * h * 4) { i ->
            if (i % 4 == 3) 1f else ((i * 57) % 89) / 89f
        }
        // Full strength: the disk reaches 16px — bands of 7 rows must
        // expand past both boundaries, and the last band clamps.
        assertBandParity(
            w, h, pixels,
            PhotoSpatialEffects(
                lensBlur = PhotoLensBlur(
                    strength = 1f, transition = 0.4f,
                    highlightBokeh = 0.8f, centerX = 0.3f, centerY = 0.6f
                )
            )
        )
        assertBandParity(
            w, h, pixels,
            PhotoSpatialEffects(
                details = PhotoDetails(texture = 0.5f, sharpening = 0.5f),
                lensBlur = PhotoLensBlur(
                    mode = LensBlurMode.Linear,
                    strength = 0.9f, transition = 0.3f,
                    angleDegrees = -30f
                )
            )
        )
    }

    // ---- guards ------------------------------------------------------------

    @Test
    fun `a region must declare a plausible origin on both axes`() = runTest {
        val frame = flatFrame(2, 2, 0.5f, 0.5f, 0.5f)
        val spatial = PhotoSpatialEffects(
            details = PhotoDetails(texture = 0.5f)
        )
        assertFailsWith<IllegalArgumentException> {
            processPhotoSpatial(frame, spatial, 2, PixelSize(2, 2))
        }
        assertFailsWith<IllegalArgumentException> {
            processPhotoSpatial(frame, spatial, 0, PixelSize(4, 2), originX = 3)
        }
        assertEquals(
            frame.size,
            processPhotoSpatial(frame, spatial, 0, PixelSize(4, 2), originX = 1).size
        )
    }

    @Test
    fun `nonfinite output is rejected`() = runTest {
        // MAX_VALUE times the sharpened impulse high band overflows.
        val pixels = FloatArray(5 * 5 * 4)
        val centre = (2 * 5 + 2) * 4
        pixels[centre] = Float.MAX_VALUE
        pixels[centre + 1] = Float.MAX_VALUE
        pixels[centre + 2] = Float.MAX_VALUE
        for (i in 3 until pixels.size step 4) pixels[i] = 1f
        assertFailsWith<IllegalArgumentException> {
            processPhotoSpatial(
                frameOf(5, 5, pixels),
                PhotoSpatialEffects(
                    details = PhotoDetails(sharpening = 1f)
                ),
                0, PixelSize(5, 5)
            )
        }
    }

    @Test
    fun `spatial effect dispatches through the graph contract`() = runTest {
        val effect = PhotoSpatialEffect()
        val size = PixelSize(4, 4)
        val frame = flatFrame(4, 4, 0.5f, 0.5f, 0.5f)
        val neutral = effect.render(
            listOf(frame),
            EffectParameters.Spatial(PhotoSpatialEffects(), 0, size)
        )
        assertTrue(neutral === frame)
        assertFailsWith<UnsupportedOperationException> {
            effect.render(listOf(frame), EffectParameters.Source("x"))
        }
        assertFailsWith<UnsupportedOperationException> {
            effect.render(
                emptyList(),
                EffectParameters.Spatial(PhotoSpatialEffects(), 0, size)
            )
        }
    }
}
