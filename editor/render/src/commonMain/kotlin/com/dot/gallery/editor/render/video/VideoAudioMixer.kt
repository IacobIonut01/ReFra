package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The deterministic stereo mix engine shared by preview playback and
 * export. [mix] fills `out` with `frames` interleaved stereo s16
 * samples starting at `windowStart` — expressed in 48 kHz sample
 * coordinates — the single implementation both consumers feed, which
 * is what keeps preview and rendered output identical by construction.
 *
 * Stage order, applied per contributed sample in 32f:
 *
 * 1. **Downmix** — a `monoDownmix` routing collapses the input pair to
 *    (L+R)/2 before anything else touches it.
 * 2. **Pan and gain** — clip × track gain in linear amplitude, then the
 *    constant-power `cos/sin((pan+1)·π/4)` law with clip and track pan
 *    combined and clamped to [-1, 1].
 * 3. **Routing** — the clip's independent left/right channel gains.
 * 4. **Fade** — linear amplitude ramps inside the clip's own
 *    [com.dot.gallery.editor.core.model.AudioClip.fadeIn]/`fadeOut`
 *    windows, evaluated at each sample's exact tick position.
 * 5. **Lane effects** — when a lane carries an active [AudioEq] its summed
 *    contribution runs through the run's biquad cascade, and an enabled
 *    [AudioDucking] then scales the lane by its sidechain envelope (the
 *    trigger is another lane's post-fader, post-EQ contribution). A
 *    sequence with no lane effects takes the same accumulate-only path
 *    it always did — zero per-lane buffers, zero DSP state.
 * 6. **Accumulate** — every input adds into one float stereo buffer.
 * 7. **Master gain** — [VideoSequence.masterGainDb] applied to the sum.
 * 8. **Output ceiling** — with [VideoSequence.limiterEnabled] the window
 *    is peak-normalized back under full scale (a lookahead-free soft
 *    limiter that preserves the waveform); without it, anything past
 *    the boundary hard-clips at quantization.
 * 9. **Quantize** — round each float to s16 with symmetric ±32767
 *    clamp, exactly once, at the end.
 *
 * The Main video lane contributes one virtual input at unity for every
 * active Main clip — [com.dot.gallery.editor.core.model.VideoClip.audioMuted]
 * drops that clip's embedded sound without disabling the lane (the
 * "detached" state after extract-audio moves it to a real track), and
 * [VideoSequence.sourceSoundMuted] drops the whole input. Because clips
 * can't overlap within a track, each contributes its own window slice;
 * a clip whose sound was extracted carries the flag, so its audio is
 * never mixed twice.
 *
 * All inputs are consumed as stereo pairs through [StereoPcmInput]:
 * video taps decode stereo through the shared PCM path and audio-only
 * readers are normalized upstream. A missing input — or a source with
 * no audio stream — contributes silence.
 *
 * @param meter when non-null, a [VideoMixMeter] the mixer fills with
 *   per-input energy while it already touches every contributed sample —
 *   the levels playback publishes to the mixer UI at its own cadence.
 *   Lane energy measures the post-fader contribution (gain, pan,
 *   routing and fade already applied), so a silent lane meters zero.
 */
object VideoAudioMixer {

    /** The fixed mix rate every input and window is expressed in. */
    const val SAMPLE_RATE = 48_000L

    /** Ticks per output sample — exact, the tick rate is a multiple. */
    private const val TICKS_PER_SAMPLE = EDITOR_TICKS_PER_SECOND / SAMPLE_RATE

