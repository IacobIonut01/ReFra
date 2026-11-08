package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange

/**
 * One placement of an [MediaAsset.Audio] source on an [AudioTrack]:
 * [timelineStart] is where the clip lands in sequence time, [sourceRange] is
 * the media range it plays back. The clip's timeline duration equals its
 * source range duration; [timelineRange] is computed eagerly at construction
 * so an overflowing placement throws here.
 *
 * The mix recipe: [gainDb] multiplies with the owning track's gain,
 * [pan] is an equal-power stereo position in [-1, 1] combined with the
 * track's pan (clamped), [muted] excludes the clip's contribution entirely,
 * [fadeIn]/[fadeOut] are linear amplitude ramps over their exact sample
 * windows, and [routing] applies independent per-channel gains after pan.
 */
data class AudioClip(
    val id: String,
    val sourceId: String,
    val timelineStart: EditorTime,
    val sourceRange: TimeRange,
    val gainDb: Float = 0f,
    val pan: Float = 0f,
    val muted: Boolean = false,
    val fadeIn: EditorTime = EditorTime.Zero,
    val fadeOut: EditorTime = EditorTime.Zero,
    val routing: AudioRouting = AudioRouting()
) {
    val timelineRange: TimeRange = TimeRange(timelineStart, sourceRange.duration)

    init {
        require(id.isNotBlank()) { "audio clip id must be non-blank" }
        require(sourceId.isNotBlank()) { "audio clip sourceId must be non-blank" }
        require(timelineStart.ticks >= 0L) {
            "timelineStart must be non-negative: ${timelineStart.ticks}"
        }
        AudioRouting.validateGain(gainDb, "gainDb")
        require(pan.isFinite() && pan in -1f..1f) {
            "pan must be in [-1, 1]: $pan"
        }
        require(fadeIn.ticks >= 0L && fadeIn <= sourceRange.duration) {
            "fadeIn must be non-negative and within the clip: ${fadeIn.ticks}"
        }
        require(fadeOut.ticks >= 0L && fadeOut <= sourceRange.duration) {
            "fadeOut must be non-negative and within the clip: ${fadeOut.ticks}"
        }
    }
}
