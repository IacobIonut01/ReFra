package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerMapping
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoMaskStroke
import com.dot.gallery.editor.core.model.PixelSize
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotoLayerPlacementEffectTest {

    private val effect = PhotoLayerPlacementEffect()
    private val canvas = PixelSize(4, 4)

    private fun layer(
        transform: PhotoLayerTransform = PhotoLayerTransform(),
        mask: PhotoLayerMask = PhotoLayerMask()
    ) = PhotoImageLayer(
        id = "l1", name = "layer",
        source = MediaAsset.Image("ls-1", "ref://l", PixelSize(4, 4)),
        transform = transform, mask = mask
    )

    private fun frame(size: PixelSize, pixels: FloatArray) = LinearRgbaFrame(size, pixels)

    /** Distinct-valued 4x4 frame: index i holds [i/16, .5, .7, 1]. */
    private fun distinct4x4(): LinearRgbaFrame {
        val px = FloatArray(64)
        for (i in 0 until 16) {
            px[i * 4] = i / 16f
            px[i * 4 + 1] = .5f
            px[i * 4 + 2] = .7f
            px[i * 4 + 3] = 1f
        }
        return frame(PixelSize(4, 4), px)
    }

    private suspend fun place(
        input: LinearRgbaFrame,
        transform: PhotoLayerTransform = PhotoLayerTransform(),
        mask: PhotoLayerMask = PhotoLayerMask(),
        outputSize: PixelSize = canvas,
        referenceOutputSize: PixelSize = canvas
    ): FloatArray = effect.render(
        listOf(input),
        EffectParameters.LayerPlacement(
            layer(transform, mask), outputSize, referenceOutputSize
        )
    ).copyPixels()

    @Test
    fun `neutral placement copies the source onto the canvas`() = runTest {
        val input = distinct4x4()
        assertContentEquals(input.copyPixels(), place(input))
    }

    @Test
    fun `scale samples bilinearly and keeps out-of-domain transparent`() = runTest {
        // scale 2 -> s=2 -> dest(0,0) samples (.75,.75): the bilinear mix of
        // indices 0,1,4,5 -> r = .75*1 + .75*4 (in 1/16 units) = .234375.
        val out = place(distinct4x4(), transform = PhotoLayerTransform(scale = 2f))
        val expected = 0.75f * (1f / 16f) + 0.75f * (4f / 16f)
        // (1-tx)(1-ty)*p0 + tx(1-ty)*p1 + (1-tx)ty*p4 + tx*ty*p5 with t=.75
        // = .0625*0 + .1875*(1/16) + .1875*(4/16) + .5625*(5/16)
        val exact = .0625f * 0f + .1875f / 16f + .1875f * 4f / 16f + .5625f * 5f / 16f
        assertEquals(exact, out[0], 1e-6f)
        assertEquals(expected, out[0], 1e-6f)
        assertEquals(1f, out[3], 1e-6f) // fully inside -> alpha 1
    }

    @Test
    fun `translation pushes pixels out of domain to transparent`() = runTest {
        val out = place(
            distinct4x4(), transform = PhotoLayerTransform(translationX = 8f)
        )
        assertContentEquals(FloatArray(64), out)
        // A +2 shift moves the layer right: the two left columns fall
        // outside the domain and stay transparent.
        val shifted = place(
            distinct4x4(), transform = PhotoLayerTransform(translationX = 2f)
        )
        for (y in 0 until 4) {
            for (x in 0..1) {
                val alpha = shifted[(y * 4 + x) * 4 + 3]
                assertEquals(0f, alpha, 1e-6f, "row $y column $x is transparent")
            }
        }
    }

    @Test
    fun `the mask multiplies sampled alpha per normalized position`() = runTest {
        val mask = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(
                PhotoMaskStroke(
                    points = persistentListOf(
                        NormalizedPoint(.2f, .5f), NormalizedPoint(.8f, .5f)
                    ),
                    // 1.6px radius with a .8px hard core leaves a real
                    // feather: the outer rows land inside the gradient.
                    diameter = .8f, hardness = .5f
                )
            )
        )
        val out = place(distinct4x4(), mask = mask)
        // Rows inside the hard core keep full alpha; rows in the feather
        // band read the painted coverage.
        var anyPartial = false
        var anyFull = false
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val alpha = out[(y * 4 + x) * 4 + 3]
                if (alpha in 0.01f..0.99f) anyPartial = true
                if (alpha > .99f) anyFull = true
            }
        }
        assertEquals(true, anyPartial, "feathered edge produces partial alpha")
        assertEquals(true, anyFull, "core of the stroke keeps full alpha")
    }

    @Test
    fun `translation scales from the full output into the render canvas`() =
        runTest {
            // tx is stored in full base-output pixels: tx=100 on a 400x200
            // reference must act as tx=10 on the 40x20 preview canvas.
            val reference = PixelSize(400, 200)
            val preview = PixelSize(40, 20)
            val scaled = place(
                distinct4x4(),
                transform = PhotoLayerTransform(translationX = 100f),
                outputSize = preview,
                referenceOutputSize = reference
            )
            val direct = place(
                distinct4x4(),
                transform = PhotoLayerTransform(translationX = 10f),
                outputSize = preview,
                referenceOutputSize = preview
            )
            assertContentEquals(direct, scaled)

            // The scaled mapping is the full-resolution mapping sampled at
            // the centre of the corresponding 10x10 block.
            val full = PhotoLayerMapping(
                PixelSize(4, 4), orientation = 1, reference,
                PhotoLayerTransform(translationX = 100f)
            )
            val previewMapping = PhotoLayerMapping(
                PixelSize(4, 4), orientation = 1, preview,
                PhotoLayerTransform(translationX = 10f)
            )
            for (y in 0 until 20 step 5) {
                for (x in 0 until 40 step 7) {
                    val expected = full.sourcePoint(x * 10.0 + 4.5, y * 10.0 + 4.5)
                    val actual = previewMapping.sourcePoint(x.toDouble(), y.toDouble())
                    if (expected == null || actual == null) {
                        assertEquals(expected, actual, "point at ($x,$y)")
                    } else {
                        assertEquals(expected.raw.x, actual.raw.x, 1e-9, "x at ($x,$y)")
                        assertEquals(expected.raw.y, actual.raw.y, 1e-9, "y at ($x,$y)")
                    }
                }
            }
        }

    @Test
    fun `arity and parameter mismatches are rejected explicitly`() = runTest {
        assertFailsWith<UnsupportedOperationException> {
            effect.render(
                emptyList(),
                EffectParameters.LayerPlacement(layer(), canvas, canvas)
            )
        }
        assertFailsWith<UnsupportedOperationException> {
            effect.render(
                listOf(distinct4x4()),
                EffectParameters.Composite(
                    com.dot.gallery.editor.core.model.PhotoBlendMode.Normal, 1f, false
                )
            )
        }
    }
}
