/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.sandbox.IsolatedMetadataService
import com.dot.gallery.core.startup.StartupMediaCache
import com.dot.gallery.core.startup.readStartupCacheStamp
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.MediaCaptureTimeEntity
import com.dot.gallery.feature_node.data.data_source.applyTo
import com.dot.gallery.feature_node.data.data_source.mediastore.queries.MediaFlow
import com.dot.gallery.feature_node.data.data_source.toEntity
import com.dot.gallery.feature_node.domain.model.CaptureTimeOrigin
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.ResolvedCaptureTime
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.core.util.MediaStoreBuckets
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

private const val CAPTURE_TIME_INDEX_BATCH = 24
private const val CAPTURE_TIME_DELETE_BATCH = 500
const val CAPTURE_TIME_INDEX_WORK = "CaptureTimeIndex"
const val CAPTURE_TIME_INDEX_PROGRESS = "progress"

fun WorkManager.enqueueCaptureTimeIndex(force: Boolean = false) {
    val request = OneTimeWorkRequestBuilder<CaptureTimeIndexWorker>()
        .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
        .setInputData(workDataOf(CaptureTimeIndexWorker.KEY_FORCE to force))
        .addTag(CAPTURE_TIME_INDEX_WORK)
        .build()
    enqueueUniqueWork(
        CAPTURE_TIME_INDEX_WORK,
        if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
        request
    )
}

@HiltWorker
class CaptureTimeIndexWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    private val isolatedParser: IsolatedMetadataParser,
    private val startupCache: StartupMediaCache,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withLogScope("worker.capture-time-index") { try {
        val force = inputData.getBoolean(KEY_FORCE, false)
        val media = MediaFlow(
            contentResolver = appContext.contentResolver,
            buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id,
            skipBatching = true
        ).flowData().first()
        val dao = database.getMediaCaptureTimeDao()
        val existing = dao.getAll().associateBy(MediaCaptureTimeEntity::mediaId)
        val pending = if (force) media else media.filter { item ->
            existing[item.id]?.isFreshFor(item) != true
        }
        val total = pending.size.coerceAtLeast(1)

        pending.chunked(CAPTURE_TIME_INDEX_BATCH).forEachIndexed { batchIndex, batch ->
            if (!currentCoroutineContext().isActive || isStopped) return@withLogScope Result.failure()
            val updatedAt = System.currentTimeMillis()
            val entries = batch.map { item -> resolve(item).toEntity(item, updatedAt) }
            dao.upsertAll(entries)
            val processed = ((batchIndex + 1) * CAPTURE_TIME_INDEX_BATCH).coerceAtMost(pending.size)
            setProgress(workDataOf(CAPTURE_TIME_INDEX_PROGRESS to (processed * 100 / total)))
        }

        if (readStartupCacheStamp(appContext) != null) {
            val currentIds = media.mapTo(HashSet(media.size)) { it.id }
            val orphanIds = dao.getAll().mapNotNull { it.mediaId.takeUnless(currentIds::contains) }
            for (ids in orphanIds.chunked(CAPTURE_TIME_DELETE_BATCH)) {
                dao.deleteByIds(ids)
            }
        }

        val indexed = dao.getAll().associateBy(MediaCaptureTimeEntity::mediaId)
        val enriched = media.map { indexed[it.id]?.applyTo(it) ?: it }
        startupCache.writeMedia(
            startupCache.currentStamp(),
            MediaOrder.Date(OrderType.Descending).sortMedia(enriched)
        )
        setProgress(workDataOf(CAPTURE_TIME_INDEX_PROGRESS to 100))
        Result.success()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        printWarning("CaptureTimeIndexWorker failed: ${error.message}")
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    } }

    private suspend fun resolve(media: Media.UriMedia): ResolvedCaptureTime {
        val bundle = when {
            media.mimeType.startsWith("image/") ->
                isolatedParser.parseImageMetadata(media.uri, media.label)
            media.mimeType.startsWith("video/") ->
                isolatedParser.parseVideoMetadata(media.uri)
            else -> null
        }
        if (bundle?.containsKey(IsolatedMetadataService.KEY_CAPTURE_TIMESTAMP_MILLIS) == true) {
            return ResolvedCaptureTime(
                timestampMillis = bundle.getLong(IsolatedMetadataService.KEY_CAPTURE_TIMESTAMP_MILLIS),
                origin = if (media.mimeType.startsWith("video/")) {
                    CaptureTimeOrigin.VIDEO_CONTAINER
                } else {
                    CaptureTimeOrigin.EMBEDDED_IMAGE
                }
            )
        }
        return ResolvedCaptureTime(
            timestampMillis = media.takenTimestamp ?: media.timestamp * 1000L,
            origin = media.captureTimeOrigin
        )
    }

    companion object {
        const val KEY_FORCE = "force"
    }
}
