package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.UnsupportedVideoException

/**
 * The validated multi-source/multi-track shape shared by the composited
 * export and preview-playback paths — everything outside
 * [SingleSourcePlan]'s one-source/one-track identity-recipe shape.
 *
 * Video at output instant `t` is the ordered
 * [VideoSequence.activeVisualClips] set decoded per clip on its own source
 * clock and stacked through the shared compositor — this plan carries no
 * pixel work itself, only the validated lookup. Sound is the sibling
 * [CompositeAudioPlan]'s contract: the Main lane's embedded audio mixes at
 * unity plus every audible audio lane through the shared mixer — overlay
 * lanes' embedded audio is ignored, never silently mixed.
 *
 * Validation rejects what cannot be honoured: no sources, a
 * [VideoTrackKind.Title] lane (title rendering does not exist yet — an
 * export must not drop it silently), an empty or >24 h duration, or more
 * than [MAX_SOURCES] sources.
 */
class CompositeVideoPlan(
    val sequence: VideoSequence,
    val mainClips: List<VideoClip>
) {

    /**
     * Distinct source ids that need a PCM reader — the audible inputs of
     * [CompositeAudioPlan]: Main-lane sources unless the sequence's
     * source sound is muted, plus unmuted audio-lane clip sources.
     */
    val audioSourceIds: Set<String>
        get() = CompositeAudioPlan.audioSourceIds(sequence)

    companion object {

        /** Import bound shared with the session's add path. */
        const val MAX_SOURCES = 64

        /** First-scope cap: 24 h keeps every tick/sample product inside Long. */
        private val MAX_DURATION: EditorTime =
            EditorTime.fromMicroseconds(24L * 60 * 60 * 1_000_000)

        /** The [require]/[UnsupportedVideoException]-checked shape. */
        fun validate(sequence: VideoSequence): CompositeVideoPlan {
            require(sequence.sources.isNotEmpty()) { "sequence has no source" }
            require(sequence.sources.size <= MAX_SOURCES) {
                "sequence has ${sequence.sources.size} sources, " +
                    "max is $MAX_SOURCES"
            }
            for (track in sequence.tracks) {
                if (track.kind == VideoTrackKind.Title) {
                    throw UnsupportedVideoException(
                        "title tracks cannot be rendered yet: ${track.id}"
                    )
                }
            }
            require(sequence.duration.ticks > 0L) { "empty sequence" }
            require(sequence.duration <= MAX_DURATION) {
                "sequence duration exceeds the 24 h limit"
            }
            val mainClips = sequence.tracks
                .firstOrNull { it.kind == VideoTrackKind.Main }
                ?.clips?.toList().orEmpty()
            return CompositeVideoPlan(sequence, mainClips)
        }
    }
}
