package com.dot.gallery.editor.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangeTest {

    @Test
    fun `half-open bounds and endExclusive`() {
        val range = TimeRange(EditorTime(10), EditorTime(5))
        assertEquals(EditorTime(15), range.endExclusive)
        assertTrue(EditorTime(10) in range)
        assertTrue(EditorTime(14) in range)
        assertFalse(EditorTime(15) in range)
        assertFalse(EditorTime(9) in range)
    }

    @Test
    fun `invalid ranges are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TimeRange(EditorTime(0), EditorTime.Zero)
        }
        assertFailsWith<IllegalArgumentException> {
            TimeRange(EditorTime(0), EditorTime(-1))
        }
        assertFailsWith<IllegalArgumentException> {
            TimeRange(EditorTime(-1), EditorTime(1))
        }
    }

    @Test
    fun `end computation is checked`() {
        assertFailsWith<ArithmeticException> {
            TimeRange(EditorTime(Long.MAX_VALUE), EditorTime(1))
        }
    }
}
