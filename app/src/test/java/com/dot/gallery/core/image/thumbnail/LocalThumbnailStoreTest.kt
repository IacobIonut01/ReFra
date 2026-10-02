/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalThumbnailStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore(maxBytes: Long = LocalThumbnailStore.DEFAULT_MAX_BYTES): LocalThumbnailStore =
        LocalThumbnailStore(tmp.newFolder("store"), maxBytes)

    private fun bytes(size: Int, seed: Int = 0): ByteArray =
        ByteArray(size) { ((it + seed) % 251).toByte() }

    @Test
    fun putThenGetReturnsStoredBytes() {
        val store = newStore()
        val payload = bytes(64, seed = 7)
        store.put("id1", payload)
        assertArrayEquals(payload, store.get("id1"))
    }

    @Test
    fun getReturnsNullForUnknownIdentity() {
        val store = newStore()
        assertNull(store.get("missing"))
    }

    @Test
    fun putLeavesNoTmpFilesBehind() {
        val store = newStore()
        store.put("id1", bytes(32))
        val leftovers = store.fileFor("id1").parentFile!!
            .listFiles { f -> f.name.endsWith(".tmp") }
        assertTrue(leftovers == null || leftovers.isEmpty())
    }

    @Test
    fun identityIsStableForSameInputs() {
        assertEquals(
            LocalThumbnailStore.identity("content://media/1", 1000L, 2048L),
            LocalThumbnailStore.identity("content://media/1", 1000L, 2048L)
        )
    }

    @Test
    fun identityChangesWhenMtimeOrSizeChanges() {
        val base = LocalThumbnailStore.identity("content://media/1", 1000L, 2048L)
        assertNotEquals(base, LocalThumbnailStore.identity("content://media/1", 1001L, 2048L))
        assertNotEquals(base, LocalThumbnailStore.identity("content://media/1", 1000L, 4096L))
        assertNotEquals(base, LocalThumbnailStore.identity("content://media/2", 1000L, 2048L))
    }

    @Test
    fun evictsOldestEntryWhenOverBudget() {
        val store = newStore(maxBytes = 100)
        store.put("old", bytes(40))
        store.fileFor("old").setLastModified(1_000)
        store.put("mid", bytes(40))
        store.fileFor("mid").setLastModified(2_000)

        // 40 + 40 + 50 = 130 > 100 → the oldest ("old") is evicted; the rest survive.
        store.put("new", bytes(50))

        assertNull(store.get("old"))
        assertArrayEquals(bytes(40), store.get("mid"))
        assertArrayEquals(bytes(50), store.get("new"))
    }

    @Test
    fun getRefreshesRecencySoHotEntriesSurviveEviction() {
        val store = newStore(maxBytes = 100)
        store.put("a", bytes(40))
        store.fileFor("a").setLastModified(1_000)
        store.put("b", bytes(40))
        store.fileFor("b").setLastModified(2_000)

        // Touching "a" makes it newer than "b"; the next over-budget put must evict "b".
        assertTrue(store.get("a") != null)
        store.put("c", bytes(50))

        assertArrayEquals(bytes(40, seed = 0), store.get("a"))
        assertNull(store.get("b"))
        assertArrayEquals(bytes(50), store.get("c"))
    }

    @Test
    fun staleTmpFilesAreReclaimedFirstDuringTrim() {
        val dir = tmp.newFolder("store")
        val stale = File(dir, "orphan.tmp").apply { writeBytes(bytes(60)) }
        val store = LocalThumbnailStore(dir, maxBytes = 100)

        // stale(60) + new(50) = 110 > 100 → tmp reclaimed first, entry survives.
        store.put("entry", bytes(50))

        assertTrue(!stale.exists())
        assertArrayEquals(bytes(50), store.get("entry"))
    }

    @Test
    fun oversizedEntryIsRejectedSilently() {
        val store = newStore(maxBytes = 16)
        store.put("huge", bytes(64))
        assertNull(store.get("huge"))
    }

    @Test
    fun removeDropsEntry() {
        val store = newStore()
        store.put("id", bytes(32))
        store.remove("id")
        assertNull(store.get("id"))
    }

    @Test
    fun clearEmptiesTheStore() {
        val store = newStore()
        store.put("a", bytes(16))
        store.put("b", bytes(16))
        store.clear()
        assertNull(store.get("a"))
        assertNull(store.get("b"))
        assertEquals(0, store.snapshot().entries)
    }

    @Test
    fun snapshotTracksOccupancyAndCounters() {
        val store = newStore()
        store.put("a", bytes(32))
        assertTrue(store.get("a") != null)   // hit
        assertNull(store.get("zzz"))          // miss
        val snap = store.snapshot()
        assertEquals(1, snap.entries)
        assertEquals(32, snap.bytes)
        assertEquals(1, snap.hits)
        assertEquals(1, snap.misses)
        assertEquals(1, snap.writes)
    }
}
