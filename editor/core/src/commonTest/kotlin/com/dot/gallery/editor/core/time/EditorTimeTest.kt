package com.dot.gallery.editor.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EditorTimeTest {

    @Test
    fun `tick base is exact`() {
        assertEquals(705_600_000L, EDITOR_TICKS_PER_SECOND)
    }

    @Test
    fun `comparison follows ticks`() {
        assertTrue(EditorTime(1) > EditorTime.Zero)
        assertTrue(EditorTime(0) == EditorTime.Zero)
        assertTrue(EditorTime(-5) < EditorTime.Zero)
    }

    @Test
    fun `plus and minus are checked`() {
        assertEquals(EditorTime(30), EditorTime(10) + EditorTime(20))
        assertEquals(EditorTime(-10), EditorTime(10) - EditorTime(20))
        assertFailsWith<ArithmeticException> {
            EditorTime(Long.MAX_VALUE) + EditorTime(1)
        }
        assertFailsWith<ArithmeticException> {
            EditorTime(Long.MIN_VALUE) - EditorTime(1)
        }
    }

    @Test
    fun `fromMilliseconds converts exactly and is checked`() {
        assertEquals(EditorTime(705_600_000L), EditorTime.fromMilliseconds(1_000L))
        assertEquals(EditorTime(705_600L), EditorTime.fromMilliseconds(1L))
        assertEquals(EditorTime.Zero, EditorTime.fromMilliseconds(0L))
        assertFailsWith<ArithmeticException> {
            EditorTime.fromMilliseconds(Long.MAX_VALUE)
        }
    }

    @Test
    fun `fromMicroseconds converts with sub-tick floor and rejects negative`() {
        assertEquals(EditorTime(705_600_000L), EditorTime.fromMicroseconds(1_000_000L))
        // 705.6 ticks per microsecond: floor applies to the remainder.
        assertEquals(EditorTime(705L), EditorTime.fromMicroseconds(1L))
        assertEquals(EditorTime(705_599_294L), EditorTime.fromMicroseconds(999_999L))
        assertEquals(EditorTime.Zero, EditorTime.fromMicroseconds(0L))
        // A large-but-safe value: 24h = 86_400_000_000us.
        assertEquals(
            EditorTime(60_963_840_000_000L),
            EditorTime.fromMicroseconds(86_400_000_000L)
        )
        assertFailsWith<IllegalArgumentException> {
            EditorTime.fromMicroseconds(-1L)
        }
        assertFailsWith<ArithmeticException> {
            EditorTime.fromMicroseconds(Long.MAX_VALUE)
        }
    }

    @Test
    fun `toMicrosecondsFloor rounds down and is checked`() {
        assertEquals(1_000_000L, EditorTime(705_600_000L).toMicrosecondsFloor())
        // 705 ticks is ~0.999us — still whole zero.
        assertEquals(0L, EditorTime(705L).toMicrosecondsFloor())
        // 706 ticks is ~1.0006us — floors to one whole microsecond.
        assertEquals(1L, EditorTime(706L).toMicrosecondsFloor())
        assertEquals(0L, EditorTime.Zero.toMicrosecondsFloor())
        assertFailsWith<IllegalArgumentException> {
            EditorTime(-1).toMicrosecondsFloor()
        }
        // Ticks-to-microseconds is a shrinking conversion (~705.6 ticks/us),
        // so even Long.MAX_VALUE ticks is checked arithmetic without
        // overflow — pin the exact quotient.
        assertEquals(
            13_071_672_387_832_732L,
            EditorTime(Long.MAX_VALUE).toMicrosecondsFloor()
        )
    }

    @Test
    fun `samplesAtOrAfter ceils exactly`() {
        // One second at 48 kHz.
        assertEquals(48_000L, EditorTime(705_600_000L).samplesAtOrAfter(48_000))
        // One 30 fps frame = 23_520_000 ticks = exactly 1600 samples.
        assertEquals(1_600L, EditorTime(23_520_000L).samplesAtOrAfter(48_000))
        // One tick past the frame boundary spills into the next sample.
        assertEquals(1_601L, EditorTime(23_520_001L).samplesAtOrAfter(48_000))
        // One NTSC frame = 23_523_520 ticks: 1600.16 samples -> ceil 1601.
        assertEquals(1_601L, EditorTime(23_523_520L).samplesAtOrAfter(48_000))
        assertEquals(0L, EditorTime.Zero.samplesAtOrAfter(48_000))
        assertFailsWith<IllegalArgumentException> {
            EditorTime(1).samplesAtOrAfter(0)
        }
        assertFailsWith<IllegalArgumentException> {
            EditorTime(-1).samplesAtOrAfter(48_000)
        }
    }
}
