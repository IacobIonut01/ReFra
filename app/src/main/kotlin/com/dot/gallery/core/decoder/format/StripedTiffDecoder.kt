/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.graphics.Bitmap
import com.dot.gallery.feature_node.presentation.util.printWarn
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.math.max

/**
 * Fast, low-memory TIFF decoder that reads strips directly from a memory-mapped buffer and
 * downsamples on the fly. Purpose-built for the strip-based, chunky, uncompressed / Deflate
 * TIFFs the app itself exports from RAW (`raw_codec_jni.cpp`: RGB, 8/16-bit, PLANARCONFIG_CONTIG,
 * predictor none) as well as common third-party strip TIFFs.
 *
 * Why this exists: the platform [android.graphics.ImageDecoder] rejects 16-bit TIFF on most
 * devices, and the pure-Java [mil.nga.tiff] fallback must hold the entire file as a heap `byte[]`
 * plus decode the full raster — a 150 MB 16-bit TIFF then triggers app-wide GC pauses. This decoder
 * instead:
 *  - reads from a [ByteBuffer] over an mmap'd file (no whole-file heap copy);
 *  - decompresses one strip at a time (peak memory ≈ one strip, often a single row);
 *  - **skips strips entirely** when the requested (thumbnail) size lets it stride past them, so a
 *    grid thumbnail inflates only a small fraction of the rows.
 *
 * Returns null (never throws) for anything it doesn't handle — tiled TIFFs, LZW/JPEG compression,
 * horizontal predictor, float samples, planar config, or unusual photometrics — so the caller can
 * fall back to the platform / [mil.nga.tiff] paths.
 */
internal object StripedTiffDecoder {

    private const val TAG = "decode.tiff"
    private const val MAX_STRIPS = 500_000

    // Compression tag values.
    private const val COMPRESSION_NONE = 1
    private const val COMPRESSION_DEFLATE_ADOBE = 8
    private const val COMPRESSION_DEFLATE = 32946

    // Field types.
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4

    private class Ifd {
        var width = 0
        var height = 0
        var bitsPerSample = 0
        var samplesPerPixel = 0
        var compression = COMPRESSION_NONE
        var photometric = 2
        var rowsPerStrip = 0
        var predictor = 1
        var sampleFormat = 1
        var stripOffsets: LongArray = LongArray(0)
        var stripByteCounts: LongArray = LongArray(0)
    }

    /**
     * Decode [buffer] (an mmap'd TIFF) to a Bitmap downsampled toward [reqW] x [reqH]. Pass 0/0 to
     * decode at full resolution. Returns null if the TIFF layout isn't supported here.
     */
    fun decode(buffer: ByteBuffer, reqW: Int, reqH: Int): Bitmap? = try {
        decodeInternal(buffer, reqW, reqH)
    } catch (e: Throwable) {
        printWarn(TAG, "striped decode failed: ${e.message}")
        null
    }

