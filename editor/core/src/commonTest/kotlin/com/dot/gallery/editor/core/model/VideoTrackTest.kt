package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.collections.immutable.persistentListOf

class VideoTrackTest {

    private fun clip(id: String, start: Long, duration: Long) = VideoClip(
        id = id,
        sourceId = "src",
        timelineStart = EditorTime(start),
        sourceRange = TimeRange(EditorTime.Zero, EditorTime(duration))
    )

    @Test
    fun `sorted non-overlapping clips with gaps are accepted`() {
        VideoTrack("t", persistentListOf(clip("a", 0, 10), clip("b", 10, 5), clip("c", 30, 5)))
    }

    @Test
    fun `duplicate clip ids rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VideoTrack("t", persistentListOf(clip("a", 0, 10), clip("a", 20, 5)))
        }
    }

    @Test
    fun `overlapping and unsorted clips rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VideoTrack("t", persistentListOf(clip("a", 0, 10), clip("b", 5, 5)))
        }
        assertFailsWith<IllegalArgumentException> {
            VideoTrack("t", persistentListOf(clip("a", 20, 5), clip("b", 0, 5)))
        }
    }

    @Test
    fun `blank track id rejected`() {
        assertFailsWith<IllegalArgumentException> { VideoTrack("") }
    }
}
