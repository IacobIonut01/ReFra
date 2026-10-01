/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
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
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.R
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudBackupRevisionEntity
import com.dot.gallery.cloud.data.entity.backupFingerprint
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarn
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
            var linked = 0
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

                // Storage preflight: bail when the pending set can't fit with headroom —
                // downloaded copies land on the primary external volume.
                var freeBytes = runCatching {
                    applicationContext.getExternalFilesDir(null)?.usableSpace
                }.getOrNull() ?: Long.MAX_VALUE
                var lowStorageHit = false

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

                    // Reconcile first: when identical content already exists locally
                    // (e.g. the library was uploaded by another app before ReFra saw
                    // it), link the row instead of downloading a duplicate.
                    val linkedUri = findVerifiedLocalCopy(applicationContext, entity)
                    if (linkedUri != null) {
                        cloudMediaDao.updateLocalCopy(
                            entity.remoteId, entity.providerType, config.id,
                            linkedUri.toString(), SyncState.SYNCED, appOwned = false
                        )
                        cloudMediaDao.upsertBackupRevision(
                            CloudBackupRevisionEntity(
                                serverConfigId = config.id,
                                providerType = entity.providerType,
                                localUri = linkedUri.toString(),
                                localSize = entity.size,
                                localTimestamp = entity.timestamp / 1000L,
                                remoteId = entity.remoteId,
                                remoteFingerprint = entity.backupFingerprint(),
                                verifiedAt = System.currentTimeMillis()
                            )
                        )
                        linked++
                        continue
                    }

                    if (entity.size > 0 && entity.size > freeBytes - DOWNLOAD_FREE_HEADROOM) {
                        lowStorageHit = true
                        continue
                    }
                    cloudMediaDao.updateSyncState(
                        entity.remoteId, entity.providerType, config.id, SyncState.DOWNLOADING
                    )
                    val cacheUri = try {
                        syncProvider.downloadAsset(entity.remoteId)
                            .onFailure {
                                printWarn(
                                    "worker.cloud-download",
                                    "asset download failed: $it",
                                    ctx = mapOf("asset" to entity.remoteId)
                                )
                            }
                            .getOrNull()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        printWarn(
                            "worker.cloud-download",
                            "asset download failed: $e",
                            ctx = mapOf("asset" to entity.remoteId)
                        )
                        null
                    }
                    if (cacheUri == null) {
                        cloudMediaDao.updateSyncState(
                            entity.remoteId, entity.providerType, config.id, SyncState.REMOTE_ONLY
                        )
                        failed++
                        continue
                    }
                    // The provider decides the destination: path-based stores mirror
                    // their real remote folders; Immich maps to `<account>/<album>`
                    // because its server-side paths are internal storage detail.
                    val subPath = runCatching {
                        syncProvider.downloadSubPath(entity, accountLabel)
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: accountLabel
                    val localUri = CloudMediaStoreWriter.write(
                        context = applicationContext,
                        source = cacheUri,
                        request = CloudMediaStoreWriter.Request(
                            displayName = entity.label.ifBlank {
                                entity.remoteId.substringAfterLast('/')
                            },
                            mimeType = entity.mimeType,
                            relativeSubPath = subPath,
                            fallbackSubPath = accountLabel,
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
                    freeBytes -= entity.size
                    cloudMediaDao.updateLocalCopy(
                        entity.remoteId, entity.providerType, config.id,
                        localUri.toString(), SyncState.SYNCED, appOwned = true
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
                if (lowStorageHit) {
                    postLowStorageNotification(config.id, accountLabel)
                }
                setProgress(workDataOf(
                    KEY_TOTAL_ITEMS to pending.size,
                    KEY_COMPLETED_ITEMS to processed,
                    KEY_CURRENT_FILE to "",
                    KEY_CURRENT_ACCOUNT to accountLabel
                ))
            }

            printDebug("CloudDownloadWorker: Done — $downloaded downloaded, $linked linked, $failed failed")
            if (isStopped) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printDebug("CloudDownloadWorker: Failed: ${e.message}")
            Result.retry()
        }
    }

    private fun ensureChannel(): String {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_STATUS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    applicationContext.getString(R.string.cloud_download_remote),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        return CHANNEL_STATUS
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    private fun postLowStorageNotification(configId: Long, accountLabel: String) {
        if (!canPostNotifications()) return
        val builder = NotificationCompat.Builder(applicationContext, ensureChannel())
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle(applicationContext.getString(R.string.cloud_download_low_space_title))
            .setContentText(
                applicationContext.getString(R.string.cloud_download_low_space_text, accountLabel)
            )
            .setAutoCancel(true)
        val intent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
        if (intent != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    applicationContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID_LOW_SPACE + configId.toInt(), builder.build())
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

        /**
         * Free-space headroom kept on the target volume — a download that would push
         * the volume below this is deferred and the user is notified instead.
         */
        private const val DOWNLOAD_FREE_HEADROOM = 512L * 1024 * 1024

        private const val CHANNEL_STATUS = "cloud_download_status"
        private const val NOTIFICATION_ID_LOW_SPACE = 4400

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