    /**
     * Fills `out` with `frames` interleaved stereo samples for the
     * 48 kHz window `[windowStart, windowStart + frames)`. [inputs]
     * provides one stereo reader per *source id* — every clip that
     * references a source pulls its own window slice out of it. A
     * source with no entry contributes silence. Returns [frames].
     *
     * [run] carries the per-lane DSP memory (EQ delay lines, ducking
     * envelopes) across the calls of one contiguous run — a stateless
     * caller leaves it null and each window is mixed from rest.
     */
    suspend fun mix(
        sequence: VideoSequence,
        windowStart: Long,
        frames: Int,
        inputs: Map<String, StereoPcmInput>,
        out: ShortArray,
        meter: VideoMixMeter? = null,
        run: VideoMixRun? = null
    ): Int {
        meter?.frames = frames
        val mix = FloatArray(frames * 2)
        mixMainLane(sequence, windowStart, frames, inputs, mix, meter)
        if (hasLaneEffects(sequence)) {
            mixAudioLanesFx(
                sequence, windowStart, frames, inputs, mix, meter,
                run ?: VideoMixRun()
            )
        } else {
            mixAudioLanes(sequence, windowStart, frames, inputs, mix, meter)
        }
        val masterGain = dbToLinear(sequence.masterGainDb)
        if (masterGain != 1f) {
            for (i in mix.indices) mix[i] *= masterGain
        }
        var peak = 0f
        for (v in mix) peak = maxOf(peak, kotlin.math.abs(v))
        if (sequence.limiterEnabled && peak > 1f) {
            // Windowed peak normalization — the limiter — pulls the
            // whole window back under full scale, preserving the
            // waveform shape. Without the limiter the quantization
            // clamp hard-clips the peaks instead.
            val scale = 1f / peak
            for (i in mix.indices) mix[i] *= scale
        }
        for (i in mix.indices) {
            val v = mix[i]
            out[i] = if (v <= -1f) Short.MIN_VALUE else
                if (v >= 1f) Short.MAX_VALUE else
                    (v * Short.MAX_VALUE).roundToInt().toShort()
        }
        return frames
    }

    // ------------------------------------------------------------------
    // Inputs
    // ------------------------------------------------------------------

    private suspend fun mixMainLane(
        sequence: VideoSequence,
        windowStart: Long,
        frames: Int,
        inputs: Map<String, StereoPcmInput>,
        mix: FloatArray,
        meter: VideoMixMeter?
    ) {
        if (sequence.sourceSoundMuted) return
        val windowEnd = windowStart + frames
        val buf = ShortArray(frames * 2)
        for (track in sequence.tracks) {
            if (track.kind != VideoTrackKind.Main) continue
            for (clip in track.clips) {
                if (clip.audioMuted) continue
                val clipStart = clip.timelineStart.ticks / TICKS_PER_SAMPLE
                val clipEnd =
                    clip.timelineRange.endExclusive.ticks / TICKS_PER_SAMPLE
                val lo = maxOf(clipStart, windowStart)
                val hi = minOf(clipEnd, windowEnd)
                if (hi <= lo) continue
                val input = inputs[clip.sourceId] ?: continue
                buf.fill(0)
                val count = (hi - lo).toInt()
                val sourceStart =
                    clip.sourceRange.start.ticks / TICKS_PER_SAMPLE +
                        (lo - clipStart)
                input.read(sourceStart, count, buf)
                val dst0 = (lo - windowStart).toInt()
                for (f in 0 until count) {
                    val dst = (dst0 + f) * 2
                    val l = buf[f * 2] * S16_TO_FLOAT
                    val r = buf[f * 2 + 1] * S16_TO_FLOAT
                    mix[dst] += l
                    mix[dst + 1] += r
                    meter?.let {
                        it.sourceSum[0] += (l * l).toDouble()
                        it.sourceSum[1] += (r * r).toDouble()
                    }
                }
            }
        }
    }

