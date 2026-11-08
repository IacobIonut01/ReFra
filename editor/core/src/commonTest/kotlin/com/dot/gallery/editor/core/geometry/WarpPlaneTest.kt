package com.dot.gallery.editor.core.geometry

import com.dot.gallery.editor.core.model.PhotoWarp
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WarpPlaneTest {

    private val warps = listOf(
        PhotoWarp(),
        PhotoWarp(rotationDegrees = 12.5f),
        PhotoWarp(verticalPerspective = 40f),
        PhotoWarp(horizontalPerspective = -35f, rotationDegrees = -7f),
        PhotoWarp(rotationDegrees = 45f, verticalPerspective = 100f, horizontalPerspective = -100f, scale = 1.5f)
    )

    @Test
    fun toPlaneInvertsToRigid() {
        for (warp in warps) {
            val plane = WarpPlane(400.0, 300.0, warp)
            for (x in listOf(0.0, 37.0, 200.0, 399.0)) for (y in listOf(0.0, 150.0, 299.5)) {
                val p = assertNotNull(plane.toPlane(x, y), "$warp ($x,$y)")
                val back = assertNotNull(plane.toRigid(p.x, p.y))
                assertEquals(x, back.x, 1e-7, "$warp x")
                assertEquals(y, back.y, 1e-7, "$warp y")
            }
        }
    }

    @Test
    fun neutralOutlineIsTheFrame() {
        val outline = WarpPlane(400.0, 300.0, PhotoWarp()).photoOutline()
        assertEquals(120000.0, outline.area, 1e-6)
    }

    @Test
    fun rotationKeepsTheOutlineAreaAndCentre() {
        val plane = WarpPlane(400.0, 300.0, PhotoWarp(rotationDegrees = 30f))
        val outline = plane.photoOutline()
        assertEquals(120000.0, outline.area, 1e-6)
        val c = outline.centroid()
        assertEquals(200.0, c.x, 1e-9)
        assertEquals(150.0, c.y, 1e-9)
        // Rotated corners extend past the frame; the valid region is the clip.
        assertTrue(plane.validRegion().area < outline.area)
    }

    @Test
    fun squareRotatedBy45HasTheExpectedOutline() {
        val outline = WarpPlane(2.0, 2.0, PhotoWarp(rotationDegrees = 45f)).photoOutline()
        val r = outline.vertices.maxOf { (it - Vec2(1.0, 1.0)).length }
        assertEquals(sqrt(2.0), r, 1e-9)
    }
}
