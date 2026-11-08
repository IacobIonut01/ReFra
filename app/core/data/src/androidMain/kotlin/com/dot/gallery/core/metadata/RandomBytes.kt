package com.dot.gallery.core.metadata

/** Positional byte access shared by in-memory and file-backed in-place editing. */
internal interface RandomBytes {
    val size: Long
    fun read(position: Long, length: Int): ByteArray
    fun write(position: Long, bytes: ByteArray)

    fun readU32(position: Long): Long = read(position, 4).u32(0)
    fun readU64(position: Long): Long = read(position, 8).u64(0)
    fun readType(position: Long): String = read(position, 4).ascii(0, 4)
}
