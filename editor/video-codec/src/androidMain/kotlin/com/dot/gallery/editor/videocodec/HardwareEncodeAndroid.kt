package com.dot.gallery.editor.videocodec

import android.media.MediaCodecList

/**
 * Android capability check over MediaCodecList: an encoder counts only when
 * it is hardware-accelerated (API 29+ `isHardwareAccelerated`; `c2.android.*`
 * / `OMX.google.*` software encoders never qualify) and its video
 * capabilities cover this size and rate. The check is a capability gate
 * only — the encoder still fails over to `libopenh264` if it cannot
 * actually open or breaks mid-encode.
 */
internal actual fun platformSupportsHardwareEncoder(
    mime: String,
    width: Int,
    height: Int,
    fps: Double
): Boolean {
    val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
    return infos.any { info ->
        if (!info.isEncoder) return@any false
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
        try {
            video.areSizeAndRateSupported(width, height, fps)
        } catch (_: Throwable) {
            try {
                width <= video.supportedWidths.upper &&
                    height <= video.supportedHeights.upper
            } catch (_: Throwable) {
                false
            }
        }
    }
}
