package com.dot.gallery.core.metadata

internal class ArrayRandomBytes(val bytes: ByteArray) : RandomBytes {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || length < 0 || position + length > bytes.size) malformed("Read past end at $position")
        return bytes.copyOfRange(position.toInt(), position.toInt() + length)
    }

    override fun write(position: Long, bytes: ByteArray) {
        if (position < 0 || position + bytes.size > this.bytes.size) malformed("Write past end at $position")
        bytes.copyInto(this.bytes, position.toInt())
    }
}
