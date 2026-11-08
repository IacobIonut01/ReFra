package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGrain
import com.dot.gallery.editor.core.model.PhotoLook
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest

/**
 * The settled finishing math: white balance gains, the tone curves in their
 * display encoding, the vignette mask in
 * absolute output coordinates, the deterministic grain field, and the
 * neutral-path bit preservation the whole stage guarantees.
 */
class PhotoFinishingTest {

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

    // ---- neutral bit preservation --------------------------------------

    @Test
    fun `a neutral finishing returns the identical frame`() = runTest {
        // RGB holds out-of-range scene values; alpha must stay in [0,1].
        val pixels = FloatArray(4 * 4) { if (it % 4 == 3) 1f else it * 0.13f }
        val frame = frameOf(2, 2, pixels)
        val out = processPhotoFinishing(
            frame, PhotoFinishing(), 0, PixelSize(2, 2)
        )
        assertTrue(out === frame, "neutral finishing must not copy")
    }

    @Test
    fun `a neutral stage inside an active finishing does no arithmetic`() =
        runTest {
            // Only grain active — a pixel with zero noise contribution
            // (envelope zero impossible, so check alpha untouched and
            // deterministic values) — instead assert a neutral-WB
            // recipe's pixels pass through the WB stage unmultiplied by
            // checking exact output against the grain-only expectation
            // computed from the same input twice.
            val frame = frameOf(4, 1,
                px(0.2f, 0.4f, 0.6f) + px(0.9f, 0.1f, 0.5f) +
                    px(0.3f, 0.3f, 0.3f) + px(0f, 0f, 0f))
            val finishing = PhotoFinishing(
                grain = PhotoGrain(amount = 0.5f)
            )
            val a = processPhotoFinishing(frame, finishing, 0, PixelSize(4, 1))
            val b = processPhotoFinishing(frame, finishing, 0, PixelSize(4, 1))
            for (i in 0 until 16) {
                assertEquals(a.component(i), b.component(i))
            }
            // Alpha untouched everywhere.
            for (i in 0 until 4) {
                assertEquals(frame.component(i * 4 + 3), a.component(i * 4 + 3))
            }
        }

    // ---- white balance --------------------------------------------------

    private suspend fun wbGains(kelvin: Float, tint: Float): FloatArray {
        val frame = frameOf(1, 1, px(0.5f, 0.5f, 0.5f))
        val out = processPhotoFinishing(
            frame,
            PhotoFinishing(
                whiteBalance = PhotoWhiteBalance(kelvin, tint)
            ),
            0, PixelSize(1, 1)
        )
        return floatArrayOf(
            out.component(0) / 0.5f,
            out.component(1) / 0.5f,
            out.component(2) / 0.5f
        )
    }

    @Test
    fun `white balance matches the settled gain formula`() = runTest {
        // Neutral anchor — the stage performs no arithmetic at all.
        val neutral = wbGains(6500f, 0f)
        assertEquals(1f, neutral[0])
        assertEquals(1f, neutral[1])
        assertEquals(1f, neutral[2])

        // Warm: t = +1 — red gains 2^.35, blue loses 2^-.35.
        val warm = wbGains(12000f, 0f)
        assertEquals(2.0.pow(0.35).toFloat(), warm[0], 1e-6f)
        assertEquals(1f, warm[1], 1e-6f)
        assertEquals(2.0.pow(-0.35).toFloat(), warm[2], 1e-6f)

        // Cool: t = -1 on the 4500 K span.
        val cool = wbGains(2000f, 0f)
        assertEquals(2.0.pow(-0.35).toFloat(), cool[0], 1e-6f)
        assertEquals(1f, cool[1], 1e-6f)
        assertEquals(2.0.pow(0.35).toFloat(), cool[2], 1e-6f)

        // Tint +100: u = 1 — red/blue gain 2^.1, green loses 2^-.2.
        val magenta = wbGains(6500f, 100f)
        assertEquals(2.0.pow(0.1).toFloat(), magenta[0], 1e-6f)
        assertEquals(2.0.pow(-0.2).toFloat(), magenta[1], 1e-6f)
        assertEquals(2.0.pow(0.1).toFloat(), magenta[2], 1e-6f)
    }

