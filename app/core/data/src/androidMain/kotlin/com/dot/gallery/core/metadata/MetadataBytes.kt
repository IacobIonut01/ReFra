package com.dot.gallery.core.metadata

import java.security.MessageDigest

internal fun ByteArray.u8(offset: Int): Int {
    if (offset !in indices) malformed("Read past end at $offset")
    return this[offset].toInt() and 0xFF
}

internal fun ByteArray.u16(offset: Int, little: Boolean = false): Int =
    if (little) u8(offset) or (u8(offset + 1) shl 8) else (u8(offset) shl 8) or u8(offset + 1)

internal fun ByteArray.u32(offset: Int, little: Boolean = false): Long =
    if (little) {
        u8(offset).toLong() or (u8(offset + 1).toLong() shl 8) or
            (u8(offset + 2).toLong() shl 16) or (u8(offset + 3).toLong() shl 24)
    } else {
        (u8(offset).toLong() shl 24) or (u8(offset + 1).toLong() shl 16) or
            (u8(offset + 2).toLong() shl 8) or u8(offset + 3).toLong()
    }

internal fun ByteArray.u64(offset: Int): Long {
    var value = 0L
    for (i in 0 until 8) value = (value shl 8) or u8(offset + i).toLong()
    if (value < 0) malformed("64-bit value exceeds signed range")
    return value
}

internal fun ByteArray.putU16(offset: Int, value: Int, little: Boolean = false) {
    if (little) {
        this[offset] = value.toByte(); this[offset + 1] = (value ushr 8).toByte()
    } else {
        this[offset] = (value ushr 8).toByte(); this[offset + 1] = value.toByte()
    }
}

internal fun ByteArray.putU32(offset: Int, value: Long, little: Boolean = false) {
    for (i in 0 until 4) {
        val shift = if (little) 8 * i else 8 * (3 - i)
        this[offset + i] = (value ushr shift).toByte()
    }
}

internal fun ByteArray.ascii(offset: Int, length: Int): String {
    if (offset < 0 || length < 0 || offset + length > size) malformed("Read past end at $offset")
    return String(this, offset, length, Charsets.ISO_8859_1)
}

internal fun ByteArray.startsWithAscii(value: String, offset: Int = 0): Boolean {
    if (offset < 0 || offset + value.length > size) return false
    return value.indices.all { this[offset + it] == value[it].code.toByte() }
}

internal fun ByteArray.indexOf(pattern: ByteArray, from: Int = 0, to: Int = size): Int {
    if (pattern.isEmpty()) return -1
    val last = to - pattern.size
    var i = from.coerceAtLeast(0)
    while (i <= last) {
        var j = 0
        while (j < pattern.size && this[i + j] == pattern[j]) j++
        if (j == pattern.size) return i
        i++
    }
    return -1
}

/** Text with trailing NULs and padding removed, for previews. */
internal fun ByteArray.cleanText(offset: Int = 0, length: Int = size - offset): String =
    String(this, offset, length.coerceAtMost(size - offset).coerceAtLeast(0), Charsets.UTF_8)
        .trim { it <= ' ' || it == '\u0000' }

internal fun ByteArray.isBlankBytes(offset: Int = 0, length: Int = size - offset): Boolean =
    (offset until (offset + length).coerceAtMost(size)).all { this[it] == 0.toByte() || this[it] == 0x20.toByte() }

internal fun MessageDigest.updateLong(value: Long) {
    for (i in 7 downTo 0) update((value ushr (8 * i)).toByte())
}
