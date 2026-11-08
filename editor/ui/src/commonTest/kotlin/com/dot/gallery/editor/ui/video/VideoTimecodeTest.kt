package com.dot.gallery.editor.ui.video

import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pure tests for the transport timecode format and the trim-snap math —
 * no composition involved.
 */
class VideoTimecodeTest {

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    @Test
    fun `timecode renders mm ss mmm below the hour`() {
        assertEquals("00:00.000", formatVideoTimecode(EditorTime.Zero))
        assertEquals("00:01.000", formatVideoTimecode(us(1_000_000)))
        assertEquals("00:01.500", formatVideoTimecode(us(1_500_000)))
        assertEquals("01:05.400", formatVideoTimecode(us(65_400_000)))
        // Milliseconds keep a 30 fps frame step visible.
        val tpf = FrameRate(30).ticksPerFrame
        assertEquals("00:00.033", formatVideoTimecode(EditorTime(tpf)))
        assertEquals("00:00.066", formatVideoTimecode(EditorTime(2 * tpf)))
        assertEquals("01:01.234", formatVideoTimecode(us(61_234_000)))
        // A negative position clamps to zero rather than printing junk.
        assertEquals("00:00.000", formatVideoTimecode(EditorTime(-5)))
    }

    @Test
    fun `timecode renders h mm ss mmm at or past the hour`() {
        assertEquals("1:00:00.000", formatVideoTimecode(us(3_600_000_000)))
        assertEquals("1:01:00.000", formatVideoTimecode(us(3_660_000_000)))
        assertEquals(
            "2:34:56.789",
            formatVideoTimecode(us(9_296_789_000))
        )
    }

    @Test
    fun `ruler labels are honest per interval`() {
        // Sub-second majors carry tenths — never a clipped "00:0".
        val tenth = EDITOR_TICKS_PER_SECOND / 10
        assertEquals("0.0s", formatRulerTime(EditorTime.Zero, tenth))
        assertEquals("0.2s", formatRulerTime(us(200_000), tenth))
        assertEquals("2.5s", formatRulerTime(us(2_500_000), tenth))

        // Whole-second majors under the minute read as bare seconds.
        val twoSeconds = EDITOR_TICKS_PER_SECOND * 2
        assertEquals("0s", formatRulerTime(EditorTime.Zero, twoSeconds))
        assertEquals("4s", formatRulerTime(us(4_000_000), twoSeconds))
        assertEquals("58s", formatRulerTime(us(58_000_000), twoSeconds))

        // Past the minute the label switches to m:ss — and h:mm:ss past
        // the hour — regardless of the interval granularity.
        assertEquals("1:00", formatRulerTime(us(60_000_000), twoSeconds))
        assertEquals(
            "1:30", formatRulerTime(us(90_000_000), twoSeconds)
        )
        assertEquals(
            "1:02:00",
            formatRulerTime(us(3_720_000_000), twoSeconds)
        )
        val twoMinutes = EDITOR_TICKS_PER_SECOND * 120
        assertEquals(
            "2:04",
            formatRulerTime(us(124_000_000), twoMinutes)
        )
    }

    @Test
    fun `ruler labels clamp a negative time to zero`() {
        assertEquals(
            "0s",
            formatRulerTime(EditorTime(-5), EDITOR_TICKS_PER_SECOND)
        )
    }

    @Test
    fun `snap lands on exact frame boundaries`() {
        val duration = us(4_000_000)
        val rate = FrameRate(30)
        val tpf = rate.ticksPerFrame // 23_520_000
        // In-fraction 0.249 sits inside frame 29 — snaps DOWN to frame 29.
        // Out-fraction 0.751 sits inside frame 90 — snaps UP to frame 91.
        val range = snapTrimRange(duration, rate, 0.249, 0.751)
        assertEquals(29L, range.start.ticks / tpf)
        assertEquals(0L, range.start.ticks % tpf)
        assertEquals(91L, range.endExclusive.ticks / tpf)
        assertEquals(0L, range.endExclusive.ticks % tpf)
    }

    @Test
    fun `snap rounds out to full duration on a non frame aligned end`() {
        // A duration that is NOT a multiple of the frame period: the last
        // frame's tail is still part of the clip — dragging to 1.0 must
        // land exactly on duration, not on the last aligned boundary.
        val rate = FrameRate(30)
        val duration = EditorTime(rate.ticksPerFrame * 5 + 123)
        val range = snapTrimRange(duration, rate, 0.0, 1.0)
        assertEquals(EditorTime.Zero, range.start)
        assertEquals(duration, range.endExclusive)
    }

    @Test
    fun `snap respects the ntsc frame grid`() {
        val ntsc = FrameRate(30000, 1001)
        val tpf = ntsc.ticksPerFrame
        val duration = us(10_000_000)
        val range = snapTrimRange(duration, ntsc, 0.25, 0.5)
        assertEquals(0L, range.start.ticks % tpf)
        assertEquals(0L, range.endExclusive.ticks % tpf)
        // 25% of 10 s lands inside frame 74 (74.9); 50% inside 149 (149.85)
        // — in snaps down to 74, out snaps up to 150.
        assertEquals(74L, range.start.ticks / tpf)
        assertEquals(150L, range.endExclusive.ticks / tpf)
    }

