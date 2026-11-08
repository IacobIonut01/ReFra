package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.core.model.PhotoMarkupStroke
import com.dot.gallery.editor.core.model.PhotoMarkupType
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

class EditorRetouchToolsTest {

    @Test
    fun markupReplacesCurvesOnTheDefaultDock() {
        assertEquals(
            listOf(
                EditorToolIds.PhotoCrop.value,
                EditorToolIds.PhotoLight.value,
                EditorToolIds.PhotoMarkup.value,
                EditorToolIds.PhotoLooks.value
            ),
            DEFAULT_PHOTO_PINS
        )
        // Curves stays a real tool that can still be pinned.
        assertTrue(EditorToolIds.PhotoCurves.value in PHOTO_PINNABLE_DESTINATIONS)
        assertTrue(DEFAULT_PHOTO_PINS.all { it in PHOTO_PINNABLE_DESTINATIONS })
    }

    @Test
    fun selectionToolsOnlyAppearWhereTheHostCanSegment() {
        val without = photoToolEntries().filter { it.catalogued }.map { it.descriptor.id }
        assertFalse(EditorToolIds.PhotoCutout in without)
        assertFalse(EditorToolIds.PhotoRemoveBackground in without)
        assertTrue(EditorToolIds.PhotoMarkup in without)
        assertTrue(EditorToolIds.PhotoColourTone in without)
        assertTrue(EditorToolIds.PhotoPosterize in without)

        val host = EditorHostTools(openSegmenter = { null })
        val with = photoToolEntries(host).filter { it.catalogued }.map { it.descriptor.id }
        assertTrue(EditorToolIds.PhotoCutout in with)
        assertTrue(EditorToolIds.PhotoRemoveBackground in with)
    }

    @Test
    fun theEraserRemovesAWholeStrokeItTouches() {
        val near = PhotoMarkupStroke(
            PhotoMarkupType.Pen,
            persistentListOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.5f, 0.5f)),
            size = 10f
        )
        val far = PhotoMarkupStroke(
            PhotoMarkupType.Pen,
            persistentListOf(NormalizedPoint(0.9f, 0.9f)),
            size = 10f
        )
        assertEquals(listOf(far), eraseStrokes(listOf(near, far), NormalizedPoint(0.5f, 0.5f), 1f))
    }

    @Test
    fun aFaceBecomesAnObscureStrokeCoveringIt() {
        val stroke = faceObscureStroke(
            EditorFaceBox(0.4f, 0.4f, 0.6f, 0.6f),
            PixelSize(1000, 1000),
            PhotoMarkupType.Blur,
            70f
        )
        assertEquals(PhotoMarkupType.Blur, stroke.type)
        assertEquals(NormalizedPoint(0.5f, 0.5f), stroke.points.single())
        // 0.2 of the long edge is beyond the largest brush, so it caps.
        assertEquals(100f, stroke.size)

        val mosaic = faceObscureStroke(
            EditorFaceBox(0.4f, 0.4f, 0.6f, 0.6f),
            PixelSize(1000, 1000),
            PhotoMarkupType.Mosaic,
            70f
        )
        assertEquals(PhotoMarkupType.Mosaic, mosaic.type)
        assertEquals(stroke.points, mosaic.points)
        assertEquals(stroke.size, mosaic.size)
    }

    @Test
    fun theAutomaticGuessRejectsSpecksAndTheWholeFrame() {
        fun matte(covered: Int) = PhotoCutoutMatte(
            10, 10, ByteArray(100) { if (it < covered) -1 else 0 }
        )
        assertFalse(isPlausibleSubject(matte(0)))
        assertTrue(isPlausibleSubject(matte(40)))
        assertFalse(isPlausibleSubject(matte(100)))
    }

    @Test
    fun applyingAMatteKeepsColourAndClearsAlpha() {
        val pixels = SrgbPixels(PixelSize(2, 1), intArrayOf(0xFF102030.toInt(), 0xFF405060.toInt()))
        val matte = PhotoCutoutMatte(2, 1, byteArrayOf(-1, 0))
        val out = applyMatteToPixels(pixels, matte)
        assertEquals(0xFF102030.toInt(), out.argbAt(0, 0))
        assertEquals(0x00405060, out.argbAt(1, 0))
    }
}
