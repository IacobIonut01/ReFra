package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.PhotoCurvePoint
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoLook
import com.dot.gallery.editor.core.model.PhotoLookId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Version contract of the photo codec's finishing stage: a written
 * document carries every finishing field through the round trip, a v1
 * document (no finishing object) decodes with a neutral finishing, and
 * corrupt finishing payloads fail inside the same loud decode path as
 * the rest of the recipe.
 */
class PhotoFinishingCodecTest {

    @Test
    fun `written documents carry the current version`() {
        val json = PhotoProjectCodec.encode(
            PhotoProjectSnapshot(PhotoEdits())
        )
        assertTrue("\"version\":${PhotoProjectCodec.VERSION}" in json, json)
        assertEquals(1, PhotoProjectCodec.MIN_VERSION)
    }

    @Test
    fun `a v1 document without finishing decodes neutral`() {
        // The exact v1 shape — no finishing key at all.
        val v1 = """
            {"version":1,
             "adjustments":{"exposureStops":0.5,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[]}
        """.trimIndent()
        val decoded = PhotoProjectCodec.decode(v1)
        assertEquals(PhotoFinishing(), decoded.edits.finishing)
        assertTrue(decoded.edits.finishing.isNeutral)
    }

    @Test
    fun `partial finishing objects decode missing fields as defaults`() {
        val json = """
            {"version":2,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "finishing":{"whiteBalance":{"temperatureKelvin":3200},
               "futureThing":{"x":1}}}
        """.trimIndent()
        val decoded = PhotoProjectCodec.decode(json)
        assertEquals(3200f, decoded.edits.finishing.whiteBalance.temperatureKelvin)
        assertEquals(0f, decoded.edits.finishing.whiteBalance.tint)
        assertEquals(1337, decoded.edits.finishing.grain.seed)
    }

    @Test
    fun `a corrupt finishing payload fails loudly`() {
        fun withFinishing(payload: String) = """
            {"version":2,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "finishing":$payload}
        """.trimIndent()

        // A four-point curve violates the model invariant — decode must
        // throw, not half-load.
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withFinishing("""{"curves":{"master":[0,0.5,0.75,1]}}""")
            )
        }
        // A decreasing curve fails the nondecreasing rule.
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withFinishing(
                    """{"curves":{"master":[0,0.8,0.4,0.75,1]}}"""
                )
            )
        }
        // Out-of-domain values fail the same way.
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withFinishing(
                    """{"whiteBalance":{"temperatureKelvin":99000}}"""
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withFinishing("""{"grain":{"roughness":2}}""")
            )
        }
        // Non-numeric curve content is a malformed document.
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withFinishing(
                    """{"curves":{"master":[0,"x",0.5,0.75,1]}}"""
                )
            )
        }
    }

    // ---- free curve points (v6) ------------------------------------------

    @Test
    fun `free curve points round trip and legacy ordinates still read`() {
        fun withCurve(version: Int, curve: String) = """
            {"version":$version,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "finishing":{"curves":{"master":$curve}}}
        """.trimIndent()
        val free = PhotoProjectCodec.decode(
            withCurve(6, """[{"x":0.1,"y":0.8},{"x":0.5,"y":0.2},{"x":0.9,"y":0.7}]""")
        ).edits.finishing.curves.master
        assertEquals(
            listOf(
                PhotoCurvePoint(0.1f, 0.8f),
                PhotoCurvePoint(0.5f, 0.2f),
                PhotoCurvePoint(0.9f, 0.7f)
            ),
            free.points.toList()
        )
        val legacy = PhotoProjectCodec.decode(
            withCurve(5, "[0,0.3,0.55,0.8,1]")
        ).edits.finishing.curves.master
        assertEquals(PhotoCurvePoint(0.25f, 0.3f), legacy.points[1])
        val legacyNeutral = PhotoProjectCodec.decode(
            withCurve(5, "[0,0.25,0.5,0.75,1]")
        ).edits.finishing.curves.master
        assertEquals(PhotoToneCurve(), legacyNeutral)
        // Points out of order are a corrupt document.
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                withCurve(6, """[{"x":0.5,"y":0},{"x":0.1,"y":1}]""")
            )
        }
    }

    // ---- look (v4) ------------------------------------------------------

    @Test
    fun `a written document carries its look through the round trip`() {
        val edits = PhotoEdits(
            finishing = PhotoFinishing(
                look = PhotoLook(PhotoLookId.Cinematic, 0.65f)
            )
        )
        val decoded = PhotoProjectCodec.decode(
            PhotoProjectCodec.encode(PhotoProjectSnapshot(edits))
        )
        assertEquals(PhotoLookId.Cinematic, decoded.edits.finishing.look.id)
        assertEquals(0.65f, decoded.edits.finishing.look.amount)
    }

    @Test
    fun `v1 through v3 documents decode a neutral look`() {
        fun withVersion(version: Int, finishing: String = "") = """
            {"version":$version,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[]$finishing}
        """.trimIndent()

        for (version in 1..3) {
            val decoded = PhotoProjectCodec.decode(withVersion(version))
            assertEquals(
                PhotoLook(), decoded.edits.finishing.look,
                "v$version must decode a neutral look"
            )
        }
        // A v3 finishing object without the look key decodes neutral too.
        val v3 = PhotoProjectCodec.decode(
            withVersion(3, ""","finishing":{"grain":{"amount":0.5}}""")
        )
        assertEquals(0.5f, v3.edits.finishing.grain.amount)
        assertEquals(PhotoLook(), v3.edits.finishing.look)
    }

    @Test
    fun `an unknown look id fails loudly`() {
        val json = """
            {"version":4,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "finishing":{"look":{"id":"SepiaDusk","amount":1}}}
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(json)
        }
    }

    @Test
    fun `an out-of-range look amount fails the model invariant`() {
        val json = """
            {"version":4,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "finishing":{"look":{"id":"Clean","amount":1.7}}}
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(json)
        }
    }
}
