/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import android.content.Context
import com.dot.gallery.feature_node.presentation.util.printWarn
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * #1276: persistent on-disk store for thumbnails of software-decoded formats
 * ([HeavyThumbFormat]: JXL, PSD, JP2, TIFF, RAW, >10-bit HEIF).
 *
 * One canonical thumbnail per media file, encoded WebP at
 * [HeavyThumbnailDecoder.CANONICAL_PX] long edge — a single entry serves both the 256 px MOTION
 * tier and any refined grid cell ≤512 px, so an expensive software decode happens once per file
 * per install instead of once per app open (Glide's 250 MB `cacheDir` LRU evicts cold entries
 * and OEM cleaners purge `cacheDir`; `filesDir` survives both).
 *
 * Layout: `filesDir/thumb_cache/<sha1(uri|mtime|size)>.webp`. An in-place edit bumps mtime/size
 * and lands a fresh key; the stale entry ages out via LRU. Writes are atomic (tmp + rename) —
 * the NetFS thumbnail cache (`netfs_thumb_cache`) is the precedent for this shape.
 *
 * Key-value-file core is plain `java.io` so the store is unit-testable on the JVM; the Android
 * bits (`filesDir`) are confined to the companion's [shared] accessor.
 */
class LocalThumbnailStore(
    private val dir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {

    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val writes = AtomicLong()

    /** File backing [identity]; callers must not mutate it. */
    fun fileFor(identity: String): File = File(dir, "$identity.$EXTENSION")

    /** Encoded thumbnail bytes for [identity], or null on miss/corruption. */
    fun get(identity: String): ByteArray? {
        val bytes = runCatching {
            fileFor(identity).takeIf { it.isFile }?.readBytes()
        }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            misses.incrementAndGet()
            return null
        }
        // LRU bookkeeping — a failed touch is harmless (slightly stale eviction order at worst).
        runCatching { fileFor(identity).setLastModified(System.currentTimeMillis()) }
        hits.incrementAndGet()
        return bytes
    }

    /** Writes [bytes] under [identity] atomically (tmp + rename) then trims to [maxBytes]. */
    fun put(identity: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > maxBytes) return
        if (!dir.isDirectory && !dir.mkdirs()) {
            printWarn(TAG, "thumbnail store dir unavailable: $dir")
            return
        }
        val target = fileFor(identity)
        // Unique tmp name per writer so concurrent puts never share a temp file.
        val tmp = File(dir, "$identity.${System.nanoTime()}$TMP_SUFFIX")
        runCatching {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            writes.incrementAndGet()
        }.onFailure {
            printWarn(TAG, "thumbnail store write failed: ${it.message}")
        }
        runCatching { tmp.delete() }
        trim()
    }

    /** Removes a stored entry — used for corrupt payloads so the next miss self-heals. */
    fun remove(identity: String) {
        runCatching { fileFor(identity).delete() }
    }

    /** Deletes every entry. Returns immediately if the store was never populated. */
    fun clear() {
        runCatching {
            dir.listFiles { f -> f.isFile }?.forEach { it.delete() }
        }
    }

    /** Evicts least-recently-touched entries (and stale tmp files) until under [maxBytes]. */
    @Synchronized
    private fun trim() {
        val files = dir.listFiles { f -> f.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        // Crash-leftover tmp files are never valid — reclaim them first.
        for (f in files) {
            if (total <= maxBytes) break
            if (f.name.endsWith(TMP_SUFFIX)) {
                total -= f.length()
                f.delete()
            }
        }
        if (total <= maxBytes) return
        files.asSequence()
            .filter { it.isFile && it.name.endsWith(".$EXTENSION") }
            .sortedBy { it.lastModified() }
            .forEach { f ->
                if (total <= maxBytes) return@forEach
                total -= f.length()
                f.delete()
            }
    }

    /** Bounded counters + directory occupancy snapshot for diagnostics (staging/debug dumps). */
    data class Snapshot(
        val entries: Int,
        val bytes: Long,
        val hits: Long,
        val misses: Long,
        val writes: Long,
    )

    fun snapshot(): Snapshot {
        val entries = dir.listFiles { f -> f.isFile && f.name.endsWith(".$EXTENSION") }
            ?: emptyArray()
        return Snapshot(
            entries = entries.size,
            bytes = entries.sumOf { it.length() },
            hits = hits.get(),
            misses = misses.get(),
            writes = writes.get(),
        )
    }

    companion object {
        private const val TAG = "decode.thumb"
        private const val EXTENSION = "webp"
        private const val TMP_SUFFIX = ".tmp"
        internal const val DIR_NAME = "thumb_cache"

        /** Own LRU budget, independent of Glide's `image_manager_disk_cache`. */
        const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024

        /**
         * Cache key: one per (uri, mtime, size). Edits/replacements bump mtime/size and land a
         * fresh entry — the same invalidation semantics as the Glide request signature, without
         * depending on `Media` internals.
         */
        fun identity(sourceId: String, modifiedSec: Long, sizeBytes: Long): String =
            sha1Hex("$sourceId|$modifiedSec|$sizeBytes")

        internal fun sha1Hex(input: String): String =
            MessageDigest.getInstance("SHA-1")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        @Volatile
        private var shared: LocalThumbnailStore? = null

        /** Process-wide instance rooted at `filesDir/thumb_cache`. The dir is created lazily on first put. */
        fun shared(context: Context): LocalThumbnailStore =
            shared ?: synchronized(this) {
                shared ?: LocalThumbnailStore(
                    File(context.applicationContext.filesDir, DIR_NAME)
                ).also { shared = it }
            }

        /** Clears the shared store's directory; safe to call before [shared] was ever requested. */
        fun clearShared(context: Context) {
            shared(context).clear()
        }
    }
}
