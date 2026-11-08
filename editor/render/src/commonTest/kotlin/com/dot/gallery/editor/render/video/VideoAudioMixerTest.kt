package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioDucking
import com.dot.gallery.editor.core.model.AudioEq
import com.dot.gallery.editor.core.model.AudioRouting
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.test.runTest

/**
 * The shared stereo-mix contract, proven against synthetic PCM providers —
 * no decoder in the loop, so every expected s16 value is computed by the
 * documented formula: accumulate in Float, apply
 * gain·pan·routing·fade per channel, clamp to [-1,1], quantize once.
 *
 * Tick/sample mapping is exact: 705 600 000 ticks/s ÷ 48 000 = 14 700
 * ticks per sample, so every boundary lands on a whole sample.
 */
class VideoAudioMixerTest {

    /** One sample frame = 14 700 ticks at the shared 48 kHz rate. */
    private fun at(samples: Long) = EditorTime(samples * TICKS_PER_SAMPLE)

    private fun videoSource(id: String, samples: Long) = MediaAsset.Video(
        id = id,
        reference = "video://$id",
        size = PixelSize(16, 16),
        duration = at(samples),
        frameRate = FrameRate(30),
        hasAudio = true
    )

    private fun audioSource(id: String, samples: Long) = MediaAsset.Audio(
        id = id,
        reference = "audio://$id",
        duration = at(samples),
        sampleRate = 48_000,
        channels = 2
    )

    private fun mainClip(id: String, sourceId: String, samples: Long) =
        VideoClip(
            id = id, sourceId = sourceId,
            timelineStart = EditorTime.Zero,
            sourceRange = TimeRange(EditorTime.Zero, at(samples))
        )

    private fun aclip(
        id: String,
        sourceId: String,
        startSamples: Long,
        sourceStartSamples: Long = 0L,
        samples: Long,
        gainDb: Float = 0f,
        pan: Float = 0f,
        muted: Boolean = false,
        fadeInSamples: Long = 0L,
        fadeOutSamples: Long = 0L,
        routing: AudioRouting = AudioRouting()
    ) = AudioClip(
        id = id, sourceId = sourceId,
        timelineStart = at(startSamples),
        sourceRange = TimeRange(at(sourceStartSamples), at(samples)),
        gainDb = gainDb, pan = pan, muted = muted,
        fadeIn = at(fadeInSamples), fadeOut = at(fadeOutSamples),
        routing = routing
    )

    private fun sequence(
        main: List<VideoClip> = emptyList(),
        audioSources: List<MediaAsset.Audio> = emptyList(),
        audioTracks: List<AudioTrack> = emptyList(),
        sourceSoundMuted: Boolean = false
    ) = VideoSequence(
        size = PixelSize(16, 16),
        frameRate = FrameRate(30),
        sources = persistentListOf(videoSource("main-src", 96_000)),
        tracks = persistentListOf(
            VideoTrack(
                "main", kind = VideoTrackKind.Main,
                clips = main.toPersistentList()
            )
        ),
        audioSources = audioSources.toPersistentList(),
        audioTracks = audioTracks.toPersistentList(),
        sourceSoundMuted = sourceSoundMuted
    )

    /** Constant-level stereo provider — every sample is (l, r). */
    private fun constant(l: Short, r: Short) =
        StereoPcmInput { _, frames, out ->
            for (i in 0 until frames) {
                out[i * 2] = l
                out[i * 2 + 1] = r
            }
        }

    /**
     * Ramp provider — the left channel encodes the source sample index so
     * a misplaced window is visible in the exact sample that lands.
     */
    private fun ramp() = StereoPcmInput { start, frames, out ->
        for (i in 0 until frames) {
            out[i * 2] = ((start + i) % 20_000).toInt().toShort()
            out[i * 2 + 1] = 0
        }
    }

    private suspend fun mix(
        sequence: VideoSequence,
        start: Long,
        frames: Int,
        inputs: Map<String, StereoPcmInput>
    ): ShortArray {
        val out = ShortArray(frames * 2)
        val written = VideoAudioMixer.mix(sequence, start, frames, inputs, out)
        assertEquals(frames, written)
        return out
    }

