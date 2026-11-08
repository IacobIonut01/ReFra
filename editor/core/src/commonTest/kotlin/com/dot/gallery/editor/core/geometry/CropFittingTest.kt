package com.dot.gallery.editor.core.geometry

import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PhotoWarpMapping
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CropFittingTest {

    private val upright = PixelSize(400, 300)

    @Test
    fun straightenConstrainsTheCropAndRegrowsWithinOneGesture() {
        val start = PhotoGeometry()
        val rotated = CropFitting.withWarp(upright, start, PhotoWarp(rotationDegrees = 10f), null)
        assertTrue(CropFitting.isValid(upright, rotated), "$rotated")
        assertTrue(rotated.crop.right - rotated.crop.left < 1.0)
        // Same aspect as the photo (Free keeps the crop's ratio).
        val r = CropFitting.planeRect(upright, rotated)
        assertEquals(400.0 / 300.0, r.aspect, 1e-3)
        // Back to 0 from the same gesture start: the full frame again.
        val back = CropFitting.withWarp(upright, start, PhotoWarp(), null)
        assertEquals(1.0, back.crop.right - back.crop.left, 1e-6)
        assertEquals(1.0, back.crop.bottom - back.crop.top, 1e-6)
    }

    @Test
    fun everyWarpFitStaysFreeOfBlankEdges() {
        val start = PhotoGeometry(crop = NormalizedCrop(0.05, 0.1, 0.7, 0.9), quarterTurns = 1, flipHorizontal = true)
        for (rot in listOf(-45f, -12f, 0.3f, 7f, 45f)) for (v in listOf(-50f, 0f, 35f)) for (hp in listOf(-50f, 20f)) {
            val warp = PhotoWarp(rotationDegrees = rot, verticalPerspective = v, horizontalPerspective = hp)
            for (aspect in listOf(null, 1.0, 4.0 / 5.0, 16.0 / 9.0)) {
                val g = CropFitting.withWarp(upright, start, warp, aspect)
                assertTrue(CropFitting.isValid(upright, g), "rot=$rot v=$v h=$hp a=$aspect -> $g")
                if (aspect != null) {
                    assertEquals(aspect, CropFitting.planeRect(upright, g).aspect, 0.02, "aspect $aspect")
                }
                // Export samples every corner pixel from the photo — nothing transparent.
                val m = PhotoWarpMapping(upright, 1, g)
                val ow = m.outputSize.width - 1.0
                val oh = m.outputSize.height - 1.0
                for ((x, y) in listOf(0.0 to 0.0, ow to 0.0, 0.0 to oh, ow to oh)) {
                    assertNotNull(m.sourcePoint(x, y), "corner ($x,$y) for rot=$rot v=$v h=$hp a=$aspect")
                }
            }
        }
    }

    @Test
    fun aspectChoiceTakesTheLargestCropAndItsReciprocal() {
        val g = PhotoGeometry()
        val square = CropFitting.withAspect(upright, g, 1.0)
        assertEquals(300.0, CropFitting.planeRect(upright, square).height, 0.5)
        val portrait = CropFitting.withAspect(upright, g, 4.0 / 5.0)
        val landscape = CropFitting.withAspect(upright, g, 5.0 / 4.0)
        assertEquals(240.0, CropFitting.planeRect(upright, portrait).width, 0.5)
        assertEquals(375.0, CropFitting.planeRect(upright, landscape).width, 0.5)
    }

    private fun outputSamples(g: PhotoGeometry): List<List<Pair<Double, Double>?>> {
        val m = PhotoWarpMapping(PixelSize(9, 7), 1, g)
        return (0 until m.outputSize.height).map { y ->
            (0 until m.outputSize.width).map { x ->
                m.sourcePoint(x.toDouble(), y.toDouble())?.let { it.x to it.y }
            }
        }
    }

    private fun assertSame(expected: Pair<Double, Double>?, actual: Pair<Double, Double>?, label: String) {
        if (expected == null || actual == null) {
            assertEquals(expected == null, actual == null, label)
            return
        }
        assertEquals(expected.first, actual.first, 1e-6, "$label x")
        assertEquals(expected.second, actual.second, 1e-6, "$label y")
    }

    @Test
    fun rotateLeftTurnsTheVisibleResultIncludingTheWarp() {
        val warps = listOf(
            PhotoWarp(),
            PhotoWarp(rotationDegrees = 8f, verticalPerspective = 30f, horizontalPerspective = -20f)
        )
        for (warp in warps) for (fh in listOf(false, true)) for (fv in listOf(false, true)) for (q in 0..3) {
            val g = PhotoGeometry(
                crop = NormalizedCrop(0.0, 0.0, 1.0, 1.0), quarterTurns = q,
                flipHorizontal = fh, flipVertical = fv, warp = warp
            )
            val before = outputSamples(g)
            val after = outputSamples(CropFitting.rotatedLeft(g))
            val oh = before.size
            val ow = before[0].size
            assertEquals(ow, after.size)
            // Counter-clockwise: new(x, y) = old(W - 1 - y, x).
            for (y in after.indices) for (x in after[y].indices) {
                assertSame(before[x][ow - 1 - y], after[y][x], "q$q fh$fh fv$fv $warp ($x,$y)")
            }
            assertEquals(oh, after[0].size)
        }
    }

    @Test
    fun flipsMirrorTheVisibleResultIncludingTheWarp() {
        val warp = PhotoWarp(rotationDegrees = -6f, verticalPerspective = 25f, horizontalPerspective = 15f)
        for (q in 0..3) {
            val g = PhotoGeometry(quarterTurns = q, warp = warp)
            val before = outputSamples(g)
            val h = outputSamples(CropFitting.flippedHorizontally(g))
            val v = outputSamples(CropFitting.flippedVertically(g))
            val ow = before[0].size
            val oh = before.size
            for (y in 0 until oh) for (x in 0 until ow) {
                assertSame(before[y][ow - 1 - x], h[y][x], "flipH q$q ($x,$y)")
                assertSame(before[oh - 1 - y][x], v[y][x], "flipV q$q ($x,$y)")
            }
        }
    }
}
