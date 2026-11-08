package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.junit.Assume

/**
 * Encode-side codec tests: a two-clip cut export re-encoded through the
 * packaged runtime, verified by reopening and by the provisioned ffprobe
 * oracle where available — never a system ffmpeg for the encode itself.
 */
class FfmpegExportTest {

    private lateinit var dir: File
    private lateinit var fixture: File
    private lateinit var rotatedFixture: File
    private lateinit var sourceProbe: FfmpegProbeResult
    private val resolver = VideoSourceResolver { reference ->
        ResolvedVideoSource(reference, File(reference).name) {}
    }
    private lateinit var exporter: FfmpegVideoExporter

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("videoexport").toFile()
        fixture = VideoTestFixtures.createFixture(dir)
        rotatedFixture = VideoTestFixtures.createRotatedFixture(dir)
        kotlinx.coroutines.runBlocking {
            resolver.resolve(fixture.absolutePath).use { source ->
                sourceProbe = FfmpegProbe.probe(source, fixture.absolutePath)
            }
        }
        exporter = FfmpegVideoExporter(resolver)
    }

    /** The cut sequence: source [1s,2s) + [3s,4s) packed on a 2 s timeline. */
    private fun cutSequence(reference: String, duration: EditorTime): VideoSequence {
        val asset = MediaAsset.Video(
            id = "source", reference = reference,
            size = PixelSize(
                VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
            ),
            duration = duration,
            frameRate = FrameRate(30), hasAudio = true
        )
        val track = VideoTrack(
            "track", persistentListOf(
                VideoClip(
                    "clip-1", "source", EditorTime.Zero,
                    TimeRange(us(1_000_000), us(1_000_000))
                ),
                VideoClip(
                    "clip-2", "source", us(1_000_000),
                    TimeRange(us(3_000_000), us(1_000_000))
                )
            )
        )
        return VideoSequence(
            size = asset.size,
            frameRate = FrameRate(30),
            sources = persistentListOf(asset),
            tracks = persistentListOf(track)
        )
    }

    private fun fullSequence(reference: String, duration: EditorTime) =
        VideoSequence(
            size = PixelSize(VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT),
            frameRate = FrameRate(30),
            sources = persistentListOf(
                MediaAsset.Video(
                    id = "source", reference = reference,
                    size = PixelSize(
                        VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
                    ),
                    duration = duration,
                    frameRate = FrameRate(30), hasAudio = true
                )
            ),
            tracks = persistentListOf(
                VideoTrack(
                    "track", persistentListOf(
                        VideoClip(
                            "clip-1", "source", EditorTime.Zero,
                            TimeRange(EditorTime.Zero, duration)
                        )
                    )
                )
            )
        )

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    @Test
    fun `cut export re-encodes only the kept ranges`() = runTest {
        val sourceHashBefore = VideoTestFixtures.sha256(fixture)
        val sequence = cutSequence(
            fixture.absolutePath, sourceProbe.info.asset.duration
        )
        val out = File(dir, "cut.mp4")
        val progress = mutableListOf<Float>()
        exporter.encode(sequence, out) { progress += it }
        assertTrue(out.isFile && out.length() > 0)
        assertTrue(progress.isNotEmpty() && progress.last() <= 0.9f)

        // Self-verification against the encode plan.
        resolver.resolve(out.absolutePath).use { staged ->
            FfmpegExportVerification.verify(
                staged, sourceProbe.rawSize,
                sourceProbe.info.asset.rotationDegrees,
                sequence.duration, expectAudio = true
            )
            val probe = FfmpegProbe.probe(staged, out.absolutePath)
            assertEquals(AV_CODEC_ID_H264, probe.videoCodecId)
            assertEquals(AV_CODEC_ID_AAC, probe.audioCodecId)
            val driftUs = abs(
                probe.info.asset.duration.toMicrosecondsFloor() - 2_000_000L
            )
            assertTrue(driftUs <= 56_000L, "duration drift ${driftUs}us")
        }

        // Content: second one of the output is the source's green second;
        // second two is the source's white second.
        val outProbe = resolver.resolve(out.absolutePath).use {
            FfmpegProbe.probe(it, out.absolutePath)
        }
        FfmpegPixelConverter(
            outProbe.colorspace, outProbe.colorRange, outProbe.rawSize.height
        ).use { conv ->
                FfmpegVideoReader(out.absolutePath, conv, 0).use { reader ->
                    var frame = reader.frameAt(
                        us(500_000), VideoTestFixtures.WIDTH
                    )
                    VideoTestFixtures.assertDominant(
                        frame.pixels.copyPixels()[0], 0, 200, 0
                    )
                    frame = reader.frameAt(
                        us(1_500_000), VideoTestFixtures.WIDTH
                    )
                    VideoTestFixtures.assertDominant(
                        frame.pixels.copyPixels()[0], 235, 235, 235
                    )
                }
            }

        // Audio survived the cut at the right places: output [0,1s) is the
        // source's 440 Hz second [1,2) and output [1,2s) its 880 Hz [3,4).
        FfmpegPcmReader(out.absolutePath).use { pcm ->
            val first = pcm.read(12_000L, 4_800)
            val second = pcm.read(60_000L, 4_800)
            assertTrue(
                VideoTestFixtures.risingCrossings(first) in 42..46,
                "first retained segment is not 440 Hz"
            )
            assertTrue(
                VideoTestFixtures.risingCrossings(second) in 86..90,
                "second retained segment is not 880 Hz"
            )
        }

        // The source file is only ever read.
        assertEquals(sourceHashBefore, VideoTestFixtures.sha256(fixture))
    }

    @Test
    fun `ffprobe oracle confirms the exported streams`() {
        // Recorded skip, not a silent pass, when no binary is provisioned.
        Assume.assumeTrue(
            "provisioned ffprobe required for the oracle check",
            oracleProbe() != null
        )
        runTest {
            val ffprobe = oracleProbe()!!
            val sequence = cutSequence(
                fixture.absolutePath, sourceProbe.info.asset.duration
            )
            val out = File(dir, "oracle.mp4")
            exporter.encode(sequence, out) {}
            val rawJson = ProcessBuilder(
                ffprobe, "-v", "error",
                "-show_entries",
                "stream=index,codec_type,width,height,r_frame_rate," +
                    "avg_frame_rate,duration,nb_frames,sample_rate,channels" +
                    ":format=duration",
                "-of", "json", out.absolutePath
            ).redirectErrorStream(true).start()
                .inputStream.bufferedReader().readText()
            // ffprobe pretty-prints; compare on a compacted copy.
            val json = rawJson.replace(" ", "")
            assertTrue("\"codec_type\":\"video\"" in json, json)
            assertTrue("\"width\":320" in json, json)
            assertTrue("\"height\":240" in json, json)
            assertTrue("\"r_frame_rate\":\"30/1\"" in json, json)
            assertTrue("\"nb_frames\":\"60\"" in json, json)
            assertTrue("\"codec_type\":\"audio\"" in json, json)
            assertTrue("\"sample_rate\":\"48000\"" in json, json)
            assertTrue("\"channels\":2" in json, json)
            // Container duration ≈ 2 s (encoder/container padding only).
            val formatDuration = Regex("\"duration\":\"([0-9.]+)\"")
                .findAll(json).map { it.groupValues[1].toDouble() }.maxOrNull()
            assertTrue(formatDuration != null, json)
            assertTrue(
                abs(formatDuration!! - 2.0) <= 0.05,
                "container duration $formatDuration"
            )
        }
    }

    @Test
    fun `rotated source exports with preserved display rotation`() = runTest {
        resolver.resolve(rotatedFixture.absolutePath).use { source ->
            val probe = FfmpegProbe.probe(source, rotatedFixture.absolutePath)
            val sequence = fullSequence(
                rotatedFixture.absolutePath, probe.info.asset.duration
            )
            val out = File(dir, "rotated-out.mp4")
            exporter.encode(sequence, out) {}
            resolver.resolve(out.absolutePath).use { staged ->
                FfmpegExportVerification.verify(
                    staged, probe.rawSize, probe.info.asset.rotationDegrees,
                    sequence.duration, probe.info.hasAudio
                )
                val outProbe = FfmpegProbe.probe(staged, out.absolutePath)
                // Pixels stay raw; the display matrix carries the turn.
                assertEquals(
                    probe.info.asset.rotationDegrees,
                    outProbe.info.asset.rotationDegrees
                )
                assertEquals(probe.rawSize, outProbe.rawSize)
            }
        }
    }

    @Test
    fun `unsupported sequence shapes are rejected`() = runTest {
        // Multi-source, overlay and graded recipes are no longer
        // "unsupported" — they composite; see VideoCompositeExportTest.
        val sequence = cutSequence(
            fixture.absolutePath, sourceProbe.info.asset.duration
        )
        val wrongRate = sequence.copy(frameRate = FrameRate(24))
        assertFailsWith<IllegalArgumentException> {
            exporter.encode(wrongRate, File(dir, "rate.mp4")) {}
        }
    }

    /** The provisioned oracle binary, or null when the host lacks one. */
    private fun oracleProbe(): String? = listOf(
        "/opt/homebrew/bin/ffprobe", "/usr/local/bin/ffprobe", "/usr/bin/ffprobe"
    ).firstOrNull { File(it).canExecute() }
}
