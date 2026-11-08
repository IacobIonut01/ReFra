package com.dot.gallery.editor.core.model

import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoFinishingTest {

    // ---- PhotoToneCurve ------------------------------------------------

    @Test
    fun `tone curve rejects bad point lists`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoToneCurve(persistentListOf(PhotoCurvePoint(0f, 0f)))
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoToneCurve.ofSteps(0 to 0, 128 to 128, 128 to 200, 255 to 255)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoToneCurve.ofSteps(*Array(17) { it * 15 to it * 15 })
        }
        assertFailsWith<IllegalArgumentException> { PhotoCurvePoint(1.1f, 0f) }
        assertFailsWith<IllegalArgumentException> { PhotoCurvePoint(0f, Float.NaN) }
    }

    @Test
    fun `falling outputs and inset end points are legal`() {
        val curve = PhotoToneCurve.ofSteps(20 to 200, 128 to 40, 230 to 180)
        assertEquals(200f / 255f, curve.spline.evaluate(0f), 1e-6f)
        assertEquals(40f / 255f, curve.spline.evaluate(128f / 255f), 1e-6f)
        assertEquals(180f / 255f, curve.spline.evaluate(1f), 1e-6f)
    }

    @Test
    fun `spline passes through every point and never overshoots`() {
        val curve = PhotoToneCurve.ofSteps(
            0 to 0, 40 to 120, 45 to 124, 200 to 130, 255 to 255
        )
        for (point in curve.points) {
            assertEquals(point.y, curve.spline.evaluate(point.x), 1e-5f)
        }
        var previous = curve.spline.evaluate(0f)
        for (i in 1..1000) {
            val next = curve.spline.evaluate(i / 1000f)
            assertTrue(next >= previous - 1e-5f, "falls back at ${i / 1000f}")
            previous = next
        }
    }

    @Test
    fun `spline extends at unit slope outside the unit domain`() {
        val curve = PhotoToneCurve.ofSteps(0 to 51, 255 to 204)
        assertEquals(0.2f - 0.25f, curve.spline.evaluate(-0.25f), 1e-6f)
        assertEquals(0.8f + 0.4f, curve.spline.evaluate(1.4f), 1e-6f)
    }

    @Test
    fun `moving a point keeps the gap and snaps to whole steps`() {
        val curve = PhotoToneCurve.ofSteps(0 to 0, 100 to 100, 200 to 200, 255 to 255)
        val up = curve.withPointMoved(1, 0.9f, 1.5f).points[1]
        assertEquals(197f / 255f, up.x)
        assertEquals(1f, up.y)
        val down = curve.withPointMoved(1, -1f, -1f).points[1]
        assertEquals(3f / 255f, down.x)
        assertEquals(0f, down.y)
        val end = curve.withPointMoved(0, 0.2f, 0.1f).points[0]
        assertEquals(51f / 255f, end.x)
        assertEquals((0.1f * 255f).roundToInt() / 255f, end.y)
        assertFailsWith<IllegalArgumentException> { curve.withPointMoved(4, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> {
            curve.withPointMoved(1, Float.POSITIVE_INFINITY, 0f)
        }
    }

    @Test
    fun `inserting lands on the curve and respects gap and capacity`() {
        val curve = PhotoToneCurve.ofSteps(10 to 0, 200 to 255)
        val inserted = curve.withPointInserted(100f / 255f)!!
        assertEquals(3, inserted.points.size)
        assertEquals(100f / 255f, inserted.points[1].x)
        assertEquals(
            (curve.spline.evaluate(100f / 255f) * 255f).roundToInt() / 255f,
            inserted.points[1].y
        )
        assertNull(curve.withPointInserted(5f / 255f), "left of the first point")
        assertNull(curve.withPointInserted(220f / 255f), "right of the last point")
        assertNull(curve.withPointInserted(12f / 255f), "crowds a neighbour")
        val full = PhotoToneCurve.ofSteps(*Array(16) { it * 17 to it * 17 })
        assertNull(full.withPointInserted(25f / 255f), "already at capacity")
    }

    @Test
    fun `only interior points can be removed`() {
        val curve = PhotoToneCurve.ofSteps(0 to 0, 128 to 140, 255 to 255)
        assertNull(curve.withoutPoint(0))
        assertNull(curve.withoutPoint(2))
        assertEquals(PhotoToneCurve(), curve.withoutPoint(1))
    }

    @Test
    fun `neutral means identity even with points on the diagonal`() {
        assertTrue(PhotoToneCurve().isNeutral)
        assertTrue(PhotoToneCurve.ofSteps(0 to 0, 128 to 128, 255 to 255).isNeutral)
        assertFalse(PhotoToneCurve.ofSteps(0 to 0, 128 to 140, 255 to 255).isNeutral)
        assertFalse(PhotoToneCurve.ofSteps(10 to 10, 255 to 255).isNeutral)
    }

    @Test
    fun `legacy ordinates become points and their identity becomes the default`() {
        assertEquals(
            PhotoToneCurve.DEFAULT,
            PhotoToneCurve.ofQuarterOrdinates(listOf(0f, .25f, .5f, .75f, 1f)).points
        )
        val bent = PhotoToneCurve.ofQuarterOrdinates(listOf(0f, .3f, .5f, .75f, 1f))
        assertEquals(PhotoCurvePoint(.25f, .3f), bent.points[1])
        assertFailsWith<IllegalArgumentException> {
            PhotoToneCurve.ofQuarterOrdinates(listOf(0f, .5f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoToneCurve.ofQuarterOrdinates(listOf(0f, .6f, .4f, .75f, 1f))
        }
    }

    // ---- PhotoCurves ---------------------------------------------------

    @Test
    fun `curves neutral requires every channel neutral`() {
        assertTrue(PhotoCurves().isNeutral)
        val bent = PhotoToneCurve.ofSteps(0 to 0, 128 to 150, 255 to 255)
        assertFalse(PhotoCurves(master = bent).isNeutral)
        assertFalse(PhotoCurves(red = bent).isNeutral)
        assertFalse(PhotoCurves(green = bent).isNeutral)
        assertFalse(PhotoCurves(blue = bent).isNeutral)
    }

    // ---- PhotoWhiteBalance ----------------------------------------------

    @Test
    fun `white balance enforces the authored domains`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoWhiteBalance(temperatureKelvin = 1999f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWhiteBalance(temperatureKelvin = 12001f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWhiteBalance(tint = 101f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWhiteBalance(tint = -101f)
        }
        assertTrue(PhotoWhiteBalance().isNeutral)
        assertFalse(PhotoWhiteBalance(temperatureKelvin = 5500f).isNeutral)
        assertFalse(PhotoWhiteBalance(tint = 4f).isNeutral)
    }

    // ---- PhotoVignette --------------------------------------------------

    @Test
    fun `vignette enforces field domains and amount gates neutrality`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoVignette(amount = -1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoVignette(midpoint = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoVignette(feather = 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoVignette(roundness = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoVignette(highlights = -0.1f)
        }
        assertTrue(PhotoVignette().isNeutral)
        // A shaped-but-zero vignette draws nothing — amount is the gate.
        assertTrue(PhotoVignette(midpoint = 0.9f).isNeutral)
        assertFalse(PhotoVignette(amount = 0.2f).isNeutral)
    }

    // ---- PhotoGrain -----------------------------------------------------

    @Test
    fun `grain enforces field domains and amount gates neutrality`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoGrain(amount = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoGrain(size = 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoGrain(roughness = 1.1f)
        }
        assertTrue(PhotoGrain().isNeutral)
        assertTrue(PhotoGrain(size = 0.9f).isNeutral)
        assertFalse(PhotoGrain(amount = 0.1f).isNeutral)
    }

    // ---- PhotoFinishing + document/edits round trip ----------------------

    @Test
    fun `finishing neutrality requires every stage neutral`() {
        assertTrue(PhotoFinishing().isNeutral)
        assertFalse(
            PhotoFinishing(
                whiteBalance = PhotoWhiteBalance(temperatureKelvin = 5500f)
            ).isNeutral
        )
        assertFalse(
            PhotoFinishing(grain = PhotoGrain(amount = 0.5f)).isNeutral
        )
    }

    @Test
    fun `finishing survives edits and document conversion`() {
        val finishing = PhotoFinishing(
            whiteBalance = PhotoWhiteBalance(4200f, 12f),
            vignette = PhotoVignette(amount = -0.4f),
            grain = PhotoGrain(amount = 0.3f, seed = 77)
        )
        val edits = PhotoEdits(finishing = finishing)
        val document = edits.toDocument(
            MediaAsset.Image("img", "ref", PixelSize(4, 4))
        )
        assertEquals(finishing, document.finishing)
        assertEquals(edits, document.toEdits())
        assertEquals(finishing, document.toEdits().finishing)
    }
}
