/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.resolveRemote
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.offline.OfflineModeManager
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.sandbox.IsolatedMetadataService
import com.dot.gallery.core.startup.StartupMediaCache
import com.dot.gallery.core.startup.readStartupCacheStamp
import com.dot.gallery.core.util.MediaStoreBuckets
import com.dot.gallery.core.util.openUnredactedFileDescriptor
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
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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

/**
 * Immich reports EXIF-derived capture time as `localDateTime`, so a populated
 * [CloudMediaEntity.takenTimestamp] from Immich is already the embedded date.
 * Path-based providers (WebDAV, Nextcloud, ownCloud, SMB, NFS) only have
 * filesystem timestamps (`creationdate` / mtime); those stay a fallback and
 * must not skip the DateTimeOriginal probe. Internal for tests.
 */
internal fun skipEmbeddedCaptureProbe(
    providerType: ProviderType,
    takenTimestamp: Long?
): Boolean = takenTimestamp != null && providerType == ProviderType.IMMICH

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
    private val providerRegistry: ProviderRegistry,
    private val offlineModeManager: OfflineModeManager,
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
            if (!currentCoroutineContext().isActive || isStopped) return@withLogScope Result.failure()
            val updatedAt = System.currentTimeMillis()
            dao.upsertAll(batch.map { item -> resolve(item).toEntity(item, updatedAt) })
            processed += batch.size
            setProgress(workDataOf(CAPTURE_TIME_INDEX_PROGRESS to (processed * 100 / total)))
        }

        val cloudFetchPermits = Semaphore(CLOUD_FETCH_CONCURRENCY)
        cloudPending.chunked(CAPTURE_TIME_INDEX_BATCH).forEach { batch ->
            if (!currentCoroutineContext().isActive || isStopped) return@withLogScope Result.failure()
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
            setProgress(workDataOf(CAPTURE_TIME_INDEX_PROGRESS to (processed * 100 / total)))
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
        media: Media.UriMedia,
        wifiOnlyByConfig: Map<Long, Boolean>
    ): ResolvedCaptureTime? {
        val isVideo = media.mimeType.startsWith("video/")
        val isImage = media.mimeType.startsWith("image/")
        if (!isImage && !isVideo) return media.fallbackCaptureTime()

        // Immich localDateTime is already EXIF-derived. WebDAV `creationdate`
        // (and other path-provider filesystem times) is only a fallback — still
        // probe DateTimeOriginal so Recheck capture dates can replace it.
        if (skipEmbeddedCaptureProbe(entity.providerType, entity.takenTimestamp)) {
            return media.fallbackCaptureTime()
        }

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

    private fun Media.UriMedia.fallbackCaptureTime(): ResolvedCaptureTime =
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
        media: Media.UriMedia,
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
    }
}
