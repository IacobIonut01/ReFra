/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import com.dot.gallery.cloud.core.resolveRemote
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.sandbox.IsolatedMetadataService
import com.dot.gallery.core.util.MediaStoreBuckets
import com.dot.gallery.core.util.openUnredactedFileDescriptor
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.mediastore.queries.MediaFlow
import com.dot.gallery.domain.model.CaptureTimeOrigin
import com.dot.gallery.domain.model.ResolvedCaptureTime
import com.dot.gallery.domain.util.MediaOrder
import com.dot.gallery.domain.util.OrderType
import com.dot.gallery.core.logging.withLogScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.offline.OfflineModeManager
import com.dot.gallery.core.startup.StartupMediaCache
import com.dot.gallery.data.entity.toEntity
import com.dot.gallery.data.entity.MediaCaptureTimeEntity
import com.dot.gallery.core.startup.readStartupCacheStamp
import com.dot.gallery.domain.uri.scheme
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.core.logging.printWarning
import com.dot.gallery.data.entity.applyTo
import me.tatarka.inject.annotations.Inject


private const val CAPTURE_TIME_INDEX_BATCH = 24
private const val CAPTURE_TIME_DELETE_BATCH = 500

/**
 * Whether the capture-time index may read remote bytes for an account:
 * never when effectively offline, and on metered networks only when the account
 * doesn't restrict transfers to unmetered (`wifiOnly`). Internal for tests.
 */
internal fun remoteCaptureFetchAllowed(
    effectiveOffline: Boolean,
    unmetered: Boolean,
    accountWifiOnly: Boolean
): Boolean = !effectiveOffline && (!accountWifiOnly || unmetered)

/** Bounded parallelism for the remote fetch+parse tier (network reads dominate). */
private const val CLOUD_FETCH_CONCURRENCY = 4

/**
 * Prefix bytes staged for the remote metadata probe. Covers JPEG APP1 (EXIF sits
 * within the first ~64 KiB), PNG eXIf, WebP chunks and virtually all HEIF/JXL
 * metadata-box placements.
 */
private const val PROBE_IMAGE_HEAD_BYTES = 256L * 1024L
private const val PROBE_VIDEO_HEAD_BYTES = 512L * 1024L

/**
 * Tail bytes staged for video probes: camera MP4/MOV files put `moov` (which holds
 * the creation time) at the end of the container.
 */
private const val PROBE_VIDEO_TAIL_BYTES = 512L * 1024L

const val CAPTURE_TIME_INDEX_WORK = "CaptureTimeIndex"
const val CAPTURE_TIME_INDEX_PROGRESS = "progress"

suspend fun BackgroundTaskScheduler.enqueueCaptureTimeIndex(force: Boolean = false) {
    enqueue(
        id = CAPTURE_TIME_INDEX_WORK,
        trigger = TaskTrigger.OneTime(),
        workerClassName = CaptureTimeIndexWorker.WORKER_NAME,
        // storageNotLow ↔ no ALLOW_LOW_STORAGE; single retry honours the old runAttemptCount<2 retry
        constraints = Constraints(maxRetries = 1),
        inputJson = workDataJson(CaptureTimeIndexWorker.KEY_FORCE to force),
        policy = if (force) ExistingPolicy.REPLACE else ExistingPolicy.KEEP,
        tags = setOf(CAPTURE_TIME_INDEX_WORK),
    )
}

