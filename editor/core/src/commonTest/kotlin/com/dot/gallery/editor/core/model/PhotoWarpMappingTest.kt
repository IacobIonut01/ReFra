package com.dot.gallery.editor.core.model

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PhotoWarpMapping] verified against the lead spec's hand-computed
 * inverse-mapping expectations on a 4x4 output frame, plus parity with the
 * integer [PhotoMapping] for the rigid chain at neutral warp.
 */
class PhotoWarpMappingTest {

    private fun mapping(
        rawSize: PixelSize = PixelSize(4, 4),
        orientation: Int = 1,
        geometry: PhotoGeometry = PhotoGeometry()
    ) = PhotoWarpMapping(rawSize, orientation, geometry)

    private fun assertPoint(
        expectedX: Double,
        expectedY: Double,
        point: ContinuousPoint?,
        tolerance: Double,
        label: String
    ) {
        assertNotNull(point, "$label: expected a valid source point")
        assertEquals(expectedX, point.x, tolerance, "$label: x")
        assertEquals(expectedY, point.y, tolerance, "$label: y")
    }

    @Test
    fun `neutral warp maps integer outputs to exact integral raw coordinates`() {
        val m = mapping()
        assertEquals(ContinuousPoint(0.0, 0.0), m.sourcePoint(0.0, 0.0))
        assertEquals(ContinuousPoint(3.0, 3.0), m.sourcePoint(3.0, 3.0))
        assertEquals(ContinuousPoint(2.0, 1.0), m.sourcePoint(2.0, 1.0))
    }