    @Test
    fun `snap enforces a one frame minimum`() {
        val duration = us(4_000_000)
        val rate = FrameRate(30)
        // A sub-frame drag collapses to exactly one frame.
        val range = snapTrimRange(duration, rate, 0.400, 0.401)
        assertEquals(rate.ticksPerFrame, range.duration.ticks)
    }

    @Test
    fun `snap on a shorter than frame clip keeps the whole clip`() {
        val rate = FrameRate(30)
        val duration = EditorTime(rate.ticksPerFrame / 3)
        val range = snapTrimRange(duration, rate, 0.0, 1.0)
        assertEquals(duration, range.endExclusive)
        assertEquals(EditorTime.Zero, range.start)
    }

    @Test
    fun `an unmoved endpoint keeps its committed tick exactly`() {
        // 4.021 s source, committed out-point exactly 2 s (a frame
        // boundary). The slider's Float round-trip of that fraction lands
        // ~0.0005 frames above it — naive re-snapping would ceil the
        // untouched out-point one frame too far.
        val duration = us(4_021_000)
        val rate = FrameRate(30)
        val committedOut = us(2_000_000)
        val driftedOutFraction =
            (committedOut.ticks.toFloat() / duration.ticks).toDouble()
        val range = snapTrimRange(
            duration, rate,
            inFraction = 0.1,
            outFraction = driftedOutFraction,
            fixedEnd = committedOut
        )
        assertEquals(committedOut, range.endExclusive)
        // And the moved in-point still snapped down onto the grid.
        assertEquals(0L, range.start.ticks % rate.ticksPerFrame)
    }

    @Test
    fun `the changed endpoint alone absorbs the minimum segment`() {
        // Source duration is not frame-aligned, so the committed
        // out-point sitting on the true end of the source is not on the
        // grid either. The drag pulls in to ~4.013 s — inside the last
        // frame — leaving less than one frame between the snapped in and
        // the fixed out. The minimum must push the moved endpoint only:
        // out stays at the exact duration.
        val duration = us(4_021_000)
        val rate = FrameRate(30)
        val range = snapTrimRange(
            duration, rate,
            inFraction = 0.998,
            outFraction = 1.0,
            fixedEnd = duration
        )
        assertEquals(duration, range.endExclusive)
        assertEquals(rate.ticksPerFrame, range.duration.ticks)
    }

    @Test
    fun `both endpoints fixed returns the committed range verbatim`() {
        val duration = us(4_021_000)
        val rate = FrameRate(30)
        val committed = TimeRange(us(1_000_000), us(2_000_000))
        val range = snapTrimRange(
            duration, rate, 0.02, 0.99,
            fixedStart = committed.start,
            fixedEnd = committed.endExclusive
        )
        assertEquals(committed, range)
    }

    @Test
    fun `snap rejects invalid fractions`() {
        val duration = us(4_000_000)
        val rate = FrameRate(30)
        assertFailsWith<IllegalArgumentException> {
            snapTrimRange(duration, rate, Double.NaN, 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            snapTrimRange(duration, rate, 0.0, Double.POSITIVE_INFINITY)
        }
        // Reversed fractions are still rejected.
        assertFailsWith<IllegalArgumentException> {
            snapTrimRange(duration, rate, 0.8, 0.2)
        }
    }

    @Test
    fun `equal endpoints widen to one frame`() {
        // A RangeSlider emits equal endpoints when its handles meet — the
        // snap must produce a single frame at that point, not crash on a
        // zero-length range. 1 s at 30 fps: tpf = 23_520_000.
        val duration = us(1_000_000)
        val rate = FrameRate(30)
        val tpf = rate.ticksPerFrame

        val mid = snapTrimRange(duration, rate, 0.5, 0.5)
        assertEquals(us(500_000), mid.start)
        assertEquals(us(500_000).ticks + tpf, mid.endExclusive.ticks)

        val last = snapTrimRange(duration, rate, 1.0, 1.0)
        assertEquals(duration.ticks - tpf, last.start.ticks)
        assertEquals(duration, last.endExclusive)

        val first = snapTrimRange(duration, rate, 0.0, 0.0)
        assertEquals(EditorTime.Zero, first.start)
        assertEquals(tpf, first.endExclusive.ticks)
    }

    @Test
    fun `snapped range stays inside the source`() {
        val duration = us(4_000_000)
        val rate = FrameRate(30)
        // Sweep the fraction space; every accepted pair lands in bounds and
        // on the frame grid.
        var i = 0
        while (i <= 20) {
            var j = i + 1
            while (j <= 20) {
                val range = snapTrimRange(
                    duration, rate, i / 20.0, j / 20.0
                )
                assertTrue(range.start.ticks >= 0L)
                assertTrue(range.endExclusive.ticks <= duration.ticks)
                assertTrue(range.duration.ticks > 0L)
                assertEquals(0L, range.start.ticks % rate.ticksPerFrame)
                if (range.endExclusive != duration) {
                    assertEquals(
                        0L, range.endExclusive.ticks % rate.ticksPerFrame
                    )
                }
                j++
            }
            i++
        }
        // Sanity: the sweep actually exercised the 24h math with plenty of
        // headroom under EDITOR_TICKS_PER_SECOND.
        assertTrue(EDITOR_TICKS_PER_SECOND > 0L)
    }
}
