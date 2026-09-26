/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import com.dot.gallery.feature_node.presentation.util.printWarn

/**
 * Kotlin binding for the `heiftiles` JNI library, which exposes libheif's memory-bounded tiled
 * decode (native HEVC via libde265). Lets [HeifRegionDecoder] decode arbitrarily large (e.g.
 * 100MP) HEIC/HEIF images tile-by-tile at native resolution for crisp zoom without OOM.
 *
 * [isAvailable] is false when the library failed to load or was built as a stub (an ABI without
 * prebuilt libheif) — callers must fall back to another decode path in that case.
 *
 * The native handle returned by [open] is NOT thread-safe; callers must serialize
 * [getInfo]/[decodeTile]/[close] on a single handle (see [HeifRegionDecoder.SharedHeifRegion]).
 */
object NativeHeifTiler {

    private const val TAG = "decode.heif"

    val isAvailable: Boolean = runCatching {
        System.loadLibrary("heiftiles")
        nativeSelfTest()
    }.getOrElse {
        printWarn(TAG, "heiftiles native library unavailable: ${it.message}")
        false
    }.also { HeifDebug.d("NativeHeifTiler.isAvailable=$it") }

    /** Opens the image from encoded bytes, returning a native handle (0 on failure). */
    fun open(data: ByteArray): Long = if (isAvailable) {
        runCatching { nativeOpen(data) }.getOrDefault(0L)
    } else 0L

    /**
     * Returns [imageWidth, imageHeight, tileWidth, tileHeight, numColumns, numRows, lumaBitsPerPixel]
     * or null. lumaBitsPerPixel > 8 indicates a 10/12-bit (HDR-capable) image.
     */
    fun getInfo(handle: Long): IntArray? =
        if (handle != 0L) runCatching { nativeGetInfo(handle) }.getOrNull() else null

    /**
     * Decodes one grid tile at native resolution. Returns an int[] laid out as
     * [width, height, pixels...] (pixels packed ARGB_8888), or null on failure.
     */
    fun decodeTile(handle: Long, tileX: Int, tileY: Int): IntArray? =
        if (handle != 0L) runCatching { nativeDecodeTile(handle, tileX, tileY) }.getOrNull() else null

    /** Releases the native handle. Safe to call with 0. */
    fun close(handle: Long) {
        if (handle != 0L) runCatching { nativeClose(handle) }
    }

    private external fun nativeSelfTest(): Boolean
    private external fun nativeOpen(data: ByteArray): Long
    private external fun nativeGetInfo(handle: Long): IntArray?
    private external fun nativeDecodeTile(handle: Long, tileX: Int, tileY: Int): IntArray?
    private external fun nativeClose(handle: Long)
}
