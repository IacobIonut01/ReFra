package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GeometryPhotoEffectTest {

    private val effect = GeometryPhotoEffect()

    /** A 2x3 frame where channel R of every pixel carries its own index. */
    private fun taggedFrame(): LinearRgbaFrame {
        val pixels = FloatArray(2 * 3 * 4)
        for (i in 0 until 6) {
            pixels[i * 4] = i.toFloat()
            pixels[i * 4 + 1] = i + 0.25f
            pixels[i * 4 + 2] = i + 0.5f
            pixels[i * 4 + 3] = 1f
        }
        return LinearRgbaFrame(PixelSize(2, 3), pixels)
    }

    private fun tags(frame: LinearRgbaFrame): IntArray =
        IntArray(frame.size.pixelCount.toInt()) { frame.component(it * 4).toInt() }

    private suspend fun render(geometry: PhotoGeometry): LinearRgbaFrame =
        effect.render(listOf(taggedFrame()), EffectParameters.Geometry(geometry))

    @Test
    fun `identity geometry shares the input frame`() = runTest {
        val input = taggedFrame()
        val output = effect.render(listOf(input), EffectParameters.Geometry(PhotoGeometry()))
        assertSame(input, output)
    }

    @Test
    fun `quarter turn rotates the frame pixel-exactly`() = runTest {
        // 0 1 / 2 3 / 4 5 rotated 90 CW -> 4 2 0 / 5 3 1.
        val output = render(PhotoGeometry(quarterTurns = 1))
        assertEquals(PixelSize(3, 2), output.size)
        assertEquals(listOf(4, 2, 0, 5, 3, 1), tags(output).toList())
        // All four components travel with the pixel.
        assertEquals(4.25f, output.component(1), 1e-6f)
        assertEquals(4.5f, output.component(2), 1e-6f)
        assertEquals(1f, output.component(3), 1e-6f)
    }

    @Test
    fun `crop then flip composes on the same frame`() = runTest {
        val geometry = PhotoGeometry(
            crop = NormalizedCrop(0.0, 1.0 / 3.0, 1.0, 1.0),
            flipHorizontal = true
        )
        val output = render(geometry)
        assertEquals(PixelSize(2, 2), output.size)
        assertEquals(listOf(3, 2, 5, 4), tags(output).toList())
    }

    /**
     * Scale 2 on a 2x2 frame: dest(0,0) samples source (.25,.25) — the
     * analytic centre blend .5625/.1875/.1875/.0625 — and dest(1,1)
     * samples (.75,.75), the same weights mirrored.
     */
    @Test
    fun `warp bilinear blends an analytic 2x2 frame`() = runTest {
        // Channel R carries the pixel index; G = index + 1, B = index + 2.
        val input = LinearRgbaFrame(
            PixelSize(2, 2),
            floatArrayOf(
                0f, 1f, 2f, 1f, 1f, 2f, 3f, 1f,
                2f, 3f, 4f, 1f, 3f, 4f, 5f, 1f
            )
        )
        val output = effect.render(
            listOf(input),
            EffectParameters.Geometry(
                PhotoGeometry(warp = PhotoWarp(scale = 2f))
            )
        )
        assertEquals(PixelSize(2, 2), output.size)
        // dest(0,0) -> source (.25,.25): every channel blends identically.
        assertEquals(.5625f * 0 + .1875f * 1 + .1875f * 2 + .0625f * 3,
            output.component(0), 1e-6f)
        assertEquals(.75f + 1f, output.component(1), 1e-6f)
        assertEquals(.75f + 2f, output.component(2), 1e-6f)
        assertEquals(1f, output.component(3), 1e-6f)
        // dest(1,1) -> source (.75,.75): r = 2.25, g = 3.25, b = 4.25.
        val diag = (1 * 2 + 1) * 4
        assertEquals(2.25f, output.component(diag), 1e-6f)
        assertEquals(3.25f, output.component(diag + 1), 1e-6f)
        assertEquals(4.25f, output.component(diag + 2), 1e-6f)
        assertEquals(1f, output.component(diag + 3), 1e-6f)
    }

    /**
     * Strong perspective pushes part of the output outside the rigid
     * domain — those pixels stay fully transparent, and pixels sampling
     * the last source row blend toward transparent instead of clamping.
     */
    @Test
    fun `warp clips out-of-domain and edge samples to transparent`() = runTest {
        val input = LinearRgbaFrame(
            PixelSize(4, 4),
            FloatArray(4 * 4 * 4) { i -> if (i % 4 == 3) 1f else 0.5f }
        )
        val output = effect.render(
            listOf(input),
            EffectParameters.Geometry(
                PhotoGeometry(
                    warp = PhotoWarp(
                        verticalPerspective = 60f,
                        horizontalPerspective = 60f
                    )
                )
            )
        )
        assertEquals(PixelSize(4, 4), output.size)
        // dest(3,3) projects to (3.69, 3.69), past the domain limit
        // -> fully transparent output.
        val corner = (3 * 4 + 3) * 4
        for (c in 0 until 4) {
            assertEquals(0f, output.component(corner + c), 0f)
        }
        // dest(1,3) maps to about (.94, 3.176): row 3 exists, row 4 does
        // not -> alpha = 1 - .176 instead of edge-clamping.
        val edge = (3 * 4 + 1) * 4
        val alpha = output.component(edge + 3)
        assertTrue(alpha in 0.8f..0.85f, "edge alpha $alpha")
        // Interior pixels stay fully opaque.
        assertEquals(1f, output.component((1 * 4 + 1) * 4 + 3), 1e-6f)
    }

    /** A neutral warp with rigid geometry preserves raw float bits. */
    @Test
    fun `neutral warp keeps the integer path bit-exact`() = runTest {
        val input = taggedFrame()
        val output = effect.render(
            listOf(input),
            EffectParameters.Geometry(
                PhotoGeometry(quarterTurns = 1, warp = PhotoWarp())
            )
        )
        val expected = floatArrayOf(
            4f, 4.25f, 4.5f, 1f, 2f, 2.25f, 2.5f, 1f, 0f, 0.25f, 0.5f, 1f,
            5f, 5.25f, 5.5f, 1f, 3f, 3.25f, 3.5f, 1f, 1f, 1.25f, 1.5f, 1f
        )
        assertContentEquals(expected, output.copyPixels())
    }

    @Test
    fun `rejects wrong arity and parameter type`() = runTest {
        assertFailsWith<UnsupportedOperationException> {
            effect.render(
                listOf(taggedFrame(), taggedFrame()),
                EffectParameters.Geometry(PhotoGeometry())
            )
        }
        assertFailsWith<UnsupportedOperationException> {
            effect.render(
                listOf(taggedFrame()),
                EffectParameters.Color(com.dot.gallery.editor.core.model.ColorAdjustments())
            )
        }
    }

    @Test
    fun `descriptor matches the registered contract`() {
        assertEquals(EffectDescriptor.GeometryPhoto, effect.descriptor)
    }
}
