package com.dot.gallery.editor.io

import java.io.EOFException
import java.io.InputStream

/**
 * Reads the EXIF orientation (tag `0x0112`) of a JPEG stream without decoding
 * the image and without buffering more than one APPn segment (< 64 KiB).
 *
 * Returns the EXIF value 1–8, or 1 when the stream is not a JPEG, carries no
 * orientation tag, or has malformed/truncated orientation metadata — the
 * actual decoder remains the arbiter of a broken image. Only explicit values
 * 1–8 are honoured; anything else falls back to 1. PNG `eXIf` chunks are not
 * consulted: only JPEG APP1/Exif is read in this slice.
 *
 * Scanning is bounded to [MAX_SCAN_BYTES]; the caller keeps ownership of
 * [input] — it is never closed here.
 */
object JpegExifOrientation {

    /** Maximum bytes read while looking for the APP1/Exif segment. */
    const val MAX_SCAN_BYTES = 1 shl 20

    private const val MARKER_PREFIX = 0xFF
    private const val MARKER_SOI = 0xD8
    private const val MARKER_SOS = 0xDA
    private const val MARKER_APP1 = 0xE1
    private const val TAG_ORIENTATION = 0x0112
    private const val TYPE_SHORT = 3
    private const val TIFF_MAGIC = 42
    private val EXIF_HEADER = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)

    fun read(input: InputStream): Int {
        val stream = BoundedInput(input)
        return try {
            scan(stream)
        } catch (e: EOFException) {
            // Truncated stream: no reliable orientation was found.
            1
        }
    }

    private fun scan(stream: BoundedInput): Int {
        if (stream.u8() != MARKER_PREFIX || stream.u8() != MARKER_SOI) return 1
        while (true) {
            // Segments are introduced by 0xFF; extra 0xFF bytes are padding.
            if (stream.u8() != MARKER_PREFIX) return 1
            var marker = stream.u8()
            while (marker == MARKER_PREFIX) marker = stream.u8()
            if (marker == MARKER_SOS || marker == 0xD9) return 1
            // Standalone markers carry no payload.
            if (marker == 0x01 || marker in 0xD0..0xD7 || marker == MARKER_SOI) {
                continue
            }
            val length = stream.u16()
            if (length < 2) return 1
            val payload = length - 2
            if (marker == MARKER_APP1) {
                val segment = ByteArray(payload)
                stream.readFully(segment)
                return orientationFromApp1(segment) ?: continue
            }
            stream.skipFully(payload)
        }
    }

    /**
     * Parses `Exif\0\0` + TIFF inside an APP1 payload. Returns null when the
     * segment is not Exif (e.g. XMP), so scanning continues at the next marker.
     */
    private fun orientationFromApp1(segment: ByteArray): Int? {
        if (segment.size < EXIF_HEADER.size + 8) return null
        for (i in EXIF_HEADER.indices) {
            if (segment[i] != EXIF_HEADER[i]) return null
        }
        val tiff = EXIF_HEADER.size
        val littleEndian = when {
            segment[tiff] == 'I'.code.toByte() && segment[tiff + 1] == 'I'.code.toByte() -> true
            segment[tiff] == 'M'.code.toByte() && segment[tiff + 1] == 'M'.code.toByte() -> false
            else -> return null
        }
        // TIFF magic (42) must follow the byte order mark — an Exif segment
        // without it is not a TIFF directory at all.
        if (u16(segment, tiff + 2, littleEndian) != TIFF_MAGIC) return null
        val ifdOffset = u32(segment, tiff + 4, littleEndian)
        // TIFF offsets are relative to the TIFF header start and 32-bit; use
        // Long arithmetic before comparing against the segment size. The
        // first IFD can legally sit at offset 8 or later only.
        if (ifdOffset < 8L || ifdOffset + 2 > (segment.size - tiff).toLong()) return null
        val ifd = tiff + ifdOffset.toInt()
        val entryCount = u16(segment, ifd, littleEndian)
        for (i in 0 until entryCount) {
            val entry = ifd + 2 + i.toLong() * 12
            if (entry + 12 > segment.size.toLong()) return null
            val entryAt = entry.toInt()
            if (u16(segment, entryAt, littleEndian) != TAG_ORIENTATION) continue
            if (u16(segment, entryAt + 2, littleEndian) != TYPE_SHORT) return null
            if (u32(segment, entryAt + 4, littleEndian) != 1L) return null
            val value = u16(segment, entryAt + 8, littleEndian)
            return if (value in 1..8) value else null
        }
        return null
    }

    private fun u16(data: ByteArray, offset: Int, littleEndian: Boolean): Int {
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        return if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
    }

    private fun u32(data: ByteArray, offset: Int, littleEndian: Boolean): Long {
        val v0 = data[offset].toLong() and 0xFF
        val v1 = data[offset + 1].toLong() and 0xFF
        val v2 = data[offset + 2].toLong() and 0xFF
        val v3 = data[offset + 3].toLong() and 0xFF
        return if (littleEndian) {
            v0 or (v1 shl 8) or (v2 shl 16) or (v3 shl 24)
        } else {
            (v0 shl 24) or (v1 shl 16) or (v2 shl 8) or v3
        }
    }

    /** InputStream wrapper that bounds total consumption and fails on EOF. */
    private class BoundedInput(private val input: InputStream) {
        private var consumed = 0

        private fun charged(n: Int) {
            if (consumed + n > MAX_SCAN_BYTES) throw EOFException("scan limit")
            consumed += n
        }

        fun u8(): Int {
            charged(1)
            val b = input.read()
            if (b < 0) throw EOFException()
            return b
        }

        fun u16(): Int = (u8() shl 8) or u8()

        fun readFully(target: ByteArray) {
            charged(target.size)
            var offset = 0
            while (offset < target.size) {
                val n = input.read(target, offset, target.size - offset)
                if (n < 0) throw EOFException()
                if (n == 0) {
                    // Some streams legally return 0-length reads; fall back to
                    // a single-byte read or EOF so the loop always progresses.
                    val b = input.read()
                    if (b < 0) throw EOFException()
                    target[offset] = b.toByte()
                    offset += 1
                } else {
                    offset += n
                }
            }
        }

        fun skipFully(count: Int) {
            charged(count)
            var remaining = count.toLong()
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped <= 0) {
                    if (input.read() < 0) throw EOFException()
                    remaining -= 1
                } else {
                    remaining -= skipped
                }
            }
        }
    }
}