    @Test
    fun `neutral warp matches the integer mapping through flips turns and EXIF`() {
        // Every output pixel of every rigid/EXIF combination must land on
        // the same raw pixel the integer PhotoMapping produces.
        val raw = PixelSize(4, 4)
        for (orientation in 1..8) {
            for (turns in 0..3) {
                for (flipH in listOf(false, true)) {
                    val geometry = PhotoGeometry(
                        quarterTurns = turns,
                        flipHorizontal = flipH
                    )
                    val integer = PhotoMapping(raw, orientation, geometry)
                    val continuous = mapping(raw, orientation, geometry)
                    for (y in 0 until integer.outputSize.height) {
                        for (x in 0 until integer.outputSize.width) {
                            val expected = integer.sourcePointPacked(x, y)
                            val point = continuous.sourcePoint(
                                x.toDouble(), y.toDouble()
                            )
                            assertNotNull(
                                point,
                                "o$orientation q$turns flipH=$flipH at ($x,$y)"
                            )
                            assertEquals(
                                (expected shr 32).toInt().toDouble(), point.x,
                                1e-9, "o$orientation q$turns flipH=$flipH x at ($x,$y)"
                            )
                            assertEquals(
                                expected.toInt().toDouble(), point.y,
                                1e-9, "o$orientation q$turns flipH=$flipH y at ($x,$y)"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `rotation 45 turns the plane about its centre without an implicit zoom`() {
        val m = mapping(
            geometry = PhotoGeometry(warp = PhotoWarp(rotationDegrees = 45f))
        )
        assertEquals(1.0, m.effectiveScale, 1e-12)
        // dest(0,0): centred (-1.5,-1.5) rotates onto (-2.1213, 0) -> the
        // left edge, 0.6213 outside the 4px frame: transparent.
        assertNull(m.sourcePoint(0.0, 0.0))
        // dest(1,1): (-.5,-.5) -> (-.7071, 0) -> source (0.7929, 1.5).
        assertPoint(1.5 - sqrt(.5), 1.5, m.sourcePoint(1.0, 1.0), 1e-9, "rot45 dest(1,1)")
    }

    @Test
    fun `cropped output samples the plane outside the crop when rotated`() {
        // A 2x2 crop in the middle of an 8x8 source: rotating by 45 must
        // reach pixels outside the crop, never clamp to it.
        val geometry = PhotoGeometry(
            crop = NormalizedCrop(0.375, 0.375, 0.625, 0.625),
            warp = PhotoWarp(rotationDegrees = 45f)
        )
        val m = mapping(rawSize = PixelSize(8, 8), geometry = geometry)
        assertEquals(PixelSize(2, 2), m.outputSize)
        assertEquals(PixelOffset(3, 3), m.planeOffset)
        // Output (0,0) centre is plane (3.5, 3.5): centred (-.5,-.5) ->
        // rotated (-.7071, 0) -> source (3.2929 - .5 + .5 ..).
        assertPoint(4.0 - sqrt(.5) - .5, 3.5, m.sourcePoint(0.0, 0.0), 1e-9, "crop rot45")
    }

    @Test
    fun `plane offset follows turns and flips`() {
        val crop = NormalizedCrop(0.1, 0.2, 0.5, 0.6) // 10x6 upright -> px [1,5) x [1.2..]
        val raw = PixelSize(10, 5)
        for (turns in 0..3) for (fh in listOf(false, true)) for (fv in listOf(false, true)) {
            val g = PhotoGeometry(crop = crop, quarterTurns = turns, flipHorizontal = fh, flipVertical = fv,
                warp = PhotoWarp(scale = 1.0001f))
            val integer = PhotoMapping(raw, 1, g.copy(warp = PhotoWarp()))
            val continuous = mapping(raw, 1, g)
            for (y in 0 until integer.outputSize.height) for (x in 0 until integer.outputSize.width) {
                val e = integer.sourcePointPacked(x, y)
                val p = continuous.sourcePoint(x.toDouble(), y.toDouble())
                assertNotNull(p)
                assertEquals((e shr 32).toInt().toDouble(), p.x, 0.01, "q$turns fh$fh fv$fv x($x,$y)")
                assertEquals(e.toInt().toDouble(), p.y, 0.01, "q$turns fh$fh fv$fv y($x,$y)")
            }
        }
    }

    @Test
    fun `user scale zooms into the centre`() {
        val m = mapping(
            geometry = PhotoGeometry(warp = PhotoWarp(scale = 2f))
        )
        assertEquals(2.0, m.effectiveScale, 1e-12)
        // Spec value: dest(0,0) maps to source (.75, .75).
        assertPoint(.75, .75, m.sourcePoint(0.0, 0.0), 1e-9, "scale2 dest(0,0)")
        // The centre pixel of an odd-sized frame is invariant.
        val odd = mapping(
            rawSize = PixelSize(5, 5),
            geometry = PhotoGeometry(warp = PhotoWarp(scale = 2f))
        )
        assertPoint(2.0, 2.0, odd.sourcePoint(2.0, 2.0), 1e-9, "scale2 centre")
    }

    @Test
    fun `horizontal perspective keystones symmetric points`() {
        val m = mapping(
            geometry = PhotoGeometry(
                warp = PhotoWarp(horizontalPerspective = 100f)
            )
        )
        // Spec value: kx = .35, d = 1.2625 at dest(0,0).
        assertPoint(
            0.3118811881188119, 0.3118811881188119,
            m.sourcePoint(0.0, 0.0), 1e-8, "hp100 dest(0,0)"
        )
    }

    @Test
    fun `perspective beyond the domain returns null`() {
        val m = mapping(
            geometry = PhotoGeometry(
                warp = PhotoWarp(verticalPerspective = 100f)
            )
        )
        // Bottom-right corner projects past the rigid frame -> transparent.
        assertNull(m.sourcePoint(3.0, 3.0))
        // Out-of-domain destination indices are rejected, not clamped.
        assertFailsWith<IllegalArgumentException> { m.sourcePoint(4.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { m.sourcePoint(0.0, -1.0) }
    }

    @Test
    fun `identity requires a neutral warp`() {
        assertTrue(mapping().isIdentity)
        assertFalse(
            mapping(
                geometry = PhotoGeometry(warp = PhotoWarp(scale = 1.5f))
            ).isIdentity
        )
        assertFalse(
            mapping(
                geometry = PhotoGeometry(quarterTurns = 1)
            ).isIdentity
        )
    }

    @Test
    fun `effective scale is exactly the user scale`() {
        val rotated = mapping(
            geometry = PhotoGeometry(
                warp = PhotoWarp(rotationDegrees = 30f, scale = 1.2f)
            )
        )
        assertEquals(1.2f.toDouble(), rotated.effectiveScale, 1e-12)
    }
}
