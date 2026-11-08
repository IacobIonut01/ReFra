package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.time.EditorTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * Hardware→software failover inside [FfmpegVideoReader], driven by scripted
 * grabbers so open-time, mid-stream and seek failures are all reproducible
 * without a real device. Each produced frame carries its PTS in `AVFrame.pts`
 * so the test can verify exactly which frames the reader returned.
 */
class FfmpegVideoReaderFallbackTest {

    private val events = mutableListOf<Pair<String, Throwable?>>()
    private lateinit var previous: (String, Throwable?) -> Unit

    @BeforeTest
    fun capture() {
        previous = VideoCodecEvents.listener
        VideoCodecEvents.listener = { m, t -> events += m to t }
    }

    @AfterTest
    fun restore() {
        VideoCodecEvents.listener = previous
    }

    /**
     * One scripted grabber "file": [frames] is the frame PTS list in order
     * (microseconds). [failOnStart] makes start() throw; [failAtGrab] makes
     * the nth grab (0-based) throw; [failOnSeek] makes setVideoTimestamp
     * throw. Failures apply only while a hardware codec name is pinned —
     * the software replacement always behaves.
     */
    private class ScriptedGrabber(
        path: String,
        val frames: List<Long>,
        val failOnStart: Boolean,
        val failAtGrab: Int,
        val failOnSeek: Boolean
    ) : FFmpegFrameGrabber(path) {
        var index = 0
        var seeks = 0
        var restarts = 0

        private fun hwActive() = videoCodecName != null

        override fun start() {
            if (hwActive() && failOnStart) {
                throw Exception("mediacodec open failed")
            }
        }

        override fun getFormatContext(): AVFormatContext? = null

        override fun grabImage(): Frame? {
            if (hwActive() && failAtGrab >= 0 && index >= failAtGrab) {
                throw Exception("mediacodec died mid-stream")
            }
            if (index >= frames.size) return null
            val pts = frames[index++]
            val av = av_frame_alloc()
                ?: throw IllegalStateException("av_frame_alloc")
            av.width(2).height(2).format(AV_PIX_FMT_YUV420P)
            av.pts(pts)
            av_frame_get_buffer(av, 0)
            return Frame().apply {
                opaque = av
                timestamp = pts
            }
        }

        override fun setVideoTimestamp(timestamp: Long) {
            if (hwActive() && failOnSeek) {
                throw Exception("mediacodec seek failed")
            }
            seeks++
            // Keyframe seek: land on the last frame at or before the target.
            index = frames.indexOfLast { it <= timestamp }.coerceAtLeast(0)
        }

        override fun restart() {
            restarts++
            index = 0
        }

        override fun stop() {}
        override fun release() {}
    }

    private fun reader(
        grabbers: MutableList<ScriptedGrabber>,
        framesHw: List<Long>,
        framesSw: List<Long>,
        failOnStart: Boolean = false,
        failAtGrab: Int = -1,
        failOnSeek: Boolean = false
    ): FfmpegVideoReader {
        var count = 0
        return FfmpegVideoReader(
            "clip.mp4",
            FfmpegPixelConverter(1, 1, 8),
            0,
            hwDecoderName = "hevc_mediacodec"
        ) { path ->
            count++
            ScriptedGrabber(
                path,
                frames = if (count == 1) framesHw else framesSw,
                failOnStart = failOnStart,
                failAtGrab = failAtGrab,
                failOnSeek = failOnSeek
            ).also { grabbers += it }
        }
    }

    // EditorTime floors to a ~1.4 ns tick grid: microsecond values that are
    // multiples of 25 round-trip exactly, so scripted PTS use those.
    @Test
    fun openFailureFallsBackToSoftware() = runTest {
        val grabbers = mutableListOf<ScriptedGrabber>()
        val frames = listOf(0L, 50_000L, 75_000L)
        reader(
            grabbers, framesHw = frames, framesSw = frames, failOnStart = true
        ).use { r ->
            val f = r.rawFrameAt(EditorTime.fromMicroseconds(60_000))
            assertEquals(50_000L, f.pts(), "frame covering 60 ms")
            assertEquals(2, grabbers.size, "hardware attempt + software reopen")
            assertNull(
                grabbers[1].videoCodecName,
                "the replacement must be the software decoder"
            )
            assertTrue(events.any { it.second != null && "open" in it.first })
        }
    }

    @Test
    fun midStreamFailureResumesAtSuccessor() = runTest {
        val grabbers = mutableListOf<ScriptedGrabber>()
        val frames = listOf(0L, 50_000L, 75_000L, 100_000L, 125_000L)
        // Hardware delivers three frames (0, 50_000, 75_000) then dies; the
        // software cursor must resume with 100_000 — no duplicates, no gap.
        reader(
            grabbers, framesHw = frames, framesSw = frames, failAtGrab = 3
        ).use { r ->
            assertEquals(0L, r.rawFrameAt(EditorTime.fromMicroseconds(0)).pts())
            assertEquals(
                50_000L,
                r.rawFrameAt(EditorTime.fromMicroseconds(50_000)).pts()
            )
            // This read crosses the failure; the held 75 ms frame's interval
            // still covers 80 ms, and the successor must be 100 ms.
            assertEquals(
                75_000L,
                r.rawFrameAt(EditorTime.fromMicroseconds(80_000)).pts()
            )
            assertEquals(
                100_000L,
                r.rawFrameAt(EditorTime.fromMicroseconds(100_000)).pts()
            )
            assertTrue(events.any { it.second != null })
        }
    }

    @Test
    fun seekFailureFallsBack() = runTest {
        val grabbers = mutableListOf<ScriptedGrabber>()
        val frames = listOf(0L, 500_000L, 1_500_000L, 2_500_000L)
        reader(
            grabbers, framesHw = frames, framesSw = frames, failOnSeek = true
        ).use { r ->
            assertEquals(
                2_500_000L,
                r.rawFrameAt(EditorTime.fromMicroseconds(3_000_000)).pts()
            )
            // Backward seek past the 1 s margin → setVideoTimestamp throws.
            val f = r.rawFrameAt(EditorTime.fromMicroseconds(1_500_000))
            assertEquals(1_500_000L, f.pts())
            assertTrue(events.any { it.second != null })
        }
    }

    @Test
    fun softwareFailureStillPropagates() = runTest {
        val grabbers = mutableListOf<ScriptedGrabber>()
        reader(
            grabbers, framesHw = emptyList(), framesSw = emptyList(),
            failOnStart = true
        ).use { r ->
            // Hardware open fails → the software stream is simply empty and
            // the EOF error surfaces normally rather than being swallowed.
            assertFailsWith<java.io.EOFException> {
                r.rawFrameAt(EditorTime.fromMicroseconds(0))
            }
        }
    }
}
