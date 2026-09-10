/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dot.gallery.R
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.data.dao.CloudDeleteLocalPrefDao
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.dao.CloudUploadPrefDao
import com.dot.gallery.cloud.data.entity.CloudBackupRevisionEntity
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.data.entity.backupFingerprint
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.work.workDataOf
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

@HiltWorker
class CloudUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val registry: ProviderRegistry,
    private val configDao: CloudServerConfigDao,
    private val uploadPrefDao: CloudUploadPrefDao,
    private val deleteLocalPrefDao: CloudDeleteLocalPrefDao,
    private val cloudMediaDao: CloudMediaDao,
    private val repository: MediaRepository
) : CoroutineWorker(context, workerParams) {

    /** A single asset queued for upload to a specific cloud account. */
    private data class UploadTask(
        val media: Media,
        val provider: SyncCapableProvider,
        val accountLabel: String,
        val configId: Long,
        val albumLabel: String,
        val checksum: String?,
        /**
         * Remote folder to upload into for path-based stores (WebDAV/ownCloud/Nextcloud/SMB/NFS),
         * so each backed-up local album lands in its own folder instead of a single flat "Photos"
         * dir. Content-addressable stores (Immich) ignore this. Null falls back to the provider's
         * default upload folder.
         */
        val targetPath: String?
    )

    /** A successfully-uploaded asset, tracked so its album can be synced to the server. */
    private data class UploadedAsset(
        val configId: Long,
        val albumLabel: String,
        val remoteId: String
    )

    private data class UploadOutcome(
        val result: kotlin.Result<CloudMediaEntity>? = null,
        val alreadyPresent: Boolean = false
    )

    override suspend fun doWork(): Result {
        printDebug("CloudUploadWorker: Starting upload check...")
        val isManual = inputData.getBoolean(KEY_MANUAL, false)
        try {
            // Manual ("Upload now" / "Start backup") runs are user-initiated and must work even
            // when periodic auto-sync (syncEnabled) is off. syncEnabled only governs the background
            // scheduler, so only require it for the periodic worker, not for a manual trigger.
            val targetConfigId = inputData.getLong(KEY_CONFIG_ID, -1L)
            val configs = configDao.getAll().first()
            val activeConfigs = configs.filter {
                it.isActive && (isManual || it.syncEnabled) &&
                    (targetConfigId <= 0L || it.id == targetConfigId)
            }
            if (activeConfigs.isEmpty()) {
                printDebug("CloudUploadWorker: No active server config (manual=$isManual, target=$targetConfigId)")
                return Result.success()
            }

            // Current network/charging state used to honour the per-account Backup Options:
            // "Use cellular data for photos/videos" and "Require charging".
            val isMetered = runCatching {
                (applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                        as? ConnectivityManager)?.isActiveNetworkMetered ?: false
            }.getOrDefault(false)
            val isCharging = (applicationContext.getSystemService(Context.BATTERY_SERVICE)
                    as? BatteryManager)?.isCharging ?: false

            // Build the upload queue per account: each account uploads ONLY the
            // local albums that have been enabled for that account.
            val tasks = mutableListOf<UploadTask>()
            val hashCache = ConcurrentHashMap<String, String>()
            var verificationTotalItems = 0
            var verificationCheckedItems = 0

            for (config in activeConfigs) {
                // "Require charging" is a background-only constraint: a user-initiated "Upload now"
                // (isManual) always proceeds, but scheduled/periodic runs are skipped for accounts
                // that require charging while the device is not charging.
                if (!isManual && config.requireCharging && !isCharging) {
                    printDebug("CloudUploadWorker: Skipping account #${config.id} — requires charging")
                    continue
                }

                val syncProvider = registry.getByConfigId(config.id) as? SyncCapableProvider
                if (syncProvider == null) {
                    printDebug("CloudUploadWorker: Provider ${config.providerType} does not support sync, skipping")
                    continue
                }

                val enabledPrefs = uploadPrefDao.getEnabledByConfigList(config.id)
                if (enabledPrefs.isEmpty()) {
                    printDebug("CloudUploadWorker: No albums enabled for account #${config.id} (${config.providerType})")
                    continue
                }

                val accountLabel = config.displayName.ifBlank { config.providerType.displayName }
                val cachedLocalRevisions = cloudMediaDao.getValidBackupRevisions(
                    config.id,
                    backupVerificationCutoff()
                ).map { backupLocalRevisionKey(it.localUri, it.localSize, it.localTimestamp) }
                    .toSet()
                val cachedRemoteRevisions = emptySet<String>()

                for (pref in enabledPrefs) {
                    // Path-based stores (WebDAV/ownCloud/Nextcloud/SMB/NFS) mirror each
                    // backed-up local album into its own remote folder named after the
                    // album, instead of flattening everything into a single "Photos" dir.
                    // Content-addressable stores (Immich) ignore this. Blank labels fall
                    // back to the provider's default upload folder (null).
                    val albumTarget = pref.albumLabel.trim().ifBlank { null }
                    val allAlbumMedia = repository.getMediaByAlbumId(pref.albumId, skipBatching = true)
                        .first().data ?: continue
                    if (allAlbumMedia.isEmpty()) continue

                    // Apply the same authoritative policy used to choose the WorkManager network
                    // constraint. On unmetered networks every enabled item remains eligible.
                    val albumMedia = if (!isMetered) allAlbumMedia else allAlbumMedia.filter { media ->
                        CloudCellularPolicy.allowsUpload(config, media.mimeType)
                    }
                    if (albumMedia.isEmpty()) {
                        printDebug("CloudUploadWorker: [$accountLabel] album ${pref.albumLabel} skipped on metered network (cellular disabled)")
                        continue
                    }

                    val candidates = albumMedia.filterNot { media ->
                        isBackupRevisionCached(
                            uri = backupRevisionLocalUri(
                                media.getUri().toString(),
                                syncProvider.deterministicRemoteId(media, albumTarget)
                            ),
                            mediaId = media.id,
                            label = media.label,
                            mimeType = media.mimeType,
                            size = media.size,
                            timestamp = media.timestamp,
                            localRevisions = cachedLocalRevisions,
                            remoteRevisions = cachedRemoteRevisions
                        )
                    }
                    val notPresent = candidates
                    if (notPresent.isEmpty()) continue

                    fun queue(media: Media, checksum: String?) {
                        tasks.add(
                            UploadTask(
                                media = media,
                                provider = syncProvider,
                                accountLabel = accountLabel,
                                configId = config.id,
                                albumLabel = pref.albumLabel,
                                checksum = checksum,
                                targetPath = albumTarget
                            )
                        )
                    }

                    if (!syncProvider.requiresUploadChecksum || shouldDeferChecksumCheck(
                            itemCount = notPresent.size,
                            maxConcurrentUploads = syncProvider.maxConcurrentUploads
                        )
                    ) {
                        notPresent.forEach { queue(it, null) }
                        continue
                    }

                    verificationTotalItems += notPresent.size
                    setProgress(workDataOf(
                        KEY_PHASE to PHASE_VERIFYING,
                        KEY_TOTAL_ITEMS to verificationTotalItems,
                        KEY_CHECKED_ITEMS to verificationCheckedItems,
                        KEY_COMPLETED_ITEMS to 0,
                        KEY_FAILED_ITEMS to 0,
                        KEY_CURRENT_FILE to pref.albumLabel,
                        KEY_CURRENT_ACCOUNT to accountLabel
                    ))
                    notPresent.chunked(BULK_CHECK_SIZE).forEach { chunk ->
                        val hashed = mapWorkerPool(
                            items = chunk,
                            maxConcurrency = syncProvider.maxConcurrentUploads
                        ) { media ->
                            media to cachedSha1(media, hashCache)
                        }
                        hashed.filter { it.second == null }.forEach { queue(it.first, null) }
                        val mediaWithHashes = hashed.mapNotNull { (media, hash) ->
                            hash?.let { media to it }
                        }
                        if (mediaWithHashes.isNotEmpty()) {
                            syncProvider.bulkUploadCheck(mediaWithHashes.map { it.second })
                                .onSuccess { alreadyUploaded ->
                                    mediaWithHashes.forEachIndexed { idx, (media, hash) ->
                                        if (alreadyUploaded[idx.toString()] == true) {
                                            cacheVerifiedBackupRevision(
                                                cloudMediaDao,
                                                config.id,
                                                media,
                                                hash,
                                                syncProvider,
                                                albumTarget
                                            )
                                        } else {
                                            queue(media, hash)
                                        }
                                    }
                                }
                                .onFailure { error ->
                                    printDebug("CloudUploadWorker: Bulk check failed for $accountLabel: ${error.message}")
                                    mediaWithHashes.forEach { queue(it.first, null) }
                                }
                        }
                        verificationCheckedItems += chunk.size
                        setProgress(workDataOf(
                            KEY_PHASE to PHASE_VERIFYING,
                            KEY_TOTAL_ITEMS to verificationTotalItems,
                            KEY_CHECKED_ITEMS to verificationCheckedItems,
                            KEY_COMPLETED_ITEMS to 0,
                            KEY_FAILED_ITEMS to 0,
                            KEY_CURRENT_FILE to pref.albumLabel,
                            KEY_CURRENT_ACCOUNT to accountLabel
                        ))
                    }
                }
            }

            val totalItems = tasks.size
            var completedItems = 0
            var failedItems = 0
            val completedFiles = mutableListOf<String>()
            val failedFiles = mutableListOf<String>()
            val uploadedAssets = mutableListOf<UploadedAsset>()

            // Notification behaviour is a per-account preference; honour it if ANY active
            // account opted in (the workers process every active account in one run).
            val showTotalProgress = activeConfigs.any { it.showBackupTotalProgress }
            val showDetailProgress = activeConfigs.any { it.showBackupDetailProgress }
            val notifyFailures = activeConfigs.any { it.notifyBackupFailures }

            setProgress(workDataOf(
                KEY_PHASE to PHASE_UPLOADING,
                KEY_TOTAL_ITEMS to totalItems,
                KEY_CHECKED_ITEMS to totalItems,
                KEY_COMPLETED_ITEMS to 0,
                KEY_FAILED_ITEMS to 0,
                KEY_CURRENT_FILE to ""
            ))

            if (showTotalProgress && totalItems > 0) {
                runCatching { setForeground(progressForegroundInfo(0, totalItems, null, showDetailProgress)) }
            }

            val progressMutex = Mutex()
            coroutineScope {
                tasks.groupBy { it.provider }.values.map { providerTasks ->
                    launch {
                        val configId = providerTasks.first().configId
                        accountUploadMutexes.computeIfAbsent(configId) { Mutex() }.withLock {
                            runWorkerPool(
                                items = providerTasks,
                                maxConcurrency = providerTasks.first().provider.maxConcurrentUploads
                            ) { task ->
                                if (isStopped) return@runWorkerPool
                                val outcome = executeUpload(task, hashCache)
                                progressMutex.withLock {
                                    if (outcome.alreadyPresent) {
                                        completedItems++
                                        completedFiles.add(task.media.label)
                                        printDebug("CloudUploadWorker: [${task.accountLabel}] already backed up ${task.media.label}")
                                    }
                                    outcome.result?.onSuccess { entity ->
                                        cacheSuccessfulBackupRevision(
                                            cloudMediaDao,
                                            task.configId,
                                            task.media,
                                            entity,
                                            task.provider.deterministicRemoteId(task.media, task.targetPath)
                                        )
                                        completedItems++
                                        completedFiles.add(task.media.label)
                                        if (entity.remoteId.isNotBlank()) {
                                            uploadedAssets.add(
                                                UploadedAsset(task.configId, task.albumLabel, entity.remoteId)
                                            )
                                        }
                                        printDebug("CloudUploadWorker: [${task.accountLabel}] uploaded ${task.media.label}")
                                    }?.onFailure { e ->
                                        failedItems++
                                        failedFiles.add(task.media.label)
                                        printDebug("CloudUploadWorker: [${task.accountLabel}] upload failed for ${task.media.label}: ${e.message}")
                                    }
                                    setProgress(workDataOf(
                                        KEY_PHASE to PHASE_UPLOADING,
                                        KEY_TOTAL_ITEMS to totalItems,
                                        KEY_CHECKED_ITEMS to totalItems,
                                        KEY_COMPLETED_ITEMS to completedItems,
                                        KEY_FAILED_ITEMS to failedItems,
                                        KEY_CURRENT_FILE to task.media.label,
                                        KEY_CURRENT_ACCOUNT to task.accountLabel,
                                        KEY_COMPLETED_FILES to completedFiles.takeLast(MAX_TRACKED_FILES).toTypedArray(),
                                        KEY_FAILED_FILES to failedFiles.takeLast(MAX_TRACKED_FILES).toTypedArray()
                                    ))
                                    if (showTotalProgress) {
                                        runCatching {
                                            setForeground(
                                                progressForegroundInfo(
                                                    completedItems + failedItems,
                                                    totalItems,
                                                    task.media.label,
                                                    showDetailProgress
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.joinAll()
            }
            if (isStopped) return Result.retry()

            setProgress(workDataOf(
                KEY_PHASE to PHASE_COMPLETE,
                KEY_TOTAL_ITEMS to totalItems,
                KEY_CHECKED_ITEMS to totalItems,
                KEY_COMPLETED_ITEMS to completedItems,
                KEY_FAILED_ITEMS to failedItems,
                KEY_CURRENT_FILE to "",
                KEY_CURRENT_ACCOUNT to "",
                KEY_COMPLETED_FILES to completedFiles.takeLast(MAX_TRACKED_FILES).toTypedArray(),
                KEY_FAILED_FILES to failedFiles.takeLast(MAX_TRACKED_FILES).toTypedArray()
            ))

            if (notifyFailures && failedItems > 0) {
                postFailureNotification(failedItems, failedFiles)
            }

            // "Sync albums to server": for accounts that opted in, create/match a server album
            // per local album and add the just-uploaded assets to it.
            runAlbumSyncPass(activeConfigs, uploadedAssets)

            // Gated local deletion: delete-local is a GLOBAL per-album setting, and an asset is
            // removed ONLY once it's confirmed present on EVERY cloud that album backs up to.
            // This runs after all uploads so partial fan-out never causes data loss.
            runDeleteLocalPass()

            printDebug("CloudUploadWorker: Upload check complete")
            return if (failedItems == 0) Result.success()
            else if (shouldRetryBackupFailure(runAttemptCount)) Result.retry()
            else Result.failure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printDebug("CloudUploadWorker: Failed: ${e.message}")
            return if (shouldRetryBackupFailure(runAttemptCount)) Result.retry() else Result.failure()
        }
    }

    /**
     * Removes local copies for albums opted into delete-local, but ONLY for assets confirmed
     * present on EVERY cloud that album backs up to. If any destination provider is unavailable or
     * its coverage check fails, the album is skipped entirely so a partially-backed-up asset is
     * never deleted.
     */
    private suspend fun runDeleteLocalPass() {
        try {
            val deleteAlbums = deleteLocalPrefDao.getEnabledAlbumIds().toSet()
            if (deleteAlbums.isEmpty()) return
            val allConfigs = configDao.getAll().first()
            val uploadPrefsByConfig = allConfigs.associate { config ->
                config.id to uploadPrefDao.getEnabledByConfigList(config.id)
            }
            val albumIdsByConfig = uploadPrefsByConfig.mapValues { (_, prefs) ->
                prefs.map { it.albumId }.toSet()
            }
            for (albumId in deleteAlbums) {
                val destinationIds = backupDestinationConfigIds(albumId, albumIdsByConfig)
                val destConfigs = allConfigs.filter { it.id in destinationIds }
                if (destConfigs.isEmpty()) continue
                val destinations = destConfigs.mapNotNull { config ->
                    val provider = registry.getByConfigId(config.id) as? SyncCapableProvider
                        ?: return@mapNotNull null
                    val targetPath = uploadPrefsByConfig[config.id]
                        ?.firstOrNull { it.albumId == albumId }
                        ?.albumLabel
                        ?.trim()
                        ?.ifBlank { null }
                    provider to targetPath
                }
                if (destinations.size != destConfigs.size) {
                    printDebug("CloudUploadWorker: delete-local skipped for album $albumId — a destination is unavailable")
                    continue
                }
                val albumMedia = repository.getMediaByAlbumId(albumId, skipBatching = true).first().data ?: continue
                val mediaWithHashes = albumMedia.mapNotNull { m -> computeSha1(m)?.let { m to it } }
                if (mediaWithHashes.isEmpty()) continue
                val hashes = mediaWithHashes.map { it.second }
                val presence = destinations.map { (provider, targetPath) ->
                    try {
                        if (provider.requiresUploadChecksum) {
                            provider.bulkUploadCheck(hashes).getOrThrow()
                        } else {
                            mediaWithHashes.mapIndexed { index, (media, hash) ->
                                index.toString() to provider.verifyRemoteContent(media, targetPath, hash).getOrThrow()
                            }.toMap()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        printDebug("CloudUploadWorker: delete-local coverage check failed: ${e.message}")
                        null
                    }
                }
                if (presence.any { it == null }) continue
                mediaWithHashes.forEachIndexed { idx, (media, _) ->
                    val onAllClouds = presence.all { it!![idx.toString()] == true }
                    if (onAllClouds) {
                        currentCoroutineContext().ensureActive()
                        try {
                            applicationContext.contentResolver.delete(media.getUri(), null, null)
                            printDebug("CloudUploadWorker: Deleted local ${media.label} (backed up to ${destinations.size} clouds)")
                        } catch (e: Exception) {
                            printDebug("CloudUploadWorker: delete-local failed for ${media.label}: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printDebug("CloudUploadWorker: delete-local pass failed: ${e.message}")
        }
    }

    /**
     * Honours the per-account "Sync albums to server" option. For each account that opted in,
     * groups the assets uploaded during this run by their local album name, ensures a remote
     * album with a matching name exists (reusing one if present, otherwise creating it), and
     * adds the uploaded assets to it. Best-effort: failures are logged and never fail the run.
     */
    private suspend fun runAlbumSyncPass(
        activeConfigs: List<CloudServerConfigEntity>,
        uploaded: List<UploadedAsset>
    ) {
        if (uploaded.isEmpty()) return
        try {
            val byConfig = uploaded.groupBy { it.configId }
            for (config in activeConfigs) {
                if (!config.syncAlbums) continue
                val records = byConfig[config.id]?.takeIf { it.isNotEmpty() } ?: continue
                val provider = registry.getByConfigId(config.id) as? RemoteMediaProvider ?: continue

                val remoteAlbums = runCatching { provider.getRemoteAlbums().first().data }
                    .getOrNull().orEmpty()

                for ((albumLabel, items) in records.groupBy { it.albumLabel }) {
                    if (albumLabel.isBlank()) continue
                    val assetIds = items.map { it.remoteId }.filter { it.isNotBlank() }.distinct()
                    if (assetIds.isEmpty()) continue

                    val remoteAlbumId = remoteAlbums
                        .firstOrNull { it.name.equals(albumLabel, ignoreCase = true) }?.remoteId
                        ?: runCatching { provider.createAlbum(albumLabel).getOrNull()?.remoteId }
                            .getOrNull()
                    if (remoteAlbumId.isNullOrBlank()) {
                        printDebug("CloudUploadWorker: album-sync could not resolve album '$albumLabel' for account #${config.id}")
                        continue
                    }

                    runCatching { provider.addToAlbum(remoteAlbumId, assetIds) }
                        .onSuccess {
                            printDebug("CloudUploadWorker: album-sync added ${assetIds.size} assets to '$albumLabel' (#${config.id})")
                        }
                        .onFailure { e ->
                            printDebug("CloudUploadWorker: album-sync addToAlbum failed for '$albumLabel': ${e.message}")
                        }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printDebug("CloudUploadWorker: album-sync pass failed: ${e.message}")
        }
    }

    // === Backup notifications ===

    private fun ensureChannel(id: String, nameRes: Int, importance: Int): String {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(
                NotificationChannel(id, applicationContext.getString(nameRes), importance)
            )
        }
        return id
    }

    private fun progressForegroundInfo(
        done: Int,
        total: Int,
        currentFile: String?,
        showDetail: Boolean
    ): ForegroundInfo {
        val channelId = ensureChannel(
            CHANNEL_PROGRESS, R.string.cloud_backup_channel_progress, NotificationManager.IMPORTANCE_LOW
        )
        val text = if (showDetail && !currentFile.isNullOrBlank()) {
            currentFile
        } else {
            applicationContext.getString(R.string.cloud_backup_progress_text, done, total)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle(applicationContext.getString(R.string.cloud_backup_notification_title))
            .setContentText(text)
            .setProgress(total, done, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    private fun postFailureNotification(failed: Int, failedFiles: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val channelId = ensureChannel(
            CHANNEL_STATUS, R.string.cloud_backup_channel_status, NotificationManager.IMPORTANCE_DEFAULT
        )
        val title = applicationContext.getString(R.string.cloud_backup_failed_title)
        val text = applicationContext.getString(R.string.cloud_backup_failed_text, failed)
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        if (failedFiles.isNotEmpty()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    text + "\n" + failedFiles.takeLast(10).joinToString("\n")
                )
            )
        }
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID_STATUS, builder.build())
        }
    }

    private suspend fun executeUpload(
        task: UploadTask,
        hashCache: ConcurrentHashMap<String, String>
    ): UploadOutcome {
        return try {
            var checksum = task.checksum
            if (task.provider.remoteExists(task.media, task.targetPath)) {
                checksum = checksum ?: cachedSha1(task.media, hashCache)
                    ?: return UploadOutcome(kotlin.Result.failure(Exception("Could not read media for checksum")))
                val verified = task.provider.verifyRemoteContent(task.media, task.targetPath, checksum)
                    .getOrElse { return UploadOutcome(kotlin.Result.failure(it)) }
                if (verified) {
                    cacheVerifiedBackupRevision(
                        cloudMediaDao,
                        task.configId,
                        task.media,
                        checksum,
                        task.provider,
                        task.targetPath
                    )
                    return UploadOutcome(alreadyPresent = true)
                }
            }
            if (checksum == null && task.provider.requiresUploadChecksum) {
                checksum = cachedSha1(task.media, hashCache)
                    ?: return UploadOutcome(kotlin.Result.failure(Exception("Could not read media for checksum")))
            }
            if (task.provider.requiresUploadChecksum && task.checksum == null) {
                val requiredChecksum = checksum
                    ?: return UploadOutcome(kotlin.Result.failure(Exception("Checksum is required")))
                val alreadyPresent = task.provider.bulkUploadCheck(listOf(requiredChecksum))
                    .getOrElse { return UploadOutcome(kotlin.Result.failure(it)) }["0"] == true
                if (alreadyPresent) {
                    cacheVerifiedBackupRevision(
                        cloudMediaDao,
                        task.configId,
                        task.media,
                        requiredChecksum,
                        task.provider,
                        task.targetPath
                    )
                    return UploadOutcome(alreadyPresent = true)
                }
            }
            val result = checksum?.let {
                task.provider.uploadAsset(task.media, task.targetPath, it)
            } ?: task.provider.uploadAsset(task.media, task.targetPath)
            UploadOutcome(result = result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UploadOutcome(kotlin.Result.failure(e))
        }
    }

    private suspend fun cachedSha1(media: Media, cache: ConcurrentHashMap<String, String>): String? {
        val key = "${media.getUri()}|${media.size}|${media.timestamp}"
        cache[key]?.let { return it.takeUnless { value -> value == HASH_FAILED } }
        val computed = computeSha1(media) ?: HASH_FAILED
        return (cache.putIfAbsent(key, computed) ?: computed).takeUnless { it == HASH_FAILED }
    }

    private suspend fun computeSha1(media: Media): String? = withContext(Dispatchers.IO) {
        try {
            val uri = media.getUri()
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(HASH_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val WORK_NAME = "cloud_upload"
        const val WORK_NAME_ONCE = "cloud_upload_now"

        const val KEY_CURRENT_FILE = "current_file"
        const val KEY_CURRENT_ACCOUNT = "current_account"
        const val KEY_PHASE = "phase"
        const val KEY_TOTAL_ITEMS = "total_items"
        const val KEY_CHECKED_ITEMS = "checked_items"
        const val KEY_COMPLETED_ITEMS = "completed_items"
        const val KEY_FAILED_ITEMS = "failed_items"
        const val KEY_COMPLETED_FILES = "completed_files"
        const val KEY_FAILED_FILES = "failed_files"
        const val KEY_MANUAL = "manual"
        const val KEY_CONFIG_ID = "config_id"
        /** Common tag for per-account manual backups; the per-account tag is "$TAG_ACCOUNT_BACKUP:<configId>". */
        const val TAG_ACCOUNT_BACKUP = "cloud_upload_account"
        /**
         * Tag applied to EVERY backup run (periodic, "back up all", and per-account manual),
         * so UI progress observers and the media distributor can track/react to any upload
         * regardless of its unique work name.
         */
        const val TAG_BACKUP = "cloud_upload_backup"
        const val TAG_PERIODIC_BACKUP = "cloud_upload_periodic"
        const val TAG_MANUAL_BACKUP = "cloud_upload_manual"
        const val PHASE_VERIFYING = "verifying"
        const val PHASE_UPLOADING = "uploading"
        const val PHASE_COMPLETE = "complete"
        private const val MAX_TRACKED_FILES = 50
        private const val HASH_BUFFER_SIZE = 64 * 1024
        private const val HASH_FAILED = ""
        private const val BULK_CHECK_SIZE = 50

        // Backup notification channels + ids.
        private const val CHANNEL_PROGRESS = "cloud_backup_progress"
        private const val CHANNEL_STATUS = "cloud_backup_status"
        private const val NOTIFICATION_ID_PROGRESS = 91001
        private const val NOTIFICATION_ID_STATUS = 91002
        private val accountUploadMutexes = ConcurrentHashMap<Long, Mutex>()

        fun schedule(
            workManager: WorkManager,
            intervalMinutes: Long = 30,
            wifiOnly: Boolean = true
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val request = PeriodicWorkRequestBuilder<CloudUploadWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(TAG_BACKUP)
                .addTag(TAG_PERIODIC_BACKUP)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * Runs a manual backup immediately. When [configId] is > 0 the run is
         * scoped to a single account and enqueued under its own unique work name
         * (and tagged so the UI can track that account's progress independently);
         * otherwise every active account is backed up.
         */
        fun triggerNow(workManager: WorkManager, configId: Long = -1L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val builder = OneTimeWorkRequestBuilder<CloudUploadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_MANUAL to true, KEY_CONFIG_ID to configId))
                .addTag(TAG_BACKUP)
                .addTag(TAG_MANUAL_BACKUP)
            val uniqueName = if (configId > 0L) "${WORK_NAME_ONCE}_$configId" else WORK_NAME_ONCE
            if (configId > 0L) {
                builder.addTag(TAG_ACCOUNT_BACKUP)
                builder.addTag("$TAG_ACCOUNT_BACKUP:$configId")
            }
            workManager.enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.REPLACE,
                builder.build()
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}

internal const val MAX_BACKUP_RUN_ATTEMPTS = 3

internal fun shouldRetryBackupFailure(runAttemptCount: Int): Boolean =
    runAttemptCount < MAX_BACKUP_RUN_ATTEMPTS - 1

internal fun isActiveBackupWork(state: WorkInfo.State, tags: Set<String>): Boolean =
    state == WorkInfo.State.RUNNING ||
        state == WorkInfo.State.ENQUEUED && CloudUploadWorker.TAG_MANUAL_BACKUP in tags

internal fun backupDestinationConfigIds(
    albumId: Long,
    albumIdsByConfig: Map<Long, Set<Long>>
): Set<Long> = albumIdsByConfig.filterValues { albumId in it }.keys

internal const val BACKUP_VERIFICATION_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

internal fun backupVerificationCutoff(now: Long = System.currentTimeMillis()): Long =
    now - BACKUP_VERIFICATION_MAX_AGE_MS

internal fun backupRevisionLocalUri(uri: String, deterministicRemoteId: String?): String =
    deterministicRemoteId?.let { "$uri\u001f$it" } ?: uri

internal fun backupLocalRevisionKey(uri: String, size: Long, timestamp: Long): String =
    "$uri|$size|$timestamp"

internal suspend fun cacheVerifiedBackupRevision(
    cloudMediaDao: CloudMediaDao,
    configId: Long,
    media: Media,
    hash: String,
    provider: SyncCapableProvider? = null,
    targetPath: String? = null
): Boolean {
    val deterministicRemoteId = provider?.deterministicRemoteId(media, targetPath)
    val remoteId = deterministicRemoteId ?: provider?.verifiedRemoteId(hash)
    val remote = if (remoteId != null && provider != null) {
        cloudMediaDao.getByRemoteId(remoteId, provider.providerType, configId)
            ?: CloudMediaEntity(
                remoteId = remoteId,
                providerType = provider.providerType,
                serverConfigId = configId,
                contentHash = hash
            )
    } else {
        cloudMediaDao.getByContentHashes(backupChecksumVariants(hash), configId)
    } ?: return false
    cacheSuccessfulBackupRevision(cloudMediaDao, configId, media, remote, deterministicRemoteId)
    return true
}

internal suspend fun cacheSuccessfulBackupRevision(
    cloudMediaDao: CloudMediaDao,
    configId: Long,
    media: Media,
    remote: CloudMediaEntity,
    deterministicRemoteId: String? = null
) {
    cloudMediaDao.upsertBackupRevision(
        CloudBackupRevisionEntity(
            serverConfigId = configId,
            providerType = remote.providerType,
            localUri = backupRevisionLocalUri(media.getUri().toString(), deterministicRemoteId),
            localSize = media.size,
            localTimestamp = media.timestamp,
            remoteId = remote.remoteId,
            remoteFingerprint = remote.backupFingerprint(),
            verifiedAt = System.currentTimeMillis()
        )
    )
}

internal fun backupChecksumVariants(hash: String): List<String> {
    val normalized = hash.lowercase()
    if (normalized.length != 40 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' }) {
        return listOf(hash)
    }
    val bytes = ByteArray(normalized.length / 2) { index ->
        normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
    return listOf(hash, Base64.getEncoder().encodeToString(bytes)).distinct()
}

internal fun backupRemoteRevisionKey(
    mediaId: String,
    label: String,
    mimeType: String,
    size: Long,
    timestamp: Long
): String = "$mediaId|$label|$mimeType|$size|$timestamp"

internal fun isBackupRevisionCached(
    uri: String,
    mediaId: Long,
    label: String,
    mimeType: String,
    size: Long,
    timestamp: Long,
    localRevisions: Set<String>,
    remoteRevisions: Set<String>
): Boolean = backupLocalRevisionKey(uri, size, timestamp) in localRevisions ||
    backupRemoteRevisionKey(mediaId.toString(), label, mimeType, size, timestamp) in remoteRevisions

internal fun shouldDeferChecksumCheck(itemCount: Int, maxConcurrentUploads: Int): Boolean =
    itemCount <= maxConcurrentUploads.coerceAtLeast(1) * 2

internal suspend fun <T> runWorkerPool(
    items: List<T>,
    maxConcurrency: Int,
    processItem: suspend (T) -> Unit
) = coroutineScope {
    if (items.isEmpty()) return@coroutineScope
    val nextIndex = AtomicInteger(0)
    List(min(maxConcurrency.coerceAtLeast(1), items.size)) {
        launch {
            while (true) {
                coroutineContext.ensureActive()
                val index = nextIndex.getAndIncrement()
                if (index >= items.size) break
                processItem(items[index])
            }
        }
    }.joinAll()
}

internal suspend fun <T, R> mapWorkerPool(
    items: List<T>,
    maxConcurrency: Int,
    transform: suspend (T) -> R
): List<R> {
    val results = arrayOfNulls<Any?>(items.size)
    runWorkerPool(items.indices.toList(), maxConcurrency) { index ->
        results[index] = transform(items[index])
    }
    @Suppress("UNCHECKED_CAST")
    return results.map { it as R }
}
