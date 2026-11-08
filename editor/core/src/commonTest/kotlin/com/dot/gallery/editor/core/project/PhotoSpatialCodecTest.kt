package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Version contract of the photo codec's spatial stage: a written v3
 * document carries every details/lens-blur field through the round
 * trip, v1 and v2 documents (no spatial object) decode with a neutral
 * stage, and corrupt spatial payloads fail inside the same loud decode
 * path as the rest of the recipe.
 */
class PhotoSpatialCodecTest {

    @Test
    fun `a v3 document round-trips every spatial field`() {
        val edits = PhotoEdits(
            spatial = PhotoSpatialEffects(
                details = PhotoDetails(
                    texture = 0.4f, clarity = -0.2f,
                    sharpening = 0.7f, noiseReduction = 0.1f
                ),
                lensBlur = PhotoLensBlur(
                    mode = LensBlurMode.Linear,
                    strength = 0.8f,
                    transition = 0.35f,
                    highlightBokeh = 0.5f,
                    centerX = 0.25f,
                    centerY = 0.75f,
                    angleDegrees = -45f
                )
            )
        )
        val decoded = PhotoProjectCodec.decode(
            PhotoProjectCodec.encode(PhotoProjectSnapshot(edits))
        )
        assertEquals(edits.spatial, decoded.edits.spatial)
        assertEquals(edits, decoded.edits)
    }

    @Test
    fun `v1 and v2 documents decode with a neutral spatial stage`() {
        // The exact v1 shape — no finishing, no spatial key.
        val v1 = """
            {"version":1,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[]}
        """.trimIndent()
        // The v2 shape — finishing present, no spatial key.
        val v2 = """
            {"version":2,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "finishing":{"grain":{"amount":0.5}}}
        """.trimIndent()
        assertEquals(
            PhotoSpatialEffects(),
            PhotoProjectCodec.decode(v1).edits.spatial
        )
        val decodedV2 = PhotoProjectCodec.decode(v2)
        assertEquals(PhotoSpatialEffects(), decodedV2.edits.spatial)
        assertEquals(0.5f, decodedV2.edits.finishing.grain.amount)
    }

    @Test
    fun `partial spatial objects decode missing fields as defaults`() {
        val json = """
            {"version":3,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "spatial":{"details":{"texture":0.5},
               "lensBlur":{"strength":0.5,"futureThing":1}}}
        """.trimIndent()
        val decoded = PhotoProjectCodec.decode(json)
        assertEquals(0.5f, decoded.edits.spatial.details.texture)
        assertEquals(0f, decoded.edits.spatial.details.clarity)
        assertEquals(0.5f, decoded.edits.spatial.lensBlur.strength)
        assertEquals(LensBlurMode.Radial, decoded.edits.spatial.lensBlur.mode)
        assertEquals(0.6f, decoded.edits.spatial.lensBlur.transition)
    }

    @Test
    fun `a corrupt spatial payload fails loudly`() {
        fun withSpatial(payload: String) = """
            {"version":3,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "spatial":$payload}
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withSpatial("""{"details":{"texture":2}}""")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withSpatial("""{"lensBlur":{"strength":1.5}}""")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withSpatial("""{"lensBlur":{"transition":0.01}}""")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withSpatial("""{"lensBlur":{"angleDegrees":400}}""")
            )
        }
        // An unknown mode name is malformed, not a silent Radial.
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withSpatial("""{"lensBlur":{"mode":"Spiral"}}""")
            )
        }
    }
}
