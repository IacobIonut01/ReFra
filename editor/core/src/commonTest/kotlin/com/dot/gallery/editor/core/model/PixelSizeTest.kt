package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PixelSizeTest {

    @Test
    fun `positive dimensions and long pixel count`() {
        val size = PixelSize(1920, 1080)
        assertEquals(2_073_600L, size.pixelCount)
        assertEquals(Int.MAX_VALUE.toLong() * 2L, PixelSize(Int.MAX_VALUE, 2).pixelCount)
    }

    @Test
    fun `non-positive dimensions rejected`() {
        assertFailsWith<IllegalArgumentException> { PixelSize(0, 1) }
        assertFailsWith<IllegalArgumentException> { PixelSize(1, 0) }
        assertFailsWith<IllegalArgumentException> { PixelSize(-1, 1) }
    }
}
