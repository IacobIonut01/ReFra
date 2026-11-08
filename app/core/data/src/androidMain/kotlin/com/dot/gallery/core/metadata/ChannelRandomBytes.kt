package com.dot.gallery.core.metadata

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class ChannelRandomBytes(private val channel: FileChannel) : RandomBytes {
    override val size: Long = channel.size()

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || length < 0 || position + length > size) malformed("Read past end at $position")
        val buffer = ByteBuffer.allocate(length)
        var at = position
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer, at)
            if (count < 0) throw EOFException()
            at += count
        }
        return buffer.array()
    }

    override fun write(position: Long, bytes: ByteArray) {
        if (position < 0 || position + bytes.size > size) malformed("Write past end at $position")
        val buffer = ByteBuffer.wrap(bytes)
        var at = position
        while (buffer.hasRemaining()) at += channel.write(buffer, at)
    }
}

/** Merges possibly overlapping half-open ranges into a sorted, disjoint list. */
internal fun mergeRanges(ranges: List<LongRange>): List<LongRange> {
    val sorted = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
    val merged = mutableListOf<LongRange>()
    for (range in sorted) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.size - 1] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}
