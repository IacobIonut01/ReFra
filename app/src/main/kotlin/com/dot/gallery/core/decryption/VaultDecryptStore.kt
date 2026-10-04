package com.dot.gallery.core.decryption

import android.content.Context
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printError
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Canonical shared store for decrypted vault content.
 *
 * Before this store existed, every consumer (Glide thumbnail fetcher, ExoPlayer, Sketch
 * decoders, metadata probes) ran its own full decrypt into its own temp file — a single
 * vault video could occupy 2–3 plaintext copies per request, and error paths leaked them
 * (#1282).
 *
 * Now each vault file has at most one decrypted file on disk:
 * `cacheDir/vault_tmp/<sha256(path|size|mtime)>.bin`, produced by a single-flight decrypt
 * and shared by all consumers through reference-counted handles. Entries expire
 * [VaultDecryptCache.ENTRY_TTL_MS] after the last release, are evicted oldest-first when the
 * directory exceeds [VaultDecryptCache.MAX_DIR_BYTES], and orphaned files are swept by
 * [sweep] (called from [com.dot.gallery.core.workers.TempVaultCleanupWorker] and when the
 * vault screen closes).
 *
 * A failed decrypt is remembered for [VaultDecryptCache.FAILURE_COOLDOWN_MS] so retry loops
 * (grid rebinds, prefetch, viewer reattach) fail fast instead of re-writing gigabytes.
 */
object VaultDecryptStore {

    private const val TAG = "vault.store"
    private const val DIR_NAME = "vault_tmp"

    private val caches = HashMap<File, VaultDecryptCache>()

    private fun cacheFor(dir: File): VaultDecryptCache =
        synchronized(caches) { caches.getOrPut(dir) { VaultDecryptCache(dir) } }

    /**
     * Decrypt [file] once (or join an in-flight decrypt / reuse a cached entry) and return a
     * reference-counted handle. Blocking — call from a background thread.
     *
     * @param ignoreCooldown pass true only for explicit user retries; otherwise a recent
     * failure throws immediately instead of paying a full decrypt again.
     */
    @Throws(IOException::class)
    fun acquire(
        keychainHolder: KeychainHolder,
        file: File,
        ignoreCooldown: Boolean = false
    ): VaultDecryptCache.Handle =
        cacheFor(dirFor(keychainHolder)).acquire(file, ignoreCooldown) { key ->
            materialize(keychainHolder, file, key)
        }

    /**
     * Delete expired/orphaned files under `vault_tmp`. Safe to call any time; skips files
     * that are still referenced unless they are older than [VaultDecryptCache.FORCE_EVICT_MS].
     * Returns the number of files deleted.
     */
    fun sweep(
        context: Context,
        maxAgeMs: Long = VaultDecryptCache.ENTRY_TTL_MS
    ): Int {
        val deleted = cacheFor(dirFor(context)).sweep(maxAgeMs)
        if (deleted > 0) printDebug(TAG, "sweep deleted $deleted stale decrypted files")
        return deleted
    }

    // ---- KeychainHolder-bound materialization ----

    private fun materialize(keychainHolder: KeychainHolder, file: File, key: String): Pair<File, String> {
        val dir = dirFor(keychainHolder).apply { mkdirs() }
        val tmp = File(dir, "$key.part")
        val out = File(dir, "$key.bin")
        var mime: String? = null
        try {
            if (keychainHolder.isPortableFile(file)) {
                val vaultUuidStr = file.parentFile?.name
                    ?: throw IOException("Cannot determine vault UUID from ${file.path}")
                val vault = Vault(uuid = UUID.fromString(vaultUuidStr), name = "")
                FileOutputStream(tmp).use { o ->
                    keychainHolder.decryptPortableStream(vault, file, o)
                }
            } else {
                // Legacy EncryptedFile-serialized blob — in-memory, kept for old vaults only.
                val enc = with(keychainHolder) { file.decryptKotlin<Media.EncryptedMedia>() }
                mime = enc.mimeType
                FileOutputStream(tmp).use { o -> o.write(enc.bytes) }
            }
            if (!tmp.renameTo(out)) throw IOException("Failed to publish decrypted file ${out.name}")
            return out to (mime ?: sniffHead(out))
        } catch (t: Throwable) {
            tmp.delete()
            out.delete()
            throw t
        }
    }

    private fun sniffHead(file: File): String {
        val header = ByteArray(12)
        file.inputStream().use { it.read(header) }
        return KeychainHolder.sniffMimeType(header)
    }

    private fun dirFor(context: Context): File = File(context.cacheDir, DIR_NAME)

    private fun dirFor(keychainHolder: KeychainHolder): File {
        // filesDir is <data>/files — cacheDir is <data>/cache on every Android build.
        val cacheRoot = keychainHolder.filesDir.parentFile ?: keychainHolder.filesDir
        return File(File(cacheRoot, "cache"), DIR_NAME)
    }
}

/**
 * File-system cache behind [VaultDecryptStore]. Pure JVM logic (no Android types) so the
 * refcount/single-flight/eviction/cooldown behavior is unit-testable.
 *
 * Each encrypted source file maps to one canonical decrypted file `<key>.bin` inside [dir],
 * where `key = sha256(path|size|mtime)`. [acquire] is single-flight: concurrent callers share
 * the same decrypt and the same on-disk result. Callers hold a [Handle] — releasing the last
 * handle makes the entry eligible for TTL/budget eviction.
 */
class VaultDecryptCache(
    private val dir: File,
    private val entryTtlMs: Long = ENTRY_TTL_MS,
    private val forceEvictMs: Long = FORCE_EVICT_MS,
    private val maxDirBytes: Long = MAX_DIR_BYTES,
    private val failureCooldownMs: Long = FAILURE_COOLDOWN_MS,
) {

    companion object {
        /** How long an unreferenced decrypted file is kept for reuse. */
        const val ENTRY_TTL_MS = 30L * 60 * 1000 // 30 min

        /** Referenced entries are force-evicted after this age; open FDs keep working on Linux. */
        const val FORCE_EVICT_MS = 4L * 60 * 60 * 1000 // 4 h

        /** Hard budget for the whole directory; oldest unreferenced entries are evicted first. */
        const val MAX_DIR_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

        const val FAILURE_COOLDOWN_MS = 60_000L
    }

    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    private val inFlight = HashMap<String, InFlight>()
    private val failures = HashMap<String, Long>()

    private class Entry(
        val file: File,
        val mimeType: String,
        val sizeBytes: Long,
        var refs: Int,
        var lastAccess: Long,
    )

    private class InFlight {
        val latch = CountDownLatch(1)
        var error: Throwable? = null
    }

    /**
     * A borrowed reference to the decrypted canonical file. Must be [release]d by the caller;
     * idempotent and safe to call from any thread.
     */
    class Handle internal constructor(
        private val key: String,
        /** Canonical decrypted file. Shared — never delete it directly. */
        val file: File,
        val mimeType: String,
        private val onRelease: (String) -> Unit,
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) onRelease(key)
        }
    }

    /**
     * Return a handle to the decrypted canonical file for [file], decrypting via [materialize]
     * if needed. `materialize(key)` must produce the plaintext at `dir/<key>.bin` and return
     * `file to mimeType`. Blocking — call from a background thread.
     */
    @Throws(IOException::class)
    fun acquire(
        file: File,
        ignoreCooldown: Boolean = false,
        materialize: (key: String) -> Pair<File, String>
    ): Handle {
        val now = System.currentTimeMillis()
        val key = keyFor(file)
        val flight: InFlight
        val isOwner: Boolean
        synchronized(lock) {
            evictExpiredLocked(now)
            val failedAt = failures[key]
            if (failedAt != null) {
                if (!ignoreCooldown && now - failedAt < failureCooldownMs) {
                    throw IOException("Vault decrypt cooling down for ${file.name}")
                }
                failures.remove(key)
            }
            val existing = entries[key]
            if (existing != null && existing.file.exists()) {
                return grantLocked(existing, now)
            }
            if (existing != null) entries.remove(key)
            val queued = inFlight[key]
            if (queued != null) {
                flight = queued
                isOwner = false
            } else {
                flight = InFlight()
                inFlight[key] = flight
                isOwner = true
            }
        }
        if (!isOwner) {
            flight.latch.await()
            synchronized(lock) {
                flight.error?.let { throw IOException("Vault decrypt failed for ${file.name}", it) }
                val entry = entries[key]
                    ?: throw IOException("Vault decrypt produced no file for ${file.name}")
                return grantLocked(entry, System.currentTimeMillis())
            }
        }
        // Owner: materialize outside the lock so waiters don't hold it during disk I/O.
        val (materialized, mime) = try {
            materialize(key)
        } catch (t: Throwable) {
            synchronized(lock) {
                inFlight.remove(key)
                failures[key] = System.currentTimeMillis()
                flight.error = t
                flight.latch.countDown()
            }
            printError("vault.store", "vault decrypt failed for ${file.name}", t)
            throw t as? IOException ?: IOException("Vault decrypt failed for ${file.name}", t)
        }
        synchronized(lock) {
            inFlight.remove(key)
            val entry = Entry(
                file = materialized,
                mimeType = mime,
                sizeBytes = materialized.length(),
                refs = 1,
                lastAccess = System.currentTimeMillis()
            )
            entries[key] = entry
            flight.latch.countDown()
            evictForBudgetLocked()
            return Handle(key, entry.file, entry.mimeType) { k -> releaseKey(k) }
        }
    }

    /**
     * Delete expired/orphaned files under [dir]. Skips still-referenced entries unless they
     * are older than [FORCE_EVICT_MS]. Also removes files with no live entry (process restart
     * orphans). Returns the number of files deleted.
     */
    fun sweep(maxAgeMs: Long = entryTtlMs): Int {
        if (!dir.isDirectory) return 0
        val now = System.currentTimeMillis()
        var deleted = 0
        synchronized(lock) {
            dir.listFiles().orEmpty().forEach { f ->
                if (!f.isFile) return@forEach
                val age = now - f.lastModified()
                val entry = entries[f.nameWithoutExtension]
                val shouldDelete = if (entry != null) {
                    (entry.refs <= 0 && age >= maxAgeMs) || age >= forceEvictMs
                } else {
                    age >= maxAgeMs
                }
                if (shouldDelete && f.delete()) {
                    deleted++
                    entries.remove(f.nameWithoutExtension)
                }
            }
            evictForBudgetLocked()
        }
        return deleted
    }

    // ---- internals ----

    private fun grantLocked(entry: Entry, now: Long): Handle {
        entry.refs++
        entry.lastAccess = now
        return Handle(entry.file.nameWithoutExtension, entry.file, entry.mimeType) { key ->
            releaseKey(key)
        }
    }

    private fun releaseKey(key: String) {
        synchronized(lock) {
            entries[key]?.let {
                it.refs = (it.refs - 1).coerceAtLeast(0)
                it.lastAccess = System.currentTimeMillis()
            }
        }
    }

    private fun evictExpiredLocked(now: Long) {
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val age = now - entry.lastAccess
            val expired = if (entry.refs > 0) age > forceEvictMs else age > entryTtlMs
            if (expired || !entry.file.exists()) {
                entry.file.delete()
                iterator.remove()
            }
        }
    }

    private fun evictForBudgetLocked() {
        var total = entries.values.sumOf { it.sizeBytes }
        if (total <= maxDirBytes) return
        val evictable = entries.values.filter { it.refs == 0 }.sortedBy { it.lastAccess }
        for (entry in evictable) {
            if (total <= maxDirBytes) break
            if (entry.file.delete()) {
                total -= entry.sizeBytes
                entries.values.remove(entry)
            }
        }
    }

    private fun keyFor(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("${file.absolutePath}|${file.length()}|${file.lastModified()}".encodeToByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
