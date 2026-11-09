/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import android.graphics.Bitmap
import com.dot.gallery.core.logging.printWarn
import java.nio.ByteBuffer

/**
 * Kotlin binding for `libcodescan.so`: the reader-only zxing-cpp build plus a frame converter.
 *
 * The library is only loaded the first time [isAvailable] is read, so with code scanning turned
 * off it never enters the process. [isAvailable] is false when loading failed or the .so is the
 * stub built for an ABI without the static zxing-cpp.
 */
object CodeScanNative {

    private const val TAG = "insight.codes"

    /** Ints the native side writes per code: format, content type, then four x/y corners. */
    const val META_STRIDE = 10

    val isAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("codescan")
            nativeIsAvailable()
        }.getOrElse {
            printWarn(TAG, "codescan native library unavailable: ${it.message}")
            false
        }
    }

    /**
     * Downscales [bitmap] (software `ARGB_8888` or `RGB_565`) to fit [maxSide] and writes its
     * luminance into [buffer], which must be a direct buffer. The frame size goes to [outSize].
     * Returns false for bitmaps it cannot read (hardware, F16, alpha-only).
     */
    fun prepareFrame(bitmap: Bitmap, buffer: ByteBuffer, maxSide: Int, outSize: IntArray): Boolean =
        isAvailable && nativePrepareFrame(bitmap, buffer, maxSide, outSize)

    /**
     * Reads codes from the [width]×[height] luminance frame in [buffer]. Returns null when there
     * are none (no allocation in that case); otherwise the texts, with [META_STRIDE] ints per code
     * written to [meta] in the same order.
     */
    fun scan(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        formatMask: Int,
        thorough: Boolean,
        maxCodes: Int,
        maxTextBytes: Int,
        meta: IntArray,
    ): Array<String>? {
        if (!isAvailable) return null
        @Suppress("UNCHECKED_CAST")
        return nativeScan(buffer, width, height, formatMask, thorough, maxCodes, maxTextBytes, meta)
            as Array<String>?
    }

    @JvmStatic
    private external fun nativeIsAvailable(): Boolean

    @JvmStatic
    private external fun nativePrepareFrame(
        bitmap: Bitmap,
        buffer: ByteBuffer,
        maxSide: Int,
        outSize: IntArray,
    ): Boolean

    @JvmStatic
    private external fun nativeScan(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        formats: Int,
        tryHarder: Boolean,
        maxCodes: Int,
        maxTextBytes: Int,
        meta: IntArray,
    ): Array<Any?>?
}
