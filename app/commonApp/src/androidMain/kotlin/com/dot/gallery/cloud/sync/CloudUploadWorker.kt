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
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.BackoffPolicy
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.QueriedTask
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import com.dot.gallery.cloud.core.UploadTargetResolver
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumWriteProvider
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.data.dao.CloudDeleteLocalPrefDao
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.data.dao.CloudUploadPrefDao
import com.dot.gallery.data.entity.CloudBackupRevisionEntity
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.domain.util.getUri
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
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.presentation.util.update
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.data.entity.backupFingerprint
import com.dot.gallery.R
import me.tatarka.inject.annotations.Inject
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_backup_channel_status
import com.dot.gallery.generated.resources.cloud_backup_failed_text
import com.dot.gallery.generated.resources.cloud_backup_failed_title
import com.dot.gallery.core.util.getString
import org.jetbrains.compose.resources.StringResource


class CloudUploadWorker @Inject constructor(
    context: Context,
    private val registry: ProviderRegistry,
    private val configDao: CloudServerConfigDao,
    private val uploadPrefDao: CloudUploadPrefDao,
    private val deleteLocalPrefDao: CloudDeleteLocalPrefDao,
    private val cloudMediaDao: CloudMediaDao,
    private val repository: MediaRepository
) : GalleryWorker {

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

    private data class AlbumSyncRecord(
        val albumLabel: String,
        val remoteId: String
    )

    private data class UploadOutcome(
        val result: kotlin.Result<CloudMediaEntity>? = null,
        val alreadyPresent: Boolean = false
    )

    private val applicationContext: Context = context

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult {
        printDebug("CloudUploadWorker: Starting upload check...")
        val isManual = input.getBoolean(KEY_MANUAL, false)
        try {
            // Manual ("Upload now" / "Start backup") runs are user-initiated and must work even
            // when periodic auto-sync (syncEnabled) is off. syncEnabled only governs the background
            // scheduler, so only require it for the periodic worker, not for a manual trigger.
            val targetConfigId = input.getLong(KEY_CONFIG_ID, -1L)
            val configs = configDao.getAll().first()
            val activeConfigs = configs.filter {
                it.isActive && (isManual || it.syncEnabled) &&
                    (targetConfigId <= 0L || it.id == targetConfigId)
            }
            if (activeConfigs.isEmpty()) {
                printDebug("CloudUploadWorker: No active server config (manual=$isManual, target=$targetConfigId)")
                return WorkerResult.Success()
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
                // Loop prevention: files this app itself downloaded from the account are
                // recorded in cloud_media.localCopyPath. If such a copy sits inside a
                // backup-enabled album it must never be re-uploaded — the MediaStore row
                // id is the stable key (URI strings differ between insert and query).
                val downloadedCopyIds = cloudMediaDao.getLocalCopyStatesForConfig(config.id)
                    .mapNotNull { localCopyMediaStoreId(it.localCopyPath) }
                    .toSet()

                for (pref in enabledPrefs) {
                    // Path-based stores (WebDAV/ownCloud/Nextcloud/SMB/NFS) place each
                    // backed-up local album under the account's configured upload folder
                    // (or the album's own custom path), instead of flattening everything
                    // into a single "Photos" dir. Content-addressable stores (Immich)
                    // ignore this. A fully-blank target falls back to the provider's
                    // default upload folder (null).
                    fun uploadTarget(media: Media) = UploadTargetResolver.resolve(config, pref, media)
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
                        media.id in downloadedCopyIds || isBackupRevisionCached(
                            uri = backupRevisionLocalUri(
                                media.getUri().toString(),
                                syncProvider.deterministicRemoteId(media, uploadTarget(media))
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
                                targetPath = uploadTarget(media)
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
                    reportProgress(workData(
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
                                                uploadTarget(media)
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
                        reportProgress(workData(
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
            // In-progress display comes from kmpworkmanager's heavy-task notification;
            // the per-account detail flags only governed the custom foreground notification.
            val notifyFailures = activeConfigs.any { it.notifyBackupFailures }

            reportProgress(workData(
                KEY_PHASE to PHASE_UPLOADING,
                KEY_TOTAL_ITEMS to totalItems,
                KEY_CHECKED_ITEMS to totalItems,
                KEY_COMPLETED_ITEMS to 0,
                KEY_FAILED_ITEMS to 0,
                KEY_CURRENT_FILE to ""
            ))

            // Progress notification comes from kmpworkmanager's generic heavy-task
            // notification (isHeavyTask at schedule time); detail updates ride WorkSignals.

            val progressMutex = Mutex()
            val progressThrottle = BackupProgressThrottle(SystemClock::elapsedRealtime)
            coroutineScope {
                tasks.groupBy { it.provider }.values.map { providerTasks ->
                    launch {
                        val configId = providerTasks.first().configId
                        accountUploadMutexes.computeIfAbsent(configId) { Mutex() }.withLock {
                            runWorkerPool(
                                items = providerTasks,
                                maxConcurrency = providerTasks.first().provider.maxConcurrentUploads
                            ) { task ->
                                if (env.isCancelled()) return@runWorkerPool
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
                                    if (progressThrottle.shouldPublish(force = completedItems + failedItems >= totalItems)) {
                                        reportProgress(workData(
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
                                    }
                                }
                            }
                        }
                    }
                }.joinAll()
            }
            if (env.isCancelled()) return WorkerResult.Retry("stopped mid-run", attemptCap = MAX_BACKUP_RUN_ATTEMPTS)

            reportProgress(workData(
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

            // "Sync albums to server": reconcile enabled local albums with their server albums.
            runAlbumSyncPass(activeConfigs, uploadedAssets)

            // Gated local deletion: delete-local is a GLOBAL per-album setting, and an asset is
            // removed ONLY once it's confirmed present on EVERY cloud that album backs up to.
            // This runs after all uploads so partial fan-out never causes data loss.
            runDeleteLocalPass()

            printDebug("CloudUploadWorker: Upload check complete")
            return if (failedItems == 0) WorkerResult.Success()
            else WorkerResult.Retry(
                reason = "$failedItems of $totalItems uploads failed",
                attemptCap = MAX_BACKUP_RUN_ATTEMPTS
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printDebug("CloudUploadWorker: Failed: ${e.message}")
            return WorkerResult.Retry(
                reason = "upload pass failed: ${e.message}",
                attemptCap = MAX_BACKUP_RUN_ATTEMPTS
            )
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
                    val pref = uploadPrefsByConfig[config.id]
                        ?.firstOrNull { it.albumId == albumId }
                    Triple(provider, config, pref)
                }
                if (destinations.size != destConfigs.size) {
                    printDebug("CloudUploadWorker: delete-local skipped for album $albumId — a destination is unavailable")
                    continue
                }
                val albumMedia = repository.getMediaByAlbumId(albumId, skipBatching = true).first().data ?: continue
                val mediaWithHashes = albumMedia.mapNotNull { m -> computeSha1(m)?.let { m to it } }
                if (mediaWithHashes.isEmpty()) continue
                val hashes = mediaWithHashes.map { it.second }
                val presence = destinations.map { (provider, config, pref) ->
                    try {
                        if (provider.requiresUploadChecksum) {
                            provider.bulkUploadCheck(hashes).getOrThrow()
                        } else {
                            mediaWithHashes.mapIndexed { index, (media, hash) ->
                                val targetPath = pref?.let { UploadTargetResolver.resolve(config, it, media) }
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
     * Honours the per-account "Sync albums to server" option. The local album selection is the
     * source of truth: already-backed-up media is included via the backup revision cache, so a
     * local album move is reflected remotely on the next run. Best-effort: failures are logged
     * and never fail the run.
     */
    private suspend fun runAlbumSyncPass(
        activeConfigs: List<CloudServerConfigEntity>,
        uploaded: List<UploadedAsset>
    ) {
        try {
            for (config in activeConfigs) {
                if (!config.syncAlbums) continue
                val provider = registry.getByConfigId(config.id) as? RemoteMediaProvider ?: continue
                val syncProvider = registry.getByConfigId(config.id) as? SyncCapableProvider
                val enabledPrefs = uploadPrefDao.getEnabledByConfigList(config.id)
                if (enabledPrefs.isEmpty()) continue

                val revisions = runCatching {
                    cloudMediaDao.getBackupRevisions(config.id)
                }.onFailure {
                    printDebug("CloudUploadWorker: album-sync revision lookup failed for account #${config.id}: ${it.message}")
                }.getOrNull() ?: continue
                val records = uploaded.filter { it.configId == config.id }
                    .map { AlbumSyncRecord(it.albumLabel, it.remoteId) }
                    .toMutableList()

                for (pref in enabledPrefs) {
                    if (pref.albumLabel.isBlank()) continue
                    val media = runCatching {
                        repository.getMediaByAlbumId(pref.albumId, skipBatching = true).first().data
                    }.onFailure {
                        printDebug("CloudUploadWorker: album-sync media lookup failed for '${pref.albumLabel}': ${it.message}")
                    }.getOrNull().orEmpty()
                    media.forEach { item ->
                        val deterministicId = syncProvider?.deterministicRemoteId(
                            item,
                            UploadTargetResolver.resolve(config, pref, item)
                        )
                        val revision = revisions.firstOrNull {
                            it.localUri == backupRevisionLocalUri(item.getUri().toString(), deterministicId)
                        } ?: revisions.firstOrNull { it.localUri == item.getUri().toString() }
                        if (revision != null && revision.remoteId.isNotBlank()) {
                            records += AlbumSyncRecord(pref.albumLabel, revision.remoteId)
                        }
                    }
                }

                val remoteAlbumsResource = runCatching { provider.getRemoteAlbums().first() }
                    .onFailure {
                        printDebug("CloudUploadWorker: album-sync listing failed for account #${config.id}: ${it.message}")
                    }.getOrNull() ?: continue
                val remoteAlbums = remoteAlbumsResource.data ?: run {
                    printDebug("CloudUploadWorker: album-sync listing failed for account #${config.id}")
                    continue
                }
                val desiredByAlbum = records
                    .filter { it.albumLabel.isNotBlank() && it.remoteId.isNotBlank() }
                    .groupBy { it.albumLabel.lowercase() }
                    .mapValues { (_, items) -> items.map { it.remoteId }.toSet() }
                val knownAssetIds = records.map { it.remoteId }.filter { it.isNotBlank() }.toSet()
                val writableProvider = provider as? RemoteAlbumWriteProvider

                for (albumLabel in enabledPrefs.map { it.albumLabel }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }) {
                    val desiredAssetIds = desiredByAlbum[albumLabel.lowercase()].orEmpty()

                    val remoteAlbumId = remoteAlbums
                        .firstOrNull { it.name.equals(albumLabel, ignoreCase = true) }?.remoteId
                        ?: if (desiredAssetIds.isNotEmpty()) runCatching {
                            provider.createAlbum(albumLabel)
                                .onFailure {
                                    printWarn(
                                        "worker.cloud-upload",
                                        "createAlbum failed for '$albumLabel': $it"
                                    )
                                }
                                .getOrNull()?.remoteId
                        }.onFailure {
                            printWarn(
                                "worker.cloud-upload",
                                "createAlbum failed for '$albumLabel': $it"
                            )
                        }.getOrNull() else null
                    if (remoteAlbumId.isNullOrBlank()) {
                        if (desiredAssetIds.isNotEmpty()) {
                            printDebug("CloudUploadWorker: album-sync could not resolve album '$albumLabel' for account #${config.id}")
                        }
                        continue
                    }

                    val remoteMembershipResource = runCatching {
                        provider.getRemoteAlbumMedia(remoteAlbumId).first()
                    }.onFailure {
                        printDebug("CloudUploadWorker: album-sync membership lookup failed for '$albumLabel': ${it.message}")
                    }.getOrNull() ?: continue
                    val remoteAssetIds = remoteMembershipResource.data ?: run {
                        printDebug("CloudUploadWorker: album-sync membership lookup failed for '$albumLabel'")
                        continue
                    }
                    val currentAssetIds = remoteAssetIds.map { it.remoteId }.filter { it.isNotBlank() }.toSet()
                    val diff = albumMembershipDiff(desiredAssetIds, currentAssetIds, knownAssetIds)

                    if (diff.additions.isNotEmpty()) {
                        runCatching { provider.addToAlbum(remoteAlbumId, diff.additions.toList()) }
                            .onSuccess { result ->
                                result.onSuccess {
                                    printDebug("CloudUploadWorker: album-sync added ${diff.additions.size} assets to '$albumLabel' (#${config.id})")
                                }
                                result.onFailure { e ->
                                    printWarn(
                                        "worker.cloud-upload",
                                        "album-sync addToAlbum discarded items for '$albumLabel': ${e.message}"
                                    )
                                }
                            }
                            .onFailure { e ->
                                printDebug("CloudUploadWorker: album-sync addToAlbum failed for '$albumLabel': ${e.message}")
                            }
                    }
                    if (writableProvider != null && diff.removals.isNotEmpty()) {
                        runCatching { writableProvider.removeFromAlbum(remoteAlbumId, diff.removals.toList()) }
                            .onSuccess { result ->
                                result.onFailure { e ->
                                    printWarn("worker.cloud-upload", "album-sync removeFromAlbum discarded items for '$albumLabel': ${e.message}")
                                }
                            }
                            .onFailure { e ->
                                printDebug("CloudUploadWorker: album-sync removeFromAlbum failed for '$albumLabel': ${e.message}")
                            }
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

    private fun reportProgress(data: kotlinx.serialization.json.JsonObject) {
        WorkSignals.reportProgress(PROGRESS_WORK_NAME, data)
    }

    private fun ensureChannel(id: String, nameRes: StringResource, importance: Int): String {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(
                NotificationChannel(id, applicationContext.getString(nameRes), importance)
            )
        }
        return id
    }

    private fun postFailureNotification(failed: Int, failedFiles: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val channelId = ensureChannel(
            CHANNEL_STATUS, Res.string.cloud_backup_channel_status, NotificationManager.IMPORTANCE_DEFAULT
        )
        val title = applicationContext.getString(Res.string.cloud_backup_failed_title)
        val text = applicationContext.getString(Res.string.cloud_backup_failed_text, failed)
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
            printWarn("worker.cloud-upload", "sha1 digest failed: $e")
            null
        }
    }

    companion object {
        const val WORKER_NAME = "CloudUploadWorker"
        /** WorkSignals key for backup progress payloads (shared across all unique names). */
        const val PROGRESS_WORK_NAME = WORKER_NAME
        private const val WORK_NAME = "cloud_upload"
        const val WORK_NAME_ONCE = "cloud_upload_now"
        private const val WORK_NAME_APP_OPEN = "cloud_upload_app_open"

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

        suspend fun schedule(
            scheduler: BackgroundTaskScheduler,
            intervalMinutes: Long = 30,
            wifiOnly: Boolean = true
        ) {
            scheduler.enqueue(
                id = WORK_NAME,
                trigger = TaskTrigger.Periodic(
                    intervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes)
                ),
                workerClassName = WORKER_NAME,
                constraints = Constraints(
                    requiresNetwork = true,
                    requiresUnmeteredNetwork = wifiOnly,
                    isHeavyTask = true,
                    backoffPolicy = BackoffPolicy.EXPONENTIAL,
                    backoffDelayMs = TimeUnit.MINUTES.toMillis(5),
                    maxRetries = MAX_BACKUP_RUN_ATTEMPTS - 1,
                ),
                policy = ExistingPolicy.UPDATE,
                tags = setOf(TAG_BACKUP, TAG_PERIODIC_BACKUP),
            )
        }

        /**
         * Runs a manual backup immediately. When [configId] is > 0 the run is
         * scoped to a single account and enqueued under its own unique work name
         * (and tagged so the UI can track that account's progress independently);
         * otherwise every active account is backed up.
         */
        suspend fun triggerNow(scheduler: BackgroundTaskScheduler, configId: Long = -1L) {
            val uniqueName = if (configId > 0L) "${WORK_NAME_ONCE}_$configId" else WORK_NAME_ONCE
            val tags = buildSet {
                add(TAG_BACKUP)
                add(TAG_MANUAL_BACKUP)
                if (configId > 0L) {
                    add(TAG_ACCOUNT_BACKUP)
                    add("$TAG_ACCOUNT_BACKUP:$configId")
                }
            }
            scheduler.enqueue(
                id = uniqueName,
                trigger = TaskTrigger.OneTime(),
                workerClassName = WORKER_NAME,
                constraints = Constraints(requiresNetwork = true, isHeavyTask = true),
                inputJson = workDataJson(KEY_MANUAL to true, KEY_CONFIG_ID to configId),
                policy = ExistingPolicy.REPLACE,
                tags = tags,
            )
        }

        /**
         * One upload pass on app open (#1241). Not a manual run: [KEY_MANUAL] is
         * left unset so the pass honours per-account sync toggles, the charging
         * requirement, and the cellular-upload policy exactly like the periodic
         * worker — it just scans enabled albums for new/changed media right away.
         */
        suspend fun triggerOnAppOpen(scheduler: BackgroundTaskScheduler) {
            scheduler.enqueue(
                id = WORK_NAME_APP_OPEN,
                trigger = TaskTrigger.OneTime(),
                workerClassName = WORKER_NAME,
                constraints = Constraints(requiresNetwork = true, isHeavyTask = true),
                policy = ExistingPolicy.KEEP,
                tags = setOf(TAG_BACKUP),
            )
        }

        fun cancel(scheduler: BackgroundTaskScheduler) {
            scheduler.cancel(WORK_NAME)
        }
    }
}

@Worker(name = CloudUploadWorker.WORKER_NAME)
class CloudUploadWorkerAdapter : GalleryWorkerAdapter(CloudUploadWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.cloudUploadWorker.doWork(input, env)
}

internal const val MAX_BACKUP_RUN_ATTEMPTS = 3

internal fun shouldRetryBackupFailure(runAttemptCount: Int): Boolean =
    runAttemptCount < MAX_BACKUP_RUN_ATTEMPTS - 1

/**
 * Whether a queried backup task counts as "active" for UI purposes. kmpworkmanager's
 * `QueriedTask` carries no tags, so the manual-vs-periodic distinction moves to the task
 * id — manual runs are enqueued as `WORK_NAME_ONCE`/`WORK_NAME_ONCE_<configId>`.
 */
internal fun isActiveBackupWork(task: QueriedTask): Boolean =
    task.state is TaskState.Running ||
        (task.state is TaskState.Enqueued &&
            (task.id == CloudUploadWorker.WORK_NAME_ONCE ||
                task.id.startsWith("${CloudUploadWorker.WORK_NAME_ONCE}_")))

internal class BackupProgressThrottle(
    private val nowMs: () -> Long,
    private val intervalMs: Long = 500L
) {
    private var lastPublishedAt: Long? = null

    fun shouldPublish(force: Boolean = false): Boolean {
        val now = nowMs()
        val previous = lastPublishedAt
        if (!force && previous != null && now - previous < intervalMs) return false
        lastPublishedAt = now
        return true
    }
}

internal data class AlbumMembershipDiff(
    val additions: Set<String>,
    val removals: Set<String>
)

internal fun albumMembershipDiff(
    desiredAssetIds: Set<String>,
    currentAssetIds: Set<String>,
    scopedAssetIds: Set<String>
): AlbumMembershipDiff = AlbumMembershipDiff(
    additions = desiredAssetIds - currentAssetIds,
    removals = (currentAssetIds intersect scopedAssetIds) - desiredAssetIds
)

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
