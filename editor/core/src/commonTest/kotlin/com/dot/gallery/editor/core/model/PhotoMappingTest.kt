package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PhotoMapping] verified against hand-computed expectations: a distinct
 * 2x3 raw raster whose pixel index `y * 2 + x` identifies each stored pixel.
 */
class PhotoMappingTest {

    // Raw 2x3, row-major:
    //   0 1
    //   2 3
    //   4 5
    private val raw = intArrayOf(0, 1, 2, 3, 4, 5)

    /** Samples the raw raster through the mapping for every output pixel. */
    private fun sample(mapping: PhotoMapping): IntArray {
        val out = IntArray(mapping.outputSize.pixelCount.toInt())
        for (y in 0 until mapping.outputSize.height) {
            for (x in 0 until mapping.outputSize.width) {
                val (sx, sy) = mapping.sourcePoint(x, y)
                out[y * mapping.outputSize.width + x] = raw[sy * 2 + sx]
            }
        }
        return out
    }

    private fun mapping(
        orientation: Int,
        quarterTurns: Int = 0,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
        crop: NormalizedCrop = NormalizedCrop()
    ) = PhotoMapping(
        rawSize = PixelSize(2, 3),
        orientation = orientation,
        geometry = PhotoGeometry(crop, quarterTurns, flipHorizontal, flipVertical)
    )

    @Test
    fun `identity geometry yields the upright raster for every EXIF orientation`() {
        // Each row-major expectation is the upright frame written out manually.
        val expected = mapOf(
            1 to Triple(2, 3, intArrayOf(0, 1, 2, 3, 4, 5)),
            2 to Triple(2, 3, intArrayOf(1, 0, 3, 2, 5, 4)),
            3 to Triple(2, 3, intArrayOf(5, 4, 3, 2, 1, 0)),
            4 to Triple(2, 3, intArrayOf(4, 5, 2, 3, 0, 1)),
            5 to Triple(3, 2, intArrayOf(0, 2, 4, 1, 3, 5)),
            6 to Triple(3, 2, intArrayOf(4, 2, 0, 5, 3, 1)),
            7 to Triple(3, 2, intArrayOf(5, 3, 1, 4, 2, 0)),
            8 to Triple(3, 2, intArrayOf(1, 3, 5, 0, 2, 4))
        )
        for ((orientation, shape) in expected) {
            val (w, h, pixels) = shape
            val m = mapping(orientation)
            assertEquals(PixelSize(w, h), m.uprightSize, "upright size o$orientation")
            assertEquals(PixelSize(w, h), m.outputSize, "output size o$orientation")
            assertContentEquals(pixels, sample(m), "upright raster o$orientation")
        }
    }

    @Test
    fun `quarter turns rotate clockwise and swap output axes when odd`() {
        // Upright o1: 0 1 / 2 3 / 4 5
        assertContentEquals(
            intArrayOf(4, 2, 0, 5, 3, 1),
            sample(mapping(orientation = 1, quarterTurns = 1))
        )
        assertEquals(PixelSize(3, 2), mapping(1, quarterTurns = 1).outputSize)
        assertContentEquals(
            intArrayOf(5, 4, 3, 2, 1, 0),
            sample(mapping(orientation = 1, quarterTurns = 2))
        )
        assertContentEquals(
            intArrayOf(1, 3, 5, 0, 2, 4),
            sample(mapping(orientation = 1, quarterTurns = 3))
        )
    }

    @Test
    fun `flips mirror the rotated output`() {
        assertContentEquals(
            intArrayOf(1, 0, 3, 2, 5, 4),
            sample(mapping(1, flipHorizontal = true))
        )
        assertContentEquals(
            intArrayOf(4, 5, 2, 3, 0, 1),
            sample(mapping(1, flipVertical = true))
        )
        assertContentEquals(
            intArrayOf(5, 4, 3, 2, 1, 0),
            sample(mapping(1, flipHorizontal = true, flipVertical = true))
        )
        // Flips apply after rotation: q1 output 4 2 0 / 5 3 1 mirrored is
        // 0 2 4 / 1 3 5 — not the same as flipping then rotating.
        assertContentEquals(
            intArrayOf(0, 2, 4, 1, 3, 5),
            sample(mapping(1, quarterTurns = 1, flipHorizontal = true))
        )
    }

