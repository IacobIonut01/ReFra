/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test


/**
 * Contract tests for [RangeBlockReader]: block-aligned bounded fetches, LRU reuse,
 * EOF handling, the fetch budget and cancellation — the pieces that keep a
 * MediaMetadataRetriever poster extraction from pulling whole remote videos.
 */
class RangeBlockReaderTest {

    // 1 MiB of deterministic bytes; small blocks keep the tests fast.
    private val data = ByteArray(1024 * 1024) { (it % 251).toByte() }

    private class FetchLog {
        val calls = mutableListOf<Pair<Long, Int>>()
        val count = AtomicInteger()
    }

    private fun reader(
        size: Long = data.size.toLong(),
        blockSize: Int = 256,
        maxCachedBlocks: Int = 8,
        budgetBytes: Long = 16L * 1024 * 1024,
        cancelled: () -> Boolean = { false },
        fetch: ((offset: Long, length: Int) -> ByteArray?)? = null,
        log: FetchLog = FetchLog()
    ): RangeBlockReader {
        val source = fetch ?: { offset, length ->
            log.calls += offset to length
            log.count.incrementAndGet()
            if (offset >= size) null
            else data.copyOfRange(offset.toInt(), minOf(offset + length, size).toInt())
        }
        return RangeBlockReader(
            size = size,
            blockSize = blockSize,
            maxCachedBlocks = maxCachedBlocks,
            budgetBytes = budgetBytes,
            isCancelled = cancelled,
            fetch = source
        )
    }

    @Test
    fun readSpanningTwoBlocksReturnsRightBytes() {
        val r = reader(blockSize = 256)
        val buffer = ByteArray(200)
        // position 200, length 200 → bytes 200..399, crossing the 256 boundary.
        val read = r.readAt(200, buffer, 0, 200)
        assertEquals(200, read)
        assertArrayEquals(data.copyOfRange(200, 400), buffer)
    }

    @Test
    fun rereadingACachedBlockDoesNotFetchAgain() {
        val log = FetchLog()
        val r = reader(blockSize = 256, log = log)
        val buffer = ByteArray(64)
        r.readAt(0, buffer, 0, 64)
        r.readAt(10, buffer, 0, 64) // same block 0
        assertEquals(1, log.count.get())
        assertEquals(listOf(0L to 256), log.calls)
    }

    @Test
    fun readsAtOrAfterEofReturnMinusOne() {
        val r = reader()
        val buffer = ByteArray(16)
        assertEquals(-1, r.readAt(data.size.toLong(), buffer, 0, 16))
        assertEquals(-1, r.readAt(data.size.toLong() + 100, buffer, 0, 16))
    }

    @Test
    fun shortLastBlockReadsCorrectly() {
        // 1 MiB + 100 tail bytes → last block is short.
        val tail = ByteArray(data.size + 100) { (it % 253).toByte() }
        val r = RangeBlockReader(
            size = tail.size.toLong(),
            blockSize = 256,
            maxCachedBlocks = 8,
            budgetBytes = Long.MAX_VALUE,
            isCancelled = { false },
            fetch = { offset, length ->
                tail.copyOfRange(offset.toInt(), minOf(offset + length, tail.size.toLong()).toInt())
            }
        )
        val buffer = ByteArray(150)
        val read = r.readAt(tail.size - 150L, buffer, 0, 150)
        assertEquals(150, read)
        assertArrayEquals(tail.copyOfRange(tail.size - 150, tail.size), buffer)
    }

    @Test
    fun overBudgetThrowsAndStopsFetching() {
        val log = FetchLog()
        // Budget fits exactly one 256-byte block.
        val r = reader(blockSize = 256, budgetBytes = 300, log = log)
        val buffer = ByteArray(64)
        assertEquals(64, r.readAt(0, buffer, 0, 64))
        try {
            r.readAt(256, buffer, 0, 64) // needs block 1 → 512 fetched > 300 budget
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e is RangeBudgetExceededException)
        }
        val fetchesAfterFailure = log.count.get()
        try {
            r.readAt(512, buffer, 0, 64)
            fail("expected IOException")
        } catch (e: IOException) {
            // still over budget — must not fetch again
        }
        assertEquals(fetchesAfterFailure, log.count.get())
    }

    @Test
    fun cancelledBeforeFetchThrowsWithoutFetching() {
        val log = FetchLog()
        val r = reader(cancelled = { true }, log = log)
        try {
            r.readAt(0, ByteArray(8), 0, 8)
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("range read cancelled", e.message)
        }
        assertEquals(0, log.count.get())
    }

    @Test
    fun nullFetchThrows() {
        val r = reader(fetch = { _, _ -> null })
        try {
            r.readAt(0, ByteArray(8), 0, 8)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("range fetch failed"))
        }
    }

    @Test
    fun shortFetchThrows() {
        val r = reader(fetch = { offset, length -> ByteArray(length / 2) })
        try {
            r.readAt(0, ByteArray(8), 0, 8)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("short range read"))
        }
    }
}
