package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixelRectTest {

    @Test
    fun `valid rect exposes exclusive bounds`() {
        val rect = PixelRect(2, 3, 4, 5)
        assertEquals(6, rect.rightExclusive)
        assertEquals(8, rect.bottomExclusive)
        assertTrue(Pair(5, 7) in rect)
        assertFalse(Pair(6, 7) in rect)
        assertFalse(Pair(5, 8) in rect)
    }

    @Test
    fun `rejects negative origin non-positive size and overflow`() {
        assertFailsWith<IllegalArgumentException> { PixelRect(-1, 0, 1, 1) }
        assertFailsWith<IllegalArgumentException> { PixelRect(0, -1, 1, 1) }
        assertFailsWith<IllegalArgumentException> { PixelRect(0, 0, 0, 1) }
        assertFailsWith<IllegalArgumentException> { PixelRect(0, 0, 1, 0) }
        assertFailsWith<IllegalArgumentException> {
            PixelRect(Int.MAX_VALUE, 0, 2, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            PixelRect(0, 1, 1, Int.MAX_VALUE)
        }
        // The largest legal extents still construct.
        PixelRect(0, 0, Int.MAX_VALUE, 1)
        PixelRect(5, 0, Int.MAX_VALUE - 5, 1)
    }

    @Test
    fun `isInside checks containment in a PixelSize`() {
        val size = PixelSize(4, 3)
        assertTrue(PixelRect(0, 0, 4, 3).isInside(size))
        assertTrue(PixelRect(1, 1, 2, 1).isInside(size))
        assertFalse(PixelRect(2, 0, 3, 1).isInside(size))
        assertFalse(PixelRect(0, 2, 1, 2).isInside(size))
    }
}
