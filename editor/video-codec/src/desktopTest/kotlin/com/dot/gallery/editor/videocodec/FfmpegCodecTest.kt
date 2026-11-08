package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.time.EditorTime
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame

/**
 * Decode-side codec tests over a deterministically generated fixture: probe
 * facts, coloured-band seeks with real timestamp semantics, quarter-turn
 * display rotation, windowed PCM reads and the packaged-runtime codec audit.
 */
class FfmpegCodecTest {

    private lateinit var dir: File
    private lateinit var fixture: File
    private val resolver = VideoSourceResolver { reference ->
        ResolvedVideoSource(reference, File(reference).name) {}
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("videocodec").toFile()
        fixture = VideoTestFixtures.createFixture(dir)
    }

    @Test
    fun `packaged codec bundle is non-GPL with required encoders`() {
        FfmpegSupport.require()
        val audit = FfmpegSupport.audit()
        assertFalse(audit.gplEnabled, "packaged FFmpeg must not enable GPL")
        assertTrue(audit.openh264, "libopenh264 encoder must be present")
        assertTrue(audit.aac, "native AAC encoder must be present")
        assertTrue(FfmpegSupport.available)
    }

    @Test
    fun `probe reports dimensions duration fps and audio`() = runTest {
        val source = resolver.resolve(fixture.absolutePath)
        try {
            val probe = FfmpegProbe.probe(source, fixture.absolutePath)
            val info = probe.info
            assertEquals(320, info.asset.size.width)
            assertEquals(240, info.asset.size.height)
            // Duration is the muxed container value; ~4 s within a frame.
            val driftUs =
                abs(info.asset.duration.toMicrosecondsFloor() - 4_000_000L)
            assertTrue(driftUs <= 50_000L, "duration drift ${driftUs}us")
            assertTrue(info.hasAudio)
            assertEquals(48_000, info.audioSampleRate)
            assertEquals(2, info.audioChannels)
            assertEquals(0, info.asset.rotationDegrees)
            assertNotNull(info.asset.frameRate)
            assertEquals(30, info.asset.frameRate?.numerator)
        } finally {
            source.close()
        }
    }

