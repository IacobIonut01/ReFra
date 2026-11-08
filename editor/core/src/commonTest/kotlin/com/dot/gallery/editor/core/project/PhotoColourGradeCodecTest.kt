package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.PhotoColourGrade
import com.dot.gallery.editor.core.model.PhotoColourWheel
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoHslAdjustment
import com.dot.gallery.editor.core.model.PhotoHslBand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The colour grade survives a project round trip, older documents without it
 * decode neutral, and out-of-range payloads fail like the rest of the recipe.
 */
class PhotoColourGradeCodecTest {

    @Test
    fun `a full grade round trips`() {
        val grade = PhotoColourGrade(
            vibrance = 0.25f,
            protectSkin = true,
            shadows = PhotoColourWheel(215f, 0.12f, -0.1f),
            midtones = PhotoColourWheel(45f, 0.06f),
            highlights = PhotoColourWheel(68f, 0.14f, 0.04f)
        ).withAdjustment(
            PhotoHslBand.Blue,
            PhotoHslAdjustment(-0.04f, 0.18f, -0.06f)
        )
        val edits = PhotoEdits(finishing = PhotoFinishing(grade = grade))
        val decoded = PhotoProjectCodec.decode(
            PhotoProjectCodec.encode(PhotoProjectSnapshot(edits))
        )
        assertEquals(grade, decoded.edits.finishing.grade)
    }

    @Test
    fun `a v4 finishing without a grade decodes neutral`() {
        val decoded = PhotoProjectCodec.decode(
            """
            {"version":4,
             "adjustments":{"exposureStops":0,"contrast":1,"saturation":1,
               "highlights":0,"shadows":0,"whites":0,"blacks":0},
             "geometry":{"crop":{"left":0,"top":0,"right":1,"bottom":1},
               "quarterTurns":0,"flipHorizontal":false,"flipVertical":false},
             "layers":[],
             "finishing":{"whiteBalance":{"temperatureKelvin":5000,"tint":0}}}
            """.trimIndent()
        )
        assertEquals(PhotoColourGrade(), decoded.edits.finishing.grade)
    }

    @Test
    fun `an out of range wheel fails the decode`() {
        val json = PhotoProjectCodec.encode(
            PhotoProjectSnapshot(
                PhotoEdits(
                    finishing = PhotoFinishing(
                        grade = PhotoColourGrade(
                            shadows = PhotoColourWheel(10f, 0.5f)
                        )
                    )
                )
            )
        ).replace("\"amount\":0.5", "\"amount\":3")
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(json)
        }
    }
}
