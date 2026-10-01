/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.video

import androidx.media3.common.C
import androidx.media3.common.MimeTypes

/**
 * HDR classification of a video track, derived from the track's declared MIME, codec string
 * and color transfer — no device state involved, so it is fully unit-testable.
 */
enum class VideoHdrType {
    NONE,

    /** PQ / BT.2020 / ST2084 — covers HDR10; HDR10+ SEI metadata is not exposed at this layer. */
    HDR10,

    /** Hybrid Log-Gamma. */
    HLG,

    /** Dolby Vision (dvhe/dvh1/dvav/dva1/dav1 codec strings or the video/dolby-vision MIME). */
    DOLBY_VISION,
}

/**
 * Detected HDR info for the currently selected video track.
 *
 * @param dolbyVisionProfile parsed from the codec string (`dvh1.08.09` → 8) when present.
 * @param dolbyVisionLevel parsed level (`dvh1.08.09` → 9) when present.
 * @param codecs the raw codec string from the track format, for display/debugging.
 * @param decoderName the decoder actually initialized for playback (filled after decoder init).
 */
data class VideoHdrInfo(
    val type: VideoHdrType = VideoHdrType.NONE,
    val dolbyVisionProfile: Int? = null,
    val dolbyVisionLevel: Int? = null,
    val codecs: String? = null,
    val decoderName: String? = null,
) {
    val isHdr: Boolean get() = type != VideoHdrType.NONE

    companion object {
        val NONE = VideoHdrInfo()
    }
}

/**
 * How Dolby Vision content actually renders on this device, given the detected track and the
 * device's decoder/display capabilities.
 */
enum class DolbyVisionPlayback {
    /** DV decoder + DV display: true Dolby Vision presentation. */
    NATIVE,

    /** DV decoder present but the display is HDR-not-DV: decoder tone-maps the DV output. */
    DECODE_ONLY,

    /** No DV decoder; Media3 falls back to the backward-compatible base layer (HEVC/AVC/AV1). */
    BASE_LAYER,

    /**
     * No DV decoder and no compatible base layer (Dolby Vision profile 5). The HEVC decode
     * produces wrong colors; nothing the app can do about it — label it honestly.
     */
    UNSUPPORTED,
}

private val DOLBY_VISION_CODEC_PREFIXES =
    arrayOf("dvhe", "dvh1", "dvav", "dva1", "dav1")

private val DOLBY_VISION_PROFILE_LEVEL_REGEX =
    Regex("""^(?:dvhe|dvh1|dvav|dva1|dav1)\.(\d{1,2})\.(\d{1,2})""", RegexOption.IGNORE_CASE)

/**
 * True when [mimeType]/[codecs] describe a Dolby Vision track, either via the
 * `video/dolby-vision` MIME or via a DV codec string riding on a container MIME.
 */
fun isDolbyVisionCodec(mimeType: String?, codecs: String?): Boolean {
    if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) return true
    val codec = codecs?.trim()?.lowercase() ?: return false
    return DOLBY_VISION_CODEC_PREFIXES.any { codec.startsWith(it) }
}

/**
 * Classify a video track into a [VideoHdrInfo]. [colorTransfer] is the `Format.colorInfo
 * .colorTransfer` value ([C.COLOR_TRANSFER_ST2084], [C.COLOR_TRANSFER_HLG], …) and may be null.
 */
fun classifyVideoHdr(mimeType: String?, codecs: String?, colorTransfer: Int?): VideoHdrInfo {
    if (isDolbyVisionCodec(mimeType, codecs)) {
        val match = codecs?.trim()?.let(DOLBY_VISION_PROFILE_LEVEL_REGEX::find)
        return VideoHdrInfo(
            type = VideoHdrType.DOLBY_VISION,
            dolbyVisionProfile = match?.groupValues?.get(1)?.toIntOrNull(),
            dolbyVisionLevel = match?.groupValues?.get(2)?.toIntOrNull(),
            codecs = codecs,
        )
    }
    return when (colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> VideoHdrInfo(type = VideoHdrType.HDR10, codecs = codecs)
        C.COLOR_TRANSFER_HLG -> VideoHdrInfo(type = VideoHdrType.HLG, codecs = codecs)
        else -> VideoHdrInfo.NONE
    }
}

/**
 * Whether a Dolby Vision stream has a backward-compatible base layer a plain decoder can render.
 * Profile 5 (the single-layer IPTPQc2 profile) is the well-known exception — without a DV
 * decoder it decodes with wrong (purple/green) colors.
 */
fun VideoHdrInfo.hasCompatibleDolbyVisionBaseLayer(): Boolean =
    type != VideoHdrType.DOLBY_VISION || dolbyVisionProfile != 5

/**
 * Combine the track's [VideoHdrInfo] with device capabilities into the effective playback mode.
 * Returns null for non-DV content.
 */
fun dolbyVisionPlayback(
    info: VideoHdrInfo,
    hasDolbyVisionDecoder: Boolean,
    hasDolbyVisionDisplay: Boolean,
): DolbyVisionPlayback? {
    if (info.type != VideoHdrType.DOLBY_VISION) return null
    return when {
        hasDolbyVisionDecoder && hasDolbyVisionDisplay -> DolbyVisionPlayback.NATIVE
        hasDolbyVisionDecoder -> DolbyVisionPlayback.DECODE_ONLY
        info.hasCompatibleDolbyVisionBaseLayer() -> DolbyVisionPlayback.BASE_LAYER
        else -> DolbyVisionPlayback.UNSUPPORTED
    }
}