    @Test
    fun `main lane mixes at unity and honours the source sound mute`() =
        runTest {
            val seq = sequence(
                main = listOf(mainClip("v", "main-src", 96_000))
            )
            val inputs = mapOf("main-src" to constant(8000, 8000))
            val out = mix(seq, 0L, 100, inputs)
            assertEquals(8000, out[0])
            assertEquals(8000, out[1])
            // Unity is exact — s16 → float → s16 round-trips unchanged.
            for (i in out.indices) assertEquals(8000, out[i])

            val muted = sequence(
                main = listOf(mainClip("v", "main-src", 96_000)),
                sourceSoundMuted = true
            )
            val silent = mix(muted, 0L, 100, inputs)
            for (i in silent.indices) assertEquals(0, silent[i])
        }

    @Test
    fun `clip and track gains multiply in linear space`() = runTest {
        // -6.02 dB ≈ ×0.5 each; clip × track ≈ ×0.25.
        val seq = sequence(
            audioSources = listOf(audioSource("song", 96_000)),
            audioTracks = listOf(
                AudioTrack(
                    "music", gainDb = -6.0206f,
                    clips = persistentListOf(
                        aclip("a", "song", 0L, samples = 96_000,
                            gainDb = -6.0206f)
                    )
                )
            )
        )
        val out = mix(
            seq, 0L, 100, mapOf("song" to constant(8000, 8000))
        )
        // 8000 × 10^(-6.0206/20)² × cos(π/4 centre pan) ≈ 1414.
        val expected = (8000 * 0.25 * cos(PI / 4)).toInt()
        assertTrue(out[0] in expected - 3..expected + 3, "left=${out[0]}")
        assertTrue(out[1] in expected - 3..expected + 3, "right=${out[1]}")
    }

    @Test
    fun `equal power pan positions a centre signal`() = runTest {
        val source = audioSource("song", 96_000)
        val base = aclip("a", "song", 0L, samples = 96_000)
        fun seqWith(pan: Float) = sequence(
            audioSources = listOf(source),
            audioTracks = listOf(
                AudioTrack(
                    "music",
                    clips = persistentListOf(base.copy(pan = pan))
                )
            )
        )
        val inputs = mapOf("song" to constant(8000, 8000))

        // Centre: both channels at cos(π/4)=√2/2.
        val centre = mix(seqWith(0f), 0L, 10, inputs)
        val expected = (8000 * sin(PI / 4)).toInt()
        assertTrue(centre[0] in expected - 2..expected + 2)
        assertTrue(centre[1] in expected - 2..expected + 2)

        // Hard right: left is silent, right is full.
        val right = mix(seqWith(1f), 0L, 10, inputs)
        assertEquals(0, right[0])
        assertEquals(8000, right[1])
        val left = mix(seqWith(-1f), 0L, 10, inputs)
        assertEquals(8000, left[0])
        assertEquals(0, left[1])

        // Clip + track pan combine and clamp at the edge.
        val overPanned = sequence(
            audioSources = listOf(source),
            audioTracks = listOf(
                AudioTrack(
                    "music", pan = 0.7f,
                    clips = persistentListOf(base.copy(pan = 0.5f))
                )
            )
        )
        val clamped = mix(overPanned, 0L, 10, inputs)
        assertEquals(0, clamped[0])
        assertEquals(8000, clamped[1])
    }

    @Test
    fun `routing gains apply after pan and downmix collapses stereo`() =
        runTest {
            val source = audioSource("song", 96_000)
            // Right channel routed -60 dB (×0.001) on a hard-right pan.
            val routed = sequence(
                audioSources = listOf(source),
                audioTracks = listOf(
                    AudioTrack(
                        "music",
                        clips = persistentListOf(
                            aclip(
                                "a", "song", 0L, samples = 96_000, pan = 1f,
                                routing = AudioRouting(rightGainDb = -60f)
                            )
                        )
                    )
                )
            )
            val out = mix(
                routed, 0L, 10, mapOf("song" to constant(8000, 8000))
            )
            assertEquals(0, out[0])
            assertTrue(out[1] in 6..10, "right=${out[1]}")

            // Downmix: (8000 + -8000)/2 = 0 — antiphase cancels.
            val downmixed = sequence(
                audioSources = listOf(source),
                audioTracks = listOf(
                    AudioTrack(
                        "music",
                        clips = persistentListOf(
                            aclip(
                                "a", "song", 0L, samples = 96_000,
                                routing = AudioRouting(monoDownmix = true)
                            )
                        )
                    )
                )
            )
            val flat = mix(
                downmixed, 0L, 10,
                mapOf("song" to constant(8000, -8000))
            )
            assertEquals(0, flat[0])
            assertEquals(0, flat[1])
        }

