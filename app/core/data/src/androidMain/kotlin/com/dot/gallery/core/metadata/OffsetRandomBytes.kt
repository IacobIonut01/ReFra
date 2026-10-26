package com.dot.gallery.core.metadata

/** A window of [base] starting at [offset], for containers embedded inside another file. */
internal class OffsetRandomBytes(private val base: RandomBytes, private val offset: Long, override val size: Long) : RandomBytes {
    init {
        if (offset < 0 || size < 0 || offset + size > base.size) malformed("Embedded range exceeds its container")
    }

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || length < 0 || position + length > size) malformed("Read past end at $position")
        return base.read(offset + position, length)
    }

    override fun write(position: Long, bytes: ByteArray) {
        if (position < 0 || position + bytes.size > size) malformed("Write past end at $position")
        base.write(offset + position, bytes)
    }
}
