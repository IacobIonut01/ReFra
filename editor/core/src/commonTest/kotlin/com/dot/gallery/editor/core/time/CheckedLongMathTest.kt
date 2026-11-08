package com.dot.gallery.editor.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CheckedLongMathTest {

    @Test
    fun `add detects positive and negative overflow`() {
        assertEquals(3L, checkedAdd(1L, 2L))
        assertFailsWith<ArithmeticException> { checkedAdd(Long.MAX_VALUE, 1L) }
        assertFailsWith<ArithmeticException> { checkedAdd(Long.MIN_VALUE, -1L) }
    }

    @Test
    fun `subtract detects overflow`() {
        assertEquals(-1L, checkedSubtract(1L, 2L))
        assertFailsWith<ArithmeticException> { checkedSubtract(Long.MIN_VALUE, 1L) }
        assertFailsWith<ArithmeticException> { checkedSubtract(Long.MAX_VALUE, -1L) }
    }

    @Test
    fun `multiply detects overflow including MIN times -1`() {
        assertEquals(6L, checkedMultiply(2L, 3L))
        assertEquals(0L, checkedMultiply(Long.MIN_VALUE, 0L))
        assertEquals(Long.MIN_VALUE, checkedMultiply(Long.MIN_VALUE, 1L))
        assertFailsWith<ArithmeticException> { checkedMultiply(Long.MIN_VALUE, -1L) }
        assertFailsWith<ArithmeticException> { checkedMultiply(-1L, Long.MIN_VALUE) }
        assertFailsWith<ArithmeticException> { checkedMultiply(Long.MIN_VALUE, 2L) }
        assertFailsWith<ArithmeticException> { checkedMultiply(Long.MAX_VALUE, Long.MAX_VALUE) }
    }
}
