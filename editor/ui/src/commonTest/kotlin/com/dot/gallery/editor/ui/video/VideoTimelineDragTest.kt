package com.dot.gallery.editor.ui.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoTimelineDragTest {

    // Three 40 px lanes 10 px apart: an overlay, two audio lanes, then the
    // new-track row. One tick is one pixel.
    private val slots = listOf(
        TimelineLaneSlot(0f, 40f, "v2", LaneDropKind.Overlay, false, emptyList()),
        TimelineLaneSlot(
            50f, 40f, "a1", LaneDropKind.Audio, false,
            listOf(Triple("c1", 0L, 100L))
        ),
        TimelineLaneSlot(
            100f, 40f, "a2", LaneDropKind.Audio, false,
            listOf(Triple("c2", 150L, 250L))
        ),
        TimelineLaneSlot(150f, 30f, null, LaneDropKind.Audio, false, emptyList())
    )

    private fun drag(dx: Float, dy: Float, snapPx: Float = 0f, frame: Long = 1L) =
        ClipLaneDrag(
            clipId = "c1", kind = LaneDropKind.Audio, originSlot = 1,
            originStartTicks = 0L, durationTicks = 100L, pixelsPerTick = 1f,
            ticksPerFrame = frame, snapPx = snapPx
        ).apply {
            this.dx = dx
            this.dy = dy
        }

    @Test
    fun `a drag lands on the lane under the clip centre`() {
        val target = resolveClipDrop(drag(dx = 20f, dy = 50f), slots)!!
        assertEquals("a2", target.trackId)
        assertEquals(20L, target.startTicks)
        assertTrue(target.valid)
        assertTrue(target.changed)
    }

    @Test
    fun `an overlap or a lane of another kind refuses the drop`() {
        assertFalse(resolveClipDrop(drag(dx = 100f, dy = 50f), slots)!!.valid)
        val overlay = resolveClipDrop(drag(dx = 0f, dy = -50f), slots)!!
        assertEquals("v2", overlay.trackId)
        assertFalse(overlay.valid)
    }

    @Test
    fun `below the last lane is the new track row`() {
        val target = resolveClipDrop(drag(dx = 0f, dy = 400f), slots)!!
        assertNull(target.trackId)
        assertTrue(target.valid)
    }

    @Test
    fun `starts snap to frames, neighbour edges and never go negative`() {
        assertEquals(30L, resolveClipDrop(drag(dx = 32f, dy = 0f, frame = 10L), slots)!!.startTicks)
        assertEquals(0L, resolveClipDrop(drag(dx = -80f, dy = 0f), slots)!!.startTicks)
        // 54 ticks is within 10 px of c2's start minus the clip length (50).
        val magnet = resolveClipDrop(drag(dx = 54f, dy = 50f, snapPx = 10f), slots)!!
        assertEquals(50L, magnet.startTicks)
        assertTrue(magnet.valid)
    }

    @Test
    fun `dropping where it started changes nothing`() {
        val target = resolveClipDrop(drag(dx = 0f, dy = 3f), slots)!!
        assertTrue(target.valid)
        assertFalse(target.changed)
    }
}
