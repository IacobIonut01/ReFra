package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.video.VideoAudioOutput
import com.dot.gallery.editor.render.video.VideoFrame
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Real-engine playback tests: [FfmpegVideoPlayback] over the packaged
 * decoder with a fake [VideoAudioOutput] — no hardware needed, the sink
 * consumes written samples in virtual time, so the audio-clock path is
 * exercised exactly like a device.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class FfmpegVideoPlaybackTest {

    private lateinit var dir: File
    private lateinit var fixture: File

    /** Counts leases so teardown can be proven. */
    private class CountingResolver : VideoSourceResolver {
        var opened = 0
        var closed = 0
        override suspend fun resolve(reference: String): ResolvedVideoSource {
            opened++
            return ResolvedVideoSource(reference, File(reference).name) {
                closed++
            }
        }
    }

    /** A sink whose "device" consumes frames at the declared rate. */
    private class FakeAudioOutput : VideoAudioOutput {
        var started = false
        var closed = false
        var failOnStart: Throwable? = null
        var failOnClose: Throwable? = null

        /** When set, every written block is appended for content checks. */
        var capture: MutableList<ShortArray>? = null

        @Volatile
        private var played = 0L
        private var rate = 48_000

        override val playedFrames: Long get() = played

        override fun start(sampleRate: Int, channels: Int) {
            failOnStart?.let { throw it }
            started = true
            rate = sampleRate
        }

        override suspend fun write(
            samples: ShortArray, offset: Int, count: Int
        ) {
            // Occupy the virtual clock for as long as real hardware would
            // take to consume this block, then count it as played.
            delay(count / 2 * 1_000L / rate)
            capture?.add(samples.copyOfRange(offset, offset + count))
            played += count / 2
        }

        override fun pauseAndFlush() {}

        override fun close() {
            closed = true
            failOnClose?.let { throw it }
        }
    }

    // The silent-source clock path is driven by an injected TestTimeSource
    // the test steps manually (kotlin.time.TestTimeSource).

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("videoplay").toFile()
        fixture = VideoTestFixtures.createFixture(dir)
    }

    private fun sequenceFor(
        reference: String,
        duration: EditorTime,
        sourceStart: EditorTime = EditorTime.Zero,
        sourceDuration: EditorTime = duration
    ): VideoSequence {
        val asset = MediaAsset.Video(
            id = "source", reference = reference,
            size = PixelSize(VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT),
            duration = sourceDuration,
            frameRate = FrameRate(30), hasAudio = true
        )
        return VideoSequence(
            size = asset.size,
            frameRate = FrameRate(30),
            sources = persistentListOf(asset),
            tracks = persistentListOf(
                VideoTrack(
                    "track", persistentListOf(
                        VideoClip(
                            "clip-1", "source", EditorTime.Zero,
                            TimeRange(sourceStart, duration)
                        )
                    )
                )
            )
        )
    }

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    @Test
    fun `play from a non-zero offset tracks the relative clock`() = runTest {
        val resolver = CountingResolver()
        val sink = FakeAudioOutput()
        val playback = FfmpegVideoPlayback(
            resolver, { sink }, StandardTestDispatcher(testScheduler),
            previewMaxEdge = 160
        )
        val sourceDuration = us(4_000_000)
        val sequence = sequenceFor(fixture.absolutePath, sourceDuration)
        val frames = mutableListOf<EditorTime>()
        val positions = mutableListOf<EditorTime>()
        playback.play(
            sequence, us(1_000_000), muted = false,
            onFrame = { frames += it.time },
            onPosition = { positions += it }
        )
        // Completes inside the virtual deadline — a non-zero start never
        // stalls on the lead counter.
        assertTrue(sink.started)
        assertTrue(frames.isNotEmpty())
        // Frame times are sequence times, starting at the playhead.
        assertEquals(us(1_000_000), frames.first())
        assertTrue(
            frames.all { it >= us(1_000_000) && it < sourceDuration },
            "frame times escape [start, duration): $frames"
        )
        assertEquals(sourceDuration, positions.last())
        assertEquals(resolver.opened, resolver.closed)
    }

    @Test
    fun `a trimmed clip reports sequence-time frames`() = runTest {
        val resolver = CountingResolver()
        val sink = FakeAudioOutput()
        val playback = FfmpegVideoPlayback(
            resolver, { sink }, StandardTestDispatcher(testScheduler),
            previewMaxEdge = 160
        )
        // Keep source [3 s, 4 s) on a 1 s timeline.
        val sequence = sequenceFor(
            fixture.absolutePath, us(1_000_000),
            sourceStart = us(3_000_000), sourceDuration = us(4_000_000)
        )
        val frames = mutableListOf<EditorTime>()
        val positions = mutableListOf<EditorTime>()
        playback.play(
            sequence, EditorTime.Zero, muted = false,
            onFrame = { frames += it.time },
            onPosition = { positions += it }
        )
        assertTrue(frames.isNotEmpty())
        assertEquals(EditorTime.Zero, frames.first())
        assertTrue(
            frames.all { it >= EditorTime.Zero && it < us(1_000_000) },
            "trimmed clip leaked source timestamps: $frames"
        )
        assertEquals(us(1_000_000), positions.last())
    }

    @Test
    fun `play at the end restarts from zero`() = runTest {
        val resolver = CountingResolver()
        val playback = FfmpegVideoPlayback(
            resolver, { FakeAudioOutput() },
            StandardTestDispatcher(testScheduler), previewMaxEdge = 160
        )
        val sequence = sequenceFor(fixture.absolutePath, us(4_000_000))
        val frames = mutableListOf<EditorTime>()
        playback.play(
            sequence, sequence.duration, muted = false,
            onFrame = { frames += it.time }, onPosition = {}
        )
        assertEquals(EditorTime.Zero, frames.first())
    }

    @Test
    fun `silent long playback never overflows the wall clock`() = runTest {
        val silent = VideoTestFixtures.createSilentFixture(dir, seconds = 16)
        val clock = TestTimeSource()
        val resolver = CountingResolver()
        val playback = FfmpegVideoPlayback(
            resolver, { FakeAudioOutput() },
            StandardTestDispatcher(testScheduler),
            previewMaxEdge = 160, timeSource = clock
        )
        val sourceDuration = us(16_000_000)
        val sequence = sequenceFor(fixture.absolutePath, us(16_000_000))
            .let { seq ->
                // The asset must describe the silent file.
                seq.copy(
                    sources = persistentListOf(
                        seq.sources[0].copy(
                            reference = silent.absolutePath, hasAudio = false
                        )
                    )
                )
            }
        val positions = mutableListOf<EditorTime>()
        val job = launch {
            playback.play(
                sequence, EditorTime.Zero, muted = true,
                onFrame = {}, onPosition = { positions += it }
            )
        }
        // Step the clock past the old ~13 s overflow boundary in 1 s jumps,
        // giving the scheduler a few polls each step.
        var steps = 0
        while (job.isActive && steps < 40) {
            clock += 1.seconds
            testScheduler.advanceTimeBy(120)
            testScheduler.runCurrent()
            steps++
        }
        job.join()
        assertFalse(job.isActive)
        // The exact end marker is emitted before the engine unwinds.
        assertEquals(sourceDuration, positions.last())
        assertEquals(resolver.opened, resolver.closed)
    }

    @Test
    fun `audio start failure still releases every native handle`() = runTest {
        val resolver = CountingResolver()
        val sink = FakeAudioOutput()
        sink.failOnStart = IOException("no audio device")
        val playback = FfmpegVideoPlayback(
            resolver, { sink }, StandardTestDispatcher(testScheduler),
            previewMaxEdge = 160
        )
        val sequence = sequenceFor(fixture.absolutePath, us(4_000_000))
        assertFailsWith<IOException> {
            playback.play(sequence, EditorTime.Zero, false, {}, {})
        }
        assertTrue(sink.closed)
        assertEquals(resolver.opened, resolver.closed)
        // Nothing leaked — a healthy sink plays afterwards.
        val second = FakeAudioOutput()
        val recovery = FfmpegVideoPlayback(
            resolver, { second }, StandardTestDispatcher(testScheduler),
            previewMaxEdge = 160
        )
        recovery.play(sequence, EditorTime.Zero, false, {}, {})
        assertTrue(second.started)
        assertEquals(resolver.opened, resolver.closed)
    }

    @Test
    fun `a failing sink close cannot strand the source lease`() = runTest {
        val resolver = CountingResolver()
        val sink = FakeAudioOutput()
        sink.failOnClose = IOException("device died on close")
        val playback = FfmpegVideoPlayback(
            resolver, { sink }, StandardTestDispatcher(testScheduler),
            previewMaxEdge = 160
        )
        val sequence = sequenceFor(fixture.absolutePath, us(4_000_000))
        // The close failure propagates, but the resolver lease is released
        // in the same finally — no stranded file handle behind the error.
        assertFailsWith<IOException> {
            playback.play(sequence, EditorTime.Zero, false, {}, {})
        }
        assertTrue(sink.closed)
        assertEquals(resolver.opened, resolver.closed)
    }

    @Test
    fun `cancelling play releases the source lease and sink`() = runTest {
        val resolver = CountingResolver()
        val sink = FakeAudioOutput()
        val playback = FfmpegVideoPlayback(
            resolver, { sink }, StandardTestDispatcher(testScheduler),
            previewMaxEdge = 160
        )
        val sequence = sequenceFor(fixture.absolutePath, us(4_000_000))
        val job = launch {
            playback.play(sequence, EditorTime.Zero, false, {}, {})
        }
        // Let the engine start and get a few blocks into playback.
        testScheduler.advanceTimeBy(200)
        testScheduler.runCurrent()
        job.cancel()
        job.join()
        testScheduler.advanceUntilIdle()
        assertTrue(sink.closed)
        assertEquals(resolver.opened, resolver.closed)
    }

    @Test
    fun `a composite sequence decodes every active clip on its own clock`() =
        runTest {
            val resolver = CountingResolver()
            val sink = FakeAudioOutput()
            val playback = FfmpegVideoPlayback(
                resolver, { sink }, StandardTestDispatcher(testScheduler),
                previewMaxEdge = 160
            )
            val overlayFixture =
                VideoTestFixtures.createFixture(dir, "overlay.mp4")
            val duration = us(4_000_000)
            fun asset(id: String, file: File) = MediaAsset.Video(
                id = id, reference = file.absolutePath,
                size = PixelSize(
                    VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
                ),
                duration = duration,
                frameRate = FrameRate(30), hasAudio = true
            )
            // Main plays the full programme; the Overlay lane drops a
            // white second at half scale over [2s,3s).
            val sequence = VideoSequence(
                size = PixelSize(
                    VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
                ),
                frameRate = FrameRate(30),
                sources = persistentListOf(
                    asset("src-main", fixture),
                    asset("src-overlay", overlayFixture)
                ),
                tracks = persistentListOf(
                    VideoTrack(
                        "main",
                        kind = VideoTrackKind.Main,
                        clips = persistentListOf(
                            VideoClip(
                                "m", "src-main", EditorTime.Zero,
                                TimeRange(EditorTime.Zero, duration)
                            )
                        )
                    ),
                    VideoTrack(
                        "overlay",
                        kind = VideoTrackKind.Overlay,
                        clips = persistentListOf(
                            VideoClip(
                                "o", "src-overlay", us(2_000_000),
                                TimeRange(us(3_000_000), us(1_000_000)),
                                transform = VideoLayerTransform(scale = 0.5f)
                            )
                        )
                    )
                )
            )
            val frames = mutableListOf<VideoFrame>()
            val positions = mutableListOf<EditorTime>()
            playback.play(
                sequence, us(2_400_000), muted = false,
                onFrame = { frames += it },
                onPosition = { positions += it }
            )
            assertTrue(frames.isNotEmpty())
            // Frame times stay on the sequence clock from the playhead.
            assertTrue(
                frames.all { it.time >= us(2_400_000) && it.time < duration },
                "frame times escape the tail: ${frames.map { it.time }}"
            )
            assertEquals(duration, positions.last())
            // Both sources were resolved for the composite and released.
            assertEquals(2, resolver.opened)
            assertEquals(resolver.opened, resolver.closed)
            // The composited frame: overlay white over the Main blue band.
            val mid = frames.first { it.time >= us(2_500_000) }
            val px = mid.pixels.copyPixels()
            val size = mid.pixels.size
            val centre = px[(size.height / 2) * size.width + size.width / 2]
            val r = (centre shr 16) and 0xFF
            val g = (centre shr 8) and 0xFF
            val b = centre and 0xFF
            assertTrue(
                minOf(r, g, b) > 150 && maxOf(r, g, b) - minOf(r, g, b) < 80,
                "overlay centre not white-ish: r=$r g=$g b=$b"
            )
            VideoTestFixtures.assertDominant(px[0], 0, 0, 255)
            assertTrue(sink.started)
            assertTrue(sink.closed)
        }

    @Test
    fun `an audio lane is mixed into playback on its own window`() = runTest {
        val resolver = CountingResolver()
        val sink = FakeAudioOutput()
        val captured = mutableListOf<ShortArray>()
        sink.capture = captured
        val playback = FfmpegVideoPlayback(
            resolver, { sink }, StandardTestDispatcher(testScheduler),
            previewMaxEdge = 160
        )
        // 110 Hz bed over timeline [1s,3s) — a real WAV the lane reader
        // opens through the audio-source resolve/probe path.
        val music = VideoTestFixtures.createToneFile(dir, "music.wav", 110.0)
        val duration = us(4_000_000L)
        val sequence = VideoSequence(
            size = PixelSize(
                VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
            ),
            frameRate = FrameRate(30),
            sources = persistentListOf(
                MediaAsset.Video(
                    id = "src-main", reference = fixture.absolutePath,
                    size = PixelSize(
                        VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
                    ),
                    duration = duration,
                    frameRate = FrameRate(30), hasAudio = true
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
                MediaAsset.Audio(
                    id = "song", reference = music.absolutePath,
                    duration = duration,
                    sampleRate = VideoTestFixtures.SAMPLE_RATE,
                    channels = 2
                )
            ),
            audioTracks = persistentListOf(
                AudioTrack(
                    "music-lane", name = "Music",
                    clips = persistentListOf(
                        AudioClip(
                            "ac-1", "song", us(1_000_000L),
                            TimeRange(EditorTime.Zero, us(2_000_000L))
                        )
                    )
                )
            )
        )
        val positions = mutableListOf<EditorTime>()
        playback.play(
            sequence, EditorTime.Zero, muted = false,
            onFrame = {}, onPosition = { positions += it }
        )
        assertEquals(duration, positions.last())
        // The audio-only source was resolved and released alongside the
        // Main video source — no lease survives playback.
        assertEquals(2, resolver.opened)
        assertEquals(resolver.opened, resolver.closed)

        // Content oracle on the captured sink stream — windows are the
        // same timeline samples the export path writes: [0.5s,0.6s) is
        // the 440 Hz bed alone, [1.5s,1.6s) adds the lane.
        fun capturedRms(startSample: Long, frames: Int): Double {
            val merged = ShortArray(frames * 2)
            var at = 0
            var cursor = 0L
            for (block in captured) {
                val blockFrames = block.size / 2
                val overlapStart = maxOf(startSample, cursor)
                val overlapEnd = minOf(startSample + frames, cursor + blockFrames)
                if (overlapEnd > overlapStart) {
                    System.arraycopy(
                        block, (overlapStart - cursor).toInt() * 2,
                        merged, (overlapStart - startSample).toInt() * 2,
                        (overlapEnd - overlapStart).toInt() * 2
                    )
                }
                cursor += blockFrames
            }
            return VideoTestFixtures.rms(merged)
        }
        val bedOnly = capturedRms(24_000L, 4_800)
        assertTrue(bedOnly in 1_600.0..4_000.0, "bed level: $bedOnly")
        // The lane adds a 16 000-amp tone at centre pan: its per-channel
        // RMS contribution is 16 000/2 = 8 000, summing in quadrature.
        val withLane = capturedRms(72_000L, 4_800)
        val expected = kotlin.math.sqrt(bedOnly * bedOnly + 8_000.0 * 8_000.0)
        assertTrue(
            withLane in expected * 0.85..expected * 1.15,
            "lane window rms $withLane, expected ≈ $expected"
        )
        // Past the lane (3.7 s) the bed is alone again — the lane's
        // window is sample-exact, never bleeding past its end.
        val tail = capturedRms(177_600L, 4_800)
        assertTrue(
            tail in bedOnly * 0.85..bedOnly * 1.15,
            "tail rms $tail vs bed $bedOnly"
        )
        assertTrue(sink.closed)
    }
}
