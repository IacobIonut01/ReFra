/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.core.graphics.scale

/**
 * JPEG 2000 (.jp2 / .j2k) decoder backed by the source-built OpenJPEG codec. Android has no native
 * JPEG 2000 support. Uses the codec's multi-resolution feature to decode directly at a reduced
 * resolution for fast thumbnails instead of decoding full-size and downscaling afterwards.
 */
object Jp2ImageDecoder {

    private const val TAG = "Jp2ImageDecoder"

    init {
        System.loadLibrary("openjpeg")
    }

    fun getSize(bytes: ByteArray): Size? = runCatching {
        val header = readJp2HeaderByteArray(bytes) ?: return null
        if (header.size < HEADER_SIZE) return null
        Size(header[WIDTH_INDEX], header[HEIGHT_INDEX])
    }.onFailure { Log.w(TAG, "readHeader failed: ${it.message}") }.getOrNull()

    /**
     * Decodes the JPEG 2000 image to a bitmap no larger than [reqW] x [reqH] (when > 0).
     */
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val header = readJp2HeaderByteArray(bytes)
            val skip = if (header != null && header.size >= HEADER_SIZE) {
                computeSkipResolutions(
                    srcW = header[WIDTH_INDEX],
                    srcH = header[HEIGHT_INDEX],
                    reqW = reqW,
                    reqH = reqH,
                    numResolutions = header[RESOLUTIONS_INDEX]
                )
            } else {
                0
            }
            val bmp = decodeBitmap(bytes, skip) ?: return null
            if (reqW > 0 && reqH > 0) fit(bmp, reqW, reqH) else bmp
        } catch (e: Throwable) {
            Log.e(TAG, "decode failed: ${e.message}", e)
            null
        }
    }

    private fun decodeBitmap(bytes: ByteArray, skipResolutions: Int): Bitmap? {
        val decoded = decodeJp2ByteArray(bytes, skipResolutions, 0) ?: return null
        if (decoded.size < PIXELS_INDEX) return null
        val width = decoded[WIDTH_INDEX]
        val height = decoded[HEIGHT_INDEX]
        val pixelCount = width.toLong() * height
        if (width <= 0 || height <= 0 || pixelCount > Int.MAX_VALUE ||
            decoded.size.toLong() < PIXELS_INDEX + pixelCount
        ) {
            return null
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(decoded, PIXELS_INDEX, width, 0, 0, width, height)
            setHasAlpha(decoded[ALPHA_INDEX] != 0)
        }
    }

    private fun computeSkipResolutions(
        srcW: Int, srcH: Int, reqW: Int, reqH: Int, numResolutions: Int
    ): Int {
        if (reqW <= 0 || reqH <= 0 || srcW <= 0 || srcH <= 0) return 0
        var skip = 0
        var w = srcW
        var h = srcH
        val maxSkip = (numResolutions - 1).coerceAtLeast(0)
        // Halve while the next halving still covers the requested size.
        while (skip < maxSkip && w / 2 >= reqW && h / 2 >= reqH) {
            w /= 2
            h /= 2
            skip++
        }
        return skip
    }

    private fun fit(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (src.width <= maxW && src.height <= maxH) return src
        val scale = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
        val tw = (src.width * scale).toInt().coerceAtLeast(1)
        val th = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = src.scale(tw, th)
        if (scaled != src) src.recycle()
        return scaled
    }

    private external fun decodeJp2ByteArray(data: ByteArray, reduce: Int, layers: Int): IntArray?
    private external fun readJp2HeaderByteArray(data: ByteArray): IntArray?

    private const val WIDTH_INDEX = 0
    private const val HEIGHT_INDEX = 1
    private const val ALPHA_INDEX = 2
    private const val RESOLUTIONS_INDEX = 3
    private const val PIXELS_INDEX = 3
    private const val HEADER_SIZE = 5
}
