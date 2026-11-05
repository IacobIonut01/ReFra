/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for #1240: bulk vault ops over a few hundred URIs crashed the
 * app because the whole URI list was serialized into a single work-input payload
 * over the ~10 KB cap. chunkStringsForWorkInput must split so every chunk stays
 * under the limit while preserving order and completeness.
 */
class VaultOpChunkingTest {

    private fun uris(n: Int, len: Int = 64) =
        (1..n).map { "content://media/external/images/media/%0${len - 35}d".format(it) }

    @Test
    fun smallListStaysSingleChunk() {
        assertEquals(1, chunkStringsForWorkInput(uris(50)).size)
    }

    @Test
    fun largeListSplitsUnderLimit() {
        val items = uris(500)
        val chunks = chunkStringsForWorkInput(items)
        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            val serialized = chunk.joinToString(",", "[", "]") { "\"$it\"" }
            assertTrue(serialized.length <= 8 * 1024)
        }
        assertEquals(items, chunks.flatten())
    }

    @Test
    fun oversizedSingleItemStillEmitted() {
        val huge = "x".repeat(20_000)
        val chunks = chunkStringsForWorkInput(listOf(huge))
        assertEquals(listOf(listOf(huge)), chunks)
    }

    @Test
    fun emptyListProducesNoChunks() {
        assertTrue(chunkStringsForWorkInput(emptyList()).isEmpty())
    }
}
