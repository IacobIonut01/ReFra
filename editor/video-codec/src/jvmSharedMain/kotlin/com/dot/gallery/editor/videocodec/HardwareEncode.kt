package com.dot.gallery.editor.videocodec

import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name

/**
 * Whether a hardware-accelerated platform encoder plausibly produces this
 * stream — implemented per target: MediaCodecList on Android, always false
 * on desktop. The check is a capability gate only — an encoder that fails
 * to open or breaks mid-encode falls back to `libopenh264` for the whole
 * export and reports through [VideoCodecEvents].
 */
internal expect fun platformSupportsHardwareEncoder(
    mime: String,
    width: Int,
    height: Int,
    fps: Double
): Boolean

/**
 * Picks a packaged hardware H.264 encoder for the export path, or null when
 * software is the right engine. Unlike decode — where the mediacodec
 * wrapper only wins above ~4K — encoding is the dominant export cost and
 * the platform encoder beats `libopenh264` at every size, so hardware is
 * tried at any resolution when both conditions hold:
 *
 * - the packaged FFmpeg build carries `h264_mediacodec` (checked live —
 *   a build without it silently stays software);
 * - a hardware-accelerated platform encoder covers this size and rate —
 *   never a hardcoded device list.
 *
 * Everything returned is a starting attempt, never a promise —
 * [FfmpegVideoExporter] retries the encode in software on any failure and
 * reports it through [VideoCodecEvents].
 */
object HardwareEncode {

    private const val H264_MEDIACODEC = "h264_mediacodec"
    private const val MIME_AVC = "video/avc"

    fun select(width: Int, height: Int, fps: Double): String? {
        if (avcodec_find_encoder_by_name(H264_MEDIACODEC) == null) {
            return null
        }
        if (!platformSupportsHardwareEncoder(MIME_AVC, width, height, fps)) {
            VideoCodecEvents.selected(
                "software encode: no hardware $MIME_AVC encoder covers " +
                    "${width}x$height@$fps"
            )
            return null
        }
        VideoCodecEvents.selected(
            "hardware encode: $H264_MEDIACODEC ${width}x$height@$fps"
        )
        return H264_MEDIACODEC
    }
}
