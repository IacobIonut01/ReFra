package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.collections.immutable.persistentListOf

class VideoClipTest {

    private fun clip(
        id: String = "c1",
        timelineStart: Long = 0,
        sourceStart: Long = 0,
        duration: Long = 10
    ) = VideoClip(
        id = id,
        sourceId = "src",
        timelineStart = EditorTime(timelineStart),
        sourceRange = TimeRange(EditorTime(sourceStart), EditorTime(duration))
    )

    @Test
    fun `timelineRange mirrors source duration at the placement`() {
        val clip = clip(timelineStart = 100, sourceStart = 40, duration = 10)
        assertEquals(TimeRange(EditorTime(100), EditorTime(10)), clip.timelineRange)
    }

    @Test
    fun `blank ids and negative placement rejected`() {
        assertFailsWith<IllegalArgumentException> { clip(id = " ") }
        assertFailsWith<IllegalArgumentException> { clip(timelineStart = -1) }
        assertFailsWith<IllegalArgumentException> {
            VideoClip(
                id = "c",
                sourceId = " ",
                timelineStart = EditorTime.Zero,
                sourceRange = TimeRange(EditorTime.Zero, EditorTime(1))
            )
        }
    }

    @Test
    fun `overflowing timeline placement rejected at construction`() {
        // timelineStart MAX_VALUE + duration 1 overflows endExclusive; the
        // clip must reject at construction, not when timelineRange is read.
        // Overflow uses the time contract's ArithmeticException.
        assertFailsWith<ArithmeticException> {
            clip(timelineStart = Long.MAX_VALUE, duration = 1)
        }
        // The same rejection applies when the overflowing clip is built as
        // part of a single-track sequence: the clip constructor throws before
        // VideoSequence validation is even reached.
        assertFailsWith<ArithmeticException> {
            val bad = clip(timelineStart = Long.MAX_VALUE - 5, duration = 10)
            VideoSequence(
                size = PixelSize(16, 16),
                frameRate = FrameRate(30),
                sources = persistentListOf(
                    MediaAsset.Video(
                        id = "src",
                        reference = "content://src",
                        size = PixelSize(16, 16),
                        duration = EditorTime(1_000_000),
                        frameRate = FrameRate(30),
                        hasAudio = false
                    )
                ),
                tracks = persistentListOf(VideoTrack("t", persistentListOf(bad)))
            )
        }
    }
}
