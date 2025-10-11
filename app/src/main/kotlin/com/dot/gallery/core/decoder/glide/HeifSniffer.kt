package com.dot.gallery.core.decoder.glide

/** Utility to locate HEIF/AVIF primary brand inside an ISO BMFF header. */
import android.util.Log

object HeifSniffer {
    private val acceptedBrands = setOf(
        "heic", "heix", "heif", "hevc", "hevx", "heim", "heis", "hevm", "hevs",
        "mif1", "msf1", "avif", "avis"
    )

    /**
     * Scan first [length] bytes of [buffer] for the sequence 'f','t','y','p' then read 4 chars brand.
     * Returns brand string if accepted, else null.
     */
    fun findBrand(buffer: ByteArray, length: Int): String? {
        if (length < 12) return null
        // Pass 1: canonical ftyp scan
        val max = minOf(length - 8, 256) // allow a bit deeper search now
        var i = 0
        while (i < max) {
            if (buffer[i] == 'f'.code.toByte() && i + 11 < length &&
                buffer[i + 1] == 't'.code.toByte() &&
                buffer[i + 2] == 'y'.code.toByte() &&
                buffer[i + 3] == 'p'.code.toByte()
            ) {
                val brand = safeString(buffer, i + 4, 4) ?: return null
                if (brand.lowercase() in acceptedBrands) return brand
            }
            i++
        }
        // Pass 2: heuristic brand substring search (may produce false positives; only used for logging & investigation)
        val window = buffer.copyOfRange(0, minOf(length, 256)).toString(Charsets.ISO_8859_1)
        val hit = acceptedBrands.firstOrNull { window.contains(it, ignoreCase = true) }
        if (hit != null) {
            Log.d(TAG, "Heuristic brand match '$hit' without ftyp alignment")
            return hit
        }
        return null
    }

    fun hexSample(buffer: ByteArray, length: Int, max: Int = 64): String =
        buffer.take(minOf(length, max)).joinToString(" ") { String.format("%02X", it) }

    private fun safeString(b: ByteArray, off: Int, len: Int): String? = try {
        String(b, off, len)
    } catch (e: Exception) { null }

    private const val TAG = "HeifSniffer"
}