    @Test
    fun `fades ramp linearly over their exact sample windows`() = runTest {
        val seq = sequence(
            audioSources = listOf(audioSource("song", 96_000)),
            audioTracks = listOf(
                AudioTrack(
                    "music",
                    clips = persistentListOf(
                        aclip(
                            "a", "song", 0L, samples = 1_000,
                            fadeInSamples = 100, fadeOutSamples = 200
                        )
                    )
                )
            )
        )
        val out = mix(
            seq, 0L, 1_000, mapOf("song" to constant(8000, 8000))
        )
        // Centre pan scales the whole clip by √2/2 → sustain ≈ 5657.
        val sustain = (8000 * cos(PI / 4)).toInt()
        // Fade-in: sample 0 is 0, sample 50 is half, sample 100 is full.
        assertEquals(0, out[0])
        assertTrue(
            out[100] in sustain / 2 - 30..sustain / 2 + 30,
            "mid-fade=${out[100]}"
        )
        assertTrue(out[200] in sustain - 5..sustain + 5)
        // Sustain region stays full.
        assertTrue(out[1_500] in sustain - 5..sustain + 5)
        // Fade-out over the last 200 samples: at 800 → still ~full;
        // at 900 → half; at 950 → a quarter; at 999 → ~0.5%.
        assertTrue(out[1_600] in sustain - 30..sustain + 5)
        assertTrue(
            out[1_800] in sustain / 2 - 30..sustain / 2 + 30,
            "mid-out=${out[1_800]}"
        )
        assertTrue(out[1_998] in 0..80)
    }

    @Test
    fun `mute and solo decide which lanes reach the accumulator`() = runTest {
        val music = audioSource("music-src", 96_000)
        val voice = audioSource("voice-src", 96_000)
        fun seq(
            musicMuted: Boolean = false,
            trackMuted: Boolean = false,
            voiceSolo: Boolean = false
        ) = sequence(
            audioSources = listOf(music, voice),
            audioTracks = listOf(
                AudioTrack(
                    "music", muted = trackMuted,
                    clips = persistentListOf(
                        aclip(
                            "m", "music-src", 0L,
                            samples = 96_000, muted = musicMuted
                        )
                    )
                ),
                AudioTrack(
                    "voice", solo = voiceSolo,
                    clips = persistentListOf(
                        aclip("v", "voice-src", 0L, samples = 96_000)
                    )
                )
            )
        )
        val inputs = mapOf(
            "music-src" to constant(4000, 4000),
            "voice-src" to constant(2000, 2000)
        )
        val musicLevel = (4000 * cos(PI / 4)).toInt()
        val voiceLevel = (2000 * cos(PI / 4)).toInt()
        // Everything live: the accumulator sums both at centre pan.
        var out = mix(seq(), 0L, 10, inputs)
        assertTrue(
            out[0] in musicLevel + voiceLevel - 5..musicLevel + voiceLevel + 5,
            "sum=${out[0]}"
        )
        // Muted clip drops its lane's level.
        out = mix(seq(musicMuted = true), 0L, 10, inputs)
        assertTrue(out[0] in voiceLevel - 5..voiceLevel + 5)
        // Muted track drops it too.
        out = mix(seq(trackMuted = true), 0L, 10, inputs)
        assertTrue(out[0] in voiceLevel - 5..voiceLevel + 5)
        // A solo elsewhere silences the non-solo lane.
        out = mix(seq(voiceSolo = true), 0L, 10, inputs)
        assertTrue(out[0] in voiceLevel - 5..voiceLevel + 5)
    }

