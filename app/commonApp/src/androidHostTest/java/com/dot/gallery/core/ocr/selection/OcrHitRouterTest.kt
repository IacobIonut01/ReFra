package com.dot.gallery.core.ocr.selection

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.domain.OcrWord
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrHitRouterTest {
    private val word = OcrWord("word", 0, "text", 0, 4, NormalizedQuad.axisAligned(0.2f, 0.2f, 0.4f, 0.3f), 1f)
    private val document = OcrDocument(
        sourceWidth = 100,
        sourceHeight = 100,
        fullText = "text",
        lines = listOf(
            OcrLine("line", 0, "text", 0, 4, NormalizedQuad.axisAligned(0.2f, 0.2f, 0.4f, 0.3f), 1f, listOf(word))
        )
    )

    @Test
    fun textHitWins() {
        assertEquals(
            ViewerLongPressResult.SelectText("word"),
            OcrHitRouter.route(NormalizedPoint(0.3f, 0.25f), document, ViewerLongPressFallback.CUTOUT)
        )
    }

    @Test
    fun missAndMissingGeometryDelegate() {
        assertEquals(
            ViewerLongPressResult.Delegate(ViewerLongPressFallback.ROTATE),
            OcrHitRouter.route(NormalizedPoint(0.8f, 0.8f), document, ViewerLongPressFallback.ROTATE)
        )
        assertEquals(
            ViewerLongPressResult.Delegate(ViewerLongPressFallback.CUTOUT),
            OcrHitRouter.route(NormalizedPoint(0.3f, 0.25f), null, ViewerLongPressFallback.CUTOUT)
        )
    }
}
