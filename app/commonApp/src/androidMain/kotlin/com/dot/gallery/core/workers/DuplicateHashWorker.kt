/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.duplicates.HashCandidate
import com.dot.gallery.core.duplicates.fullSha1
import com.dot.gallery.core.duplicates.partialSha1
import com.dot.gallery.core.duplicates.runHashPass
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.core.startup.readStartupCacheStamp
import com.dot.gallery.core.util.MediaStoreBuckets
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.mediastore.queries.MediaFlow
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject

const val DUPLICATE_HASH_WORK = "DuplicateHash"
const val DUPLICATE_HASH_PROGRESS = "progress"
const val DUPLICATE_HASH_DONE = "done"
const val DUPLICATE_HASH_TOTAL = "total"

private const val DUPLICATE_HASH_DELETE_BATCH = 500

/**
 * Looks for exact duplicates in the local library. [force] replaces a queued or running pass,
 * for Check again and after the large-file setting changes. [includeLarge] checks files over
 * 2 GB this once, even when the Check large files setting is off.
 */
suspend fun BackgroundTaskScheduler.enqueueDuplicateHash(
    force: Boolean = false,
    includeLarge: Boolean = false,
) {
    enqueue(
        id = DUPLICATE_HASH_WORK,
        trigger = TaskTrigger.OneTime(),
        workerClassName = DuplicateHashWorker.WORKER_NAME,
        constraints = Constraints(maxRetries = 1),
        inputJson = workDataJson(DuplicateHashWorker.KEY_INCLUDE_LARGE to includeLarge),
        policy = if (force) ExistingPolicy.REPLACE else ExistingPolicy.KEEP,
        tags = setOf(DUPLICATE_HASH_WORK),
    )
}

/**
 * Hashes the local files that can have an exact copy and stores the result in
 * `media_content_hash`. Only files whose byte size another file shares are read: first a
 * partial hash of both ends, then a full SHA-1 only while that still matches. Stored hashes are
 * reused until a file changes, so later passes read almost nothing.
 */
class DuplicateHashWorker @Inject constructor(
    private val database: InternalDatabase,
    private val appContext: Context,
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withLogScope("worker.duplicates") {
            try {
                val settings = DataStoreSettingsStore(appContext.activeDataStore)
                val includeLarge = input.getBoolean(KEY_INCLUDE_LARGE, false) ||
                    settings.observe(SettingsKeys.duplicateCheckLargeFiles).first()
                val media = MediaFlow(
                    contentResolver = appContext.contentResolver,
                    buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id,
                    skipBatching = true
                ).flowData().first()
                val uris = media.associate { it.id to it.uri }
                val dao = database.getMediaContentHashDao()
                val existing = dao.getAll().associateBy { it.mediaId }
                val resolver = appContext.contentResolver

                fun opener(item: HashCandidate) = { uris[item.id]?.let { resolver.openInputStream(it) } }

                val result = runHashPass(
                    items = media.map { HashCandidate(it.id, it.size, it.timestamp, it.path) },
                    existing = existing,
                    includeLarge = includeLarge,
                    nowMillis = System.currentTimeMillis(),
                    partialHash = { item -> readHash(item) { partialSha1(item.size, opener(item)) } },
                    fullHash = { item -> readHash(item) { fullSha1(opener(item)) } },
                    write = dao::upsertAll,
                    onProgress = { done, total ->
                        if (env.isCancelled()) throw CancellationException("cancelled")
                        WorkSignals.reportProgress(
                            DUPLICATE_HASH_WORK,
                            workData(
                                DUPLICATE_HASH_PROGRESS to (done * 100 / total.coerceAtLeast(1)),
                                DUPLICATE_HASH_DONE to done,
                                DUPLICATE_HASH_TOTAL to total,
                            )
                        )
                    },
                )

                // Orphan cleanup needs the complete media list; a partial grant would wipe rows
                // for media that still exists.
                if (readStartupCacheStamp(appContext) != null) {
                    val live = media.mapTo(HashSet(media.size)) { it.id }
                    val orphans = existing.keys.filterNot(live::contains)
                    for (ids in orphans.chunked(DUPLICATE_HASH_DELETE_BATCH)) dao.deleteByIds(ids)
                }

                settings.put(SettingsKeys.duplicateSkippedLarge, result.skippedLarge)
                settings.put(SettingsKeys.duplicateCheckedCount, media.size)
                settings.put(SettingsKeys.duplicateLastCheckedMillis, System.currentTimeMillis())
                printInfo(
                    "worker.duplicates",
                    "duplicate hash pass finished: items=${media.size} candidates=${result.candidates} " +
                        "written=${result.written} skippedLarge=${result.skippedLarge}",
                )
                WorkSignals.reportProgress(DUPLICATE_HASH_WORK, workData(DUPLICATE_HASH_PROGRESS to 100))
                WorkerResult.Success()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                printWarn(
                    "worker.duplicates",
                    "duplicate hash pass failed",
                    ctx = mapOf("error" to error.toString()),
                )
                WorkerResult.Retry(error.message ?: "duplicate hash pass failed", attemptCap = 2)
            }
        }

    private suspend fun readHash(item: HashCandidate, block: suspend () -> String?): String? =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printWarn(
                    "worker.duplicates",
                    "could not read a file for hashing",
                    ctx = mapOf("mediaId" to item.id.toString(), "error" to e.toString()),
                )
                null
            }
        }

    companion object {
        const val WORKER_NAME = "DuplicateHashWorker"
        const val KEY_INCLUDE_LARGE = "include_large"
    }
}

@Worker(name = DuplicateHashWorker.WORKER_NAME)
class DuplicateHashWorkerAdapter :
    GalleryWorkerAdapter(DuplicateHashWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.duplicateHashWorker.doWork(input, env)
}
