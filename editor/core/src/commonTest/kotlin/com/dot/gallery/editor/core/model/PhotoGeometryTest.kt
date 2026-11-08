package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotoGeometryTest {

    @Test
    fun `defaults are the identity transform`() {
        val geometry = PhotoGeometry()
        assertEquals(NormalizedCrop(), geometry.crop)
        assertEquals(0, geometry.quarterTurns)
        assertEquals(false, geometry.flipHorizontal)
        assertEquals(false, geometry.flipVertical)
    }

    @Test
    fun `quarter turns are bounded to 0 to 3`() {
        for (q in 0..3) PhotoGeometry(quarterTurns = q)
        assertFailsWith<IllegalArgumentException> { PhotoGeometry(quarterTurns = -1) }
        assertFailsWith<IllegalArgumentException> { PhotoGeometry(quarterTurns = 4) }
    }

    @Test
    fun `PhotoEdits carries colour and geometry`() {
        val edits = PhotoEdits(
            color = ColorAdjustments(exposureStops = 1f),
            geometry = PhotoGeometry(quarterTurns = 1)
        )
        assertEquals(1f, edits.color.exposureStops)
        assertEquals(1, edits.geometry.quarterTurns)
        assertEquals(PhotoEdits(), PhotoEdits())
    }
}
