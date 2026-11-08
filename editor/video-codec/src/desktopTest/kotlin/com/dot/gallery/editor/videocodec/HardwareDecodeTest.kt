package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.render.video.VideoInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PRORES
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP9

/**
 * Selection logic for [HardwareDecode] — the platform check is stubbed so
 * every gate is exercised on the host.
 */
class HardwareDecodeTest {

    private val events = mutableListOf<Pair<String, Throwable?>>()

    private fun probe(
        codecId: Int = AV_CODEC_ID_HEVC,
        width: Int = 3840,
        height: Int = 2160,
        bitDepth: Int = 8,
        profile: Int = 1,
        fps: Double = 30.0
    ) = FfmpegProbeResult(
        info = VideoInfo(
            asset = MediaAsset.Video(
                id = "s", reference = "r", size = PixelSize(width, height),
                duration = EditorTime.fromMicroseconds(1_000_000),
                frameRate = FrameRate(30), hasAudio = false,
                rotationDegrees = 0
            ),
            displayName = "clip.mp4",
            audioSampleRate = null,
            audioChannels = null
        ),
        rawSize = PixelSize(width, height),
        colorspace = 1, colorRange = 1, colorPrimaries = 1, colorTrc = 1,
        videoCodecId = codecId, audioCodecId = null,
        videoStreamIndex = 0, audioStreamIndex = -1,
        videoProfile = profile, videoBitDepth = bitDepth, avgFps = fps
    )

    private fun select(
        probe: FfmpegProbeResult,
        platformOk: Boolean = true
    ): String? {
        val calls = mutableListOf<String>()
        val chosen = HardwareDecode.select(probe) { mime, decoder, _, _, _, _ ->
            calls += "$decoder/$mime"
            platformOk
        }
        if (platformOk && chosen != null) {
            assertTrue(calls.isNotEmpty(), "platform check must run")
        }
        return chosen
    }

    @Test
    fun hevc4kChoosesMediacodec() {
        assertEquals("hevc_mediacodec", select(probe()))
    }

    @Test
    fun av1AndVp9AndOthersMap() {
        assertEquals("av1_mediacodec",
            select(probe(codecId = AV_CODEC_ID_AV1, profile = 0)))
        assertEquals("vp9_mediacodec",
            select(probe(codecId = AV_CODEC_ID_VP9, profile = 0, height = 720)))
        assertEquals("h264_mediacodec",
            select(probe(codecId = AV_CODEC_ID_H264, height = 2160)))
    }

    @Test
    fun unknownCodecFallsToSoftware() {
        assertNull(select(probe(codecId = AV_CODEC_ID_PRORES)))
    }

    @Test
    fun noHardwareDecoderFallsToSoftware() {
        assertNull(select(probe(), platformOk = false))
    }

    @Test
    fun tenBitStaysSoftware() {
        assertNull(
            select(probe(bitDepth = 10)),
            "10-bit must not go through an 8-bit NV12 copy"
        )
    }

    @Test
    fun unknownBitDepthStaysSoftware() {
        assertNull(select(probe(bitDepth = 0)))
    }

    @Test
    fun smallH264StaysSoftware() {
        assertNull(
            select(probe(codecId = AV_CODEC_ID_H264, width = 1280, height = 720)),
            "720p H.264 decodes faster in software"
        )
        assertNull(
            select(probe(codecId = AV_CODEC_ID_H264, width = 1920, height = 1080)),
            "1080p H.264 still decodes faster in software (measured)"
        )
    }

    @Test
    fun boundaryH264GoesHardware() {
        // 2560x1600 = 4.1M px — above the ~4K-class gate.
        assertEquals("h264_mediacodec",
            select(probe(codecId = AV_CODEC_ID_H264, width = 2560, height = 1600)))
    }

    @Test
    fun smallHevcGoesHardware() {
        // HEVC is expensive in software at any size.
        assertEquals("hevc_mediacodec",
            select(probe(width = 1280, height = 720)))
    }
}
