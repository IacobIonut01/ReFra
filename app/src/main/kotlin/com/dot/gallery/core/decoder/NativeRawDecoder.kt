/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.util.Size
import com.dot.gallery.feature_node.presentation.util.printWarn

/**
 * Kotlin binding for the `rawcodec` JNI library — LibRaw true RAW decode (demosaic) plus a libtiff
 * 8/16-bit TIFF writer. Gives the app native full-resolution rendering for every LibRaw-supported
 * camera RAW format, with white balance / exposure / highlight / colour-space / demosaic controls,
 * and a develop-to-TIFF export path.
 *
 * [isAvailable] is false when the library failed to load or was built as a stub (an ABI without
 * prebuilt LibRaw) — callers must fall back to the embedded-JPEG-preview path in that case.
 *
 * Every native call is stateless (opens/unpacks the RAW per call) and guarded; failures surface as
 * null / false rather than crashes.
 */
object NativeRawDecoder {

    private const val TAG = "decode.raw"

    val isAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("rawcodec")
            nativeSelfTest()
        }.getOrElse {
            printWarn(TAG, "rawcodec native library unavailable: ${it.message}")
            false
        }
    }

    /** Full sensor size (post-crop) reported by LibRaw, orientation-swapped to match display. */
    fun getSize(data: ByteArray): Size? {
        if (!isAvailable) return null
        val info = runCatching { nativeGetInfo(data) }.getOrNull() ?: return null
        if (info.size < 2 || info[0] <= 0 || info[1] <= 0) return null
        return if (RawOrientation.swapsDimensions(RawOrientation.exifOrientation(data))) {
            Size(info[1], info[0])
        } else {
            Size(info[0], info[1])
        }
    }

    /**
     * Decodes the embedded camera thumbnail bitmap (fast), or null when LibRaw returns JPEG. The
     * result is rotated to match the container's EXIF orientation so it agrees with every other path.
     */
    fun getThumbnail(data: ByteArray): Bitmap? {
        if (!isAvailable) return null
        val packed = runCatching { nativeGetThumbnail(data) }.getOrNull() ?: return null
        val bmp = packedToBitmap(packed) ?: return null
        return RawOrientation.applyToBitmap(bmp, RawOrientation.exifOrientation(data))
    }

    /**
     * Demosaics [data] to an 8-bit ARGB [Bitmap] using [params]. Runs on the caller's thread and can
     * be expensive/memory-heavy for large sensors — use [RawDevelopParams.halfSize] to bound it.
     * [userFlip] (< 0 keeps LibRaw's parsed orientation) overrides the sensor orientation with the
     * EXIF-derived one so rotation is consistent across the app.
     */
    fun demosaic(data: ByteArray, params: RawDevelopParams, userFlip: Int = -1): Bitmap? {
        if (!isAvailable) return null
        val packed = runCatching {
            nativeDemosaic(data, params.toIntParams(userFlip), params.toFloatParams(), params.effectiveUserMul)
        }.getOrElse {
            printWarn(TAG, "demosaic failed: ${it.message}")
            null
        } ?: return null
        return packedToBitmap(packed)
    }

    /**
     * Develops [data] and writes a TIFF to open file descriptor [fd] at [bits] (8 or 16) bits per
     * sample. Returns true on success. The caller retains ownership of [fd] (the native side dup()s
     * the descriptor). Designed to plug into `ContentResolver.saveImageStreaming { fd -> ... }`.
     */
    fun exportTiff(
        data: ByteArray,
        params: RawDevelopParams,
        fd: Int,
        bits: Int = 16,
        compression: RawTiffCompression = RawTiffCompression.DEFLATE,
        userFlip: Int = -1,
    ): Boolean {
        if (!isAvailable || fd < 0) return false
        return runCatching {
            nativeExportTiff(
                data,
                params.toIntParams(userFlip),
                params.toFloatParams(),
                params.effectiveUserMul,
                if (bits == 16) 16 else 8,
                fd,
                compression.nativeValue,
            )
        }.getOrElse {
            printWarn(TAG, "exportTiff failed: ${it.message}")
            false
        }
    }

    /**
     * Re-applies the post-demosaic tone stage (contrast, shadows, highlights, saturation, vibrance,
     * sharpen) of [params] to an already-demosaiced [bitmap] WITHOUT re-decoding the RAW. Used for
     * the editor's live tone preview so sliders stay instant while matching the export tone math
     * exactly. Returns a new bitmap (the input is untouched), or null when unavailable/failed.
     */
    fun applyTone(bitmap: Bitmap, params: RawDevelopParams): Bitmap? {
        if (!isAvailable) return null
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val packed = runCatching {
            nativeApplyTone(argb, w, h, params.toFloatParams())
        }.getOrElse {
            printWarn(TAG, "applyTone failed: ${it.message}")
            null
        } ?: return null
        return packedToBitmap(packed)
    }

    /** Converts a native [w, h, ARGB...] packed int array into a [Bitmap]. */
    private fun packedToBitmap(packed: IntArray): Bitmap? {
        if (packed.size < 2) return null
        val w = packed[0]
        val h = packed[1]
        if (w <= 0 || h <= 0 || packed.size < 2 + w * h) return null
        return runCatching {
            Bitmap.createBitmap(packed, 2, w, w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private external fun nativeSelfTest(): Boolean
    private external fun nativeGetInfo(data: ByteArray): IntArray?
    private external fun nativeGetThumbnail(data: ByteArray): IntArray?
    private external fun nativeDemosaic(
        data: ByteArray,
        intParams: IntArray,
        floatParams: FloatArray,
        userMul: FloatArray?,
    ): IntArray?

    private external fun nativeExportTiff(
        data: ByteArray,
        intParams: IntArray,
        floatParams: FloatArray,
        userMul: FloatArray?,
        outputBps: Int,
        fd: Int,
        compression: Int,
    ): Boolean

    private external fun nativeApplyTone(
        argb: IntArray,
        w: Int,
        h: Int,
        floatParams: FloatArray,
    ): IntArray?
}