    @Test
    fun `clip windows land sample-exact and source offsets are honoured`() =
        runTest {
            val seq = sequence(
                audioSources = listOf(audioSource("song", 96_000)),
                audioTracks = listOf(
                    AudioTrack(
                        "music",
                        clips = persistentListOf(
                            // Timeline [1_000, 2_000) reads source [500, 1_500)
                            // at full left — pan -1 keeps the ramp's exact
                            // s16 value so a misplaced window is visible.
                            aclip(
                                "a", "song", 1_000, pan = -1f,
                                sourceStartSamples = 500, samples = 1_000
                            )
                        )
                    )
                )
            )
            val inputs = mapOf("song" to ramp())
            // Before the clip: pure silence.
            var out = mix(seq, 0L, 500, inputs)
            for (i in out.indices) assertEquals(0, out[i])
            // Straddling window [900, 1_100): 100 silent, then source 500..
            out = mix(seq, 900L, 200, inputs)
            for (i in 0 until 100) assertEquals(0, out[i * 2])
            for (i in 100 until 200) {
                assertEquals(
                    (500 + i - 100).toShort(), out[i * 2],
                    "sample ${i - 100} misplaced"
                )
            }
            // After the clip: silent again.
            out = mix(seq, 2_000L, 100, inputs)
            for (i in out.indices) assertEquals(0, out[i])
        }

    @Test
    fun `the limiter clamps once at full scale`() = runTest {
        // Two hot inputs sum to 1.83 of full scale — output pins at 32767.
        val seq = sequence(
            audioSources = listOf(
                audioSource("a-src", 96_000),
                audioSource("b-src", 96_000)
            ),
            audioTracks = listOf(
                AudioTrack(
                    "a", clips = persistentListOf(
                        aclip("ca", "a-src", 0L, samples = 96_000)
                    )
                ),
                AudioTrack(
                    "b", clips = persistentListOf(
                        aclip("cb", "b-src", 0L, samples = 96_000)
                    )
                )
            )
        )
        val out = mix(
            seq, 0L, 10,
            mapOf(
                "a-src" to constant(30_000, 30_000),
                "b-src" to constant(30_000, 30_000)
            )
        )
        assertEquals(32767, out[0])
        assertEquals(32767, out[1])
        // A missing input contributes silence, never a crash — the lone
        // 4000 lane lands at its centre-pan level.
        val partial = mix(
            seq, 0L, 10, mapOf("a-src" to constant(4000, 4000))
        )
        val expected = (4000 * cos(PI / 4)).toInt()
        assertTrue(partial[0] in expected - 5..expected + 5)
    }

    // ---- A1c: lane effects — EQ cascade + sidechain ducking ---------

    /** Same-phase stereo sine at [hz], amplitude [amplitude]. */
    private fun tone(hz: Float, amplitude: Int) =
        StereoPcmInput { start, frames, out ->
            for (i in 0 until frames) {
                val v = (amplitude * sin(
                    2.0 * PI * hz * (start + i) / 48_000.0
                )).toInt().toShort()
                out[i * 2] = v
                out[i * 2 + 1] = v
            }
        }

    private suspend fun mixRun(
        sequence: VideoSequence,
        start: Long,
        frames: Int,
        inputs: Map<String, StereoPcmInput>,
        run: VideoMixRun
    ): ShortArray {
        val out = ShortArray(frames * 2)
        val written = VideoAudioMixer.mix(
            sequence, start, frames, inputs, out, run = run
        )
        assertEquals(frames, written)
        return out
    }

    private fun rms(samples: ShortArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += samples[i].toDouble() * samples[i]
        return kotlin.math.sqrt(sum / (to - from))
    }

    /** RMS of the left channel only — tests pan lanes hard left. */
    private fun rmsLeft(samples: ShortArray, fromFrame: Int, toFrame: Int): Double {
        var sum = 0.0
        for (i in fromFrame until toFrame) {
            val v = samples[i * 2].toDouble()
            sum += v * v
        }
        return kotlin.math.sqrt(sum / (toFrame - fromFrame))
    }