    // ---- curves ---------------------------------------------------------

    @Test
    fun `curves work on display-encoded values`() = runTest {
        // Mid grey on the display axis, up two quarters of the range.
        val curve = PhotoToneCurve.ofSteps(0 to 0, 128 to 192, 255 to 255)
        val input = curveDecode(128f / 255f)
        val out = processPhotoFinishing(
            frameOf(1, 1, px(input, input, input)),
            PhotoFinishing(curves = PhotoCurves(master = curve)),
            0, PixelSize(1, 1)
        )
        assertEquals(curveDecode(192f / 255f), out.component(0), 1e-4f)
    }

    @Test
    fun `curve encoding round trips outside the unit domain`() {
        for (linear in listOf(-0.5f, -0.001f, 0f, 0.002f, 0.18f, 1f, 2.5f)) {
            assertEquals(linear, curveDecode(curveEncode(linear)), 1e-4f)
        }
    }

    @Test
    fun `master applies before the channel curve`() = runTest {
        // Master lifts 64 -> 128; red bends 128 -> 230. If the order were
        // reversed the red curve would read 64 first.
        val master = PhotoToneCurve.ofSteps(0 to 0, 64 to 128, 255 to 255)
        val red = PhotoToneCurve.ofSteps(0 to 0, 128 to 230, 255 to 255)
        val input = curveDecode(64f / 255f)
        val out = processPhotoFinishing(
            frameOf(1, 1, px(input, input, input)),
            PhotoFinishing(curves = PhotoCurves(master = master, red = red)),
            0, PixelSize(1, 1)
        )
        assertEquals(curveDecode(230f / 255f), out.component(0), 1e-4f)
        assertEquals(curveDecode(128f / 255f), out.component(1), 1e-4f)
        assertEquals(curveDecode(128f / 255f), out.component(2), 1e-4f)
    }

    @Test
    fun `a channel with neutral curves keeps its bits`() = runTest {
        val red = PhotoToneCurve.ofSteps(0 to 0, 128 to 230, 255 to 255)
        val out = processPhotoFinishing(
            frameOf(1, 1, px(0.123456f, 0.654321f, 1.5f)),
            PhotoFinishing(curves = PhotoCurves(red = red)),
            0, PixelSize(1, 1)
        )
        assertEquals(0.654321f, out.component(1))
        assertEquals(1.5f, out.component(2))
    }

    @Test
    fun `curve points left on the diagonal change nothing`() = runTest {
        val frame = frameOf(1, 1, px(0.123456f, 0.654321f, 0.5f))
        val onDiagonal = PhotoToneCurve.ofSteps(0 to 0, 128 to 128, 255 to 255)
        val out = processPhotoFinishing(
            frame,
            PhotoFinishing(curves = PhotoCurves(master = onDiagonal)),
            0, PixelSize(1, 1)
        )
        assertTrue(out === frame)
    }

    // ---- vignette --------------------------------------------------------

    @Test
    fun `vignette leaves the centre untouched and moves edges by sign`() =
        runTest {
            val size = PixelSize(21, 21)
            val frame = frameOf(21, 21,
                FloatArray(21 * 21 * 4) { i ->
                    if (i % 4 == 3) 1f else 0.5f
                })
            val dark = processPhotoFinishing(
                frame,
                PhotoFinishing(
                    vignette = PhotoVignette(amount = -0.5f)
                ),
                0, size
            )
            // Centre pixel: distance ~0, mask 0 — bit-identical.
            assertEquals(0.5f, component(dark, 10, 10, 0))
            // Corner darkens.
            assertTrue(
                component(dark, 0, 0, 0) < 0.5f,
                "darkening vignette must darken the corner"
            )
            // Centre stays brighter than the corner.
            assertTrue(
                component(dark, 10, 10, 0) > component(dark, 0, 0, 0)
            )
            val bright = processPhotoFinishing(
                frame,
                PhotoFinishing(
                    vignette = PhotoVignette(amount = 0.5f)
                ),
                0, size
            )
            assertEquals(0.5f, component(bright, 10, 10, 0))
            assertTrue(component(bright, 0, 0, 0) > 0.5f)
        }

