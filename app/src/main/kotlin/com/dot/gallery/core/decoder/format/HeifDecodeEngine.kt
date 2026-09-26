/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printError
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.PreferredColorConfig
import com.radzivon.bartoshyk.avif.coder.ScaleMode
import com.radzivon.bartoshyk.avif.coder.ScalingQuality
import kotlin.math.roundToInt

/**
 * Single source of truth for HEIC/HEIF (and AVIF, same container) still-image decoding.
 *
 * Hardware-first: routes to the device HEVC/AV1 hardware codec via the platform
 * [android.graphics.ImageDecoder] ([HardwareHeifDecoder]) when available, which is dramatically
 * faster and — when [allowHdr] is set and the device supports it — can preserve HDR / wide-gamut
 * output automatically.
 *
 * Software fallback: the native libheif/libavif [HeifCoder]. It handles all major color spaces
 * (BT.601/709/2020), HDR transfer functions (PQ/HLG, tone-mapped to SDR here), and every chroma
 * subsampling mode (4:2:0/4:2:2/4:4:4). The fallback always produces a CPU-accessible
 * [PreferredColorConfig.RGBA_8888] bitmap so downstream transforms (Glide centerCrop, Sketch
 * resize, region cropping) never hit HARDWARE/F16 artifacts.
 *
 * Every native call is guarded so a failure degrades to `null` instead of crashing. The engine
 * holds no mutable bitmap state; only the stateless [HeifCoder] instance is retained.
 */
object HeifDecodeEngine {

    private const val TAG = "decode.heif"

    private val coder = HeifCoder()

    /** Reads the intrinsic pixel size without decoding, or `null` if it can't be determined. */
    fun getSize(bytes: ByteArray): Size? = try {
        coder.getSize(bytes)
    } catch (_: Throwable) {
        null
    }

    /** True if libheif recognizes these bytes as a supported HEIF/AVIF image. */
    fun isHeif(bytes: ByteArray): Boolean = try {
        coder.isSupportedImage(bytes)
    } catch (_: Throwable) {
        false
    }

    /**
     * Decodes a still HEIC/HEIF frame, optionally downscaled to fit [reqW]x[reqH] (pass 0 for the
     * full resolution). Tries the hardware path first, then the software coder. Returns `null` when
     * both fail so callers can apply their own last-resort behavior.
     *
     * @param allowHdr when true and supported, the hardware path may return an HDR / wide-gamut
     *   bitmap. When false (e.g. the grid, which centerCrops), output stays SDR-safe.
     */
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int, allowHdr: Boolean = false): Bitmap? {
        val size = getSize(bytes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HardwareHeifDecoder.decode(bytes, reqW, reqH, allowHdr)?.let {
                printDebug(
                    TAG,
                    "base decode HARDWARE req=${reqW}x${reqH} orig=${size?.width}x${size?.height}" +
                        " -> ${it.width}x${it.height} allowHdr=$allowHdr"
                )
                return it
            }
        }
        return decodeSoftware(bytes, reqW, reqH)?.also {
            printDebug(
                TAG,
                "base decode SOFTWARE req=${reqW}x${reqH} orig=${size?.width}x${size?.height}" +
                    " -> ${it.width}x${it.height}"
            )
        }
    }

    /**
     * Software-only decode via [HeifCoder], forced to SDR [PreferredColorConfig.RGBA_8888]. Pass 0
     * for [reqW]/[reqH] to decode at full resolution.
     */
    fun decodeSoftware(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? = try {
        val size = coder.getSize(bytes)
        if (size == null) {
            null
        } else {
            val tw = if (reqW > 0) reqW else size.width
            val th = if (reqH > 0) reqH else size.height
            coder.decodeSampled(
                bytes,
                tw,
                th,
                PreferredColorConfig.RGBA_8888,
                ScaleMode.FIT,
                ScalingQuality.HIGH,
            )
        }
    } catch (e: Throwable) {
        printError(TAG, "software HEIF decode failed: ${e.message}")
        null
    }

    /**
     * Full-resolution software decode, but capped so the long edge never exceeds [maxDim]. Used by
     * the subsampling region-decoder fallback to bound peak RAM on very large (e.g. 48MP / HDR)
     * images while still supplying more detail than the screen-resolution base painter.
     */
    fun decodeCapped(bytes: ByteArray, maxDim: Int): Bitmap? {
        val size = getSize(bytes) ?: return decodeSoftware(bytes, 0, 0)
        val longEdge = maxOf(size.width, size.height)
        if (maxDim <= 0 || longEdge <= maxDim) return decodeSoftware(bytes, 0, 0)
        val scale = maxDim.toFloat() / longEdge
        val tw = (size.width * scale).roundToInt().coerceAtLeast(1)
        val th = (size.height * scale).roundToInt().coerceAtLeast(1)
        return decodeSoftware(bytes, tw, th)
    }
}