    private fun laneWithClip(
        trackId: String,
        clipId: String,
        sourceId: String,
        samples: Long,
        startSamples: Long = 0L,
        pan: Float = -1f,
        eq: AudioEq = AudioEq(),
        ducking: AudioDucking = AudioDucking(),
        muted: Boolean = false
    ) = AudioTrack(
        trackId, muted = muted,
        clips = persistentListOf(
            aclip(clipId, sourceId, startSamples, pan = pan, samples = samples)
        ),
        eq = eq, ducking = ducking
    )

    @Test
    fun `a bypassed or neutral recipe leaves the lane untouched`() = runTest {
        val source = audioSource("song", 96_000)
        fun seqWith(eq: AudioEq) = sequence(
            audioSources = listOf(source),
            audioTracks = listOf(
                laneWithClip("music", "a", "song", 96_000, eq = eq)
            )
        )
        val inputs = mapOf("song" to constant(8000, 8000))
        val neutral = mix(seqWith(AudioEq()), 0L, 100, inputs)
        // Bypass keeps every stored value but filters nothing — the
        // output is bit-identical to the empty recipe.
        val bypassed = mix(
            seqWith(AudioEq(lowDb = 12f, highDb = -12f, bypass = true)),
            0L, 100, inputs
        )
        assertTrue(neutral.contentEquals(bypassed))
        // The same values un-bypassed must actually shape the DC lane —
        // the low shelf's settled DC gain (≈ +12 dB → ×4) lifts the tail
        // of a long window hard, proving the effects path ran. The first
        // samples are the biquad's step transient, not its DC gain.
        val shaped = mix(
            seqWith(AudioEq(lowDb = 12f)), 0L, 4_800, inputs
        )
        val tailRms = rmsLeft(shaped, 2_400, 4_800)
        assertTrue(tailRms > 8_000 * 3, "shaped tail rms=$tailRms")
    }

    @Test
    fun `the eq chain shapes a tone by its own predicted response`() =
        runTest {
            val eq = AudioEq(midDb = 12f)
            val source = audioSource("song", 96_000)
            fun seqWith(recipe: AudioEq) = sequence(
                audioSources = listOf(source),
                audioTracks = listOf(
                    laneWithClip("music", "a", "song", 96_000, eq = recipe)
                )
            )
            val inputs = mapOf("song" to tone(1_000f, 6_000))
            // Warm each chain up on its own run so the measured window is
            // steady-state, then read the hard-left channel.
            val flatRun = VideoMixRun()
            val flatSeq = seqWith(AudioEq())
            mixRun(flatSeq, 0L, 2_400, inputs, flatRun)
            val flat = mixRun(flatSeq, 2_400L, 2_400, inputs, flatRun)
            val shapedRun = VideoMixRun()
            val shapedSeq = seqWith(eq)
            mixRun(shapedSeq, 0L, 2_400, inputs, shapedRun)
            val shaped = mixRun(shapedSeq, 2_400L, 2_400, inputs, shapedRun)
            val ratio = rmsLeft(shaped, 0, 2_400) /
                rmsLeft(flat, 0, 2_400)
            // Mid bell centred at 1 kHz: +12 dB ≈ ×3.98 — the chain's
            // own magnitude response predicts the measured ratio.
            val expected = 10.0.pow(
                AudioEqChain(eq).responseDb(1_000f) / 20.0
            )
            assertTrue(
                kotlin.math.abs(ratio - expected) / expected < 0.03,
                "measured $ratio vs predicted $expected"
            )
            assertTrue(expected in 3.5..4.5, "sanity: +12 dB mid ≈ ×4")
        }