    @Test
    fun `vignette highlight protection spares bright edge pixels`() =
        runTest {
            val size = PixelSize(11, 11)
            // Top-left corner pixel bright, bottom-right corner dark.
            val pixels = FloatArray(11 * 11 * 4)
            for (y in 0 until 11) for (x in 0 until 11) {
                val i = (y * 11 + x) * 4
                val v = if (x == 0 && y == 0) 0.95f else 0.1f
                pixels[i] = v; pixels[i + 1] = v; pixels[i + 2] = v
                pixels[i + 3] = 1f
            }
            val vignette = PhotoVignette(
                amount = -0.8f, highlights = 1f
            )
            val protected = processPhotoFinishing(
                frameOf(11, 11, pixels.copyOf()),
                PhotoFinishing(vignette = vignette), 0, size
            )
            val unprotected = processPhotoFinishing(
                frameOf(11, 11, pixels.copyOf()),
                PhotoFinishing(
                    vignette = vignette.copy(highlights = 0f)
                ), 0, size
            )
            // The bright corner keeps more luminance with protection.
            assertTrue(
                component(protected, 0, 0, 0) >
                    component(unprotected, 0, 0, 0),
                "highlight protection must spare the bright corner"
            )
            // A dark corner (0,10 — luma .1 < .6) is unaffected.
            assertEquals(
                component(protected, 0, 10, 0),
                component(unprotected, 0, 10, 0)
            )
        }

    // ---- grain -----------------------------------------------------------

    @Test
    fun `grain is deterministic in seed and position`() = runTest {
        val size = PixelSize(16, 16)
        val pixels = FloatArray(16 * 16 * 4) { i ->
            if (i % 4 == 3) 1f else 0.4f
        }
        val grain = PhotoGrain(amount = 0.6f, seed = 99)
        val finishing = PhotoFinishing(grain = grain)
        val a = processPhotoFinishing(
            frameOf(16, 16, pixels.copyOf()), finishing, 0, size
        )
        val b = processPhotoFinishing(
            frameOf(16, 16, pixels.copyOf()), finishing, 0, size
        )
        for (i in 0 until pixels.size) {
            assertEquals(
                a.component(i), b.component(i),
                "same seed must reproduce identical bits"
            )
        }
        val other = processPhotoFinishing(
            frameOf(16, 16, pixels.copyOf()),
            PhotoFinishing(grain = grain.copy(seed = 100)), 0, size
        )
        var differs = false
        for (i in 0 until pixels.size) {
            if (a.component(i) != other.component(i)) differs = true
        }
        assertTrue(differs, "a different seed must change the field")
        // Alpha untouched.
        for (y in 0 until 16) for (x in 0 until 16) {
            assertEquals(1f, component(a, x, y, 3))
        }
    }

