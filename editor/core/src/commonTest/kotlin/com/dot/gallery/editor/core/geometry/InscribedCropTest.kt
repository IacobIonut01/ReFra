package com.dot.gallery.editor.core.geometry

import com.dot.gallery.editor.core.model.PhotoWarp
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InscribedCropTest {

    private fun rect(w: Double, h: Double) = ConvexPolygon(
        listOf(Vec2(0.0, 0.0), Vec2(w, 0.0), Vec2(w, h), Vec2(0.0, h))
    )

    @Test
    fun largestSquareInAWideRectangle() {
        val (t, c) = assertNotNull(InscribedCrop.largest(rect(400.0, 300.0), 1.0))
        assertEquals(300.0, t, 1e-6)
        assertEquals(150.0, c.y, 1e-6)
    }

    @Test
    fun aspectAndItsReciprocalFitTheirOwnLimitingAxis() {
        val region = rect(400.0, 300.0)
        // 4:5 portrait is height-limited; 5:4 landscape too (375 < 400).
        val portrait = assertNotNull(InscribedCrop.fit(region, 4.0 / 5.0, Vec2(200.0, 150.0)))
        assertEquals(300.0, portrait.height, 1e-3)
        assertEquals(240.0, portrait.width, 1e-3)
        val landscape = assertNotNull(InscribedCrop.fit(region, 5.0 / 4.0, Vec2(200.0, 150.0)))
        assertEquals(300.0, landscape.height, 1e-3)
        assertEquals(375.0, landscape.width, 1e-3)
        // 16:9 is width-limited, its reciprocal height-limited.
        val wide = assertNotNull(InscribedCrop.fit(region, 16.0 / 9.0, Vec2(200.0, 150.0)))
        assertEquals(400.0, wide.width, 1e-3)
        val tall = assertNotNull(InscribedCrop.fit(region, 9.0 / 16.0, Vec2(200.0, 150.0)))
        assertEquals(300.0, tall.height, 1e-3)
        for (r in listOf(portrait, landscape, wide, tall)) assertTrue(InscribedCrop.contains(region, r))
    }

    @Test
    fun largestSquareInsideASquareRotatedBy45() {
        val plane = WarpPlane(100.0, 100.0, PhotoWarp(rotationDegrees = 45f))
        val (t, c) = assertNotNull(InscribedCrop.largest(plane.validRegion(), 1.0))
        // The 45° diamond of side 100 holds an axis square of side 100/sqrt2.
        assertEquals(100.0 / sqrt(2.0), t, 1e-4)
        assertEquals(50.0, c.x, 1e-4)
        assertEquals(50.0, c.y, 1e-4)
    }

    @Test
    fun rotatedLandscapeInscribedRectMatchesTheClosedForm() {
        // Largest w:h = 4:3 rect inside a 4000x3000 photo rotated by θ.
        val w = 4000.0
        val h = 3000.0
        for (deg in listOf(1f, 5f, 12f, 30f, 45f)) {
            val plane = WarpPlane(w, h, PhotoWarp(rotationDegrees = deg))
            val fitted = assertNotNull(InscribedCrop.fit(plane.validRegion(), w / h, Vec2(w / 2, h / 2)))
            val rad = deg * kotlin.math.PI / 180.0
            val s = kotlin.math.sin(rad)
            val c = kotlin.math.cos(rad)
            // Same-aspect centred rect: scale k = h / (h c + w s) (the short
            // side is the binding one for a landscape frame).
            val k = minOf(h / (h * c + w * s), w / (w * c + h * s))
            assertEquals(h * k, fitted.height, 0.5, "θ=$deg")
            assertEquals(w / 2, fitted.center.x, 0.5)
            assertTrue(InscribedCrop.contains(plane.validRegion(), fitted))
        }
    }

    @Test
    fun fitPrefersTheRequestedCentreWhenItIsLegal() {
        val region = rect(400.0, 300.0)
        val r = assertNotNull(InscribedCrop.fit(region, 1.0, Vec2(100.0, 100.0), 100.0))
        assertEquals(100.0, r.center.x, 1e-6)
        assertEquals(100.0, r.center.y, 1e-6)
        // An out-of-reach centre slides to the nearest legal one.
        val edge = assertNotNull(InscribedCrop.fit(region, 1.0, Vec2(0.0, 0.0), 100.0))
        assertEquals(50.0, edge.center.x, 1e-6)
        assertEquals(50.0, edge.center.y, 1e-6)
    }

    @Test
    fun clampTowardStopsAtTheBoundary() {
        val region = rect(400.0, 300.0)
        val from = PlaneRect(100.0, 100.0, 200.0, 200.0)
        val to = PlaneRect(100.0, 100.0, 600.0, 200.0)
        val r = InscribedCrop.clampToward(region, from, to)
        assertEquals(400.0, r.right, 1e-6)
        assertEquals(100.0, r.left, 1e-9)
    }
}
