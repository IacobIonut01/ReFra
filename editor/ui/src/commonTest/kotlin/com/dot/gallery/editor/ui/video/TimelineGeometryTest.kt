package com.dot.gallery.editor.ui.video

import com.dot.gallery.editor.core.time.EditorTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pure tests for the shared timeline time<->pixel mapping — the same
 * instance drives the ruler ticks, clip widths, the playhead and scrub
 * hit-testing, so the round-trip and linearity properties are what keep
 * every layer drawing the same time origin.
 */
class TimelineGeometryTest {

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    private val fourSeconds = us(4_000_000)

    @Test
    fun `time and pixel round trip`() {
        // Zoom 1 (content == viewport), no scroll.
        val fit = TimelineGeometry(
            totalTicks = fourSeconds.ticks,
            contentWidthPx = 1000f,
            scrollPx = 0f
        )
        assertEquals(250f, fit.timeToX(us(1_000_000)), 0.01f)
        assertEquals(us(1_000_000), fit.xToTime(250f))
        assertEquals(fourSeconds, fit.xToTime(1000f))

        // Zoom 4 (content 4x viewport) with the viewport scrolled 500px
        // into the content: local x adds the scroll offset back.
        val zoomed = TimelineGeometry(
            totalTicks = fourSeconds.ticks,
            contentWidthPx = 4000f,
            scrollPx = 500f
        )
        assertEquals(2000f, zoomed.timeToX(us(2_000_000)), 0.01f)
        // localX 1500 + scroll 500 = contentX 2000 -> 2 s.
        assertEquals(us(2_000_000), zoomed.xToTime(1500f))

        // Inverse holds across the range at both scales.
        for (t in 0L..4_000_000L step 500_000L) {
            val time = us(t)
            assertEquals(time, fit.xToTime(fit.timeToX(time)))
            assertEquals(
                time,
                zoomed.xToTime(zoomed.timeToX(time) - 500f)
            )
        }
    }

    @Test
    fun `clip widths stay linear in duration`() {
        // A 1 s clip in a 4 s timeline is exactly a quarter of the
        // content; a 3 s clip is three quarters — a 1:3 ratio, no
        // minimum-width warping.
        val g = TimelineGeometry(
            totalTicks = fourSeconds.ticks,
            contentWidthPx = 800f,
            scrollPx = 0f
        )
        val oneSecondWidth = g.timeToX(us(1_000_000)) - g.timeToX(us(0))
        val threeSecondWidth = g.timeToX(fourSeconds) -
            g.timeToX(us(1_000_000))
        assertEquals(200f, oneSecondWidth, 0.01f)
        assertEquals(600f, threeSecondWidth, 0.01f)
        assertEquals(1f / 3f, oneSecondWidth / threeSecondWidth, 0.001f)
    }

    @Test
    fun `hit testing clamps to the sequence bounds`() {
        val g = TimelineGeometry(
            totalTicks = fourSeconds.ticks,
            contentWidthPx = 1000f,
            scrollPx = 100f
        )
        assertEquals(EditorTime.Zero, g.xToTime(-200f))
        assertEquals(fourSeconds, g.xToTime(10_000f))
    }

    @Test
    fun `degenerate geometry is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TimelineGeometry(0L, 1000f, 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            TimelineGeometry(fourSeconds.ticks, 0f, 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            TimelineGeometry(fourSeconds.ticks, Float.NaN, 0f)
        }
    }
}