    private fun decodeInternal(buffer: ByteBuffer, reqW: Int, reqH: Int): Bitmap? {
        val little = when {
            buffer.limit() < 8 -> return null
            buffer.get(0).toInt() == 0x49 && buffer.get(1).toInt() == 0x49 -> true
            buffer.get(0).toInt() == 0x4D && buffer.get(1).toInt() == 0x4D -> false
            else -> return null
        }
        buffer.order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        // Classic TIFF only (magic 42); BigTIFF (43) is not handled here.
        if (u16(buffer, 2) != 42) return null
        val ifdOffset = u32(buffer, 4)
        val ifd = parseIfd(buffer, ifdOffset) ?: return null

        // Only the chunky RGB / grayscale, uint, no-predictor, NONE/Deflate layout is supported.
        if (ifd.predictor != 1) return null
        if (ifd.sampleFormat != 1) return null
        if (ifd.width <= 0 || ifd.height <= 0 || ifd.rowsPerStrip <= 0) return null
        if (ifd.bitsPerSample != 8 && ifd.bitsPerSample != 16) return null
        val samples = ifd.samplesPerPixel
        if (samples != 1 && samples != 3 && samples != 4) return null
        if (ifd.compression != COMPRESSION_NONE &&
            ifd.compression != COMPRESSION_DEFLATE &&
            ifd.compression != COMPRESSION_DEFLATE_ADOBE
        ) return null
        // 2 = RGB, 1 = BlackIsZero grayscale, 0 = WhiteIsZero grayscale.
        if (ifd.photometric != 2 && ifd.photometric != 1 && ifd.photometric != 0) return null
        val stripCount = ifd.stripOffsets.size
        if (stripCount == 0 || stripCount != ifd.stripByteCounts.size || stripCount > MAX_STRIPS) return null

        val srcW = ifd.width
        val srcH = ifd.height
        val stride = computeStride(srcW, srcH, reqW, reqH)
        val outW = (srcW + stride - 1) / stride
        val outH = (srcH + stride - 1) / stride
        if (outW <= 0 || outH <= 0) return null

        val bytesPerSample = ifd.bitsPerSample / 8
        val rowBytes = srcW * samples * bytesPerSample
        // Byte offset of the most-significant byte within a 16-bit sample (file byte order).
        val hiByte = if (ifd.bitsPerSample == 16 && little) 1 else 0
        val whiteIsZero = ifd.photometric == 0

        val pixels = IntArray(outW * outH)
        val inflater = if (ifd.compression != COMPRESSION_NONE) Inflater() else null
        var stripBuf = ByteArray(ifd.rowsPerStrip * rowBytes)

        var outY = 0
        var y = 0
        while (y < srcH && outY < outH) {
            val strip = y / ifd.rowsPerStrip
            if (strip >= stripCount) break
            val stripStartRow = strip * ifd.rowsPerStrip
            val rowsInStrip = minOf(ifd.rowsPerStrip, srcH - stripStartRow)
            val uncompressedLen = rowsInStrip * rowBytes
            if (stripBuf.size < uncompressedLen) stripBuf = ByteArray(uncompressedLen)

            // Materialise this strip's uncompressed bytes only when it holds a row we actually need.
            val decoded = decodeStrip(
                buffer, ifd.stripOffsets[strip], ifd.stripByteCounts[strip],
                stripBuf, uncompressedLen, inflater,
            )
            if (!decoded) return null

            // Emit every stride-th row that falls inside this strip.
            while (y < stripStartRow + rowsInStrip && outY < outH) {
                val rowOffInStrip = (y - stripStartRow) * rowBytes
                var outX = 0
                var x = 0
                val rowPixelBase = outY * outW
                while (x < srcW && outX < outW) {
                    val p = rowOffInStrip + x * samples * bytesPerSample
                    pixels[rowPixelBase + outX] = pixelToArgb(
                        stripBuf, p, samples, bytesPerSample, hiByte, whiteIsZero,
                    )
                    outX++
                    x += stride
                }
                outY++
                y += stride
            }
        }
        inflater?.end()
        if (outY == 0) return null
        return Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
    }

    private fun decodeStrip(
        buffer: ByteBuffer,
        offset: Long,
        byteCount: Long,
        out: ByteArray,
        expectedLen: Int,
        inflater: Inflater?,
    ): Boolean {
        if (offset < 0 || byteCount <= 0 || offset + byteCount > buffer.limit()) return false
        val compressed = ByteArray(byteCount.toInt())
        // Absolute bulk read from the mmap'd buffer (doesn't disturb position for other strips).
        val dup = buffer.duplicate()
        dup.position(offset.toInt())
        dup.get(compressed, 0, compressed.size)
        return if (inflater == null) {
            // Uncompressed: copy straight through.
            System.arraycopy(compressed, 0, out, 0, minOf(compressed.size, expectedLen))
            true
        } else {
            inflater.reset()
            inflater.setInput(compressed)
            var written = 0
            while (!inflater.finished() && written < expectedLen) {
                val n = inflater.inflate(out, written, expectedLen - written)
                if (n == 0) {
                    if (inflater.finished() || inflater.needsDictionary()) break
                    if (inflater.needsInput()) break
                }
                written += n
            }
            written > 0
        }
    }

