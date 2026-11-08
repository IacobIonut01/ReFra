package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PixelSize
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotoCompositeEffectTest {

    private val effect = PhotoCompositeEffect()

    private fun frame(r: Float, g: Float, b: Float, a: Float) = LinearRgbaFrame(
        PixelSize(1, 1), floatArrayOf(r, g, b, a)
    )

    private suspend fun pixel(
        base: LinearRgbaFrame,
        layer: LinearRgbaFrame,
        mode: PhotoBlendMode = PhotoBlendMode.Normal,
        opacity: Float = 1f,
        clip: Boolean = false
    ): FloatArray = effect.render(
        listOf(base, layer),
        EffectParameters.Composite(mode, opacity, clip)
    ).copyPixels()

    private fun assertPixel(actual: FloatArray, vararg expected: Float, tol: Float = 1e-6f) {
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { i, v -> assertEquals(v, actual[i], tol, "channel $i") }
    }

    @Test
    fun `opaque source over opaque backdrop is the plain blend result`() = runTest {
        val base = frame(.5f, .5f, .5f, 1f)
        val layer = frame(.4f, .4f, .4f, 1f)
        // as=ab=1 -> Co = Blend(.5, .4) per channel, ao=1.
        assertPixel(pixel(base, layer, PhotoBlendMode.Normal), .4f, .4f, .4f, 1f)
        assertPixel(pixel(base, layer, PhotoBlendMode.Multiply), .2f, .2f, .2f, 1f)
        assertPixel(pixel(base, layer, PhotoBlendMode.Screen), .7f, .7f, .7f, 1f)
        // cb=.5 boundary takes the dark branch: 2*.5*.4=.4.
        assertPixel(pixel(base, layer, PhotoBlendMode.Overlay), .4f, .4f, .4f, 1f)
        // softlight: cs<=.5 -> .5 - (1-.8)*.5*.5 = .45.
        assertPixel(pixel(base, layer, PhotoBlendMode.SoftLight), .45f, .45f, .45f, 1f)
    }

    @Test
    fun `overlay and softlight switch branches at the boundaries`() = runTest {
        // cb=.6 > .5 -> overlay light branch: 1-2*(1-.6)*(1-.4) = 1-.48 = .52.
        assertPixel(
            pixel(frame(.6f, .6f, .6f, 1f), frame(.4f, .4f, .4f, 1f),
                PhotoBlendMode.Overlay),
            .52f, .52f, .52f, 1f
        )
        // cs=.8 > .5 and cb=.5 -> d=sqrt(.5); .5 + .6*(sqrt(.5)-.5).
        val expected = .5f + .6f * (kotlin.math.sqrt(.5f) - .5f)
        assertPixel(
            pixel(frame(.5f, .5f, .5f, 1f), frame(.8f, .8f, .8f, 1f),
                PhotoBlendMode.SoftLight),
            expected, expected, expected, 1f
        )
    }

    @Test
    fun `opacity halves the effective alpha inside source-over`() = runTest {
        // as=.5, ab=1, Normal: premul = .5*0*.4 + .5*1*.4 + 1*.5*.5 = .45; ao=1.
        assertPixel(
            pixel(frame(.5f, .5f, .5f, 1f), frame(.4f, .4f, .4f, 1f),
                PhotoBlendMode.Normal, opacity = .5f),
            .45f, .45f, .45f, 1f
        )
    }

    @Test
    fun `translucent backdrop and clip to below follow the alpha equation`() = runTest {
        // as=1, ab=.5: premul = .5*cs + .5*Blend = .4; ao=1 -> .4 opaque.
        assertPixel(
            pixel(frame(.9f, .9f, .9f, .5f), frame(.4f, .4f, .4f, 1f)),
            .4f, .4f, .4f, 1f
        )
        // clipped: as = 1 * .5 = .5 -> Normal result (.4+.9)/2 premul / ao.
        // premul = .5*.5*.4 + .5*.5*.4 + .5*.5*.9 = .1+.1+.225 = .425; ao=.75.
        assertPixel(
            pixel(frame(.9f, .9f, .9f, .5f), frame(.4f, .4f, .4f, 1f),
                PhotoBlendMode.Normal, clip = true),
            .425f / .75f, .425f / .75f, .425f / .75f, .75f
        )
    }

    @Test
    fun `a transparent layer preserves backdrop bits exactly`() = runTest {
        val base = frame(.33f, .55f, .77f, .8f)
        val transparent = frame(.1f, .2f, .3f, 0f)
        // Even with a non-neutral blend, zero effective alpha is pass-through.
        assertContentEquals(
            base.copyPixels(),
            pixel(base, transparent, PhotoBlendMode.Multiply)
        )
        // Same for a layer that only becomes transparent through opacity 0.
        assertContentEquals(
            base.copyPixels(),
            pixel(base, frame(.4f, .5f, .6f, 1f), opacity = 0f)
        )
    }

    @Test
    fun `arity and parameter mismatches are rejected explicitly`() = runTest {
        assertFailsWith<UnsupportedOperationException> {
            effect.render(
                listOf(frame(0f, 0f, 0f, 1f)),
                EffectParameters.Composite(PhotoBlendMode.Normal, 1f, false)
            )
        }
        assertFailsWith<UnsupportedOperationException> {
            effect.render(
                listOf(frame(0f, 0f, 0f, 1f), frame(0f, 0f, 0f, 1f)),
                EffectParameters.Color(com.dot.gallery.editor.core.model.ColorAdjustments())
            )
        }
        assertFailsWith<IllegalArgumentException> {
            // Different canvas sizes can never composite.
            effect.render(
                listOf(
                    frame(0f, 0f, 0f, 1f),
                    LinearRgbaFrame(PixelSize(2, 1), FloatArray(8))
                ),
                EffectParameters.Composite(PhotoBlendMode.Normal, 1f, false)
            )
        }
    }
}