    private suspend fun mixAudioLanes(
        sequence: VideoSequence,
        windowStart: Long,
        frames: Int,
        inputs: Map<String, StereoPcmInput>,
        mix: FloatArray,
        meter: VideoMixMeter?
    ) {
        val windowEnd = windowStart + frames
        val anySolo = sequence.audioTracks.any { it.solo }
        val buf = ShortArray(frames * 2)
        sequence.audioTracks.forEachIndexed { trackIndex, track ->
            if (track.muted || (anySolo && !track.solo)) {
                return@forEachIndexed
            }
            val laneSum = meter?.laneSum?.get(trackIndex)
            for (clip in track.clips) {
                if (clip.muted) continue
                val clipStart = clip.timelineStart.ticks / TICKS_PER_SAMPLE
                val clipEnd =
                    clip.timelineRange.endExclusive.ticks / TICKS_PER_SAMPLE
                val lo = maxOf(clipStart, windowStart)
                val hi = minOf(clipEnd, windowEnd)
                if (hi <= lo) continue
                val input = inputs[clip.sourceId] ?: continue
                mixClipContribution(
                    track, clip, input, lo, hi, clipStart,
                    windowStart, mix, buf, laneSum
                )
            }
        }
    }

    /**
     * The lane-effects path — the same contribution math as
     * [mixAudioLanes], rendered per lane so [AudioEq] and [AudioDucking]
     * inserts can run on the summed signal:
     *
     * 1. each audible lane accumulates into its own float buffer;
     * 2. an active EQ cascade filters that buffer in place;
     * 3. every ducked lane reads its trigger's *rendered* buffer —
     *    snapshotted before any duck runs, so a lane that is both a
     *    trigger and a duck target sidechains on its un-ducked signal;
     * 4. the finished lanes sum into the mix and meter their actual
     *    contribution — EQ and ducking included, matching what plays.
     */
    private suspend fun mixAudioLanesFx(
        sequence: VideoSequence,
        windowStart: Long,
        frames: Int,
        inputs: Map<String, StereoPcmInput>,
        mix: FloatArray,
        meter: VideoMixMeter?,
        run: VideoMixRun
    ) {
        val windowEnd = windowStart + frames
        val anySolo = sequence.audioTracks.any { it.solo }
        val buf = ShortArray(frames * 2)
        val laneBuffers = arrayOfNulls<FloatArray>(sequence.audioTracks.size)
        sequence.audioTracks.forEachIndexed { trackIndex, track ->
            if (track.muted || (anySolo && !track.solo)) {
                return@forEachIndexed
            }
            var laneBuffer: FloatArray? = null
            for (clip in track.clips) {
                if (clip.muted) continue
                val clipStart = clip.timelineStart.ticks / TICKS_PER_SAMPLE
                val clipEnd =
                    clip.timelineRange.endExclusive.ticks / TICKS_PER_SAMPLE
                val lo = maxOf(clipStart, windowStart)
                val hi = minOf(clipEnd, windowEnd)
                if (hi <= lo) continue
                val input = inputs[clip.sourceId] ?: continue
                val target = laneBuffer
                    ?: FloatArray(frames * 2).also { laneBuffer = it }
                mixClipContribution(
                    track, clip, input, lo, hi, clipStart,
                    windowStart, target, buf, null
                )
            }
            val rendered = laneBuffer ?: return@forEachIndexed
            if (track.eq.active) {
                run.eqFor(trackIndex, track.eq).process(rendered, frames)
            }
            laneBuffers[trackIndex] = rendered
        }
        // The sidechain taps each lane as rendered — snapshot before the
        // first duck mutates anything, so a chained lane (ducked and a
        // trigger) is still heard un-ducked by its dependents.
        val triggerSnapshots = HashMap<Int, FloatArray?>()
        sequence.audioTracks.forEachIndexed { trackIndex, track ->
            val ducking = track.ducking
            if (!ducking.enabled || laneBuffers[trackIndex] == null) {
                return@forEachIndexed
            }
            val triggerIndex = sequence.audioTracks.indexOfFirst {
                it.id == ducking.triggerTrackId
            }
            triggerSnapshots[trackIndex] =
                laneBuffers.getOrNull(triggerIndex)?.copyOf()
        }
        for ((trackIndex, trigger) in triggerSnapshots) {
            val target = laneBuffers[trackIndex] ?: continue
            run.duckerFor(trackIndex, sequence.audioTracks[trackIndex].ducking)
                .process(target, frames, trigger)
        }
        sequence.audioTracks.forEachIndexed { trackIndex, _ ->
            val rendered = laneBuffers[trackIndex] ?: return@forEachIndexed
            val laneSum = meter?.laneSum?.get(trackIndex)
            for (i in rendered.indices step 2) {
                mix[i] += rendered[i]
                mix[i + 1] += rendered[i + 1]
                laneSum?.let {
                    it[0] += (rendered[i] * rendered[i]).toDouble()
                    it[1] += (rendered[i + 1] * rendered[i + 1]).toDouble()
                }
            }
        }
    }

