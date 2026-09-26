/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.dot.gallery.feature_node.presentation.util.printWarn

/**
 * Minimal Adobe Photoshop (PSD/PSB) decoder. Android has no native PSD support.
 *
 * Strategy:
 *  1. Use the embedded JPEG thumbnail (image resource block 1033/1036) when it is large
 *     enough for the requested size — this is fast and ideal for grid thumbnails.
 *  2. Otherwise decode the flattened "merged image data" section. Supports the common case of
 *     8-bit Grayscale and RGB, both raw and RLE (PackBits) compressed.
 *
 * Unsupported variants (16/32-bit, CMYK, Lab, ZIP compression) fall back to the embedded
 * thumbnail if present, else return null so the caller can fall through.
 */
object PsdImageDecoder {

    private const val TAG = "decode.psd"

    private data class Header(
        val width: Int,
        val height: Int,
        val channels: Int,
        val depth: Int,
        val colorMode: Int,
        val isPsb: Boolean,
    )

    fun getSize(bytes: ByteArray): Size? {
        val h = parseHeader(bytes) ?: return null
        return Size(h.width, h.height)
    }

    /**
     * Decodes the PSD to a bitmap no larger than [reqW] x [reqH] (when > 0), preserving aspect.
     */
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        val header = parseHeader(bytes) ?: return null
        if (header.width <= 0 || header.height <= 0) return null

        val targetW = if (reqW > 0) reqW else header.width
        val targetH = if (reqH > 0) reqH else header.height

        val thumb = runCatching { extractThumbnail(bytes) }.getOrNull()
        if (thumb != null && thumb.width >= targetW && thumb.height >= targetH) {
            return fit(thumb, targetW, targetH)
        }

        val merged = runCatching { decodeMerged(bytes, header) }
            .onFailure { printWarn(TAG, "merged image decode failed: ${it.message}") }
            .getOrNull()
        if (merged != null) return fit(merged, targetW, targetH)