    @Test
    fun `crop resolves on the upright frame before rotation`() {
        // Off-centre crop on upright o1 (2x3): right column, bottom two rows.
        val crop = NormalizedCrop(left = 0.5, top = 1.0 / 3.0, right = 1.0, bottom = 1.0)
        val m = mapping(1, crop = crop)
        assertEquals(PixelRect(1, 1, 1, 2), m.cropRect)
        assertEquals(PixelSize(1, 2), m.outputSize)
        assertContentEquals(intArrayOf(3, 5), sample(m))

        // The same crop on the already-landscape upright of o6 (3x2):
        // upright is 4 2 0 / 5 3 1; crop left=1 top=0.5*... resolves to
        // left=floor(1.5)=1, right=3, top=floor(2/3)=0, bottom=2.
        val crop6 = NormalizedCrop(left = 0.5, top = 1.0 / 3.0, right = 1.0, bottom = 1.0)
        val m6 = mapping(6, crop = crop6)
        assertEquals(PixelRect(1, 0, 2, 2), m6.cropRect)
        assertContentEquals(intArrayOf(2, 0, 3, 1), sample(m6))
    }

    @Test
    fun `combined orientation crop rotation and flip produce exact pixels`() {
        // Upright o6 (3x2): 4 2 0 / 5 3 1. Crop middle column -> 2 / 3 (1x2),
        // q1 rotates CW -> 3 2 (2x1), flipHorizontal -> 2 3.
        val m = mapping(
            orientation = 6,
            quarterTurns = 1,
            flipHorizontal = true,
            crop = NormalizedCrop(1.0 / 3.0, 0.0, 2.0 / 3.0, 1.0)
        )
        assertEquals(PixelRect(1, 0, 1, 2), m.cropRect)
        assertEquals(PixelSize(2, 1), m.outputSize)
        assertContentEquals(intArrayOf(2, 3), sample(m))
    }

    @Test
    fun `sourceBounds is the exact raw rectangle covering an output rect`() {
        // Identity band: one output row reads one raw row.
        val identity = mapping(1)
        assertEquals(
            PixelRect(0, 1, 2, 1),
            identity.sourceBounds(PixelRect(0, 1, 2, 1))
        )
        // Rotated band: output row 0 of o6 maps to raw column x=0 (all rows).
        val rotated = mapping(6)
        assertEquals(
            PixelRect(0, 0, 1, 3),
            rotated.sourceBounds(PixelRect(0, 0, 3, 1))
        )
        // A full output always covers the whole raw raster.
        assertEquals(
            PixelRect(0, 0, 2, 3),
            rotated.sourceBounds(PixelRect(0, 0, 3, 2))
        )
        // Cropped+rotated output row maps to a raw sub-rectangle, not the image.
        val cropped = mapping(
            6,
            quarterTurns = 1,
            crop = NormalizedCrop(0.0, 0.0, 2.0 / 3.0, 1.0)
        )
        // cropRect on upright 3x2 is (0,0,2,2); q1 output is 2x2. Output row 0
        // maps to raw pixels (1,2) and (0,2) -> the bottom raw row.
        assertEquals(
            PixelRect(0, 2, 2, 1),
            cropped.sourceBounds(PixelRect(0, 0, 2, 1))
        )
    }

    @Test
    fun `out-of-range coordinates and rects are rejected`() {
        val m = mapping(1)
        assertFailsWith<IllegalArgumentException> { m.sourcePoint(-1, 0) }
        assertFailsWith<IllegalArgumentException> { m.sourcePoint(0, -1) }
        assertFailsWith<IllegalArgumentException> { m.sourcePoint(2, 0) }
        assertFailsWith<IllegalArgumentException> { m.sourcePoint(0, 3) }
        assertFailsWith<IllegalArgumentException> {
            m.sourceBounds(PixelRect(0, 0, 2, 4))
        }
        assertFailsWith<IllegalArgumentException> {
            m.sourceBounds(PixelRect(1, 0, 2, 1))
        }
    }

    @Test
    fun `invalid orientation and identity detection`() {
        assertFailsWith<IllegalArgumentException> { mapping(0) }
        assertFailsWith<IllegalArgumentException> { mapping(9) }
        assertTrue(mapping(1).isIdentity)
        assertFalse(mapping(6).isIdentity)
        assertFalse(mapping(1, quarterTurns = 2).isIdentity)
        assertFalse(mapping(1, flipHorizontal = true).isIdentity)
        assertFalse(
            mapping(1, crop = NormalizedCrop(0.0, 0.0, 0.5, 1.0)).isIdentity
        )
        // Sub-pixel normalized edges still clamp to a full crop -> identity.
        assertTrue(
            mapping(
                1,
                crop = NormalizedCrop(0.0, 0.0, 1.0, 1.0)
            ).isIdentity
        )
    }
}
