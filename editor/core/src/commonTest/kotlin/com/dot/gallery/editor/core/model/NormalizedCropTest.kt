package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NormalizedCropTest {

    @Test
    fun `default crop is the whole frame`() {
        val crop = NormalizedCrop()
        assertEquals(0.0, crop.left)
        assertEquals(1.0, crop.right)
    }

    @Test
    fun `rejects out-of-range inverted and non-finite values`() {
        assertFailsWith<IllegalArgumentException> { NormalizedCrop(left = -0.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedCrop(right = 1.1) }
        assertFailsWith<IllegalArgumentException> {
            NormalizedCrop(left = 0.5, right = 0.5)
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedCrop(left = 0.8, right = 0.2)
        }
        assertFailsWith<IllegalArgumentException> { NormalizedCrop(top = 0.9, bottom = 0.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedCrop(left = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { NormalizedCrop(right = Double.POSITIVE_INFINITY) }
        // Edge values are legal.
        NormalizedCrop(0.0, 0.0, 1.0, 1.0)
        NormalizedCrop(0.999999, 0.0, 1.0, 1.0)
    }
}
