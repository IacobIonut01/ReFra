package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoColourTone
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoEffects
import com.dot.gallery.editor.core.model.PhotoMarkup
import com.dot.gallery.editor.core.model.PhotoMarkupStroke
import com.dot.gallery.editor.core.model.PhotoMarkupText
import com.dot.gallery.editor.core.model.PhotoMarkupType
import com.dot.gallery.editor.core.model.PhotoRetouch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

class PhotoRetouchCodecTest {

    private fun roundTrip(retouch: PhotoRetouch): PhotoRetouch =
        PhotoProjectCodec.decode(
            PhotoProjectCodec.encode(PhotoProjectSnapshot(PhotoEdits(retouch = retouch)))
        ).edits.retouch

    @Test
    fun everyRetouchFieldSurvivesARoundTrip() {
        val alpha = ByteArray(600) { if (it < 300) 0 else if (it % 7 == 0) 12 else -1 }
        val retouch = PhotoRetouch(
            tone = PhotoColourTone(skin = 0.25f, blue = -0.5f, hue = 0.75f, blackWhite = -1f),
            effects = PhotoEffects(
                posterize = 0.4f, edges = 0.6f, borderWidth = 0.35f, borderColor = 0xFF2962FF.toInt()
            ),
            markup = PhotoMarkup(
                strokes = persistentListOf(
                    PhotoMarkupStroke(
                        type = PhotoMarkupType.Highlighter,
                        points = persistentListOf(
                            NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.3f, 0.4f)
                        ),
                        size = 22f,
                        strength = 40f,
                        color = 0xFFFFFF00.toInt()
                    ),
                    PhotoMarkupStroke(
                        type = PhotoMarkupType.Mosaic,
                        points = persistentListOf(NormalizedPoint(1f, 0f)),
                        size = 54f
                    )
                ),
                texts = persistentListOf(
                    PhotoMarkupText(3L, "Fort \"Point\"\nSecond", 0.3f, 0.2f, -1, 0.09f, -42.5f)
                )
            ),
            matte = PhotoCutoutMatte(30, 20, alpha)
        )
        assertEquals(retouch, roundTrip(retouch))
    }

    @Test
    fun oldProjectsDecodeWithANeutralStage() {
        val json = PhotoProjectCodec.encode(PhotoProjectSnapshot(PhotoEdits()))
            .replace(Regex(""","retouch":\{.*\}\}$"""), "}")
        assertTrue("retouch" !in json)
        assertTrue(PhotoProjectCodec.decode(json).edits.retouch.isNeutral)
    }

    @Test
    fun aTruncatedMatteIsRejected() {
        val json = PhotoProjectCodec.encode(
            PhotoProjectSnapshot(
                PhotoEdits(retouch = PhotoRetouch(matte = PhotoCutoutMatte(2, 2, ByteArray(4))))
            )
        ).replace(""""width":2""", """"width":3""")
        assertFailsWith<IllegalArgumentException> { PhotoProjectCodec.decode(json) }
    }
}