    @Test
    fun `frame seeks land inside the right colour band`() = runTest {
        val source = resolver.resolve(fixture.absolutePath)
        try {
            val probe = FfmpegProbe.probe(source, fixture.absolutePath)
            FfmpegPixelConverter(
                probe.colorspace, probe.colorRange, probe.rawSize.height
            ).use { conv ->
                FfmpegVideoReader(
                    fixture.absolutePath, conv, 0
                ).use { reader ->
                    listOf(500_000L, 1_500_000L, 2_500_000L, 3_500_000L)
                        .forEachIndexed { band, targetUs ->
                            val frame = reader.frameAt(
                                EditorTime.fromMicroseconds(targetUs),
                                VideoTestFixtures.WIDTH
                            )
                            val (r, g, b) = VideoTestFixtures.bandAt(band)
                            VideoTestFixtures.assertDominant(
                                frame.pixels.copyPixels()[0], r, g, b
                            )
                            // The reported timestamp is the frame's real PTS:
                            // at or before the target, at most one frame of
                            // source cadence (plus 1 ms slack) behind it.
                            val frameUs = frame.time.toMicrosecondsFloor()
                            assertTrue(
                                frameUs <= targetUs,
                                "frame pts ${frameUs}us past target ${targetUs}us"
                            )
                            val gap = targetUs - frameUs
                            assertTrue(
                                gap <= 33_334L + 1_000L,
                                "frame gap ${gap}us exceeds one frame"
                            )
                        }
                }
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun `rotated fixture reports rotation and previews upright`() = runTest {
        val rotated = VideoTestFixtures.createRotatedFixture(dir)
        val source = resolver.resolve(rotated.absolutePath)
        try {
            val probe = FfmpegProbe.probe(source, rotated.absolutePath)
            assertEquals(
                90, probe.info.asset.rotationDegrees % 180,
                "display rotation should read as a quarter turn"
            )
            FfmpegPixelConverter(
                probe.colorspace, probe.colorRange, probe.rawSize.height
            ).use { conv ->
                FfmpegVideoReader(
                    rotated.absolutePath, conv,
                    probe.info.asset.rotationDegrees
                ).use { reader ->
                    val frame = reader.frameAt(
                        EditorTime.Zero, VideoTestFixtures.WIDTH
                    )
                    // Raw 320x240 + quarter turn => upright 240x320 pixels.
                    assertEquals(240, frame.pixels.size.width)
                    assertEquals(320, frame.pixels.size.height)
                }
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun `pcm reader serves exact windows with tone content`() = runTest {
        FfmpegPcmReader(fixture.absolutePath).use { pcm ->
            // One second of 440 Hz at the start.
            val first = pcm.read(0, 48_000)
            assertEquals(48_000 * 2, first.size)
            assertTrue(first.any { it != 0.toShort() }, "tone expected")
            // And 880 Hz three seconds in — a sparse window stays exact.
            val late = pcm.read(48_000L * 3, 8_000)
            assertEquals(8_000 * 2, late.size)
            assertTrue(late.any { it != 0.toShort() }, "tone expected")
            // Past EOF the window is honest silence, never renumbered.
            val tail = pcm.read(48_000L * 4 + 1_000, 4_000)
            assertEquals(4_000 * 2, tail.size)
        }
    }

    /**
     * A backward reseek must re-serve the same window at the same position:
     * the window at 3.011 s (sample 144 528, off the packet grid) read after
     * a fresh seek matches the sequential baseline within ±1 LSB — AAC
     * re-decode across a seek boundary is not bit-exact (priming state),
     * but a real misalignment would shift every sample by hundreds, so
     * element-wise |diff| ≤ 1 is a strict position check, not a tolerance
     * for drift.
     */
    @Test
    fun `pcm reread after a backward reseek matches the sequential baseline`() =
        runTest {
            val at = 144_528L // 3.011 s at 48 kHz
            val baseline = FfmpegPcmReader(fixture.absolutePath).use { pcm ->
                pcm.read(0, 48_000 * 4) // decode straight through to the target
                pcm.read(at, 4_800)
            }
            val reread = FfmpegPcmReader(fixture.absolutePath).use { pcm ->
                pcm.read(48_000L * 3 + 20_000, 1_000) // land ahead of target
                pcm.read(at, 4_800) // force the backward reseek path
            }
            assertEquals(baseline.size, reread.size)
            var maxDiff = 0
            for (i in baseline.indices) {
                val d = abs(baseline[i].toInt() - reread[i].toInt())
                if (d > maxDiff) maxDiff = d
            }
            assertTrue(
                maxDiff <= 1,
                "reseek misaligned: max sample diff ±$maxDiff"
            )
        }

    /**
     * RGB preview bounds: maxEdge=1 collapses to a 1x1 thumbnail, an odd
     * 3x5 preview stays legal (even alignment is only an encoder rule) and
     * a non-positive bound is rejected.
     */
    @Test
    fun `preview bounds allow tiny and odd sizes but reject non-positive`() =
        runTest {
            val rotated = VideoTestFixtures.createRotatedFixture(dir)
            val source = resolver.resolve(rotated.absolutePath)
            try {
                val probe = FfmpegProbe.probe(source, rotated.absolutePath)
                FfmpegPixelConverter(
                    probe.colorspace, probe.colorRange, probe.rawSize.height
                ).use { conv ->
                    FfmpegVideoReader(
                        rotated.absolutePath, conv,
                        probe.info.asset.rotationDegrees
                    ).use { reader ->
                        val tiny = reader.frameAt(EditorTime.Zero, 1)
                        assertEquals(1, tiny.pixels.size.width)
                        assertEquals(1, tiny.pixels.size.height)
                        // 240x320 upright at maxEdge 5 -> 3x5, odd on both.
                        val odd = reader.frameAt(EditorTime.Zero, 5)
                        assertEquals(3, odd.pixels.size.width)
                        assertEquals(5, odd.pixels.size.height)
                        assertFailsWith<IllegalArgumentException> {
                            reader.frameAt(EditorTime.Zero, 0)
                        }
                    }
                }
            } finally {
                source.close()
            }
        }

    @Test
    fun `a source without audio is reported silent`() = runTest {
        val silent = File(dir, "silent.mp4")
        val recorder = FFmpegFrameRecorder(silent.absolutePath, 160, 120, 0)
            .apply {
                format = "mp4"
                videoCodecName = "libopenh264"
                pixelFormat = AV_PIX_FMT_YUV420P
                frameRate = 30.0
                gopSize = 30
                videoBitrate = 200_000
            }
        val rgb = ByteBuffer.allocateDirect(160 * 120 * 3)
        try {
            recorder.start()
            repeat(30) {
                rgb.clear()
                while (rgb.hasRemaining()) rgb.put(64.toByte())
                rgb.flip()
                recorder.recordImage(
                    160, 120, Frame.DEPTH_UBYTE, 3,
                    160 * 3, AV_PIX_FMT_RGB24, rgb
                )
            }
            recorder.stop()
        } finally {
            recorder.release()
        }
        val source = resolver.resolve(silent.absolutePath)
        try {
            val probe = FfmpegProbe.probe(source, silent.absolutePath)
            assertFalse(probe.info.hasAudio)
            assertEquals(null, probe.info.audioSampleRate)
            assertEquals(null, probe.info.audioChannels)
        } finally {
            source.close()
        }
    }

    @Test
    fun `missing or junk inputs fail typed`() = runTest {
        assertFailsWith<Exception> {
            FfmpegProbe.probe(
                resolver.resolve(File(dir, "missing.mp4").absolutePath),
                "missing.mp4"
            )
        }
        val junk = File(dir, "junk.mp4").apply { writeBytes(ByteArray(2048)) }
        assertFailsWith<Exception> {
            FfmpegProbe.probe(resolver.resolve(junk.absolutePath), "junk.mp4")
        }
    }
}