    /** Whether any lane carries an effect that needs the buffered path. */
    private fun hasLaneEffects(sequence: VideoSequence): Boolean =
        sequence.audioTracks.any { it.eq.active || it.ducking.enabled }

    /**
     * One clip's post-fader contribution added into [dst] — every stage
     * before the lane inserts: downmix, clip × track gain, constant-power
     * pan, routing gains and the fade ramps. [laneSum] meters the
     * contributed samples; the effects path meters later, on the
     * post-insert sum, so it passes null here.
     */
    private suspend fun mixClipContribution(
        track: AudioTrack,
        clip: AudioClip,
        input: StereoPcmInput,
        lo: Long,
        hi: Long,
        clipStart: Long,
        windowStart: Long,
        dst: FloatArray,
        buf: ShortArray,
        laneSum: DoubleArray?
    ) {
        buf.fill(0)
        val count = (hi - lo).toInt()
        val sourceStart =
            clip.sourceRange.start.ticks / TICKS_PER_SAMPLE +
                (lo - clipStart)
        input.read(sourceStart, count, buf)
        val clipGain =
            dbToLinear(clip.gainDb) * dbToLinear(track.gainDb)
        if (clipGain == 0f) return
        val pan = (clip.pan + track.pan).coerceIn(-1f, 1f)
        val panL = cos((pan + 1f) * PI.toFloat() / 4f)
        val panR = sin((pan + 1f) * PI.toFloat() / 4f)
        val routing = clip.routing
        val routeL = dbToLinear(routing.leftGainDb)
        val routeR = dbToLinear(routing.rightGainDb)
        val dst0 = (lo - windowStart).toInt()
        for (f in 0 until count) {
            val src = f * 2
            var l = buf[src] * S16_TO_FLOAT
            var r = buf[src + 1] * S16_TO_FLOAT
            if (routing.monoDownmix) {
                val mono = (l + r) * 0.5f
                l = mono
                r = mono
            }
            var outL = l * panL * clipGain * routeL
            var outR = r * panR * clipGain * routeR
            val fade =
                fadeAt(clip, (lo + f) * TICKS_PER_SAMPLE)
            outL *= fade
            outR *= fade
            val out = (dst0 + f) * 2
            dst[out] += outL
            dst[out + 1] += outR
            laneSum?.let {
                it[0] += (outL * outL).toDouble()
                it[1] += (outR * outR).toDouble()
            }
        }
    }

    /**
     * Linear amplitude ramp at the sample sitting at sequence tick
     * [ticks] — inside [clip]'s own fade windows, measured from the
     * clip's timeline placement.
     */
    private fun fadeAt(
        clip: AudioClip,
        ticks: Long
    ): Float {
        val into = ticks - clip.timelineStart.ticks
        val toEnd = clip.timelineRange.endExclusive.ticks - ticks
        var gain = 1f
        if (clip.fadeIn.ticks > 0 && into < clip.fadeIn.ticks) {
            gain = min(gain, into.toFloat() / clip.fadeIn.ticks)
        }
        if (clip.fadeOut.ticks > 0 && toEnd < clip.fadeOut.ticks) {
            gain = min(gain, toEnd.toFloat() / clip.fadeOut.ticks)
        }
        return gain
    }

    private fun dbToLinear(db: Float): Float =
        10.0.pow((db / 20.0)).toFloat()

    private const val S16_TO_FLOAT = 1f / 32768f
}
