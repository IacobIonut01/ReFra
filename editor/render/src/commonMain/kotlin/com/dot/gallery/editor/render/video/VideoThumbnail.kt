package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.SrgbPixels

/**
 * One decoded filmstrip frame for a clip-strip cell. [clipId] names the
 * timeline segment it belongs to and [sourceTime] the source instant it was
 * decoded at — a trim that moves a clip's sampled instants invalidates
 * exactly those entries, and an edit that removes the clip drops it with
 * the id.
 */
data class VideoThumbnail(
    val clipId: String,
    val sourceTime: EditorTime,
    val pixels: SrgbPixels
) {
    companion object {
        /** Filmstrip cells per clip: 1/8, 3/8, 5/8 and 7/8 of the range. */
        const val SAMPLES_PER_CLIP = 4

        /**
         * The [SAMPLES_PER_CLIP] source instants sampled for a clip's strip
         * cells — exact integer eighths of [sourceRange], so a sample can
         * never land on `endExclusive`. Shared by the session's decoder and
         * the strip's cell layout so both agree on slot positions.
         */
        fun sampleTimes(sourceRange: TimeRange): List<EditorTime> {
            val start = sourceRange.start
            val durationTicks = sourceRange.duration.ticks
            // Real source durations are bounded far below Long overflow;
            // the plus() below is checked regardless.
            return listOf(1L, 3L, 5L, 7L).map { eighth ->
                start + EditorTime(durationTicks * eighth / 8L)
            }
        }
    }
}
