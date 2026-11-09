/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Owns the one luminance buffer the scanner uses, so scanning never allocates per photo. The
 * buffer is created on first use at [maxSide]² bytes and kept until [release].
 *
 * Not thread-safe: use it from a single thread (the insight dispatcher).
 */
class FramePool(val maxSide: Int = DEFAULT_MAX_SIDE) {

    private var frame: LuminanceFrame? = null
    private val size = IntArray(2)

    /**
     * Fills the pooled frame from [bitmap], downscaling to [maxSide]. A hardware bitmap is copied
     * to software first (and that copy is recycled at once). Returns null if the bitmap cannot be
     * read, so callers simply skip the photo.
     */
    fun fill(bitmap: Bitmap): LuminanceFrame? {
        if (bitmap.isRecycled || !CodeScanNative.isAvailable) return null
        val target = frame ?: LuminanceFrame(
            ByteBuffer.allocateDirect(maxSide * maxSide).order(ByteOrder.nativeOrder())
        ).also { frame = it }
        val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            bitmap
        }
        try {
            if (!CodeScanNative.prepareFrame(readable, target.buffer, maxSide, size)) return null
        } finally {
            if (readable !== bitmap) readable.recycle()
        }
        target.width = size[0]
        target.height = size[1]
        return target
    }

    /** Drops the buffer; the next [fill] allocates a new one. */
    fun release() {
        frame = null
    }

    companion object {
        /** Long side of the analysed frame: 1600² bytes is 2.5 MB of native memory at most. */
        const val DEFAULT_MAX_SIDE = 1600
    }
}