    private fun pixelToArgb(
        buf: ByteArray,
        p: Int,
        samples: Int,
        bytesPerSample: Int,
        hiByte: Int,
        whiteIsZero: Boolean,
    ): Int {
        fun sample(i: Int): Int {
            val idx = p + i * bytesPerSample + hiByte
            if (idx >= buf.size) return 0
            return buf[idx].toInt() and 0xFF
        }
        return when (samples) {
            1 -> {
                var g = sample(0)
                if (whiteIsZero) g = 255 - g
                (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
            else -> {
                val r = sample(0)
                val g = sample(1)
                val b = sample(2)
                val a = if (samples >= 4) sample(3) else 0xFF
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun parseIfd(buffer: ByteBuffer, ifdOffset: Long): Ifd? {
        if (ifdOffset <= 0 || ifdOffset + 2 > buffer.limit()) return null
        val ifd = Ifd()
        val count = u16(buffer, ifdOffset.toInt())
        var e = ifdOffset.toInt() + 2
        for (i in 0 until count) {
            if (e + 12 > buffer.limit()) break
            val tag = u16(buffer, e)
            val type = u16(buffer, e + 2)
            val cnt = u32(buffer, e + 4)
            val valueOff = e + 8
            when (tag) {
                256 -> ifd.width = scalar(buffer, type, valueOff).toInt()
                257 -> ifd.height = scalar(buffer, type, valueOff).toInt()
                // BitsPerSample / SampleFormat are per-sample arrays (3 for RGB) stored out-of-line;
                // read the first element (libtiff writes equal values for every sample).
                258 -> ifd.bitsPerSample = firstOfArray(buffer, type, cnt, valueOff)
                259 -> ifd.compression = scalar(buffer, type, valueOff).toInt()
                262 -> ifd.photometric = scalar(buffer, type, valueOff).toInt()
                273 -> ifd.stripOffsets = array(buffer, type, cnt, valueOff)
                277 -> ifd.samplesPerPixel = scalar(buffer, type, valueOff).toInt()
                278 -> ifd.rowsPerStrip = scalar(buffer, type, valueOff).toInt()
                279 -> ifd.stripByteCounts = array(buffer, type, cnt, valueOff)
                284 -> if (scalar(buffer, type, valueOff).toInt() != 1) return null // planar
                317 -> ifd.predictor = scalar(buffer, type, valueOff).toInt()
                322, 323, 324, 325 -> return null // tiled TIFF: not supported here
                339 -> ifd.sampleFormat = firstOfArray(buffer, type, cnt, valueOff)
            }
            e += 12
        }
        if (ifd.samplesPerPixel == 0) ifd.samplesPerPixel = 1
        // Some encoders omit RowsPerStrip (single strip = whole image).
        if (ifd.rowsPerStrip == 0 && ifd.height > 0) ifd.rowsPerStrip = ifd.height
        return ifd
    }

    /** Reads a scalar value from an IFD entry whose value fits inline. */
    private fun scalar(buffer: ByteBuffer, type: Int, valueOff: Int): Long = when (type) {
        TYPE_SHORT -> u16(buffer, valueOff).toLong()
        else -> u32(buffer, valueOff)
    }

    /**
     * Reads the first element of a possibly-multi-valued entry, resolving the out-of-line offset
     * when the array doesn't fit in the 4-byte value field (e.g. BitsPerSample = [16,16,16]).
     */
    private fun firstOfArray(buffer: ByteBuffer, type: Int, count: Long, valueOff: Int): Int {
        val typeSize = if (type == TYPE_SHORT) 2 else 4
        val pos = if (count * typeSize <= 4L) valueOff else u32(buffer, valueOff).toInt()
        if (pos < 0 || pos + typeSize > buffer.limit()) return 0
        return if (type == TYPE_SHORT) u16(buffer, pos) else u32(buffer, pos).toInt()
    }

    /** Reads a SHORT/LONG array, resolving the out-of-line offset when it doesn't fit inline. */
    private fun array(buffer: ByteBuffer, type: Int, count: Long, valueOff: Int): LongArray {
        val typeSize = if (type == TYPE_SHORT) 2 else 4
        val n = count.toInt()
        if (n <= 0 || n > MAX_STRIPS) return LongArray(0)
        val base = if (count * typeSize <= 4L) valueOff else u32(buffer, valueOff).toInt()
        val out = LongArray(n)
        for (i in 0 until n) {
            val pos = base + i * typeSize
            if (pos + typeSize > buffer.limit()) return out.copyOf(i)
            out[i] = if (type == TYPE_SHORT) u16(buffer, pos).toLong() else u32(buffer, pos)
        }
        return out
    }

    private fun computeStride(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        if (reqW <= 0 || reqH <= 0) return 1
        val sx = srcW / reqW
        val sy = srcH / reqH
        return max(1, minOf(sx, sy))
    }

    private fun u16(buffer: ByteBuffer, pos: Int): Int = buffer.getShort(pos).toInt() and 0xFFFF

    private fun u32(buffer: ByteBuffer, pos: Int): Long = buffer.getInt(pos).toLong() and 0xFFFFFFFFL
}
