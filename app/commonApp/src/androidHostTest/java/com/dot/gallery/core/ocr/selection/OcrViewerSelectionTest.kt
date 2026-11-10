package com.dot.gallery.core.ocr.selection

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.domain.OcrWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrViewerSelectionTest {
    @Test
    fun coordinateMappingRoundTripsFitZoomPanAndQuarterTurns() {
        val source = NormalizedPoint(0.2f, 0.7f)
        val viewports = listOf(
            OcrViewportRect(100f, 200f, 900f, 800f),
            OcrViewportRect(-350f, -120f, 1_650f, 1_380f),
        )
        viewports.forEach { viewport ->
            listOf(0, 90, 180, 270).forEach { rotation ->
                val mapped = OcrViewerCoordinateMapper.sourceToViewport(source, viewport, rotation)
                val restored = OcrViewerCoordinateMapper.viewportToSource(
                    mapped.first,
                    mapped.second,
                    viewport,
                    rotation,
                )
                assertEquals(source.x, restored!!.x, 0.0001f)
                assertEquals(source.y, restored.y, 0.0001f)
            }
        }
        assertNull(OcrViewerCoordinateMapper.viewportToSource(99f, 300f, viewports.first(), 0))
    }

    @Test
    fun contiguousSelectionPreservesOriginalUnicodeAndLineBreaks() {
        val document = document()
        val range = OcrSelectionRange(0, 2)
        assertEquals(linkedSetOf("a", "b", "c"), OcrViewerSelection.selectedWordIds(document, range))
        assertEquals("A\u00a0β\n猫", OcrViewerSelection.selectedText(document, range))

        val moved = OcrViewerSelection.moveHandle(range, OcrSelectionHandle.START, 1, 3)
        assertEquals(OcrSelectionRange(1, 2), moved)
        assertEquals("β\n猫", OcrViewerSelection.selectedText(document, moved))
    }

    @Test
    fun dragSelectionExtendsContiguouslyFromItsAnchor() {
        val document = document()
        val forward = OcrViewerSelection.rangeBetween(document, "a", "c")
        val firstBackwardStep = OcrViewerSelection.rangeBetween(document, "c", "b")
        val secondBackwardStep = OcrViewerSelection.rangeBetween(document, "c", "a")

        assertEquals(OcrSelectionRange(0, 2), forward)
        assertEquals(OcrSelectionRange(1, 2), firstBackwardStep)
        assertEquals(OcrSelectionRange(0, 2), secondBackwardStep)
        assertEquals(linkedSetOf("a", "b", "c"), OcrViewerSelection.selectedWordIds(document, forward))
    }

    @Test
    fun nearestWordHysteresisRetainsCurrentWordNearBoundary() {
        val document = document()
        assertEquals(
            0,
            OcrViewerSelection.nearestWordIndex(
                document,
                NormalizedPoint(0.36f, 0.25f),
                currentIndex = 0,
            ),
        )
        assertEquals(
            1,
            OcrViewerSelection.nearestWordIndex(
                document,
                NormalizedPoint(0.55f, 0.25f),
                currentIndex = 0,
            ),
        )
    }

    private fun document(): OcrDocument {
        val text = "A\u00a0β\n猫"
        val a = OcrWord("a", 0, "A", 0, 1, quad(0.1f, 0.3f), 1f)
        val b = OcrWord("b", 1, "β", 2, 3, quad(0.42f, 0.62f), 1f)
        val c = OcrWord("c", 2, "猫", 4, 5, quad(0.1f, 0.3f, 0.5f), 1f)
        return OcrDocument(
            sourceWidth = 100,
            sourceHeight = 100,
            fullText = text,
            lines = listOf(
                OcrLine("l1", 0, "A\u00a0β", 0, 3, NormalizedQuad.axisAligned(0.1f, 0.2f, 0.62f, 0.3f), 1f, listOf(a, b)),
                OcrLine("l2", 1, "猫", 4, 5, NormalizedQuad.axisAligned(0.1f, 0.5f, 0.3f, 0.6f), 1f, listOf(c)),
            ),
        )
    }

    private fun quad(left: Float, right: Float, top: Float = 0.2f) =
        NormalizedQuad.axisAligned(left, top, right, top + 0.1f)
}