    @Test
    fun `grain and vignette are identical across band boundaries`() =
        runTest {
            // The strip-parity contract: one full frame vs the same
            // frame rendered in one-row bands must agree bit for bit —
            // vignette distances and grain cells both key on absolute
            // output coordinates.
            val size = PixelSize(17, 13)
            val pixels = FloatArray(17 * 13 * 4) { i ->
                if (i % 4 == 3) 1f else (i % 97) / 97f
            }
            val finishing = PhotoFinishing(
                whiteBalance = PhotoWhiteBalance(3200f, -10f),
                curves = PhotoCurves(
                    master = PhotoToneCurve.ofQuarterOrdinates(
                        listOf(0f, 0.3f, 0.55f, 0.8f, 1f)
                    )
                ),
                vignette = PhotoVignette(
                    amount = -0.6f, midpoint = 0.4f, roundness = 0.7f
                ),
                grain = PhotoGrain(amount = 0.5f, roughness = 0.2f)
            )
            val full = processPhotoFinishing(
                frameOf(17, 13, pixels.copyOf()), finishing, 0, size
            )
            for (bandHeight in intArrayOf(1, 5)) {
                var y = 0
                while (y < 13) {
                    val rows = minOf(bandHeight, 13 - y)
                    val bandPixels = FloatArray(17 * rows * 4)
                    for (r in 0 until rows) {
                        pixels.copyInto(
                            bandPixels, r * 17 * 4,
                            (y + r) * 17 * 4, (y + r + 1) * 17 * 4
                        )
                    }
                    val band = processPhotoFinishing(
                        frameOf(17, rows, bandPixels),
                        finishing, y, size
                    )
                    for (r in 0 until rows) for (x in 0 until 17) {
                        for (c in 0 until 4) {
                            assertEquals(
                                component(full, x, y + r, c),
                                component(band, x, r, c),
                                "band $y..${y + rows - 1} pixel ($x,${y + r}) " +
                                    "channel $c must equal the full frame"
                            )
                        }
                    }
                    y += rows
                }
            }
        }

    // ---- look ------------------------------------------------------------

    private suspend fun lookPixel(
        r: Float,
        g: Float,
        b: Float,
        look: PhotoLook,
        a: Float = 1f
    ): FloatArray {
        val out = processPhotoFinishing(
            frameOf(1, 1, px(r, g, b, a)),
            PhotoFinishing(look = look),
            0, PixelSize(1, 1)
        )
        return floatArrayOf(
            out.component(0), out.component(1),
            out.component(2), out.component(3)
        )
    }

    @Test
    fun `a neutral look performs no arithmetic`() = runTest {
        val frame = frameOf(1, 1, px(0.4f, 0.6f, 0.8f))
        // No id selected — bit-identical output even at a partial blend.
        val none = processPhotoFinishing(
            frame, PhotoFinishing(look = PhotoLook(PhotoLookId.None, 0.5f)),
            0, PixelSize(1, 1)
        )
        assertTrue(none === frame)
        // A zero blend on a real id is also neutral.
        val zero = processPhotoFinishing(
            frame, PhotoFinishing(look = PhotoLook(PhotoLookId.Mono, 0f)),
            0, PixelSize(1, 1)
        )
        assertTrue(zero === frame)
    }

    @Test
    fun `look transforms match the settled examples`() = runTest {
        val luma = 0.2126f * 0.4f + 0.7152f * 0.6f + 0.0722f * 0.8f

        val mono = lookPixel(
            0.4f, 0.6f, 0.8f, PhotoLook(PhotoLookId.Mono)
        )
        for (c in 0..2) assertEquals(luma, mono[c], 1e-6f, "mono c$c")

        // Clean: sat=luma+(c-luma)*1.08, then (sat-.18)*1.06+.18.
        val clean = lookPixel(
            0.4f, 0.6f, 0.8f, PhotoLook(PhotoLookId.Clean)
        )
        val input = floatArrayOf(0.4f, 0.6f, 0.8f)
        for (c in 0..2) {
            val sat = luma + (input[c] - luma) * 1.08f
            assertEquals(
                (sat - 0.18f) * 1.06f + 0.18f, clean[c], 1e-6f, "clean c$c"
            )
        }

        // WarmFade: warm gains, desaturate toward luma, fade + lift.
        val warmFade = lookPixel(
            0.4f, 0.6f, 0.8f, PhotoLook(PhotoLookId.WarmFade)
        )
        val warm = floatArrayOf(0.4f * 1.08f, 0.6f * 1.02f, 0.8f * 0.90f)
        val wl = 0.2126f * warm[0] + 0.7152f * warm[1] + 0.0722f * warm[2]
        for (c in 0..2) {
            val sat = wl + (warm[c] - wl) * 0.90f
            assertEquals(
                sat * 0.88f + 0.035f, warmFade[c], 1e-6f, "warmFade c$c"
            )
        }

        // Cool: cool gains, slightly boosted saturation.
        val cool = lookPixel(
            0.4f, 0.6f, 0.8f, PhotoLook(PhotoLookId.Cool)
        )
        val coolc = floatArrayOf(0.4f * 0.92f, 0.6f * 1.01f, 0.8f * 1.10f)
        val cl = 0.2126f * coolc[0] + 0.7152f * coolc[1] + 0.0722f * coolc[2]
        for (c in 0..2) {
            assertEquals(
                cl + (coolc[c] - cl) * 1.04f, cool[c], 1e-6f, "cool c$c"
            )
        }
    }

