package com.dot.gallery.editor.core.project

/**
 * Content checksum for persisted project files. The host marker (a
 * SavedState key or the recents index) stores the checksum beside the
 * file name, so a torn or truncated write is detected before the codec
 * even parses — corruption reports as a restore failure, never as a
 * half-loaded document. FNV-1a 64-bit over UTF-8 bytes, rendered as
 * sixteen lowercase hex digits.
 */
object ProjectChecksum {

    fun of(content: String): String {
        var hash = FNV_OFFSET
        for (byte in content.encodeToByteArray()) {
            hash = hash xor (byte.toULong() and 0xFFu)
            hash *= FNV_PRIME
        }
        return hash.toString(16).padStart(16, '0')
    }

    private const val FNV_OFFSET = 0xCBF29CE484222325UL
    private const val FNV_PRIME = 0x100000001B3UL
}