        return thumb
    }

    private fun parseHeader(bytes: ByteArray): Header? {
        if (bytes.size < 26) return null
        if (!ImageFormatSniffer.isPsd(bytes)) return null
        val version = u16(bytes, 4)
        val isPsb = version == 2
        val channels = u16(bytes, 12)
        val height = u32(bytes, 14)
        val width = u32(bytes, 18)
        val depth = u16(bytes, 22)
        val colorMode = u16(bytes, 24)
        if (width <= 0 || height <= 0 || channels <= 0) return null
        return Header(width, height, channels, depth, colorMode, isPsb)
    }

    /** Extracts the embedded JPEG thumbnail from the Image Resources section, if present. */
    private fun extractThumbnail(bytes: ByteArray): Bitmap? {
        var pos = 26
        // Color Mode Data section
        if (pos + 4 > bytes.size) return null
        val colorModeLen = u32(bytes, pos); pos += 4 + colorModeLen
        // Image Resources section
        if (pos + 4 > bytes.size) return null
        val resourcesLen = u32(bytes, pos); pos += 4
        val resourcesEnd = (pos + resourcesLen).coerceAtMost(bytes.size)

        while (pos + 12 <= resourcesEnd) {
            // Signature "8BIM"
            if (!(bytes[pos] == 0x38.toByte() && bytes[pos + 1] == 0x42.toByte() &&
                        bytes[pos + 2] == 0x49.toByte() && bytes[pos + 3] == 0x4D.toByte())
            ) break
            pos += 4
            val resId = u16(bytes, pos); pos += 2
            // Pascal string name, padded to even length
            val nameLen = bytes[pos].toInt() and 0xFF
            var nameField = 1 + nameLen
            if (nameField % 2 != 0) nameField++
            pos += nameField
            if (pos + 4 > resourcesEnd) break
            val size = u32(bytes, pos); pos += 4
            val dataStart = pos
            // Thumbnail resources: 1033 (old, BGR) and 1036 (RGB)
            if ((resId == 1033 || resId == 1036) && size > 28) {
                val format = u32(bytes, dataStart) // 1 = kJpegRGB
                if (format == 1) {
                    val jpegStart = dataStart + 28
                    val jpegLen = size - 28
                    if (jpegStart + jpegLen <= bytes.size) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, jpegStart, jpegLen)
                        if (bmp != null) return bmp
                    }
                }
            }
            pos = dataStart + size
            if (size % 2 != 0) pos++ // padded to even
        }
        return null
    }

    /** Decodes the flattened composite (merged image data) for 8-bit Gray / RGB. */
    private fun decodeMerged(bytes: ByteArray, header: Header): Bitmap? {
        if (header.depth != 8) return null
        if (header.colorMode != 1 && header.colorMode != 3) return null // Gray or RGB only

        var pos = 26
        // Skip Color Mode Data
        if (pos + 4 > bytes.size) return null
        pos += 4 + u32(bytes, pos)
        // Skip Image Resources
        if (pos + 4 > bytes.size) return null
        pos += 4 + u32(bytes, pos)
        // Skip Layer and Mask Information (length is 8 bytes for PSB)
        if (header.isPsb) {
            if (pos + 8 > bytes.size) return null
            val len = u64(bytes, pos); pos += 8 + len.toInt()
        } else {
            if (pos + 4 > bytes.size) return null
            pos += 4 + u32(bytes, pos)
        }
        if (pos + 2 > bytes.size) return null
        val compression = u16(bytes, pos); pos += 2

        val w = header.width
        val h = header.height
        val totalChannels = header.channels
        val pixelCount = w * h

        val planes: Array<ByteArray> = when (compression) {
            0 -> readRawPlanes(bytes, pos, w, h, totalChannels) ?: return null
            1 -> readRlePlanes(bytes, pos, w, h, totalChannels, header.isPsb) ?: return null
            else -> return null // ZIP not supported
        }

        val pixels = IntArray(pixelCount)
        val isRgb = header.colorMode == 3
        val hasAlpha = if (isRgb) totalChannels >= 4 else totalChannels >= 2
        for (i in 0 until pixelCount) {
            val a = if (hasAlpha) {
                val alphaPlane = if (isRgb) 3 else 1
                planes[alphaPlane][i].toInt() and 0xFF
            } else 0xFF
            if (isRgb) {
                val r = planes[0][i].toInt() and 0xFF
                val g = planes[1][i].toInt() and 0xFF
                val b = planes[2][i].toInt() and 0xFF
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            } else {
                val v = planes[0][i].toInt() and 0xFF
                pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        val bmp = createBitmap(w, h)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun readRawPlanes(
        bytes: ByteArray, start: Int, w: Int, h: Int, channels: Int
    ): Array<ByteArray>? {
        val planeSize = w * h
        var pos = start
        return Array(channels) {
            if (pos + planeSize > bytes.size) return null
            val plane = bytes.copyOfRange(pos, pos + planeSize)
            pos += planeSize
            plane
        }
    }

    private fun readRlePlanes(
        bytes: ByteArray, start: Int, w: Int, h: Int, channels: Int, isPsb: Boolean
    ): Array<ByteArray>? {
        var pos = start
        // Byte counts per scanline: channels * h, each 2 bytes (PSD) or 4 bytes (PSB).
        val countBytes = if (isPsb) 4 else 2
        val totalLines = channels * h
        if (pos + totalLines * countBytes > bytes.size) return null
        val lineLengths = IntArray(totalLines) {
            val v = if (isPsb) u32(bytes, pos) else u16(bytes, pos)
            pos += countBytes
            v
        }
        val planeSize = w * h
        var lineIdx = 0
        return Array(channels) {
            val plane = ByteArray(planeSize)
            for (row in 0 until h) {
                val lineLen = lineLengths[lineIdx++]
                val lineEnd = pos + lineLen
                if (lineEnd > bytes.size) return null
                val rowStart = row * w
                var written = 0
                while (pos < lineEnd && written < w) {
                    val n = bytes[pos++].toInt()
                    when {
                        n >= 0 -> {
                            val count = n + 1
                            for (k in 0 until count) {
                                if (pos >= bytes.size || written >= w) break
                                plane[rowStart + written] = bytes[pos++]
                                written++
                            }
                        }
                        n != -128 -> {
                            val count = 1 - n
                            if (pos >= bytes.size) break
                            val value = bytes[pos++]
                            for (k in 0 until count) {
                                if (written >= w) break
                                plane[rowStart + written] = value
                                written++
                            }
                        }
                    }
                }
                pos = lineEnd
            }
            plane
        }
    }

    /** Scales [src] down to fit within [maxW] x [maxH] preserving aspect ratio. */
    private fun fit(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (maxW <= 0 || maxH <= 0) return src
        if (src.width <= maxW && src.height <= maxH) return src
        val scale = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
        val tw = (src.width * scale).toInt().coerceAtLeast(1)
        val th = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = src.scale(tw, th)
        if (scaled != src) src.recycle()
        return scaled
    }

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
                ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun u64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return v
    }
}