    @Test
    fun `the high pass decays DC to silence`() = runTest {
        val source = audioSource("song", 96_000)
        val seq = sequence(
            audioSources = listOf(source),
            audioTracks = listOf(
                laneWithClip(
                    "music", "a", "song", 96_000,
                    eq = AudioEq(highPassHz = 500f)
                )
            )
        )
        val run = VideoMixRun()
        val inputs = mapOf("song" to constant(8_000, 8_000))
        mixRun(seq, 0L, 48_000, inputs, run)
        val tail = mixRun(seq, 48_000L, 48_000, inputs, run)
        // One second in, a 500 Hz high-pass has eaten DC entirely.
        val tailRms = rmsLeft(tail, 0, 48_000)
        assertTrue(tailRms < 5.0, "dc tail rms=$tailRms")
    }

    @Test
    fun `chunked windows through one run equal the single pass`() = runTest {
        // Both inserts active: EQ shapes lane B while lane A ducks it —
        // the run carries filter memory and the duck envelope across
        // every window boundary, so chunking must be bit-exact.
        val seq = sequence(
            audioSources = listOf(
                audioSource("trig", 96_000),
                audioSource("song", 96_000)
            ),
            audioTracks = listOf(
                laneWithClip("voice", "t", "trig", 96_000),
                laneWithClip(
                    "music", "m", "song", 96_000,
                    eq = AudioEq(midDb = 6f),
                    ducking = AudioDucking(
                        enabled = true, triggerTrackId = "voice",
                        reductionDb = -12f, attackMs = 40f,
                        releaseMs = 200f
                    )
                )
            )
        ).copy(limiterEnabled = false)
        val inputs = mapOf(
            "trig" to tone(440f, 4_000),
            "song" to constant(2_000, 2_000)
        )
        val whole = mixRun(seq, 0L, 9_600, inputs, VideoMixRun())
        val run = VideoMixRun()
        val chunked = ShortArray(9_600 * 2)
        for (w in 0 until 10) {
            val part = mixRun(seq, w * 960L, 960, inputs, run)
            part.copyInto(chunked, w * 960 * 2)
        }
        assertTrue(
            whole.contentEquals(chunked),
            "chunked output diverges from the single pass"
        )
        // A fresh run for every window cannot reproduce the single pass —
        // the ducked lane comes back at unity each time. This is the
        // contrast that proves the state is what makes chunks agree.
        val stateless = ShortArray(9_600 * 2)
        for (w in 0 until 10) {
            val part = mix(seq, w * 960L, 960, inputs)
            part.copyInto(stateless, w * 960 * 2)
        }
        assertTrue(
            !whole.contentEquals(stateless),
            "per-window state should differ from one continuous run"
        )
    }

    @Test
    fun `the duck falls to its floor then recovers on release`() = runTest {
        // Trigger lane: 0..48k loud tone. Target: 0..96k constant,
        // ducking -20 dB, 5 ms attack, 40 ms release. The limiter is
        // disabled — the early attack frames sum past full scale, and a
        // windowed peak normalize would rescale the whole measurement.
        val seq = sequence(
            audioSources = listOf(
                audioSource("trig", 96_000),
                audioSource("song", 96_000)
            ),
            audioTracks = listOf(
                laneWithClip("voice", "t", "trig", 48_000),
                laneWithClip(
                    "music", "m", "song", 96_000,
                    ducking = AudioDucking(
                        enabled = true, triggerTrackId = "voice",
                        reductionDb = -20f, attackMs = 5f, releaseMs = 40f
                    )
                )
            )
        ).copy(limiterEnabled = false)
        val inputs = mapOf(
            "trig" to constant(30_000, 30_000),
            "song" to constant(8_000, 8_000)
        )
        val run = VideoMixRun()
        // Both lanes are panned hard left: left carries trigger (30 000)
        // plus the ducked target, right stays silent.
        val during = mixRun(seq, 0L, 48_000, inputs, run)
        val lastFrames = rmsLeft(during, 47_000, 48_000)
        val expectedMix = 30_000 + 8_000 * 0.1
        assertTrue(
            kotlin.math.abs(lastFrames - expectedMix) / expectedMix < 0.05,
            "ducked mix rms=$lastFrames vs $expectedMix"
        )
        // The trigger's clip ends, but the 25 ms peak detector holds
        // the envelope open — ~8.5 constants (≈213 ms) to fall below
        // the gate — so the duck stays pinned just after silence.
        val held = mixRun(seq, 48_000L, 1_920, inputs, run)
        assertTrue(
            rmsLeft(held, 0, 1_920) < 1_500,
            "detector hold rms=${rmsLeft(held, 0, 1_920)}"
        )
        // Run through the detector drain (~10.3k samples of silence),
        // then measure one release-constant of recovery:
        // g ≈ 1 − 0.9·e⁻¹ ≈ 0.67.
        mixRun(seq, 49_920L, 8_640, inputs, run)
        val released = mixRun(seq, 58_560L, 1_920, inputs, run)
        val gEarly = rmsLeft(released, 1_720, 1_920) / 8_000
        assertTrue(gEarly in 0.4..0.9, "release midpoint gain=$gEarly")
        // A handful of constants later the duck has fully released.
        mixRun(seq, 60_480L, 9_600, inputs, run)
        val late = mixRun(seq, 70_080L, 960, inputs, run)
        val lateRms = rmsLeft(late, 0, 960)
        assertTrue(
            lateRms > 8_000 * 0.95,
            "recovered rms=$lateRms vs unity 8000"
        )
    }

