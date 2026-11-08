package com.dot.gallery.core.decryption

import android.content.Context
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printError
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.uuid.Uuid
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import com.dot.gallery.domain.model.EncryptedMedia
import com.dot.gallery.domain.uri.path
import java.util.UUID
import com.dot.gallery.data.data_source.KeychainHolder






/**
 * Canonical shared store for decrypted vault content.
 *
 * Before this store existed, every consumer (Glide thumbnail fetcher, ExoPlayer, Sketch
 * decoders, metadata probes) ran its own full decrypt into its own temp file — a single
 * vault video could occupy 2–3 plaintext copies per request, and error paths leaked them.
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
                    ?: throw IOException("Cannot determine vault Uuid from ${file.path}")
                val vault = Vault(uuid = Uuid.parse(vaultUuidStr), name = "")
                FileOutputStream(tmp).use { o ->
                    keychainHolder.decryptPortableStream(vault, file, o)
                }
            } else {
                // Legacy EncryptedFile-serialized blob — in-memory, kept for old vaults only.
                val enc = with(keychainHolder) { file.decryptKotlin<EncryptedMedia>() }
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