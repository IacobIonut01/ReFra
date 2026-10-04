package com.dot.gallery.core.decryption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * JVM tests for [VaultDecryptCache] — the refcount/single-flight/eviction mechanics behind
 * [VaultDecryptStore] (#1282). The materializer fakes decrypt by writing dummy bytes to
 * `dir/<key>.bin`, mirroring the production contract.
 */
class VaultDecryptCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var encDir: File
    private val materializeCalls = AtomicInteger(0)

    private fun newCache(
        entryTtlMs: Long = VaultDecryptCache.ENTRY_TTL_MS,
        forceEvictMs: Long = VaultDecryptCache.FORCE_EVICT_MS,
        maxDirBytes: Long = VaultDecryptCache.MAX_DIR_BYTES,
        failureCooldownMs: Long = VaultDecryptCache.FAILURE_COOLDOWN_MS,
    ) = VaultDecryptCache(dir, entryTtlMs, forceEvictMs, maxDirBytes, failureCooldownMs)

    private fun fakeMaterialize(bytes: ByteArray = "plaintext".toByteArray()): (String) -> Pair<File, String> =
        { key ->
            materializeCalls.incrementAndGet()
            val out = File(dir, "$key.bin")
            out.writeBytes(bytes)
            out to "video/mp4"
        }

    private fun encFile(name: String): File =
        File(encDir, name).apply { writeBytes("ciphertext-$name".toByteArray()) }

    @Before
    fun setUp() {
        dir = tmp.newFolder("vault_tmp")
        encDir = tmp.newFolder("enc")
    }

    @Test
    fun acquireMaterializesOnceAndSharesFile() {
        val cache = newCache()
        val enc = encFile("1.enc")

        val h1 = cache.acquire(enc, materialize = fakeMaterialize())
        val h2 = cache.acquire(enc, materialize = fakeMaterialize())

        assertEquals(1, materializeCalls.get())
        assertEquals(h1.file, h2.file)
        assertEquals("video/mp4", h1.mimeType)
        assertTrue(h1.file.exists())
        h1.release()
        h2.release()
    }

    @Test
    fun releaseThenReacquireReusesEntry() {
        val cache = newCache()
        val enc = encFile("1.enc")

        cache.acquire(enc, materialize = fakeMaterialize()).release()
        val again = cache.acquire(enc, materialize = fakeMaterialize())

        assertEquals(1, materializeCalls.get())
        assertTrue(again.file.exists())
        again.release()
    }

    @Test
    fun expiredUnreferencedEntryIsEvictedOnNextAcquire() {
        // -1 so `age > ttl` holds even when both acquires land in the same millisecond.
        val cache = newCache(entryTtlMs = -1L)
        val enc = encFile("1.enc")

        val first = cache.acquire(enc, materialize = fakeMaterialize())
        first.release()

        val second = cache.acquire(enc, materialize = fakeMaterialize())
        // Same file state -> same key, so the re-materialization lands at the same path;
        // the observable contract is that a fresh decrypt happened.
        assertEquals(2, materializeCalls.get())
        second.release()
    }

    @Test
    fun failureIsRememberedAndCooldownBlocksRetry() {
        val cache = newCache()
        val enc = encFile("1.enc")

        try {
            cache.acquire(enc) { throw IOException("boom") }
            fail("expected IOException")
        } catch (_: IOException) {
        }

        // Immediate retry must not call materialize again (cooldown)
        try {
            cache.acquire(enc, materialize = fakeMaterialize())
            fail("expected cooldown IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("cooling down"))
        }
        assertEquals(0, materializeCalls.get())
    }

    @Test
    fun ignoreCooldownBypassesFailureWindow() {
        val cache = newCache()
        val enc = encFile("1.enc")

        try {
            cache.acquire(enc) { throw IOException("boom") }
            fail("expected IOException")
        } catch (_: IOException) {
        }

        val handle = cache.acquire(enc, ignoreCooldown = true, materialize = fakeMaterialize())
        assertEquals(1, materializeCalls.get())
        handle.release()
    }

    @Test
    fun concurrentAcquiresShareSingleMaterialization() {
        val cache = newCache()
        val enc = encFile("1.enc")
        val started = CountDownLatch(2)
        val gate = CountDownLatch(1)

        val slowMaterialize: (String) -> Pair<File, String> = { key ->
            materializeCalls.incrementAndGet()
            gate.await() // hold the decrypt open until both waiters have joined
            val out = File(dir, "$key.bin")
            out.writeBytes("plaintext".toByteArray())
            out to "video/mp4"
        }

        var h1: VaultDecryptCache.Handle? = null
        var h2: VaultDecryptCache.Handle? = null
        val t1 = thread {
            started.countDown()
            h1 = cache.acquire(enc, materialize = slowMaterialize)
        }
        val t2 = thread {
            started.countDown()
            Thread.sleep(50) // let t1 become owner first
            h2 = cache.acquire(enc, materialize = slowMaterialize)
        }
        started.await()
        gate.countDown()
        t1.join(5_000)
        t2.join(5_000)

        assertNotNull(h1)
        assertNotNull(h2)
        assertEquals(1, materializeCalls.get())
        assertEquals(h1!!.file, h2!!.file)
        h1!!.release()
        h2!!.release()
    }

    @Test
    fun ownerFailureReleasesWaitersWithError() {
        val cache = newCache()
        val enc = encFile("1.enc")
        val gate = CountDownLatch(1)

        val slowFailing: (String) -> Pair<File, String> = {
            gate.await()
            throw IOException("owner boom")
        }

        var waiterError: Throwable? = null
        val t1 = thread { runCatching { cache.acquire(enc, materialize = slowFailing) } }
        val t2 = thread {
            Thread.sleep(50)
            runCatching { cache.acquire(enc, materialize = fakeMaterialize()) }
                .onFailure { waiterError = it }
        }
        gate.countDown()
        t1.join(5_000)
        t2.join(5_000)

        assertNotNull("waiter must not hang and must see the failure", waiterError)
        assertTrue(waiterError is IOException)
    }

    @Test
    fun sweepDeletesOrphanedStaleFilesOnly() {
        val cache = newCache()
        val stale = File(dir, "orphan1.bin").apply {
            writeBytes(ByteArray(4))
            setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
        }
        val fresh = File(dir, "orphan2.bin").apply { writeBytes(ByteArray(4)) }
        val stalePart = File(dir, "orphan3.part").apply {
            writeBytes(ByteArray(4))
            setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
        }

        val deleted = cache.sweep()

        assertEquals(2, deleted)
        assertTrue(!stale.exists())
        assertTrue(!stalePart.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun budgetEvictsOldestUnreferencedEntries() {
        val cache = newCache(maxDirBytes = 16)
        val enc1 = encFile("1.enc")
        val enc2 = encFile("2.enc")

        val h1 = cache.acquire(enc1, materialize = fakeMaterialize(ByteArray(10)))
        val file1 = h1.file
        h1.release()
        Thread.sleep(10) // make h1's entry the oldest
        val h2 = cache.acquire(enc2, materialize = fakeMaterialize(ByteArray(10)))

        // Total (20) exceeds budget (16) -> oldest unreferenced entry (enc1) is evicted.
        assertEquals("video/mp4", h2.mimeType)
        assertTrue(h2.file.exists())
        assertTrue("oldest unreferenced entry must be evicted", !file1.exists())
        h2.release()
    }

    @Test
    fun differentFileStatesGetDifferentKeys() {
        val cache = newCache()
        val enc = encFile("1.enc")
        val h1 = cache.acquire(enc, materialize = fakeMaterialize())
        val fileA = h1.file
        h1.release()

        // Simulate re-encrypt: different content + mtime changes the key.
        // (setLastModified must run AFTER writeBytes — the write stamps its own mtime.)
        enc.writeBytes("other-ciphertext-different".toByteArray())
        enc.setLastModified(System.currentTimeMillis() + 10_000)
        val cache2 = VaultDecryptCache(dir)
        val h2 = cache2.acquire(enc, materialize = fakeMaterialize())
        assertNotEquals(fileA.name, h2.file.name)
        h2.release()
    }
}
