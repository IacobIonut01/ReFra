package com.dot.gallery.editor.core.geometry

import com.dot.gallery.editor.core.model.PhotoWarp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuidedUprightTest {

    private val w = 4000.0
    private val h = 3000.0

    /**
     * A scene that is upright under [truth]: segments that are exactly
     * vertical/horizontal in the corrected plane, carried back to the
     * rigid (captured) frame — what the user traces on the tilted photo.
     */
    private fun guidesFor(truth: PhotoWarp, vertical: Int, horizontal: Int): List<UprightGuide> {
        val plane = WarpPlane(w, h, truth)
        fun rigid(x: Double, y: Double) = assertNotNull(plane.toRigid(x, y))
        val out = mutableListOf<UprightGuide>()
        val xs = listOf(1300.0, 2700.0)
        val ys = listOf(1000.0, 2000.0)
        for (i in 0 until vertical) out += UprightGuide(rigid(xs[i], 800.0), rigid(xs[i], 2200.0), GuideAxis.Vertical)
        for (i in 0 until horizontal) out += UprightGuide(rigid(1000.0, ys[i]), rigid(3000.0, ys[i]), GuideAxis.Horizontal)
        return out
    }

    @Test
    fun oneGuideSolvesRotationOnly() {
        val truth = PhotoWarp(rotationDegrees = -3.4f)
        val solved = assertNotNull(GuidedUpright.solve(w, h, guidesFor(truth, 1, 0)))
        assertEquals(-3.4f, solved.rotationDegrees, 0.02f)
        assertEquals(0f, solved.verticalPerspective)
        assertEquals(0f, solved.horizontalPerspective)
    }

    @Test
    fun twoVerticalGuidesRecoverRotationAndVerticalKeystone() {
        val truth = PhotoWarp(rotationDegrees = 2.0f, verticalPerspective = 18f)
        val guides = guidesFor(truth, 2, 0)
        val solved = assertNotNull(GuidedUpright.solve(w, h, guides))
        assertEquals(2.0f, solved.rotationDegrees, 0.05f)
        assertEquals(18f, solved.verticalPerspective, 0.3f)
        assertTrue(GuidedUpright.error(w, h, guides, solved) < 1e-4)
    }

    @Test
    fun fourGuidesRecoverAllThreeTerms() {
        val truth = PhotoWarp(rotationDegrees = -1.5f, verticalPerspective = -22f, horizontalPerspective = 12f)
        val guides = guidesFor(truth, 2, 2)
        val solved = assertNotNull(GuidedUpright.solve(w, h, guides))
        assertEquals(-1.5f, solved.rotationDegrees, 0.05f)
        assertEquals(-22f, solved.verticalPerspective, 0.5f)
        assertEquals(12f, solved.horizontalPerspective, 0.5f)
        assertTrue(GuidedUpright.error(w, h, guides, solved) < 1e-4)
    }

    @Test
    fun alreadyUprightGuidesSolveToNeutral() {
        val solved = assertNotNull(GuidedUpright.solve(w, h, guidesFor(PhotoWarp(), 2, 2)))
        assertTrue(solved.isNeutral, "$solved")
    }

    @Test
    fun scaleIsPreservedAndDegenerateGuidesAreIgnored() {
        assertNull(GuidedUpright.solve(w, h, listOf(UprightGuide(Vec2(5.0, 5.0), Vec2(5.0, 5.0), GuideAxis.Vertical))))
        val solved = assertNotNull(
            GuidedUpright.solve(w, h, guidesFor(PhotoWarp(rotationDegrees = 4f), 1, 0), PhotoWarp(scale = 1.25f))
        )
        assertEquals(1.25f, solved.scale)
    }
}
