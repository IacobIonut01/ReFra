/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.duplicates

import com.dot.gallery.data.entity.MediaContentHashEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class ContentHashingTest {

    private fun item(id: Long, size: Long, modified: Long = 1) =
        HashCandidate(id = id, size = size, modifiedSeconds = modified, path = "/p/$id")

    /** Runs a pass with fake hashers keyed by id, recording which files each tier read. */
    private class Pass(
        val partial: Map<Long, String>,
        val full: Map<Long, String>,
    ) {
        val partialReads = mutableListOf<Long>()
        val fullReads = mutableListOf<Long>()
        val written = mutableMapOf<Long, MediaContentHashEntity>()

        fun run(
            items: List<HashCandidate>,
            existing: Map<Long, MediaContentHashEntity> = emptyMap(),
            includeLarge: Boolean = false,
        ) = runBlocking {
            runHashPass(
                items = items,
                existing = existing,
                includeLarge = includeLarge,
                nowMillis = 7,
                partialHash = { synchronized(partialReads) { partialReads += it.id }; partial[it.id] },
                fullHash = { synchronized(fullReads) { fullReads += it.id }; full[it.id] },
                write = { rows -> rows.forEach { written[it.mediaId] = it } },
            )
        }
    }

    @Test
    fun onlyFilesWithASharedSizeAreRead() {
        val pass = Pass(partial = mapOf(1L to "a", 2L to "a"), full = mapOf(1L to "x", 2L to "x"))
        val result = pass.run(listOf(item(1, 500), item(2, 500), item(3, 700), item(4, 0), item(5, 0)))
        assertEquals(setOf(1L, 2L), pass.partialReads.toSet())
        assertEquals(setOf(1L, 2L), pass.fullReads.toSet())
        assertEquals(2, result.candidates)
        assertEquals("x", pass.written.getValue(1).sha1)
    }

    @Test
    fun aDifferentPartialHashStopsBeforeTheFullRead() {
        val pass = Pass(partial = mapOf(1L to "a", 2L to "b", 3L to "a"), full = mapOf(1L to "x", 3L to "x"))
        pass.run(listOf(item(1, 500), item(2, 500), item(3, 500)))
        assertEquals(setOf(1L, 2L, 3L), pass.partialReads.toSet())
        assertEquals(setOf(1L, 3L), pass.fullReads.toSet())
        assertNull(pass.written.getValue(2).sha1)
    }

    @Test
    fun freshRowsAreReusedAndChangedFilesAreHashedAgain() {
        val existing = listOf(
            MediaContentHashEntity(1, 500, 1, "/p/1", "a", "x", 0),
            MediaContentHashEntity(2, 500, 1, "/p/2", "a", "x", 0),
            MediaContentHashEntity(3, 500, 1, "/p/3", "a", "x", 0),
        ).associateBy { it.mediaId }
        val pass = Pass(partial = mapOf(3L to "c"), full = emptyMap())
        // Item 3 was edited: its modified time moved on.
        val result = pass.run(listOf(item(1, 500), item(2, 500), item(3, 500, modified = 2)), existing)
        assertEquals(listOf(3L), pass.partialReads)
        assertTrue(pass.fullReads.isEmpty())
        assertEquals(setOf(3L), pass.written.keys)
        assertNull(pass.written.getValue(3).sha1)
        assertEquals(1, result.written)
    }

    @Test
    fun aNewTwinOnlyCostsItsOwnHashes() {
        val existing = mapOf(1L to MediaContentHashEntity(1, 500, 1, "/p/1", "a", null, 0))
        val pass = Pass(partial = mapOf(2L to "a"), full = mapOf(1L to "x", 2L to "x"))
        pass.run(listOf(item(1, 500), item(2, 500)), existing)
        assertEquals(listOf(2L), pass.partialReads)
        assertEquals(setOf(1L, 2L), pass.fullReads.toSet())
    }

    @Test
    fun largeFilesAreSkippedUnlessAskedFor() {
        val big = LARGE_FILE_BYTES + 1
        val skip = Pass(partial = emptyMap(), full = emptyMap())
        val skipped = skip.run(listOf(item(1, big), item(2, big), item(3, 10), item(4, 10)))
        assertEquals(2, skipped.skippedLarge)
        assertEquals(setOf(3L, 4L), skip.partialReads.toSet())

        val include = Pass(partial = emptyMap(), full = emptyMap())
        val included = include.run(listOf(item(1, big), item(2, big)), includeLarge = true)
        assertEquals(0, included.skippedLarge)
        assertEquals(setOf(1L, 2L), include.partialReads.toSet())
    }

    @Test
    fun largeFilesHashedByAOneOffCheckStayChecked() {
        val big = LARGE_FILE_BYTES + 1
        val existing = mapOf(1L to MediaContentHashEntity(1, big, 1, "/p/1", "a", "x", 0))
        val pass = Pass(partial = emptyMap(), full = emptyMap())
        val result = pass.run(listOf(item(1, big), item(2, big)), existing)
        assertEquals(1, result.skippedLarge)
        assertTrue(pass.partialReads.isEmpty())
    }

    @Test
    fun unreadableFilesAreLeftOut() {
        val pass = Pass(partial = mapOf(1L to "a"), full = mapOf(1L to "x"))
        pass.run(listOf(item(1, 500), item(2, 500)))
        assertEquals(setOf(1L), pass.written.keys)
        assertTrue(pass.fullReads.isEmpty())
    }

    @Test
    fun fullSha1MatchesMessageDigest() = runBlocking {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        val expected = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, fullSha1 { ByteArrayInputStream(bytes) })
    }

    @Test
    fun partialSha1ReadsBothEnds() = runBlocking {
        val size = 400_000
        val base = ByteArray(size) { (it % 251).toByte() }
        val middleChanged = base.copyOf().also { it[size / 2] = 99 }
        val tailChanged = base.copyOf().also { it[size - 10] = 99 }
        val headChanged = base.copyOf().also { it[10] = 99 }
        fun hash(bytes: ByteArray) = runBlocking { partialSha1(bytes.size.toLong()) { ByteArrayInputStream(bytes) } }
        assertEquals(hash(base), hash(middleChanged))
        assertNotEquals(hash(base), hash(tailChanged))
        assertNotEquals(hash(base), hash(headChanged))
    }

    @Test
    fun partialSha1CoversSmallFilesCompletely() = runBlocking {
        // Between one and two chunks long: every byte must count.
        val size = PARTIAL_HASH_CHUNK + 1_000
        val base = ByteArray(size) { (it % 7).toByte() }
        val changed = base.copyOf().also { it[PARTIAL_HASH_CHUNK + 500] = 42 }
        val a = partialSha1(size.toLong()) { ByteArrayInputStream(base) }
        val b = partialSha1(size.toLong()) { ByteArrayInputStream(changed) }
        assertNotEquals(a, b)
    }
}
