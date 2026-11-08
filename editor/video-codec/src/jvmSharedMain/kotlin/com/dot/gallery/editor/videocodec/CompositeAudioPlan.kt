package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.render.video.StereoPcmInput
import com.dot.gallery.editor.render.video.VideoAudioMixer
import com.dot.gallery.editor.render.video.VideoMixMeter
import com.dot.gallery.editor.render.video.VideoMixRun

/**
 * The validated audio side of a composited sequence — the counterpart of
 * [CompositeVideoPlan], which covers pixels. Every audible input gets one
 * open [FfmpegPcmReader] keyed by source id: the Main lane's video sources
 * (unity, unless [VideoSequence.sourceSoundMuted]) plus every audio-lane
 * clip that could produce sound. Muted clips, muted tracks and non-solo
 * lanes under a solo never open a reader — there is nothing to decode.
 *
 * [fillPcmWindow] maps the 48 kHz timeline window through the shared
 * [VideoAudioMixer], so preview playback and export mix the identical
 * samples — parity is structural, not tested into existence. Uncovered
 * positions (a gap, a silent source) stay silent.
 *
 * [expectsAudio] decides whether the recorder/verification expects an
 * audio stream at all: no audible inputs means no PCM readers, no AAC
 * stream — a muted sequence exports video-only rather than shipping a
 * hidden silence track.
 */
class CompositeAudioPlan private constructor(
    val sequence: VideoSequence,
    val audioSourceIds: Set<String>
) {

    /**
     * The per-run DSP memory — one plan instance per contiguous playback
     * or export session, so lane EQs and ducking envelopes stream across
     * windows without a boundary click. A re-validated plan (a recipe
     * edit, a seek, a new export) starts the filters from rest.
     */
    private val mixRun = VideoMixRun()

    /**
     * Fills [out] with interleaved stereo samples for the 48 kHz timeline
     * window `[windowStart, windowEnd)` — the mixed sum of every audible
     * input. [readers] holds one open reader per [audioSourceIds] entry;
     * a missing entry leaves that input silent. Returns the requested
     * frame count.
     */
    suspend fun fillPcmWindow(
        readers: Map<String, FfmpegPcmReader>,
        windowStart: Long,
        windowEnd: Long,
        out: ShortArray,
        meter: VideoMixMeter? = null
    ): Int {
        require(windowStart >= 0L && windowEnd >= windowStart) {
            "invalid PCM window [$windowStart, $windowEnd)"
        }
        val frames = windowEnd - windowStart
        require(frames <= Int.MAX_VALUE) { "PCM window too large: $frames" }
        val inputs = readers.mapValues { (_, reader) ->
            StereoPcmInput { start, count, buffer ->
                val data = reader.read(start, count)
                System.arraycopy(data, 0, buffer, 0, data.size)
            }
        }
        return VideoAudioMixer.mix(
            sequence, windowStart, frames.toInt(), inputs, out, meter, mixRun
        )
    }

    companion object {

        /**
         * The source ids that could ever contribute samples: Main-lane
         * clips on audio-bearing video sources whose embedded sound was
         * not detached ([com.dot.gallery.editor.core.model.VideoClip.audioMuted])
         * or dropped sequence-wide ([VideoSequence.sourceSoundMuted]),
         * plus audio-lane clips that are not muted, on a lane that is not
         * muted, and — while any lane solos — on a solo lane.
         */
        fun audioSourceIds(sequence: VideoSequence): Set<String> {
            val ids = LinkedHashSet<String>()
            if (!sequence.sourceSoundMuted) {
                val sourcesById = sequence.sources.associateBy { it.id }
                for (track in sequence.tracks) {
                    if (track.kind != VideoTrackKind.Main) continue
                    for (clip in track.clips) {
                        if (clip.audioMuted) continue
                        if (sourcesById[clip.sourceId]?.hasAudio == true) {
                            ids += clip.sourceId
                        }
                    }
                }
            }
            val anySolo = sequence.audioTracks.any { it.solo }
            for (track in sequence.audioTracks) {
                if (track.muted || (anySolo && !track.solo)) continue
                for (clip in track.clips) {
                    if (!clip.muted) ids += clip.sourceId
                }
            }
            return ids
        }

        /** Whether the composited output should carry an audio stream. */
        fun expectsAudio(sequence: VideoSequence): Boolean =
            audioSourceIds(sequence).isNotEmpty()

        /**
         * The validated plan. Model invariants are the [VideoSequence]'s
         * own — this only restates the audio-input shape the mixer and
         * the resource openers share.
         */
        fun validate(sequence: VideoSequence): CompositeAudioPlan =
            CompositeAudioPlan(sequence, audioSourceIds(sequence))
    }
}
