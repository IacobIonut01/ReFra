package com.dot.gallery.editor.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FrameRateTest {

    @Test
    fun `integer frame rates are exact`() {
        assertEquals(29_400_000L, FrameRate(24).ticksPerFrame)
        assertEquals(28_224_000L, FrameRate(25).ticksPerFrame)
        assertEquals(23_520_000L, FrameRate(30).ticksPerFrame)
        assertEquals(11_760_000L, FrameRate(60).ticksPerFrame)
    }

    @Test
    fun `NTSC frame rates are exact`() {
        assertEquals(29_429_400L, FrameRate(24_000, 1_001).ticksPerFrame)
        assertEquals(23_543_520L, FrameRate(30_000, 1_001).ticksPerFrame)
        assertEquals(11_771_760L, FrameRate(60_000, 1_001).ticksPerFrame)
    }

    @Test
    fun `invalid and unrepresentable rates are rejected`() {
        assertFailsWith<IllegalArgumentException> { FrameRate(0) }
        assertFailsWith<IllegalArgumentException> { FrameRate(-24) }
        assertFailsWith<IllegalArgumentException> { FrameRate(24, 0) }
        assertFailsWith<IllegalArgumentException> { FrameRate(24, -1) }
        // 705600000 = 2^9 * 3^2 * 5^5 * 7^2 — primes outside that set cannot
        // divide it, so 11 fps is not an exact number of ticks.
        assertFailsWith<IllegalArgumentException> { FrameRate(11) }
        assertFailsWith<IllegalArgumentException> { FrameRate(1_001) }
    }

    @Test
    fun `timeAtFrame computes the frame start`() {
        val rate = FrameRate(30)
        assertEquals(EditorTime.Zero, rate.timeAtFrame(0))
        assertEquals(EditorTime(23_520_000L), rate.timeAtFrame(1))
        assertEquals(EditorTime(47_040_000L), rate.timeAtFrame(2))
    }

    @Test
    fun `frameAtOrBefore is half-open`() {
        val rate = FrameRate(30)
        val firstFrameStart = rate.timeAtFrame(1)
        assertEquals(0L, rate.frameAtOrBefore(EditorTime.Zero))
        assertEquals(0L, rate.frameAtOrBefore(firstFrameStart - EditorTime(1)))
        assertEquals(1L, rate.frameAtOrBefore(firstFrameStart))
    }

    @Test
    fun `hour-long index does not drift`() {
        val rate = FrameRate(30_000, 1_001)
        val hour = EditorTime(3_600L * EDITOR_TICKS_PER_SECOND)
        // 3600 s at 30000/1001 fps covers 107892 full frames with a remainder.
        assertEquals(107_892L, rate.frameAtOrBefore(hour))
        val frameStart = rate.timeAtFrame(107_892L)
        assertTrue(frameStart < hour)
        assertEquals(107_892L, rate.frameAtOrBefore(frameStart))
        assertEquals(frameStart, rate.timeAtFrame(rate.frameAtOrBefore(frameStart)))
    }

    @Test
    fun `negative frame and time are rejected`() {
        val rate = FrameRate(24)
        assertFailsWith<IllegalArgumentException> { rate.timeAtFrame(-1) }
        assertFailsWith<IllegalArgumentException> { rate.frameAtOrBefore(EditorTime(-1)) }
    }

    @Test
    fun `timeAtFrame multiplication is checked`() {
        val rate = FrameRate(24)
        assertFailsWith<ArithmeticException> {
            rate.timeAtFrame(Long.MAX_VALUE / rate.ticksPerFrame + 1)
        }
    }

    @Test
    fun `equivalent rates written differently are not equal`() {
        // Documented field-based equality: same real rate, different objects.
        assertNotEquals(FrameRate(30_000, 1_001), FrameRate(60_000, 2_002))
        assertEquals(
            FrameRate(30_000, 1_001).ticksPerFrame,
            FrameRate(60_000, 2_002).ticksPerFrame
        )
    }
}
