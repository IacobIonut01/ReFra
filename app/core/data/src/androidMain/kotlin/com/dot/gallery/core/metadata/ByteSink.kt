package com.dot.gallery.core.metadata

/** Growable byte buffer with in-place patching, used to assemble rebuilt containers. */
internal class ByteSink(initial: Int = 1024) {
    private var buffer = ByteArray(initial)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }

    fun write(byte: Int) {
        ensure(1); buffer[size++] = byte.toByte()
    }

    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        ensure(length); bytes.copyInto(buffer, size, offset, offset + length); size += length
    }

    fun writeU16(value: Int, little: Boolean = false) {
        ensure(2); buffer.putU16(size, value, little); size += 2
    }

    fun writeU32(value: Long, little: Boolean = false) {
        ensure(4); buffer.putU32(size, value, little); size += 4
    }

    fun ascii(value: String) = write(value.toByteArray(Charsets.ISO_8859_1))

    fun align2() {
        if (size and 1 == 1) write(0)
    }

    fun patchU16(offset: Int, value: Int, little: Boolean = false) = buffer.putU16(offset, value, little)
    fun patchU32(offset: Int, value: Long, little: Boolean = false) = buffer.putU32(offset, value, little)

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}
