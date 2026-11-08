package com.dot.gallery.editor.io

/**
 * Best-effort estimate of the quality a JPEG was encoded at, read from its
 * luminance quantization table (DQT).
 *
 * JPEG is the only format that records its encode settings, so this is what
 * lets an export match the source's fidelity instead of guessing: the copy is
 * written at the same quality the original was created with, which keeps the
 * file size in the same order as the original instead of the orders-of-magnitude
 * jump a lossless PNG copy of a photo produces.
 *
 * Uses the classic IJG scaling relation: the luminance table is the quality-50
 * standard table scaled by `5000 / q` below 50 and `200 - 2q` at or above it.
 * Everything is bounds-checked; malformed or non-JPEG input returns `null`
 * (the caller then uses its own default), never an exception.
 */
object JpegQualityProbe {

    /** Standard IJG luminance quantization table (quality 50 baseline). */
    private val STD_LUMINANCE_QT = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99
    )

    /**
     * Estimated quality in 1..100 for the JPEG [bytes] (which may be only the
     * first block of the file), or `null` when it is not a JPEG or carries no
     * readable luminance DQT.
     */
    fun estimate(bytes: ByteArray): Int? {
        val table = firstLuminanceQuantTable(bytes) ?: return null
        var observed = 0L
        var standard = 0L
        for (i in 0 until 64) {
            observed += table[i]
            standard += STD_LUMINANCE_QT[i]
        }
        if (observed <= 0L || standard <= 0L) return null
        // Scale of the source table against the quality-50 baseline, in percent.
        val scale = observed.toDouble() / standard.toDouble() * 100.0
        val quality = if (scale <= 100.0) {
            (200.0 - scale) / 2.0
        } else {
            5000.0 / scale
        }
        return quality.toInt().coerceIn(1, 100)
    }

    /** Scans the JPEG markers for the first DQT and returns its 64 entries. */
    private fun firstLuminanceQuantTable(bytes: ByteArray): IntArray? {
        if (bytes.size < 4) return null
        // SOI
        if (bytes[0].u() != 0xFF || bytes[1].u() != 0xD8) return null
        var i = 2
        while (i + 4 < bytes.size) {
            if (bytes[i].u() != 0xFF) {
                i++
                continue
            }
            val marker = bytes[i + 1].u()
            // Standalone markers carry no length.
            if (marker == 0xD9 || marker == 0x01 || marker in 0xD0..0xD7) {
                i += 2
                continue
            }
            val length = (bytes[i + 2].u() shl 8) or bytes[i + 3].u()
            if (length < 2) return null
            if (marker == 0xDB) { // DQT
                var p = i + 4
                val end = minOf(i + 2 + length, bytes.size)
                while (p < end) {
                    val precision = bytes[p].u() shr 4 // 0 = 8-bit, 1 = 16-bit
                    p++
                    val table = IntArray(64)
                    if (precision == 0) {
                        if (p + 64 > bytes.size) return null
                        for (k in 0 until 64) table[k] = bytes[p + k].u()
                        p += 64
                    } else {
                        if (p + 128 > bytes.size) return null
                        for (k in 0 until 64) {
                            table[k] = (bytes[p + 2 * k].u() shl 8) or bytes[p + 2 * k + 1].u()
                        }
                        p += 128
                    }
                    // The first table in a DQT segment is the luminance table.
                    return table
                }
            }
            // Start of scan: no more header segments worth parsing.
            if (marker == 0xDA) return null
            i += 2 + length
        }
        return null
    }

    private fun Byte.u(): Int = toInt() and 0xFF
}
