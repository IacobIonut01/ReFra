/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.duplicates

import com.dot.gallery.data.entity.MediaContentHashEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.InputStream
import java.security.MessageDigest

/** Bytes read from each end of a file for its partial hash. */
internal const val PARTIAL_HASH_CHUNK = 64 * 1024

/** Files larger than this are skipped unless the user turns on Check large files. */
const val LARGE_FILE_BYTES = 2L * 1024 * 1024 * 1024

private const val HASH_BUFFER_SIZE = 64 * 1024
private const val HASH_CONCURRENCY = 2
private const val HASH_BATCH = 24

/** What the hash job needs to know about one local file. */
data class HashCandidate(
    val id: Long,
    val size: Long,
    val modifiedSeconds: Long,
    val path: String,
)

/** The outcome of one hashing pass. */
data class HashPassResult(
    /** Rows written in this pass (new or refreshed). */
    val written: Int,
    /** Files over [LARGE_FILE_BYTES] that could have a copy but have never been hashed. */
    val skippedLarge: Int,
    /** Files whose size another file shares: the only ones that can have an exact copy. */
    val candidates: Int,
)

/**
 * Files that can have an exact copy: those whose byte size another file shares. Empty files are
 * never duplicates worth reporting. Large files are left out unless [includeLarge] is set, and
 * returned separately so the screen can say how many were skipped.
 */
internal fun sizeCollisions(
    items: List<HashCandidate>,
    includeLarge: Boolean,
): Pair<List<HashCandidate>, List<HashCandidate>> {
    val colliding = items
        .filter { it.size > 0 }
        .groupBy { it.size }
        .values
        .filter { it.size > 1 }
        .flatten()
    val (large, regular) = colliding.partition { it.size > LARGE_FILE_BYTES }
    return if (includeLarge) colliding to emptyList() else regular to large
}

/**
 * Hashes what [items] needs, reusing [existing] rows that are still fresh, and hands each batch
 * of new or changed rows to [write].
 *
 * Only files whose size collides are read. Each gets a partial hash ([partialHash]); only files
 * whose size and partial hash still collide get a full hash ([fullHash]). A hasher returning null
 * (unreadable file) leaves that file out. At most two files are read at once.
 */
internal suspend fun runHashPass(
    items: List<HashCandidate>,
    existing: Map<Long, MediaContentHashEntity>,
    includeLarge: Boolean,
    nowMillis: Long,
    partialHash: suspend (HashCandidate) -> String?,
    fullHash: suspend (HashCandidate) -> String?,
    write: suspend (List<MediaContentHashEntity>) -> Unit,
    onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
): HashPassResult {
    val (candidates, skippedLarge) = sizeCollisions(items, includeLarge)
    val permits = Semaphore(HASH_CONCURRENCY)
    var written = 0

    fun freshRow(item: HashCandidate) = existing[item.id]
        ?.takeIf { it.isFreshFor(item.size, item.modifiedSeconds, item.path) }

    // Tier 2: partial hashes, reused while the file is unchanged.
    val partials = HashMap<Long, String>(candidates.size)
    val total = candidates.size
    var done = 0
    for (batch in candidates.chunked(HASH_BATCH)) {
        currentCoroutineContext().ensureActive()
        val computed = coroutineScope {
            batch.map { item ->
                async {
                    val reused = freshRow(item)?.partialHash
                    item to (reused ?: permits.withPermit { partialHash(item) })
                }
            }.awaitAll()
        }
        val rows = computed.mapNotNull { (item, hash) ->
            hash ?: return@mapNotNull null
            partials[item.id] = hash
            val fresh = freshRow(item)
            if (fresh?.partialHash == hash) return@mapNotNull null
            MediaContentHashEntity(
                mediaId = item.id,
                sourceSize = item.size,
                sourceModifiedSeconds = item.modifiedSeconds,
                sourcePath = item.path,
                partialHash = hash,
                sha1 = null,
                updatedAtMillis = nowMillis,
            )
        }
        if (rows.isNotEmpty()) {
            write(rows)
            written += rows.size
        }
        done += batch.size
        onProgress(done, total)
    }

    // Tier 3: full hashes for files whose size and partial hash still collide.
    val needFull = candidates
        .filter { it.id in partials }
        .groupBy { it.size to partials.getValue(it.id) }
        .values
        .filter { it.size > 1 }
        .flatten()
        .filter { item -> freshRow(item)?.let { it.partialHash == partials[item.id] && it.sha1 != null } != true }
    for (batch in needFull.chunked(HASH_BATCH)) {
        currentCoroutineContext().ensureActive()
        val rows = coroutineScope {
            batch.map { item ->
                async {
                    val sha1 = permits.withPermit { fullHash(item) } ?: return@async null
                    MediaContentHashEntity(
                        mediaId = item.id,
                        sourceSize = item.size,
                        sourceModifiedSeconds = item.modifiedSeconds,
                        sourcePath = item.path,
                        partialHash = partials.getValue(item.id),
                        sha1 = sha1,
                        updatedAtMillis = nowMillis,
                    )
                }
            }.awaitAll().filterNotNull()
        }
        if (rows.isNotEmpty()) {
            write(rows)
            written += rows.size
        }
    }
    // Large files a one-off check already hashed still count as checked while they are unchanged.
    val unchecked = skippedLarge.count { item -> freshRow(item)?.partialHash == null }
    return HashPassResult(written = written, skippedLarge = unchecked, candidates = candidates.size)
}

/**
 * SHA-1 of the first and last [PARTIAL_HASH_CHUNK] bytes plus the size, as lowercase hex.
 * [open] is called once for the head and, for larger files, once more for the tail.
 */
internal suspend fun partialSha1(size: Long, open: () -> InputStream?): String? {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(size.toString().toByteArray())
    val head = open() ?: return null
    head.use { readInto(digest, it, PARTIAL_HASH_CHUNK.toLong()) }
    if (size > 2L * PARTIAL_HASH_CHUNK) {
        val tail = open() ?: return null
        tail.use {
            if (!skipFully(it, size - PARTIAL_HASH_CHUNK)) return null
            readInto(digest, it, PARTIAL_HASH_CHUNK.toLong())
        }
    } else if (size > PARTIAL_HASH_CHUNK) {
        val rest = open() ?: return null
        rest.use {
            if (!skipFully(it, PARTIAL_HASH_CHUNK.toLong())) return null
            readInto(digest, it, size - PARTIAL_HASH_CHUNK)
        }
    }
    return digest.digest().toHex()
}

/** Full-file SHA-1 as lowercase hex, the same format the cloud upload and Free up space use. */
internal suspend fun fullSha1(open: () -> InputStream?): String? {
    val digest = MessageDigest.getInstance("SHA-1")
    val input = open() ?: return null
    input.use { readInto(digest, it, Long.MAX_VALUE) }
    return digest.digest().toHex()
}

private suspend fun readInto(digest: MessageDigest, input: InputStream, limit: Long) {
    val buffer = ByteArray(HASH_BUFFER_SIZE)
    var remaining = limit
    while (remaining > 0) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read == -1) break
        digest.update(buffer, 0, read)
        remaining -= read
    }
}

private fun skipFully(input: InputStream, count: Long): Boolean {
    var remaining = count
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped <= 0) {
            // skip() may stop early; fall back to reading so a short skip never shifts the window.
            if (input.read() == -1) return false
            remaining--
        } else {
            remaining -= skipped
        }
    }
    return true
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
