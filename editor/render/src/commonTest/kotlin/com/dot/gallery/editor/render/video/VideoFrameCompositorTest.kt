package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.render.SrgbPixels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class VideoFrameCompositorTest {

    private val compositor = VideoFrameCompositor()

    private fun solid(width: Int, height: Int, argb: Int) =
        SrgbPixels(PixelSize(width, height), IntArray(width * height) { argb })

    /** Left half [left], right half [right] — deterministic under flips. */
    private fun split(width: Int, height: Int, left: Int, right: Int): SrgbPixels {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (x < width / 2) left else right
            }
        }
        return SrgbPixels(PixelSize(width, height), pixels)
    }

    private fun layer(
        pixels: SrgbPixels,
        transform: VideoLayerTransform = VideoLayerTransform(),
        opacity: Float = 1f,
        blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
        adjustments: ColorAdjustments = ColorAdjustments()
    ) = VideoFrameCompositor.Layer(
        pixels = pixels,
        transform = transform,
        opacity = opacity,
        blendMode = blendMode,
        adjustments = adjustments
    )

    private fun argb(px: Int) = intArrayOf(
        (px ushr 24) and 0xFF, (px ushr 16) and 0xFF,
        (px ushr 8) and 0xFF, px and 0xFF
    )

    private fun assertChannel(actual: Int, expected: Int, tol: Int = 2) {
        assertTrue(
            kotlin.math.abs(actual - expected) <= tol,
            "channel $actual not within $tol of $expected"
        )
    }

    private fun assertPixelNear(
        pixels: IntArray,
        size: PixelSize,
        x: Int,
        y: Int,
        expectedArgb: Int,
        tol: Int = 2
    ) {
        val actual = argb(pixels[y * size.width + x])
        val expected = argb(expectedArgb)
        for (c in 0..3) {
            assertChannel(actual[c], expected[c], tol)
        }
    }

    // ----- geometry -------------------------------------------------

    @Test
    fun `an empty layer list leaves the canvas transparent black`() = runTest {
        val out = compositor.composite(PixelSize(4, 4), emptyList())
        assertTrue(out.copyPixels().all { it == 0 })
    }

    @Test
    fun `a neutral main layer fills the canvas fit-inside`() = runTest {
        val out = compositor.composite(
            PixelSize(8, 8),
            listOf(layer(solid(8, 8, 0xFF3366CC.toInt())))
        )
        assertTrue(out.copyPixels().all { it == 0xFF3366CC.toInt() })
    }

    @Test
    fun `a mismatched main layer letterboxes to transparent surround`() = runTest {
        // 8x4 source on an 8x8 canvas: fit=.5 → content rows 2..5.
        val out = compositor.composite(
            PixelSize(8, 8),
            listOf(layer(solid(8, 4, 0xFF224466.toInt())))
        )
        val px = out.copyPixels()
        assertPixelNear(px, out.size, 0, 0, 0x00000000, tol = 0)
        assertPixelNear(px, out.size, 4, 3, 0xFF224466.toInt())
        assertPixelNear(px, out.size, 4, 4, 0xFF224466.toInt())
        assertPixelNear(px, out.size, 7, 7, 0x00000000, tol = 0)
    }

    @Test
    fun `scale and translation place a shrunken layer exactly`() = runTest {
        // 8x8 solid on an 8x8 canvas at scale .25, tx=1: the covered block
        // is exactly x∈{4,5}, y∈{3,4} (see PhotoLayerMapping bounds).
        val out = compositor.composite(
            PixelSize(8, 8),
            listOf(
                layer(
                    solid(8, 8, 0xFF102030.toInt()),
                    transform = VideoLayerTransform(
                        translationX = 1f, scale = 0.25f
                    )
                )
            )
        )
        val px = out.copyPixels()
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val expected = if (x in 4..5 && y in 3..4) {
                    0xFF102030.toInt()
                } else {
                    0
                }
                assertEquals(
                    expected, px[y * 8 + x],
                    "pixel ($x,$y)"
                )
            }
        }
    }

    @Test
    fun `translations scale with the reference canvas`() = runTest {
        // The stored transform speaks reference pixels; rendering at half
        // size halves the translation — identical placement, smaller
        // frame. effective = fit(4/8) * .5 = .25 → a centred 2x2 block at
        // x∈{2,3}, y∈{1,2}, every sample interior.
        val out = compositor.composite(
            canvas = PixelSize(4, 4),
            layers = listOf(
                layer(
                    solid(8, 8, 0xFF102030.toInt()),
                    transform = VideoLayerTransform(
                        translationX = 2f, scale = 0.5f
                    )
                )
            ),
            reference = PixelSize(8, 8)
        )
        val px = out.copyPixels()
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val expected = if (x in 2..3 && y in 1..2) {
                    0xFF102030.toInt()
                } else {
                    0
                }
                assertEquals(expected, px[y * 4 + x], "pixel ($x,$y)")
            }
        }
    }

    @Test
    fun `flips mirror the layer inside its bounds`() = runTest {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val half = split(8, 8, left = red, right = blue)
        val flipped = compositor.composite(
            PixelSize(8, 8),
            listOf(
                layer(
                    half,
                    transform = VideoLayerTransform(flipHorizontal = true)
                )
            )
        ).copyPixels()
        assertEquals(blue, flipped[0])          // top-left was red, now blue
        assertEquals(red, flipped[7])           // top-right was blue, now red
        // Rotation 180 equals both flips: bottom-right blue lands top-left.
        val rotated = compositor.composite(
            PixelSize(8, 8),
            listOf(
                layer(
                    half,
                    transform = VideoLayerTransform(rotationDegrees = 180f)
                )
            )
        ).copyPixels()
        assertEquals(blue, rotated[0])
        assertEquals(red, rotated[7])
    }

    // ----- blending ---------------------------------------------------

    @Test
    fun `all blend modes run the shared equation over opaque layers`() = runTest {
        val base = solid(4, 4, 0xFFFF00FF.toInt())   // magenta
        val over = solid(4, 4, 0xFF00FFFF.toInt())   // cyan
        val expected = mapOf(
            PhotoBlendMode.Normal to 0xFF00FFFF.toInt(),
            PhotoBlendMode.Multiply to 0xFF0000FF.toInt(),
            PhotoBlendMode.Screen to 0xFFFFFFFF.toInt(),
            // cb=1/cs=0 →1 ; cb=0/cs=1 →0 ; cb=1/cs=1 →1 → magenta.
            PhotoBlendMode.Overlay to 0xFFFF00FF.toInt(),
            // W3C softlight on 0/1 endpoints: r stays 1, g stays 0, b 1.
            PhotoBlendMode.SoftLight to 0xFFFF00FF.toInt()
        )
        for ((mode, colour) in expected) {
            val out = compositor.composite(
                PixelSize(4, 4),
                listOf(layer(base), layer(over, blendMode = mode))
            )
            assertPixelNear(
                out.copyPixels(), out.size, 1, 1, colour,
                tol = 1
            )
        }
    }

    @Test
    fun `layer order decides the winner`() = runTest {
        val red = layer(solid(4, 4, 0xFFFF0000.toInt()))
        val blue = layer(solid(4, 4, 0xFF0000FF.toInt()))
        val redOnTop = compositor.composite(
            PixelSize(4, 4), listOf(blue, red)
        ).copyPixels()
        val blueOnTop = compositor.composite(
            PixelSize(4, 4), listOf(red, blue)
        ).copyPixels()
        assertEquals(0xFFFF0000.toInt(), redOnTop[0])
        assertEquals(0xFF0000FF.toInt(), blueOnTop[0])
    }

    @Test
    fun `opacity halves the source contribution inside source-over`() = runTest {
        // .5 blue over opaque red → linear (0.5, 0, 0.5) → sRGB ≈ 0xBC00BC.
        val out = compositor.composite(
            PixelSize(4, 4),
            listOf(
                layer(solid(4, 4, 0xFFFF0000.toInt())),
                layer(solid(4, 4, 0xFF0000FF.toInt()), opacity = 0.5f)
            )
        )
        val px = out.copyPixels()[0]
        val (a, r, g, b) = argb(px)
        assertEquals(255, a)
        assertChannel(r, 188, tol = 2)
        assertEquals(0, g)
        assertChannel(b, 188, tol = 2)
    }

    @Test
    fun `a fully transparent layer preserves the backdrop bits`() = runTest {
        val out = compositor.composite(
            PixelSize(4, 4),
            listOf(
                layer(solid(4, 4, 0xFF123456.toInt())),
                layer(solid(4, 4, 0xFF00FF00.toInt()), opacity = 0f)
            )
        )
        assertTrue(out.copyPixels().all { it == 0xFF123456.toInt() })
        // And a translucent source pixel honours its own alpha too.
        val translucent = compositor.composite(
            PixelSize(4, 4),
            listOf(layer(solid(4, 4, 0x80123456.toInt())))
        ).copyPixels()[0]
        // Effective as ≈ 128/255 ≈ .502 → out alpha ≈ .502 → ~128.
        assertChannel(argb(translucent)[0], 128, tol = 2)
    }

    @Test
    fun `clip adjustments grade before placement`() = runTest {
        // Mid-grey 128 → linear .21586; exposure +1 stop doubles to
        // .43172 → sRGB ≈ 176.
        val graded = compositor.composite(
            PixelSize(4, 4),
            listOf(
                layer(
                    solid(4, 4, 0xFF808080.toInt()),
                    adjustments = ColorAdjustments(exposureStops = 1f)
                )
            )
        ).copyPixels()[0]
        val (a, r, g, b) = argb(graded)
        assertEquals(255, a)
        assertChannel(r, 176, tol = 3)
        assertChannel(g, 176, tol = 3)
        assertChannel(b, 176, tol = 3)
    }

    @Test
    fun `canvas and reference must be non-empty`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            compositor.composite(PixelSize(0, 4), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            compositor.composite(
                PixelSize(4, 4), emptyList(), reference = PixelSize(0, 4)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            VideoFrameCompositor.Layer(
                pixels = solid(1, 1, 0xFF000000.toInt()),
                opacity = 1.5f
            )
        }
    }
}
