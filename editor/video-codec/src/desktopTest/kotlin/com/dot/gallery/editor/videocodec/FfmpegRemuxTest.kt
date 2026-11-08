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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264

/**
 * Packet-remux path tests: trims and cuts on the 4 s colour-band fixture
 * (keyframes every second — the 30-frame GOP) take the copy path, with only
 * the GOP span ahead of an unaligned cut re-encoded. Every assertion also
 * decodes the result so a muxed-but-corrupt splice cannot pass.
 */
class FfmpegRemuxTest {

    private lateinit var dir: File
    private lateinit var fixture: File
    private lateinit var sourceProbe: FfmpegProbeResult
    private val resolver = VideoSourceResolver { reference ->
        ResolvedVideoSource(reference, File(reference).name) {}
    }
    private lateinit var exporter: FfmpegVideoExporter

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("videoremux").toFile()
        fixture = VideoTestFixtures.createFixture(dir)
        kotlinx.coroutines.runBlocking {
            resolver.resolve(fixture.absolutePath).use { source ->
                sourceProbe = FfmpegProbe.probe(source, fixture.absolutePath)
            }
        }
        exporter = FfmpegVideoExporter(resolver)
    }

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    private fun sequence(
        reference: String, vararg ranges: Pair<Long, Long>
    ): VideoSequence {
        val duration = sourceProbe.info.asset.duration
        val asset = MediaAsset.Video(
            id = "source", reference = reference,
            size = PixelSize(VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT),
            duration = duration, frameRate = FrameRate(30), hasAudio = true
        )
        var cursor = EditorTime.Zero
        val clips = ranges.mapIndexed { i, (startUs, endUs) ->
            val range = TimeRange(us(startUs), us(endUs - startUs))
            VideoClip("clip-$i", "source", cursor, range).also {
                cursor += range.duration
            }
        }
        return VideoSequence(
            size = asset.size, frameRate = FrameRate(30),
            sources = persistentListOf(asset),
            tracks = persistentListOf(
                VideoTrack("track", persistentListOf(*clips.toTypedArray()))
            )
        )
    }

    /** Probe + frame grab helper: decoded dominant channel at [atUs]. */
    private suspend fun bandAt(out: File, atUs: Long): Int {
        val probe = probe(out)
        FfmpegPixelConverter(
            probe.colorspace, probe.colorRange, probe.rawSize.height
        ).use { conv ->
            FfmpegVideoReader(out.absolutePath, conv, 0).use { reader ->
                return reader.frameAt(us(atUs), VideoTestFixtures.WIDTH)
                    .pixels.copyPixels()[0]
            }
        }
    }

    private suspend fun probe(out: File): FfmpegProbeResult =
        resolver.resolve(out.absolutePath).use {
            FfmpegProbe.probe(it, out.absolutePath)
        }

    @Test
    fun `keyframe-aligned trim is a pure packet copy`() = runTest {
        // [1s,3s) — starts exactly on a GOP boundary: no re-encode at all.
        val seq = sequence(fixture.absolutePath, 1_000_000L to 3_000_000L)
        val out = File(dir, "aligned.mp4")
        val report = exporter.encode(seq, out) {}
        assertTrue(out.isFile && out.length() > 0)

        // The remux path keeps the source codec and reports it.
        assertEquals(AV_CODEC_ID_H264, report.videoCodecId)
        val outProbe = probe(out)
        assertEquals(AV_CODEC_ID_H264, outProbe.videoCodecId)
        assertEquals(AV_CODEC_ID_AAC, outProbe.audioCodecId)
        assertEquals(sourceProbe.rawSize, outProbe.rawSize)
        val driftUs = abs(
            outProbe.info.asset.duration.toMicrosecondsFloor() - 2_000_000L
        )
        assertTrue(
            driftUs <= 90_000L + report.slackTicks.toUs(),
            "duration drift ${driftUs}us"
        )

        // Copied content: first frame is the source's green second,
        // mid-second-two is the blue band.
        VideoTestFixtures.assertDominant(bandAt(out, 60_000), 0, 200, 0)
        VideoTestFixtures.assertDominant(bandAt(out, 1_600_000), 0, 0, 255)

        // Copied audio is the source's: 440 Hz in second one, 880 Hz after.
        FfmpegPcmReader(out.absolutePath).use { pcm ->
            val first = pcm.read(12_000L, 4_800)
            val second = pcm.read(60_000L, 4_800)
            assertTrue(VideoTestFixtures.risingCrossings(first) in 42..46)
            assertTrue(VideoTestFixtures.risingCrossings(second) in 86..90)
        }

        // The staged file passes the standard export verification.
        resolver.resolve(out.absolutePath).use { staged ->
            FfmpegExportVerification.verify(
                staged, sourceProbe.rawSize, 0, seq.duration, true,
                report.videoCodecId, report.slackTicks
            )
        }
    }

    @Test
    fun `unaligned trim re-encodes the boundary GOP only`() = runTest {
        // [1.5s,3s): 15 frames re-encoded, the [2s,3s) second packet-copied.
        val seq = sequence(fixture.absolutePath, 1_500_000L to 3_000_000L)
        val out = File(dir, "unaligned.mp4")
        val report = exporter.encode(seq, out) {}
        assertTrue(out.isFile && out.length() > 0)

        val outProbe = probe(out)
        val driftUs = abs(
            outProbe.info.asset.duration.toMicrosecondsFloor() - 1_500_000L
        )
        assertTrue(
            driftUs <= 90_000L + report.slackTicks.toUs(),
            "duration drift ${driftUs}us"
        )

        // First half-second is the re-encoded green tail of source [1.5,2),
        // the rest is the copied blue second.
        VideoTestFixtures.assertDominant(bandAt(out, 60_000), 0, 200, 0)
        VideoTestFixtures.assertDominant(bandAt(out, 1_100_000), 0, 0, 255)

        // Audio: source's 880 Hz tone from 2s onwards dominates the tail.
        FfmpegPcmReader(out.absolutePath).use { pcm ->
            val tail = pcm.read(55_000L, 4_800)
            assertTrue(VideoTestFixtures.risingCrossings(tail) in 86..90)
        }

        resolver.resolve(out.absolutePath).use { staged ->
            FfmpegExportVerification.verify(
                staged, sourceProbe.rawSize, 0, seq.duration, true,
                report.videoCodecId, report.slackTicks
            )
        }
    }

    @Test
    fun `multi-clip cut keeps each span decodable`() = runTest {
        // [0.5,1s) + [2.5,3s): two unaligned heads, two copied tails.
        val seq = sequence(
            fixture.absolutePath,
            500_000L to 1_000_000L,
            2_500_000L to 3_000_000L
        )
        val out = File(dir, "multi.mp4")
        val report = exporter.encode(seq, out) {}
        val outProbe = probe(out)

        val driftUs = abs(
            outProbe.info.asset.duration.toMicrosecondsFloor() - 1_000_000L
        )
        assertTrue(
            driftUs <= 90_000L + report.slackTicks.toUs(),
            "duration drift ${driftUs}us"
        )

        // Output second-by-second: red tail (source 0-1s band) then blue.
        VideoTestFixtures.assertDominant(bandAt(out, 60_000), 255, 0, 0)
        VideoTestFixtures.assertDominant(bandAt(out, 560_000), 0, 0, 255)

        resolver.resolve(out.absolutePath).use { staged ->
            FfmpegExportVerification.verify(
                staged, sourceProbe.rawSize, 0, seq.duration, true,
                report.videoCodecId, report.slackTicks
            )
        }
    }

    @Test
    fun `remux leaves the source file untouched`() = runTest {
        val before = VideoTestFixtures.sha256(fixture)
        val seq = sequence(fixture.absolutePath, 500_000L to 2_500_000L)
        exporter.encode(seq, File(dir, "readonly.mp4")) {}
        assertEquals(before, VideoTestFixtures.sha256(fixture))
    }

    private fun Long.toUs(): Long =
        this * 1_000_000L / com.dot.gallery.editor.core.time
            .EDITOR_TICKS_PER_SECOND
}
