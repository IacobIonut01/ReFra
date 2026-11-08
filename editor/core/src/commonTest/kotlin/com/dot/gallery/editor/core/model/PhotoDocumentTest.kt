package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PhotoDocumentTest {

    @Test
    fun `holds an image source and default identity adjustments`() {
        val image = MediaAsset.Image("img", "content://media/1", PixelSize(4, 4))
        val document = PhotoDocument(image)
        assertSame(image, document.source)
        assertEquals(ColorAdjustments(), document.adjustments)
    }
}
