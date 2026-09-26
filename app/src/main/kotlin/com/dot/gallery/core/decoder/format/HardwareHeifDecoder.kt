/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.dot.gallery.feature_node.presentation.util.printDebug
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Hardware-accelerated HEIC/AVIF decoding via the platform [ImageDecoder].
 *
 * Unlike the software [com.radzivon.bartoshyk.avif.coder.HeifCoder] (libheif/libavif), [ImageDecoder]
 * routes to the device's HEVC/AV1 hardware codec when available, which is dramatically faster.
 * Coverage varies by device/OS (HEIC works on API 28+, AVIF needs API 31+ and AV1 HW/SW support),
 * so callers must treat a null result as "unsupported here" and fall back to the software coder.
 *
 * The output is requested with [ImageDecoder.ALLOCATOR_SOFTWARE] so the result is a CPU-accessible
 * ARGB_8888 bitmap (required by Glide's centerCrop and Sketch transforms). The expensive entropy
 * decode still runs on the hardware codec regardless of the output allocator.
 */
object HardwareHeifDecoder {

    private const val TAG = "decode.heif"

    @RequiresApi(Build.VERSION_CODES.P)
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? = decode(bytes, reqW, reqH, false)

    /**
     * @param allowHdr when true, the platform [ImageDecoder] keeps its default (possibly HDR /
     *   wide-gamut) output. When false, the color space is pinned to sRGB so downstream transforms
     *   (Glide centerCrop) never receive an F16/wide-gamut bitmap that would corrupt colors.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int, allowHdr: Boolean): Bitmap? {
        // The platform AV1 stack (C2SoftDav1dDec) silently decodes >10-bit (12-bit) AVIF as 8-bit
        // and returns the corrupted result as a success, so the caller's software fallback never
        // runs. Refuse up front and let the bundled HeifCoder (libavif/dav1d) handle it.
        if (HeifBitstreamProbe.needsSoftwareDecode(bytes)) {
            printDebug(TAG, "refusing platform decode: >10-bit HEIF/AVIF")
            return null
        }
        return try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                if (!allowHdr) {
                    // SDR path (grid thumbnails): pin sRGB so downstream Glide centerCrop never
                    // receives an F16/wide-gamut bitmap. The embedded gain map is left intact — it
                    // is only rendered as HDR when the host window opts into COLOR_MODE_HDR, which
                    // the grid never does, so it is harmless here.
                    decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                }
                // When allowHdr is true, the platform keeps the image's native output — an Ultra HDR
                // gain map or a 10-bit HLG/PQ color space — so the viewer base renders true HDR on a
                // capable display (the media viewer toggles COLOR_MODE_HDR per page via hasGainmap()).
                if (reqW > 0 && reqH > 0) {
                    val sw = info.size.width
                    val sh = info.size.height
                    if (sw > 0 && sh > 0) {
                        val scale = minOf(reqW.toFloat() / sw, reqH.toFloat() / sh)
                        if (scale < 1f) {
                            decoder.setTargetSize(
                                (sw * scale).roundToInt().coerceAtLeast(1),
                                (sh * scale).roundToInt().coerceAtLeast(1)
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
