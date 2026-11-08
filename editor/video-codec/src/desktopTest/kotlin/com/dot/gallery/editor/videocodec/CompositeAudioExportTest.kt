package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.AudioClip
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
import java.io.File
import java.nio.file.Files
import kotlin.math.sqrt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC

/**
 * The A1a export oracle: a Main lane plus two independent audio lanes —
 * an audio-only WAV music bed and a voice-over lane — encoded through
 * [FfmpegVideoExporter]'s composite path and verified by reopening the
 * MP4. Each lane covers a distinct timeline window at a known level, so
 * per-window RMS proves every input's samples landed on its own span
 * (the shared [com.dot.gallery.editor.render.video.VideoAudioMixer]'s
 * float sum, clamped and quantized once) — and that a muted lane or a
 * muted Main sound removes exactly its contribution.
 */
class CompositeAudioExportTest {

    private lateinit var dir: File
    private lateinit var mainFixture: File
    private lateinit var musicFixture: File
    private lateinit var voiceFixture: File
    private val resolver = VideoSourceResolver { reference ->
        ResolvedVideoSource(reference, File(reference).name) {}
    }
    private lateinit var exporter: FfmpegVideoExporter

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("audioexport").toFile()
        // Main: the colour-band programme, 440 Hz then 880 Hz at 4000.
        mainFixture = VideoTestFixtures.createFixture(dir, "main.mp4")
        // Exact-sample WAV tones, louder than the Main bed so each lane's
        // contribution is unmistakable in the window's energy.
        musicFixture = VideoTestFixtures.createToneFile(
            dir, "music.wav", 110.0
        )
        voiceFixture = VideoTestFixtures.createToneFile(
            dir, "voice.wav", 220.0
        )
        exporter = FfmpegVideoExporter(resolver)
    }

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    private fun audio(id: String, file: File, duration: EditorTime) =
        MediaAsset.Audio(
            id = id,
            reference = file.absolutePath,
            duration = duration,
            sampleRate = VideoTestFixtures.SAMPLE_RATE,
            channels = 2
        )

    /**
     * Main video [0,4s) at unity; music bed 110 Hz over timeline
     * [1s,3s); voice-over 220 Hz over [2s,3.5s). Uncovered audio
     * timeline keeps the Main bed alone.
     */
    private fun mixedSequence(sourceSoundMuted: Boolean = false): VideoSequence {
        val duration = us(4_000_000L)
        return VideoSequence(
            size = PixelSize(
                VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
            ),
            frameRate = FrameRate(30),
            sources = persistentListOf(
                MediaAsset.Video(
                    id = "src-main",
                    reference = mainFixture.absolutePath,
                    size = PixelSize(
                        VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
                    ),
                    duration = duration,
                    frameRate = FrameRate(30),
                    hasAudio = true
                )
            ),
            tracks = persistentListOf(
                VideoTrack(
                    "main", kind = VideoTrackKind.Main,
                    clips = persistentListOf(
                        VideoClip(
                            "clip-main", "src-main", EditorTime.Zero,
                            TimeRange(EditorTime.Zero, duration)
                        )
                    )
                )
            ),
            audioSources = persistentListOf(
                audio("song-music", musicFixture, duration),
                audio("song-voice", voiceFixture, duration)
            ),
            audioTracks = persistentListOf(
                AudioTrack(
                    "music", name = "Music bed",
                    clips = persistentListOf(
                        AudioClip(
                            "ac-music", "song-music", us(1_000_000L),
                            TimeRange(EditorTime.Zero, us(2_000_000L))
                        )
                    )
                ),
                AudioTrack(
                    "voice", name = "Voice-over",
                    clips = persistentListOf(
                        AudioClip(
                            "ac-voice", "song-voice", us(2_000_000L),
                            TimeRange(EditorTime.Zero, us(1_500_000L))
                        )
                    )
                )
            ),
            sourceSoundMuted = sourceSoundMuted
        )
    }

    /** RMS of the [startUs, startUs+100ms) decoded output window. */
    private suspend fun windowRms(
        pcm: FfmpegPcmReader,
        startUs: Long
    ): Double {
        val start = startUs * VideoTestFixtures.SAMPLE_RATE / 1_000_000L
        return VideoTestFixtures.rms(pcm.read(start, 4_800))
    }

    /**
     * The sine-RMS model: every contributor is a tone at amplitude A,
     * centre-panned to √2/2 per channel — its per-channel RMS is
     * A·√2/2·1/√2 = A/2. Uncorrelated tones add in quadrature.
     */
    private fun expectedRms(mainRms: Double, vararg toneAmplitudes: Int) =
        sqrt(
            mainRms * mainRms +
                toneAmplitudes.sumOf { a -> (a / 2.0) * (a / 2.0) }
        )

    private fun assertRms(actual: Double, expected: Double, what: String) {
        assertTrue(
            actual in expected * 0.85..expected * 1.15,
            "$what: rms $actual, expected ≈ $expected"
        )
    }

    @Test
    fun `two audio lanes and the Main bed land in one mixed AAC stream`() =
        runTest {
            val mainHash = VideoTestFixtures.sha256(mainFixture)
            val musicHash = VideoTestFixtures.sha256(musicFixture)
            val voiceHash = VideoTestFixtures.sha256(voiceFixture)
            val sequence = mixedSequence()

            // One reader per audible input — Main bed plus both lanes.
            assertEquals(
                setOf("src-main", "song-music", "song-voice"),
                CompositeVideoPlan.validate(sequence).audioSourceIds
            )
            assertTrue(CompositeAudioPlan.expectsAudio(sequence))

            val out = File(dir, "mixed.mp4")
            exporter.encode(sequence, out) {}
            assertTrue(out.isFile && out.length() > 0)

            resolver.resolve(out.absolutePath).use { staged ->
                FfmpegExportVerification.verify(
                    staged, sequence.size, 0, sequence.duration,
                    expectAudio = true
                )
                assertEquals(
                    AV_CODEC_ID_AAC,
                    FfmpegProbe.probe(staged, out.absolutePath).audioCodecId
                )
            }

            FfmpegPcmReader(out.absolutePath).use { pcm ->
                // [0.5s,0.6s): Main 440 Hz alone — calibrates the bed's
                // decoded level for the analytic model below.
                val mainOnly = windowRms(pcm, 500_000L)
                assertTrue(
                    mainOnly in 1_600.0..4_000.0,
                    "main bed level unexpected: $mainOnly"
                )
                // [1.5s,1.6s): Main + 110 Hz music bed.
                assertRms(
                    windowRms(pcm, 1_500_000L),
                    expectedRms(mainOnly, 16_000), "main+music window"
                )
                // [2.5s,2.6s): Main 880 Hz + both lanes at once.
                assertRms(
                    windowRms(pcm, 2_500_000L),
                    expectedRms(mainOnly, 16_000, 16_000), "three-way window"
                )
                // [3.2s,3.3s): the music clip ended at 3s — voice only.
                assertRms(
                    windowRms(pcm, 3_200_000L),
                    expectedRms(mainOnly, 16_000), "main+voice window"
                )
                // [3.7s,3.8s): every lane is past its window — the bed
                // returns to exactly the Main-only level, proving no
                // lane bleeds past its sample boundary.
                assertRms(
                    windowRms(pcm, 3_700_000L),
                    mainOnly, "main-only tail window"
                )
            }

            // Every source stayed read-only.
            assertEquals(mainHash, VideoTestFixtures.sha256(mainFixture))
            assertEquals(musicHash, VideoTestFixtures.sha256(musicFixture))
            assertEquals(voiceHash, VideoTestFixtures.sha256(voiceFixture))
        }

    @Test
    fun `a muted Main sound exports the lanes without the bed`() = runTest {
        val sequence = mixedSequence(sourceSoundMuted = true)
        // The plan drops the Main input entirely.
        assertEquals(
            setOf("song-music", "song-voice"),
            CompositeVideoPlan.validate(sequence).audioSourceIds
        )

        val out = File(dir, "muted-main.mp4")
        exporter.encode(sequence, out) {}
        FfmpegPcmReader(out.absolutePath).use { pcm ->
            // [0.5s,0.6s): no lane covers it and the bed is muted —
            // digital silence.
            val silent = windowRms(pcm, 500_000L)
            assertTrue(silent < 200.0, "muted window not silent: $silent")
            // [1.5s,1.6s): the 110 Hz bed alone at half full scale RMS.
            assertRms(
                windowRms(pcm, 1_500_000L),
                expectedRms(0.0, 16_000), "music-only window"
            )
            // [2.5s,2.6s): both lanes, no bed underneath.
            assertRms(
                windowRms(pcm, 2_500_000L),
                expectedRms(0.0, 16_000, 16_000), "lanes-only window"
            )
        }
    }

    @Test
    fun `an inaudible sequence exports without an audio stream`() = runTest {
        // Muted bed and no lanes: expectsAudio drives the recorder's
        // channel count — the output must carry no AAC stream rather
        // than a hidden silence track.
        val sequence = mixedSequence(sourceSoundMuted = true).copy(
            audioSources = persistentListOf(),
            audioTracks = persistentListOf()
        )
        assertEquals(
            emptySet(),
            CompositeVideoPlan.validate(sequence).audioSourceIds
        )
        val out = File(dir, "video-only.mp4")
        exporter.encode(sequence, out) {}
        resolver.resolve(out.absolutePath).use { staged ->
            FfmpegExportVerification.verify(
                staged, sequence.size, 0, sequence.duration,
                expectAudio = false
            )
            assertNull(
                FfmpegProbe.probe(staged, out.absolutePath).audioCodecId
            )
        }
    }
}
