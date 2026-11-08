package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.UnsupportedVideoException

/**
 * The validated export/playback shape shared by [FfmpegVideoExporter] and
 * [FfmpegVideoPlayback]: exactly one track and one source, clips packed
 * contiguously from timeline zero, non-empty duration bounded at 24 h
 * (keeps every sample/tick multiplication inside `Long`), and neutral
 * [ColorAdjustments] on every clip — video grading is not supported and a
 * hidden ignored grade is a rejected plan, never a silently dropped effect.
 *
 * Carries the timeline→source mapping both engines need: [clipAt] for video
 * frames and [fillPcmWindow] for audio, where a 48 kHz output window maps to
 * source samples by `samplesAtOrAfter(range.start) + timelineOffset`, split
 * at clip boundaries. One implementation so export and playback can never
 * disagree about where the audio lands.
 */
class SingleSourcePlan(
    val sequence: VideoSequence,
    val source: MediaAsset.Video,
    val clips: List<VideoClip>
) {

    /** Clip whose timeline range contains [t]; packing guarantees exactly one. */
    fun clipAt(t: EditorTime): VideoClip {
        for (clip in clips) {
            if (t in clip.timelineRange) return clip
        }
        throw IllegalStateException("no clip covers timeline ${t.ticks} ticks")
    }

    /**
     * Fills [out] with interleaved stereo samples for the 48 kHz timeline
     * window `[windowStart, windowEnd)`, split across clip boundaries. Each
     * piece lands at its absolute offset in the window — a hole would be
     * left silent, but a packed timeline covers the window exactly, so a
     * partial coverage is a caller bug and throws. [out] is cleared in the
     * requested window first; the returned count is always
     * `windowEnd - windowStart` sample frames.
     */
    suspend fun fillPcmWindow(
        pcm: FfmpegPcmReader,
        windowStart: Long,
        windowEnd: Long,
        out: ShortArray
    ): Int {
        require(windowStart >= 0L && windowEnd >= windowStart) {
            "invalid PCM window [$windowStart, $windowEnd)"
        }
        val frames = windowEnd - windowStart
        require(frames <= Int.MAX_VALUE / CHANNELS) {
            "PCM window too large: $frames frames"
        }
        require(out.size >= frames.toInt() * CHANNELS) {
            "PCM window buffer too small: ${out.size} < ${frames * CHANNELS}"
        }
        out.fill(0, 0, frames.toInt() * CHANNELS)
        var covered = 0L
        for (clip in clips) {
            val clipStart =
                clip.timelineStart.samplesAtOrAfter(FfmpegPcmReader.OUTPUT_RATE)
            val clipEnd = clip.timelineRange.endExclusive
                .samplesAtOrAfter(FfmpegPcmReader.OUTPUT_RATE)
            val pieceStart = maxOf(windowStart, clipStart)
            val pieceEnd = minOf(windowEnd, clipEnd)
            if (pieceEnd <= pieceStart) continue
            val count = (pieceEnd - pieceStart).toInt()
            // Timeline sample → source sample, all ceil-exact.
            val srcStart =
                clip.sourceRange.start.samplesAtOrAfter(FfmpegPcmReader.OUTPUT_RATE) +
                    (pieceStart - clipStart)
            val pcmData = pcm.read(srcStart, count)
            System.arraycopy(
                pcmData, 0,
                out, (pieceStart - windowStart).toInt() * CHANNELS,
                count * CHANNELS
            )
            covered += pieceEnd - pieceStart
        }
        check(covered == frames) {
            "packed timeline left $windowStart..$windowEnd uncovered " +
                "($covered of $frames frames)"
        }
        return frames.toInt()
    }

    companion object {
        private const val CHANNELS = FfmpegPcmReader.OUTPUT_CHANNELS

        /** First-scope cap: 24 h keeps every tick/sample product inside Long. */
        private val MAX_DURATION: EditorTime =
            EditorTime.fromMicroseconds(24L * 60 * 60 * 1_000_000)

        /**
         * Cheap pre-check for the single-source fast path: one source, one
         * [com.dot.gallery.editor.core.model.VideoTrackKind.Main] track,
         * every clip carrying the identity visual recipe and its own
         * sound (not `audioMuted`), no audio lanes, the Main lane's
         * embedded sound unmuted, and unity master gain. Anything else
         * belongs to the composited path — a transformed or blended
         * clip, a detached clip sound, a music track, a muted source
         * sound or a master-gained mix routed here would lose its
         * recipe silently.
         */
        fun isEligible(sequence: VideoSequence): Boolean =
            sequence.sources.size == 1 &&
                sequence.tracks.size == 1 &&
                sequence.tracks[0].kind == VideoTrackKind.Main &&
                sequence.tracks[0].clips.all {
                    it.hasNeutralVisuals && !it.audioMuted &&
                        it.adjustments == ColorAdjustments()
                } &&
                sequence.audioTracks.isEmpty() &&
                sequence.audioSources.isEmpty() &&
                !sequence.sourceSoundMuted &&
                sequence.masterGainDb == 0f

        /** The [require]-checked shape; throws on anything outside it. */
        fun validate(sequence: VideoSequence): SingleSourcePlan {
            require(sequence.sources.size == 1 && sequence.tracks.size == 1) {
                "supports exactly one source and one track " +
                    "(${sequence.sources.size} sources, ${sequence.tracks.size} tracks)"
            }
            val clips = sequence.tracks[0].clips
            var cursor = EditorTime.Zero
            for (clip in clips) {
                require(clip.timelineStart == cursor) {
                    "clips must be contiguous from t=0 (gap before ${clip.id})"
                }
                cursor += clip.sourceRange.duration
            }
            require(sequence.duration.ticks > 0L) { "empty sequence" }
            require(sequence.duration <= MAX_DURATION) {
                "sequence duration exceeds the 24 h limit"
            }
            val neutral = ColorAdjustments()
            for (clip in clips) {
                require(clip.adjustments == neutral) {
                    "clip ${clip.id} carries colour adjustments, which only " +
                        "the composited path bakes; they must not be " +
                        "silently dropped"
                }
            }
            return SingleSourcePlan(sequence, sequence.sources[0], clips.toList())
        }

        /** Ceiling division on non-negative longs (exact, no float). */
        fun ceilDiv(a: Long, b: Long): Long {
            require(a >= 0L) { "ceilDiv requires a >= 0: $a" }
            require(b > 0L) { "ceilDiv requires b > 0: $b" }
            return a / b + if (a % b == 0L) 0L else 1L
        }
    }
}
