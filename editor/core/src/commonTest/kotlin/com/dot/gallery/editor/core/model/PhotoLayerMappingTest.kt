package com.dot.gallery.editor.core.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoLayerMappingTest {

    private fun mapping(
        rawW: Int,
        rawH: Int,
        orientation: Int = 1,
        outW: Int = 4,
        outH: Int = 4,
        transform: PhotoLayerTransform = PhotoLayerTransform()
    ) = PhotoLayerMapping(
        PixelSize(rawW, rawH), orientation,
        PixelSize(outW, outH), transform
    )

    private fun assertPoint(
        sample: PhotoLayerSample?,
        x: Double,
        y: Double,
        tolerance: Double = 1e-12
    ) {
        assertNotNull(sample)
        assertTrue(
            abs(sample.raw.x - x) <= tolerance && abs(sample.raw.y - y) <= tolerance,
            "expected ($x, $y), got (${sample.raw.x}, ${sample.raw.y})"
        )
    }

    @Test
    fun `neutral fit of a 2x2 source on a 4x4 canvas halves in the edge domain`() {
        val m = mapping(2, 2)
        assertEquals(2.0, m.fitScale, 1e-12)
        assertEquals(2.0, m.effectiveScale, 1e-12)
        // dx = (0.5-2)/2 = -0.75 -> sx = -0.75 + 1 - .5 = -0.25.
        assertPoint(m.sourcePoint(0.0, 0.0), -0.25, -0.25)
        // The canvas centre lands on the source centre.
        assertPoint(m.sourcePoint(1.0, 1.0), 0.25, 0.25)
    }

    @Test
    fun `identity placement maps integer output to exact integral source`() {
        val m = mapping(4, 4)
        assertEquals(1.0, m.fitScale, 1e-12)
        assertPoint(m.sourcePoint(0.0, 0.0), 0.0, 0.0)
        assertPoint(m.sourcePoint(3.0, 3.0), 3.0, 3.0)
        val corner = m.sourcePoint(0.0, 0.0)
        assertNotNull(corner)
        assertEquals(0.125, corner.normalizedX, 1e-12)
        assertEquals(0.125, corner.normalizedY, 1e-12)
    }

    @Test
    fun `transform scale and translation move the sample exactly`() {
        val scaled = mapping(4, 4, transform = PhotoLayerTransform(scale = 2f))
        // s = 2 -> dx = -0.75 -> sx = 0.75.
        assertPoint(scaled.sourcePoint(0.0, 0.0), 0.75, 0.75)

        val shifted = mapping(4, 4, transform = PhotoLayerTransform(translationX = 1f))
        val plain = mapping(4, 4)
        // +1 output pixel of translation moves the sampled source one pixel left.
        val a = shifted.sourcePoint(2.0, 1.0)
        val b = plain.sourcePoint(2.0, 1.0)
        assertNotNull(a); assertNotNull(b)
        assertEquals(b.raw.x - 1.0, a.raw.x, 1e-12)
        assertEquals(b.raw.y, a.raw.y, 1e-12)
    }

    @Test
    fun `rotation flips and out of domain points behave per contract`() {
        val rotated = mapping(
            4, 4, transform = PhotoLayerTransform(rotationDegrees = 90f)
        )
        // 90 degrees maps output centre-left to below centre: exact trig.
        val p = rotated.sourcePoint(1.0, 2.0)
        assertNotNull(p)
        val flipped = mapping(
            4, 4, transform = PhotoLayerTransform(flipHorizontal = true)
        )
        // dest(0,0) upright (0,0) mirrors to (3,0).
        assertPoint(flipped.sourcePoint(0.0, 0.0), 3.0, 0.0)
        val pushed = mapping(
            4, 4, transform = PhotoLayerTransform(translationX = 8f)
        )
        assertNull(pushed.sourcePoint(0.0, 0.0))
    }

    @Test
    fun `neutral placement agrees with the integer mapping for every EXIF orientation`() {
        for (orientation in 1..8) {
            val raw = PixelSize(4, 2)
            val upright =
                if (orientation in 5..8) PixelSize(raw.height, raw.width) else raw
            val m = mapping(
                raw.width, raw.height, orientation,
                outW = upright.width, outH = upright.height
            )
            val rigid = PhotoMapping(raw, orientation, PhotoGeometry())
            for (y in 0 until upright.height) {
                for (x in 0 until upright.width) {
                    val sample = m.sourcePoint(x.toDouble(), y.toDouble())
                    assertNotNull(sample, "orientation $orientation at ($x, $y)")
                    val packed = rigid.sourcePointPacked(x, y)
                    assertEquals(
                        (packed shr 32).toInt().toDouble(), sample.raw.x, 1e-9,
                        "orientation $orientation at ($x, $y)"
                    )
                    assertEquals(
                        packed.toInt().toDouble(), sample.raw.y, 1e-9,
                        "orientation $orientation at ($x, $y)"
                    )
                }
            }
        }
    }
}
