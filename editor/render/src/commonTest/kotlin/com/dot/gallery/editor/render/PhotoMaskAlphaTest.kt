package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PhotoMaskStroke
import com.dot.gallery.editor.core.model.PixelSize
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoMaskAlphaTest {

    private val size = PixelSize(10, 10)

    private fun stroke(
        vararg points: NormalizedPoint,
        diameter: Float = 0.2f,
        hardness: Float = 1f,
        flow: Float = 1f,
        mode: PhotoMaskMode = PhotoMaskMode.Paint
    ) = PhotoMaskStroke(
        points = points.toList().toPersistentList(),
        diameter = diameter,
        hardness = hardness,
        flow = flow,
        mode = mode
    )

    @Test
    fun `neutral mask returns base alpha without touching strokes`() {
        assertEquals(
            0.7f,
            photoMaskAlpha(PhotoLayerMask(baseAlpha = 0.7f), 0.5, 0.5, size),
            1e-6f
        )
        assertEquals(
            1f,
            photoMaskAlpha(PhotoLayerMask(), 0.0, 1.0, size),
            1e-6f
        )
    }

    @Test
    fun `a single point stroke paints a dot with feathered edge`() {
        // radius = .2 * 10 / 2 = 1px around normalized (5,5).
        val mask = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(
                stroke(NormalizedPoint(.5f, .5f), hardness = 0f)
            )
        )
        assertEquals(1f, photoMaskAlpha(mask, 0.5, 0.5, size), 1e-6f)
        // distance .5px inside the 1px radius -> coverage (1-.5)/1 = .5.
        assertEquals(.5f, photoMaskAlpha(mask, 0.55, 0.5, size), 1e-6f)
        // distance 1px at the radius -> 0.
        assertEquals(0f, photoMaskAlpha(mask, 0.6, 0.5, size), 1e-6f)
    }

    @Test
    fun `hardness keeps full coverage inside the core`() {
        val mask = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(
                stroke(NormalizedPoint(.5f, .5f), hardness = .5f)
            )
        )
        // core = .5px; dist .25 < .5 -> full coverage.
        assertEquals(1f, photoMaskAlpha(mask, 0.525, 0.5, size), 1e-6f)
        // dist .75 between core .5 and radius 1 -> coverage (1-.75)/(1-.5)=.5.
        assertEquals(.5f, photoMaskAlpha(mask, 0.575, 0.5, size), 1e-6f)
        // fully hard edge: coverage is a step at the radius.
        val hard = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(
                stroke(NormalizedPoint(.5f, .5f), hardness = 1f)
            )
        )
        assertEquals(1f, photoMaskAlpha(hard, 0.59, 0.5, size), 1e-6f)
        assertEquals(0f, photoMaskAlpha(hard, 0.61, 0.5, size), 1e-6f)
    }

    @Test
    fun `flow scales the applied coverage and erase subtracts`() {
        val softPaint = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(
                stroke(NormalizedPoint(.5f, .5f), flow = .5f)
            )
        )
        // a += (1-a)*coverage*flow = .5
        assertEquals(.5f, photoMaskAlpha(softPaint, 0.5, 0.5, size), 1e-6f)

        val erase = PhotoLayerMask(
            baseAlpha = 1f,
            strokes = persistentListOf(
                stroke(NormalizedPoint(.5f, .5f), mode = PhotoMaskMode.Erase)
            )
        )
        assertEquals(0f, photoMaskAlpha(erase, 0.5, 0.5, size), 1e-6f)

        // Paint then erase on the same centre: 0 -> 1 -> 0.
        val ordered = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(
                stroke(NormalizedPoint(.5f, .5f)),
                stroke(NormalizedPoint(.5f, .5f), mode = PhotoMaskMode.Erase)
            )
        )
        assertEquals(0f, photoMaskAlpha(ordered, 0.5, 0.5, size), 1e-6f)
        // Order matters: erase then paint leaves paint.
        val reversed = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(
                stroke(NormalizedPoint(.5f, .5f), mode = PhotoMaskMode.Erase),
                stroke(NormalizedPoint(.5f, .5f))
            )
        )
        assertEquals(1f, photoMaskAlpha(reversed, 0.5, 0.5, size), 1e-6f)
    }

    @Test
    fun `a segment paints along its length not just its endpoints`() {
        val line = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(
                stroke(NormalizedPoint(.2f, .5f), NormalizedPoint(.8f, .5f))
            )
        )
        // Midpoint of the segment: dist 0 -> coverage 1.
        assertEquals(1f, photoMaskAlpha(line, 0.5, 0.5, size), 1e-6f)
        // Two pixels under the midpoint is well past the 1px radius.
        assertEquals(0f, photoMaskAlpha(line, 0.5, 0.7, size), 1e-6f)
        // Past the segment end the cap is the endpoint distance.
        assertEquals(0f, photoMaskAlpha(line, 0.95, 0.5, size), 1e-6f)
    }

    @Test
    fun `inversion applies last to the whole replayed mask`() {
        val painted = PhotoLayerMask(
            baseAlpha = 0f,
            strokes = persistentListOf(stroke(NormalizedPoint(.5f, .5f))),
            inverted = true
        )
        // Inside the dot the replayed alpha is 1 -> inverted 0.
        assertEquals(0f, photoMaskAlpha(painted, 0.5, 0.5, size), 1e-6f)
        // Outside it stays 0 -> inverted 1.
        assertEquals(1f, photoMaskAlpha(painted, 0.0, 0.0, size), 1e-6f)

        val neutral = PhotoLayerMask(baseAlpha = .3f, inverted = true)
        assertEquals(.7f, photoMaskAlpha(neutral, 0.5, 0.5, size), 1e-6f)
    }
}