    @Test
    fun `a muted or absent trigger contributes silence and never ducks`() =
        runTest {
            val sources = listOf(
                audioSource("trig", 96_000),
                audioSource("song", 96_000)
            )
            fun seqWith(muted: Boolean) = sequence(
                audioSources = sources,
                audioTracks = listOf(
                    laneWithClip("voice", "t", "trig", 96_000, muted = muted),
                    laneWithClip(
                        "music", "m", "song", 96_000,
                        ducking = AudioDucking(
                            enabled = true, triggerTrackId = "voice",
                            reductionDb = -20f
                        )
                    )
                )
            )
            val inputs = mapOf(
                "trig" to constant(30_000, 30_000),
                "song" to constant(8_000, 8_000)
            )
            val out = mix(seqWith(muted = true), 0L, 48_000, inputs)
            val tailRms = rmsLeft(out, 47_000, 48_000)
            assertTrue(
                kotlin.math.abs(tailRms - 8_000) / 8_000 < 0.05,
                "muted trigger should leave the lane at unity: $tailRms"
            )
        }

    @Test
    fun `extreme settings stay finite and bounded`() {
        // Every stage boosted to the edge — the cascade must not blow
        // up or NaN; worst-case gain is the sum of the shelves.
        val chain = AudioEqChain(
            AudioEq(
                lowDb = 12f, midDb = 12f, highDb = 12f, highPassHz = 500f
            )
        )
        val buf = FloatArray(48_000 * 2) { i ->
            if (i % 2 == 0) 0.9f else -0.9f
        }
        chain.process(buf, 48_000)
        for (v in buf) {
            assertTrue(v.isFinite(), "chain produced a non-finite sample")
            assertTrue(
                kotlin.math.abs(v) < 8f,
                "unstable gain: $v on a ±0.9 input"
            )
        }
        // The empty recipe never allocates stages — it is a pass-through.
        val flat = FloatArray(200) { 0.5f }
        AudioEqChain(AudioEq()).process(flat, 100)
        for (v in flat) assertEquals(0.5f, v)
    }

    @Test
    fun `audio running past the video tail extends the mix`() = runTest {
        val seq = sequence(
            main = listOf(mainClip("v", "main-src", 48_000)),
            audioSources = listOf(audioSource("song", 96_000)),
            audioTracks = listOf(
                AudioTrack(
                    "music",
                    clips = persistentListOf(
                        aclip("a", "song", 0L, samples = 96_000)
                    )
                )
            )
        )
        val out = mix(
            seq, 48_000L, 100,
            mapOf(
                "main-src" to constant(4000, 4000),
                "song" to constant(6000, 6000)
            )
        )
        // Past the main clip's end only the song remains, at centre pan.
        val expected = (6000 * cos(PI / 4)).toInt()
        assertTrue(out[0] in expected - 5..expected + 5)
        // And the sequence duration already includes the audio lane.
        assertEquals(at(96_000), seq.duration)
    }

    private companion object {
        const val TICKS_PER_SAMPLE = 14_700L
    }
}
