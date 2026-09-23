/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.content.Context
import android.net.ConnectivityManager
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudBackupRevisionEntity
import com.dot.gallery.cloud.data.entity.backupFingerprint
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Automatic remote -> local download. For every account that enabled
 * `downloadRemoteEnabled`, pulls `REMOTE_ONLY` cloud rows through the provider's
 * `downloadAsset`, writes them into MediaStore via [CloudMediaStoreWriter], and marks
 * the row `SYNCED` with its new `localCopyPath`.
 *
 * Loop prevention: `CloudUploadWorker` skips every local media whose MediaStore id is
 * recorded in `cloud_media.localCopyPath`, so a downloaded copy inside a backup-enabled
 * album is never re-uploaded. The `cloud_backup_revision` row additionally documents
 * the local <-> remote binding.
 */
@HiltWorker
class CloudDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val registry: ProviderRegistry,
    private val configDao: CloudServerConfigDao,
    private val cloudMediaDao: CloudMediaDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        printDebug("CloudDownloadWorker: Starting remote download pass...")
        return try {
            val isManual = inputData.getBoolean(KEY_MANUAL, false)
            val targetConfigId = inputData.getLong(KEY_CONFIG_ID, -1L)
            val isMetered = runCatching {
                (applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                        as? ConnectivityManager)?.isActiveNetworkMetered ?: false
            }.getOrDefault(false)

            // Manual runs ("Download all") apply to every active account — the user
            // explicitly asked; the periodic flag only gates scheduled passes.
            val configs = configDao.getAll().first().filter {
                it.isActive && (it.downloadRemoteEnabled || isManual) &&
                    (isManual || it.syncEnabled) &&
                    (targetConfigId <= 0L || it.id == targetConfigId)
            }
            if (configs.isEmpty()) {
                printDebug("CloudDownloadWorker: No download-enabled configs")
                return Result.success()
            }

            var downloaded = 0
            var failed = 0

            for (config in configs) {
                if (isStopped) break
                if (!isManual && isMetered && !CloudCellularPolicy.allowsSync(config)) continue
                val provider = registry.getByConfigId(config.id) as? RemoteMediaProvider ?: continue
                if (!provider.isAvailable) continue
                val syncProvider = provider as? SyncCapableProvider ?: continue
                val accountLabel = config.displayName.ifBlank { config.providerType.displayName }

                val pending = cloudMediaDao.getBySyncStateForConfig(config.id, SyncState.REMOTE_ONLY)
                    .asSequence()
                    .filter { config.downloadVideos || !it.mimeType.startsWith("video/") }
                    .take(MAX_ITEMS_PER_RUN)
                    .toList()
                if (pending.isEmpty()) continue

                printDebug("CloudDownloadWorker: ${pending.size} items to download for $accountLabel")
                var processed = 0
                for (entity in pending) {
                    if (isStopped) break
                    processed++
                    setProgress(workDataOf(
                        KEY_TOTAL_ITEMS to pending.size,
                        KEY_COMPLETED_ITEMS to processed - 1,
                        KEY_CURRENT_FILE to entity.label,
                        KEY_CURRENT_ACCOUNT to accountLabel
                    ))
                    cloudMediaDao.updateSyncState(
                        entity.remoteId, entity.providerType, config.id, SyncState.DOWNLOADING
                    )
                    val cacheUri = try {
                        syncProvider.downloadAsset(entity.remoteId).getOrNull()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (cacheUri == null) {
                        cloudMediaDao.updateSyncState(
                            entity.remoteId, entity.providerType, config.id, SyncState.REMOTE_ONLY
                        )
                        failed++
                        continue
                    }
                    // Mirror the remote folder layout under Pictures/ or Movies/ so a
                    // downloaded album lands in an identically-named local album; Immich
                    // has no paths, so its files collect under the provider name.
                    val subPath = entity.relativePath.trim('/')
                        .ifBlank { config.providerType.displayName }
                    val localUri = CloudMediaStoreWriter.write(
                        context = applicationContext,
                        source = cacheUri,
                        request = CloudMediaStoreWriter.Request(
                            displayName = entity.label.ifBlank {
                                entity.remoteId.substringAfterLast('/')
                            },
                            mimeType = entity.mimeType,
                            relativeSubPath = subPath,
                            takenTimestamp = entity.takenTimestamp
                        )
                    )
                    CloudMediaStoreWriter.deleteSource(applicationContext, cacheUri)
                    if (localUri == null) {
                        cloudMediaDao.updateSyncState(
                            entity.remoteId, entity.providerType, config.id, SyncState.REMOTE_ONLY
                        )
                        failed++
                        continue
                    }
                    cloudMediaDao.updateLocalCopy(
                        entity.remoteId, entity.providerType, config.id,
                        localUri.toString(), SyncState.SYNCED
                    )
                    // Loop prevention marker: binds the fresh local file to the remote id.
                    // CloudUploadWorker primarily skips via localCopyPath, but the revision
                    // row keeps UploadDetails/backup-evidence queries consistent.
                    cloudMediaDao.upsertBackupRevision(
                        CloudBackupRevisionEntity(
                            serverConfigId = config.id,
                            providerType = entity.providerType,
                            localUri = localUri.toString(),
                            localSize = entity.size,
                            localTimestamp = entity.timestamp / 1000L,
                            remoteId = entity.remoteId,
                            remoteFingerprint = entity.backupFingerprint(),
                            verifiedAt = System.currentTimeMillis()
                        )
                    )
                    downloaded++
                }
                setProgress(workDataOf(
                    KEY_TOTAL_ITEMS to pending.size,
                    KEY_COMPLETED_ITEMS to processed,
                    KEY_CURRENT_FILE to "",
                    KEY_CURRENT_ACCOUNT to accountLabel
                ))
            }

            printDebug("CloudDownloadWorker: Done — $downloaded downloaded, $failed failed")
            if (isStopped) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printDebug("CloudDownloadWorker: Failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "cloud_download"
        private const val WORK_NAME_ONCE = "cloud_download_now"
        private const val WORK_NAME_APP_OPEN = "cloud_download_app_open"

        const val KEY_MANUAL = "manual"
        const val KEY_CONFIG_ID = "config_id"
        const val KEY_TOTAL_ITEMS = "total_items"
        const val KEY_COMPLETED_ITEMS = "completed_items"
        const val KEY_CURRENT_FILE = "current_file"
        const val KEY_CURRENT_ACCOUNT = "current_account"

        /**
         * Cap on a single run's downloads per account. The periodic worker simply picks
         * the remainder up on the next pass; bounding one run keeps a huge first
         * backfill from pinning the scheduler slot for hours.
         */
        private const val MAX_ITEMS_PER_RUN = 500

        fun schedule(workManager: WorkManager, intervalMinutes: Long, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()
            val request = PeriodicWorkRequestBuilder<CloudDownloadWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        /** Runs a manual "Download all" for one account ([configId] <= 0 means every enabled account). */
        fun triggerNow(workManager: WorkManager, configId: Long = -1L) {
            val request = OneTimeWorkRequestBuilder<CloudDownloadWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(workDataOf(KEY_MANUAL to true, KEY_CONFIG_ID to configId))
                .build()
            val uniqueName = if (configId > 0L) "${WORK_NAME_ONCE}_$configId" else WORK_NAME_ONCE
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * One download pass on app open (#1241). Non-manual: the per-account
         * `downloadRemoteEnabled`/`syncEnabled` gating and the metered-network
         * check apply exactly like the periodic worker.
         */
        fun triggerOnAppOpen(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<CloudDownloadWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            workManager.enqueueUniqueWork(
                WORK_NAME_APP_OPEN,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