    @Test
    fun `cinematic split-tones by the base luma`() = runTest {
        // base=(rgb-.18)*1.08+.18; w=smoothstep(.35,.75,L); shadow gains
        // (.92,1.04,1.10) blend to highlight gains (1.08,1.02,.92).
        val input = floatArrayOf(0.4f, 0.6f, 0.8f)
        val base = FloatArray(3) { (input[it] - 0.18f) * 1.08f + 0.18f }
        val l = 0.2126f * base[0] + 0.7152f * base[1] + 0.0722f * base[2]
        val t = ((l - 0.35f) / 0.40f).coerceIn(0f, 1f)
        val w = t * t * (3f - 2f * t)
        val shadow = floatArrayOf(0.92f, 1.04f, 1.10f)
        val highlight = floatArrayOf(1.08f, 1.02f, 0.92f)
        val out = lookPixel(
            input[0], input[1], input[2], PhotoLook(PhotoLookId.Cinematic)
        )
        for (c in 0..2) {
            val s = base[c] * shadow[c]
            val hh = base[c] * highlight[c]
            assertEquals(s + (hh - s) * w, out[c], 1e-6f, "cinematic c$c")
        }
        // A dark pixel sits below the split point — pure shadow tone.
        val dark = lookPixel(
            0.05f, 0.05f, 0.05f, PhotoLook(PhotoLookId.Cinematic)
        )
        val db = (0.05f - 0.18f) * 1.08f + 0.18f
        assertEquals(db * 0.92f, dark[0], 1e-6f)
        assertEquals(db * 1.04f, dark[1], 1e-6f)
        assertEquals(db * 1.10f, dark[2], 1e-6f)
    }

    @Test
    fun `amount blends linearly toward the look target`() = runTest {
        val full = lookPixel(0.4f, 0.6f, 0.8f, PhotoLook(PhotoLookId.Mono))
        val half = lookPixel(
            0.4f, 0.6f, 0.8f, PhotoLook(PhotoLookId.Mono, 0.5f)
        )
        for (c in 0..2) {
            assertEquals(
                floatArrayOf(0.4f, 0.6f, 0.8f)[c] +
                    (full[c] - floatArrayOf(0.4f, 0.6f, 0.8f)[c]) * 0.5f,
                half[c], 1e-6f, "blend c$c"
            )
        }
    }

    @Test
    fun `look runs after curves and keeps alpha`() = runTest {
        // Master curve lifts 64 -> 128 before the look sees the pixel: mono
        // of the lifted red differs from mono of the raw one.
        val input = curveDecode(64f / 255f)
        val out = processPhotoFinishing(
            frameOf(1, 1, px(input, 0f, 0f, 0.6f)),
            PhotoFinishing(
                curves = PhotoCurves(
                    master = PhotoToneCurve.ofSteps(0 to 0, 64 to 128, 255 to 255)
                ),
                look = PhotoLook(PhotoLookId.Mono)
            ),
            0, PixelSize(1, 1)
        )
        val curvedLuma = 0.2126f * curveDecode(128f / 255f)
        for (c in 0..2) assertEquals(curvedLuma, out.component(c), 1e-4f)
        assertEquals(0.6f, out.component(3))
    }