class CaptureTimeIndexWorker @Inject constructor(
    private val database: InternalDatabase,
    private val isolatedParser: IsolatedMetadataParser,
    private val startupCache: StartupMediaCache,
    private val providerRegistry: ProviderRegistry,
    private val offlineModeManager: OfflineModeManager,
    private val appContext: Context,
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult = withLogScope("worker.capture-time-index") { try {
        val force = input.getBoolean(KEY_FORCE, false)
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

        // Cloud items share the index: providers that can't report a capture date
        // (WebDAV/SMB/NFS) get theirs from embedded metadata or the filename, so
        // cloud media sorts by capture time exactly like local media.
        val cloudEntities = database.getCloudMediaDao().getAllAsync()
        val cloudItems = cloudEntities.map { it to it.toUriMedia() }
        val cloudPending = if (force) cloudItems else cloudItems.filter { (_, item) ->
            existing[item.id]?.isFreshFor(item) != true
        }
        val wifiOnlyByConfig = database.getCloudServerConfigDao().getAll().first()
            .associate { it.id to it.wifiOnly }

        val total = (pending.size + cloudPending.size).coerceAtLeast(1)
        var processed = 0

        pending.chunked(CAPTURE_TIME_INDEX_BATCH).forEach { batch ->
            if (!currentCoroutineContext().isActive || env.isCancelled()) return@withLogScope WorkerResult.Failure("cancelled")
            val updatedAt = System.currentTimeMillis()
            dao.upsertAll(batch.map { item -> resolve(item).toEntity(item, updatedAt) })
            processed += batch.size
            WorkSignals.reportProgress(CAPTURE_TIME_INDEX_WORK, workData(CAPTURE_TIME_INDEX_PROGRESS to (processed * 100 / total)))
        }

        val cloudFetchPermits = Semaphore(CLOUD_FETCH_CONCURRENCY)
        cloudPending.chunked(CAPTURE_TIME_INDEX_BATCH).forEach { batch ->
            if (!currentCoroutineContext().isActive || env.isCancelled()) return@withLogScope WorkerResult.Failure("cancelled")
            val updatedAt = System.currentTimeMillis()
            // resolveCloud returns null when the remote tier couldn't run (offline,
            // metered-gated, provider down, fetch failed) — those items stay pending
            // instead of pinning a fallback row that would suppress every future run.
            val entries = coroutineScope {
                batch.map { (entity, item) ->
                    async {
                        cloudFetchPermits.withPermit {
                            resolveCloud(entity, item, wifiOnlyByConfig)
                                ?.toEntity(item, updatedAt)
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            if (entries.isNotEmpty()) dao.upsertAll(entries)
            processed += batch.size
            WorkSignals.reportProgress(CAPTURE_TIME_INDEX_WORK, workData(CAPTURE_TIME_INDEX_PROGRESS to (processed * 100 / total)))
        }

        if (readStartupCacheStamp(appContext) != null) {
            // Orphan cleanup must see both id spaces — cloud globalMediaIds are
            // negative and were previously treated as orphans on every run.
            val currentIds = HashSet<Long>(media.size + cloudEntities.size)
            media.forEach { currentIds += it.id }
            cloudEntities.forEach { currentIds += it.globalMediaId }
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
        WorkSignals.reportProgress(CAPTURE_TIME_INDEX_WORK, workData(CAPTURE_TIME_INDEX_PROGRESS to 100))
        WorkerResult.Success()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        printWarning("CaptureTimeIndexWorker failed: ${error.message}")
        // runAttemptCount < 2 ↔ original attempt + one retry
        WorkerResult.Retry(error.message ?: "index pass failed", attemptCap = 2)
    } }

    private suspend fun resolve(media: UriMedia): ResolvedCaptureTime {
        val bundle = when {
            media.mimeType.startsWith("image/") ->
                isolatedParser.parseImageMetadata(media.uri, media.label)
            media.mimeType.startsWith("video/") ->
                isolatedParser.parseVideoMetadata(media.uri)
            else -> null
        }
        bundle?.captureTimestampMillis()?.let {
            return ResolvedCaptureTime(
                timestampMillis = it,
                origin = if (media.mimeType.startsWith("video/")) {
                    CaptureTimeOrigin.VIDEO_CONTAINER
                } else {
                    CaptureTimeOrigin.EMBEDDED_IMAGE
                }
            )
        }
        return media.fallbackCaptureTime()
    }

    /**
     * Cloud variant of [resolve]. Returns null when the remote tier couldn't run
     * (provider unavailable, offline, metered-gated, or the fetch failed) so the
     * caller writes no row and the item stays pending for a later run.
     */
    private suspend fun resolveCloud(
        entity: CloudMediaEntity,
        media: UriMedia,
        wifiOnlyByConfig: Map<Long, Boolean>
    ): ResolvedCaptureTime? {
        val isVideo = media.mimeType.startsWith("video/")
        val isImage = media.mimeType.startsWith("image/")
        if (!isImage && !isVideo) return media.fallbackCaptureTime()

        // A provider-reported capture time (Immich localDateTime, WebDAV
        // creationdate) is already embedded-derived upstream — nothing to fetch.
        if (entity.takenTimestamp != null) return media.fallbackCaptureTime()

        // A synced/uploaded local copy is free to open and needs no network, and
        // carries the same bytes as the remote — a definitive parse result here
        // makes the remote tier unnecessary.
        var locallyParsed = false
        openLocalCopyPfd(entity)?.let { pfd ->
            val bundle = parsePfd(pfd, media, isVideo)
            if (bundle != null) {
                locallyParsed = true
                bundle.captureTimestampMillis()?.let {
                    return ResolvedCaptureTime(
                        it, if (isVideo) CaptureTimeOrigin.VIDEO_CONTAINER else CaptureTimeOrigin.EMBEDDED_IMAGE
                    )
                }
            }
        }

        // Remote embedded read, gated on connectivity + the account's wifiOnly flag.
        // Skip (stay pending) only while the remote tier could still add value —
        // a definitive local "no capture time" settles on the fallback now.
        if (!remoteFetchAllowed(entity, wifiOnlyByConfig)) {
            return if (locallyParsed) media.fallbackCaptureTime() else null
        }
        val staged = stageCloudProbe(entity, isVideo) ?: return null
        try {
            ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY)
                .let { pfd -> parsePfd(pfd, media, isVideo)?.captureTimestampMillis() }
                ?.let {
                    return ResolvedCaptureTime(
                        it, if (isVideo) CaptureTimeOrigin.VIDEO_CONTAINER else CaptureTimeOrigin.EMBEDDED_IMAGE
                    )
                }
        } finally {
            staged.delete()
        }

        // Bytes were fetched and parsed but carry no capture time — a definitive
        // result, so the fallback row suppresses future re-fetch attempts.
        return media.fallbackCaptureTime()
    }

    private fun UriMedia.fallbackCaptureTime(): ResolvedCaptureTime =
        ResolvedCaptureTime(
            timestampMillis = takenTimestamp ?: timestamp * 1000L,
            origin = captureTimeOrigin
        )

    private fun remoteFetchAllowed(
        entity: CloudMediaEntity,
        wifiOnlyByConfig: Map<Long, Boolean>
    ): Boolean = remoteCaptureFetchAllowed(
        effectiveOffline = offlineModeManager.effectiveOfflineNow,
        unmetered = offlineModeManager.unmeteredNow,
        // An unknown account resolves to wifi-only — conservative for a bulk read.
        accountWifiOnly = wifiOnlyByConfig[entity.serverConfigId] ?: true
    )

    /**
     * Opens the item's local copy (user original for uploads, app-downloaded
     * MediaStore copy for `downloadRemoteEnabled`) for metadata reads.
     */
    private fun openLocalCopyPfd(entity: CloudMediaEntity): ParcelFileDescriptor? {
        val localPath = entity.localCopyPath.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = localPath.toUri()
            if (uri.scheme.isNullOrEmpty()) {
                ParcelFileDescriptor.open(File(localPath), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                appContext.openUnredactedFileDescriptor(uri)
            }
        }.getOrNull()
    }

    /** The parser takes ownership of and closes the descriptor. */
    private suspend fun parsePfd(
        pfd: ParcelFileDescriptor,
        media: UriMedia,
        isVideo: Boolean
    ): Bundle? = if (isVideo) {
        isolatedParser.parseVideoMetadata(pfd)
    } else {
        isolatedParser.parseImageMetadata(pfd, media.label)
    }

    private fun Bundle?.captureTimestampMillis(): Long? =
        this?.takeIf { it.containsKey(IsolatedMetadataService.KEY_CAPTURE_TIMESTAMP_MILLIS) }
            ?.getLong(IsolatedMetadataService.KEY_CAPTURE_TIMESTAMP_MILLIS)

    /**
     * Downloads head (+ tail for videos) byte ranges of the remote file into a
     * sparse cache file sized like the original, so parsers see embedded metadata
     * wherever the container keeps it — EXIF/moov-at-head in the prefix, trailing
     * moov/XMP at the end — without transferring the body in between. Returns null
     * when the provider is gone/unavailable or the head range can't be fetched.
     */
    private suspend fun stageCloudProbe(entity: CloudMediaEntity, isVideo: Boolean): File? {
        val provider = providerRegistry.resolveRemote(entity.providerType, entity.serverConfigId)
            ?.takeIf { it.isAvailable } ?: return null
        val headCap = if (isVideo) PROBE_VIDEO_HEAD_BYTES else PROBE_IMAGE_HEAD_BYTES
        val file = File.createTempFile("ct_probe_", ".tmp", appContext.cacheDir)
        var staged = false
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                val head = provider.fetchRange(entity.remoteId, 0L, headCap)
                    ?.takeIf { it.isNotEmpty() } ?: return null
                if (isVideo && entity.size > head.size.toLong()) {
                    raf.setLength(entity.size)
                    raf.write(head)
                    val tailLength = minOf(PROBE_VIDEO_TAIL_BYTES, entity.size - head.size)
                    if (tailLength > 0L) {
                        provider.fetchRange(entity.remoteId, entity.size - tailLength, tailLength)
                            ?.let { tail -> raf.seek(entity.size - tail.size); raf.write(tail) }
                    }
                } else {
                    raf.write(head)
                }
            }
            staged = true
            file
        } finally {
            if (!staged) file.delete()
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        const val WORKER_NAME = "CaptureTimeIndexWorker"
    }
}

@Worker(name = CaptureTimeIndexWorker.WORKER_NAME)
class CaptureTimeIndexWorkerAdapter :
    GalleryWorkerAdapter(CaptureTimeIndexWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.captureTimeIndexWorker.doWork(input, env)
}
