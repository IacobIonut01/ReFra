/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image

import android.media.MediaDataSource
import java.io.IOException


/** Thrown when a [RangeBlockReader] exceeds its fetch budget. */
internal class RangeBudgetExceededException(message: String) : IOException(message)

/**
 * Bounded, cancellable random-access reader over a remote file, for
 * [android.media.MediaMetadataRetriever] via [RemoteVideoDataSource].
 *
 * Reads are served from an LRU of block-aligned range fetches (default 256 KiB,
 * at most 8 cached blocks and 16 MiB fetched total), so extracting a video poster
 * touches only the moov atom and the frames MMR actually decodes instead of the
 * whole remote file. [isCancelled] is checked before every fetch so a scrolled-off
 * grid cell stops pulling bytes; exceeding [budgetBytes] or a failed/short fetch
 * fails the read with [IOException].
 */
internal class RangeBlockReader(
    val size: Long,
    private val blockSize: Int = 256 * 1024,
    private val maxCachedBlocks: Int = 8,
    private val budgetBytes: Long = 16L * 1024 * 1024,
    private val isCancelled: () -> Boolean,
    private val fetch: (offset: Long, length: Int) -> ByteArray?,
) {
    /** Total bytes received from [fetch] so far. */
    @Volatile
    var fetchedBytes: Long = 0L
        private set

    private val blocks = LinkedHashMap<Long, ByteArray>(16, 0.75f, true)

    /**
     * Copies up to [length] bytes starting at [position] into [buffer].
     * Returns the number of bytes copied, or -1 at/after EOF.
     *
     * @throws IOException when cancelled, over budget, or a fetch fails or
     * short-reads.
     */
    @Synchronized
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position < 0L) throw IOException("negative read position: $position")
        if (position >= size) return -1
        if (length <= 0) return 0
        if (isCancelled()) throw IOException("range read cancelled")

        val remaining = size - position
        val limit = position + minOf(length.toLong(), remaining)
        var cursor = position
        var copied = 0
        while (cursor < limit) {
            val blockStart = cursor - cursor % blockSize
            val block = blockAt(blockStart)
            val within = (cursor - blockStart).toInt()
            val available = block.size - within
            val count = minOf(
                available.toLong(),
                limit - cursor,
                (length - copied).toLong()
            ).toInt()
            System.arraycopy(block, within, buffer, offset + copied, count)
            copied += count
            cursor += count
        }
        return if (copied == 0) -1 else copied
    }

    private fun blockAt(blockStart: Long): ByteArray {
        blocks[blockStart]?.let { return it }
        if (isCancelled()) throw IOException("range read cancelled")
        if (fetchedBytes > budgetBytes) {
            throw RangeBudgetExceededException(
                "range read budget exceeded: $fetchedBytes > $budgetBytes"
            )
        }
        val expected = minOf(blockSize.toLong(), size - blockStart).toInt()
        val fetched = fetch(blockStart, expected)
            ?: throw IOException("range fetch failed at offset $blockStart")
        if (fetched.size < expected) {
            throw IOException(
                "short range read at offset $blockStart: ${fetched.size}/$expected"
            )
        }
        fetchedBytes += fetched.size
        if (fetchedBytes > budgetBytes) {
            throw RangeBudgetExceededException(
                "range read budget exceeded: $fetchedBytes > $budgetBytes"
            )
        }
        val block = if (fetched.size == expected) fetched else fetched.copyOf(expected)
        blocks[blockStart] = block
        while (blocks.size > maxCachedBlocks) {
            blocks.remove(blocks.keys.first())
        }
        return block
    }
}

/**
 * [MediaDataSource] view of a [RangeBlockReader], so MediaMetadataRetriever reads
 * bounded ranged blocks through the provider's authenticated fetch instead of
 * doing its own unbounded HTTP range requests.
 */
internal class RemoteVideoDataSource(
    private val reader: RangeBlockReader
) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        reader.readAt(position, buffer, offset, size)

    override fun getSize(): Long = reader.size

    override fun close() = Unit
}