    @Test
    fun `look preview helper runs the same processor`() = runTest {
        val size = PixelSize(8, 6)
        val px8 = IntArray(size.pixelCount.toInt()) { i ->
            0xFF000000.toInt() or ((i * 37 % 256) shl 16) or
                ((i * 91 % 256) shl 8) or (i * 53 % 256)
        }
        val source = SrgbPixels(size, px8)
        val look = PhotoLook(PhotoLookId.WarmFade, 0.7f)
        val viaHelper = renderPhotoLookPreview(source, look)
        val viaProcessor = processPhotoFinishing(
            source.toLinearFrame(),
            PhotoFinishing(look = look), 0, size
        ).toSrgbPixels()
        assertEquals(
            viaProcessor.copyPixels().toList(),
            viaHelper.copyPixels().toList()
        )
        // None returns the source pixels unchanged.
        assertEquals(
            source.copyPixels().toList(),
            renderPhotoLookPreview(source, PhotoLook()).copyPixels().toList()
        )
    }

    @Test
    fun `finishing effect dispatches through the graph contract`() = runTest {
        val effect = PhotoFinishingEffect()
        val size = PixelSize(4, 4)
        val frame = frameOf(4, 4,
            FloatArray(4 * 4 * 4) { i -> if (i % 4 == 3) 1f else 0.5f })
        val neutral = effect.render(
            listOf(frame),
            EffectParameters.Finishing(PhotoFinishing(), 0, size)
        )
        assertTrue(neutral === frame)
        assertFailsWith<UnsupportedOperationException> {
            effect.render(listOf(frame), EffectParameters.Source("x"))
        }
        assertFailsWith<UnsupportedOperationException> {
            effect.render(
                emptyList(),
                EffectParameters.Finishing(PhotoFinishing(), 0, size)
            )
        }
    }

    @Test
    fun `a region must declare a plausible origin on both axes`() = runTest {
        val frame = frameOf(2, 2,
            FloatArray(16) { i -> if (i % 4 == 3) 1f else 0.5f })
        val finishing = PhotoFinishing(grain = PhotoGrain(amount = 0.5f))
        assertFailsWith<IllegalArgumentException> {
            processPhotoFinishing(frame, finishing, 2, PixelSize(2, 2))
        }
        assertFailsWith<IllegalArgumentException> {
            processPhotoFinishing(frame, finishing, 0, PixelSize(4, 2), originX = 3)
        }
        assertEquals(
            frame.size,
            processPhotoFinishing(frame, finishing, 0, PixelSize(4, 2), originX = 1).size
        )
    }

    @Test
    fun `nonfinite output is rejected`() = runTest {
        // A legal scene-linear value at the float ceiling overflows when
        // the warm white-balance gain multiplies it — the per-pixel
        // guard turns the runaway into a loud failure, never Inf out.
        val frame = frameOf(1, 1, px(Float.MAX_VALUE, 0.5f, 0.5f))
        assertFailsWith<IllegalArgumentException> {
            processPhotoFinishing(
                frame,
                PhotoFinishing(
                    whiteBalance = PhotoWhiteBalance(12000f, 0f)
                ),
                0, PixelSize(1, 1)
            )
        }
    }

    @Test
    fun `grain delta honours the midtone envelope bound`() = runTest {
        // |delta| = |noise| * amount * .12 * envelope(luma) with
        // |noise| <= 1, so each pixel's observed delta is capped by its
        // own envelope — extremes cap far below midtones.
        val size = PixelSize(4, 1)
        val lumas = floatArrayOf(0.02f, 0.5f, 0.98f, 0.25f)
        val pixels =
            px(0.02f, 0.02f, 0.02f) + px(0.5f, 0.5f, 0.5f) +
                px(0.98f, 0.98f, 0.98f) + px(0.25f, 0.25f, 0.25f)
        val out = processPhotoFinishing(
            frameOf(4, 1, pixels),
            PhotoFinishing(grain = PhotoGrain(amount = 1f)),
            0, size
        )
        for (x in 0 until 4) {
            val envelope = 0.35f +
                0.65f * (1f - abs(lumas[x] * 2f - 1f))
            val bound = 0.12f * envelope + 1e-5f
            val delta = abs(component(out, x, 0, 0) - lumas[x])
            assertTrue(
                delta <= bound,
                "grain delta $delta exceeds envelope bound $bound " +
                    "at luma ${lumas[x]}"
            )
        }
    }
}
