package com.dot.gallery.editor.videocodec

import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Android capability check over MediaCodecList: a decoder counts only when it
 * is hardware-accelerated (API 29+ `isHardwareAccelerated`; `c2.android.*` /
 * `OMX.google.*` software codecs never qualify) and its video capabilities
 * cover this stream's size and rate. When the FFmpeg profile maps to a known
 * Android profile constant, at least one advertised profile/level entry must
 * match; unmapped or unknown profiles impose no extra constraint — the
 * decoder still fails over to software if it cannot actually open.
 */
internal actual fun platformSupportsHardwareDecoder(
    mime: String,
    decoderName: String,
    width: Int,
    height: Int,
    fps: Double,
    profile: Int
): Boolean {
    val wanted = androidProfiles(mime, profile)
    val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
    return infos.any { info ->
        if (info.isEncoder) return@any false
        val hw = try {
            info.isHardwareAccelerated
        } catch (_: Throwable) {
            false
        }
        if (!hw) return@any false
        val type = info.supportedTypes.firstOrNull { it.equals(mime, true) }
            ?: return@any false
        val capabilities = try {
            info.getCapabilitiesForType(type)
        } catch (_: Throwable) {
            return@any false
        }
        val video = capabilities.videoCapabilities ?: return@any false
        val sizeFits = try {
            video.areSizeAndRateSupported(width, height, fps)
        } catch (_: Throwable) {
            try {
                width <= video.supportedWidths.upper &&
                    height <= video.supportedHeights.upper
            } catch (_: Throwable) {
                false
            }
        }
        if (!sizeFits) return@any false
        wanted == null || capabilities.profileLevels.any {
            it.profile in wanted
        }
    }
}

/**
 * Best-effort FFmpeg→Android profile mapping per MIME; null means "no known
 * constraint". Sets cover equivalent profiles that decode the same stream
 * (e.g. an HEVC Main10HDR10 decoder also reads plain Main10).
 */
private fun androidProfiles(mime: String, profile: Int): Set<Int>? =
    when (mime) {
        MediaFormat.MIMETYPE_VIDEO_AVC -> when (profile) {
            FF_PROFILE_H264_BASELINE, FF_PROFILE_H264_CONSTRAINED_BASELINE ->
                setOf(AVC_PROFILE_BASELINE)
            FF_PROFILE_H264_MAIN -> setOf(AVC_PROFILE_MAIN)
            FF_PROFILE_H264_EXTENDED -> setOf(AVC_PROFILE_EXTENDED)
            FF_PROFILE_H264_HIGH -> setOf(AVC_PROFILE_HIGH)
            FF_PROFILE_H264_HIGH_10 -> setOf(AVC_PROFILE_HIGH10)
            FF_PROFILE_H264_HIGH_422 -> setOf(AVC_PROFILE_HIGH422)
            FF_PROFILE_H264_HIGH_444 -> setOf(AVC_PROFILE_HIGH444)
            else -> null
        }
        MediaFormat.MIMETYPE_VIDEO_HEVC -> when (profile) {
            FF_PROFILE_HEVC_MAIN -> setOf(HEVC_PROFILE_MAIN)
            FF_PROFILE_HEVC_MAIN_10 -> setOf(
                HEVC_PROFILE_MAIN10, HEVC_PROFILE_MAIN10_HDR10,
                HEVC_PROFILE_MAIN10_HDR10_PLUS
            )
            FF_PROFILE_HEVC_MAIN_STILL -> setOf(HEVC_PROFILE_MAIN_STILL)
            else -> null
        }
        MediaFormat.MIMETYPE_VIDEO_VP9 -> when (profile) {
            FF_PROFILE_VP9_0 -> setOf(VP9_PROFILE_0)
            FF_PROFILE_VP9_1 -> setOf(VP9_PROFILE_1)
            FF_PROFILE_VP9_2 -> setOf(VP9_PROFILE_2, VP9_PROFILE_2_HDR)
            FF_PROFILE_VP9_3 -> setOf(VP9_PROFILE_3, VP9_PROFILE_3_HDR)
            else -> null
        }
        MediaFormat.MIMETYPE_VIDEO_AV1 -> when (profile) {
            FF_PROFILE_AV1_MAIN -> setOf(AV1_PROFILE_MAIN_8)
            FF_PROFILE_AV1_HIGH -> setOf(
                AV1_PROFILE_MAIN_10, AV1_PROFILE_MAIN_10_HDR10
            )
            else -> null
        }
        else -> null
    }

// FFmpeg profile constants (libavcodec/avcodec.h, defs.h).
private const val FF_PROFILE_H264_CONSTRAINED_BASELINE = 578
private const val FF_PROFILE_H264_BASELINE = 66
private const val FF_PROFILE_H264_MAIN = 77
private const val FF_PROFILE_H264_EXTENDED = 88
private const val FF_PROFILE_H264_HIGH = 100
private const val FF_PROFILE_H264_HIGH_10 = 110
private const val FF_PROFILE_H264_HIGH_422 = 122
private const val FF_PROFILE_H264_HIGH_444 = 244
private const val FF_PROFILE_HEVC_MAIN = 1
private const val FF_PROFILE_HEVC_MAIN_10 = 2
private const val FF_PROFILE_HEVC_MAIN_STILL = 3
private const val FF_PROFILE_VP9_0 = 0
private const val FF_PROFILE_VP9_1 = 1
private const val FF_PROFILE_VP9_2 = 2
private const val FF_PROFILE_VP9_3 = 3
private const val FF_PROFILE_AV1_MAIN = 0
private const val FF_PROFILE_AV1_HIGH = 1

// android.media.MediaCodecInfo.CodecProfileLevel constants.
private const val AVC_PROFILE_BASELINE = 0x01
private const val AVC_PROFILE_MAIN = 0x02
private const val AVC_PROFILE_EXTENDED = 0x04
private const val AVC_PROFILE_HIGH = 0x08
private const val AVC_PROFILE_HIGH10 = 0x10
private const val AVC_PROFILE_HIGH422 = 0x20
private const val AVC_PROFILE_HIGH444 = 0x40
private const val HEVC_PROFILE_MAIN = 0x01
private const val HEVC_PROFILE_MAIN10 = 0x02
private const val HEVC_PROFILE_MAIN_STILL = 0x04
private const val HEVC_PROFILE_MAIN10_HDR10 = 0x1000
private const val HEVC_PROFILE_MAIN10_HDR10_PLUS = 0x2000
private const val VP9_PROFILE_0 = 0x01
private const val VP9_PROFILE_1 = 0x02
private const val VP9_PROFILE_2 = 0x04
private const val VP9_PROFILE_3 = 0x08
private const val VP9_PROFILE_2_HDR = 0x1000
private const val VP9_PROFILE_3_HDR = 0x2000
private const val AV1_PROFILE_MAIN_8 = 0x01
private const val AV1_PROFILE_MAIN_10 = 0x02
private const val AV1_PROFILE_MAIN_10_HDR10 = 0x04
