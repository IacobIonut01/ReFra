package com.dot.gallery.editor.videocodec

import java.util.logging.Level
import java.util.logging.Logger
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG2VIDEO
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP8
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP9

/**
 * Whether a hardware-accelerated platform decoder plausibly handles this
 * stream — implemented per target: MediaCodecList on Android, always false
 * on desktop. [mime] is the Android media type of the codec and
 * [decoderName] the FFmpeg `*_mediacodec` decoder the check stands in for;
 * [profile] is the FFmpeg codec profile (unmapped values mean "no profile
 * constraint"). The check is a capability gate only — the decoder still
 * fails over to software if it cannot open or breaks mid-stream.
 */
internal expect fun platformSupportsHardwareDecoder(
    mime: String,
    decoderName: String,
    width: Int,
    height: Int,
    fps: Double,
    profile: Int
): Boolean

/**
 * Picks a packaged `*_mediacodec` hardware decoder for a probed stream, or
 * null when software is the right engine. Hardware is used automatically —
 * no setting — but only where it is a faithful substitute:
 *
 * - the codec must have a `*_mediacodec` sibling and a hardware-accelerated
 *   platform decoder covering this size/rate/profile (never a hardcoded
 *   device list);
 * - 8-bit sources only. On the Pixel the mediacodec decoder emits 8-bit
 *   NV12, so a 10-bit source would silently lose precision before the
 *   colour pipeline runs — measured as a visible divergence after HLG tone
 *   mapping. Deeper or unknown-depth streams stay on the full-precision
 *   software path rather than approximate the picture;
 * - H.264 below [H264_MIN_PIXELS] stays software: FFmpeg's mediacodec
 *   wrapper pays a per-frame codec→CPU copy that outweighs the decode win
 *   for an easy codec (measured on a Pixel 10 Pro, debuggable build:
 *   1080p preview 25 ms vs 11 ms software, seeks 668 ms vs 462 ms). Only
 *   near-4K H.264, where software decode cost grows into the overhead,
 *   is worth trying in hardware. Every other supported codec is
 *   expensive enough in software to try hardware at any size.
 *
 * Everything returned by [select] is a starting attempt, never a promise —
 * [FfmpegVideoReader] falls back to software on any open or mid-stream
 * failure and reports it through [VideoCodecEvents].
 */
object HardwareDecode {

    /** An FFmpeg codec → its `*_mediacodec` decoder and Android MIME type. */
    private data class Candidate(val decoderName: String, val mime: String)

    private val candidates = mapOf(
        AV_CODEC_ID_H264 to Candidate("h264_mediacodec", "video/avc"),
        AV_CODEC_ID_HEVC to Candidate("hevc_mediacodec", "video/hevc"),
        AV_CODEC_ID_VP8 to Candidate("vp8_mediacodec", "video/x-vnd.on2.vp8"),
        AV_CODEC_ID_VP9 to Candidate("vp9_mediacodec", "video/x-vnd.on2.vp9"),
        AV_CODEC_ID_AV1 to Candidate("av1_mediacodec", "video/av01"),
        AV_CODEC_ID_MPEG2VIDEO to Candidate("mpeg2_mediacodec", "video/mpeg2"),
        AV_CODEC_ID_MPEG4 to Candidate("mpeg4_mediacodec", "video/mp4v-es")
    )

    /** Software H.264 wins below this area (~4K class and above only). */
    internal const val H264_MIN_PIXELS = 4_000_000L

    /** Deepest component depth still faithful through 8-bit NV12. */
    internal const val MAX_HW_BIT_DEPTH = 8

    fun select(probe: FfmpegProbeResult): String? =
        select(probe, ::platformSupportsHardwareDecoder)

    internal fun select(
        probe: FfmpegProbeResult,
        platform: (
            mime: String, decoderName: String,
            width: Int, height: Int, fps: Double, profile: Int
        ) -> Boolean
    ): String? {
        val candidate = candidates[probe.videoCodecId] ?: return null
        if (probe.videoBitDepth <= 0 ||
            probe.videoBitDepth > MAX_HW_BIT_DEPTH
        ) {
            VideoCodecEvents.selected(
                "software decode: ${probe.info.displayName} is " +
                    "${probe.videoBitDepth}-bit, hardware copies lose precision"
            )
            return null
        }
        val pixels = probe.rawSize.width.toLong() * probe.rawSize.height
        if (probe.videoCodecId == AV_CODEC_ID_H264 && pixels < H264_MIN_PIXELS) {
            VideoCodecEvents.selected(
                "software decode: ${probe.info.displayName} H.264 " +
                    "${probe.rawSize.width}x${probe.rawSize.height} is small " +
                    "enough that hardware decode is slower"
            )
            return null
        }
        val supported = platform(
            candidate.mime, candidate.decoderName,
            probe.rawSize.width, probe.rawSize.height,
            probe.avgFps, probe.videoProfile
        )
        if (!supported) {
            VideoCodecEvents.selected(
                "software decode: no hardware ${candidate.mime} decoder " +
                    "covers ${probe.rawSize.width}x${probe.rawSize.height}@" +
                    probe.avgFps
            )
            return null
        }
        VideoCodecEvents.selected(
            "hardware decode: ${candidate.decoderName} for " +
                "${probe.info.displayName}"
        )
        return candidate.decoderName
    }
}

/**
 * Diagnostics channel for decoder selection and fallback — the library has
 * no app-logging dependency, so the default listener writes java.util.logging
 * (logcat on Android) and hosts may swap in their own sink via [listener].
 * A non-null [Throwable] marks a real fallback the app layer should mirror
 * into its session log; null marks a selection explanation.
 */
object VideoCodecEvents {

    class HardwareDecodeFallback internal constructor(
        message: String,
        cause: Throwable
    ) : Exception("$message: ${cause.message}", cause)

    private val logger = Logger.getLogger(VideoCodecEvents::class.java.name)

    val defaultListener: (String, Throwable?) -> Unit = { message, failure ->
        if (failure != null) {
            logger.log(Level.WARNING, message, failure)
        } else {
            logger.info(message)
        }
    }

    @Volatile
    var listener: (String, Throwable?) -> Unit = defaultListener

    internal fun selected(message: String) = listener(message, null)

    internal fun fallback(message: String, failure: Throwable) =
        listener(message, HardwareDecodeFallback(message, failure))
}
