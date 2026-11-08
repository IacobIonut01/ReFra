package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PhotoMaskStroke
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * The rasterizer must agree with [photoMaskAlpha] at every pixel centre —
 * it is the GPU mask texture's only definition of the mask, so any drift
 * from the evaluator the CPU pipeline samples is a compositing bug.
 */
class PhotoMaskRasterTest {

    private val size = PixelSize(16, 12)

    private fun stroke(
        vararg points: NormalizedPoint,
        diameter: Float = 0.3f,
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

    private fun assertRasterParity(mask: PhotoLayerMask, label: String) {
        val raster = rasterizePhotoMask(mask, size)
        assertEquals(size, raster.size, "$label: raster size")
        val pixels = raster.copyPixels()
        for (y in 0 until size.height) {
            for (x in 0 until size.width) {
                val argb = pixels[y * size.width + x]
                assertEquals(
                    0xFFFFFF, argb and 0xFFFFFF,
                    "$label pixel ($x,$y): RGB must be white"
                )
                val expected = photoMaskAlpha(
                    mask,
                    (x + .5) / size.width,
                    (y + .5) / size.height,
                    size
                )
                val expectedByte = (expected * 255f + .5f).toInt()
                val actualByte = argb ushr 24
                assertTrue(
                    abs(actualByte - expectedByte) <= 1,
                    "$label pixel ($x,$y): alpha $actualByte vs " +
                        "${expectedByte} ($expected)"
                )
            }
        }
    }

    @Test
    fun neutralMaskFastFillsBaseAlpha() {
        val raster = rasterizePhotoMask(
            PhotoLayerMask(baseAlpha = .6f), size
        )
        val byte = (.6f * 255f + .5f).toInt()
        for (argb in raster.copyPixels()) {
            assertEquals(byte shl 24 or 0xFFFFFF, argb)
        }
    }

    @Test
    fun dotStrokeMatchesEvaluator() {
        assertRasterParity(
            PhotoLayerMask(
                baseAlpha = 0f,
                strokes = persistentListOf(
                    stroke(
                        NormalizedPoint(.4f, .6f),
                        diameter = .4f, hardness = .3f, flow = .7f
                    )
                )
            ),
            "dot"
        )
    }

    @Test
    fun overlappingSegmentsMatchEvaluator() {
        assertRasterParity(
            PhotoLayerMask(
                baseAlpha = 0f,
                strokes = persistentListOf(
                    stroke(
                        NormalizedPoint(.1f, .5f),
                        NormalizedPoint(.9f, .5f),
                        NormalizedPoint(.9f, .9f),
                        NormalizedPoint(.5f, .9f),
                        diameter = .35f, hardness = .5f
                    )
                )
            ),
            "polyline"
        )
    }

    @Test
    fun orderedPaintEraseAndInvertMatchEvaluator() {
        assertRasterParity(
            PhotoLayerMask(
                baseAlpha = .8f,
                strokes = persistentListOf(
                    stroke(
                        NormalizedPoint(.3f, .3f),
                        NormalizedPoint(.7f, .3f),
                        mode = PhotoMaskMode.Erase, flow = .6f
                    ),
                    stroke(
                        NormalizedPoint(.5f, .7f),
                        diameter = .5f, hardness = 0f,
                        mode = PhotoMaskMode.Paint, flow = .9f
                    ),
                    stroke(
                        NormalizedPoint(.8f, .8f),
                        NormalizedPoint(.9f, .2f),
                        mode = PhotoMaskMode.Erase
                    )
                ),
                inverted = true
            ),
            "ordered+invert"
        )
    }

    /** Inversion alone (no strokes) must still flip the whole plane. */
    @Test
    fun invertedNeutralMatchesEvaluator() {
        assertRasterParity(
            PhotoLayerMask(baseAlpha = .25f, inverted = true),
            "inverted-neutral"
        )
    }
}
