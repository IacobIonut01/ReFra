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
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.data.entity.CloudBackupRevisionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import com.dot.gallery.core.util.or
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.data.entity.backupFingerprint
import com.dot.gallery.R
import me.tatarka.inject.annotations.Inject
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_download_low_space_text
import com.dot.gallery.generated.resources.cloud_download_low_space_title
import com.dot.gallery.generated.resources.cloud_download_remote
import com.dot.gallery.core.util.getString


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
class CloudDownloadWorker @Inject constructor(
    context: Context,
    private val registry: ProviderRegistry,
    private val configDao: CloudServerConfigDao,
    private val cloudMediaDao: CloudMediaDao
) : GalleryWorker {

    private val applicationContext: Context = context

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult {
        printDebug("CloudDownloadWorker: Starting remote download pass...")
        return try {
            val isManual = input.getBoolean(KEY_MANUAL, false)
            val targetConfigId = input.getLong(KEY_CONFIG_ID, -1L)
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
                return WorkerResult.Success()
            }

            var downloaded = 0
            var linked = 0
            var failed = 0

            for (config in configs) {
                if (env.isCancelled()) break
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
                    if (env.isCancelled()) break
                    processed++
                    reportProgress(workData(
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
                reportProgress(workData(
                    KEY_TOTAL_ITEMS to pending.size,
                    KEY_COMPLETED_ITEMS to processed,
                    KEY_CURRENT_FILE to "",
                    KEY_CURRENT_ACCOUNT to accountLabel
                ))
            }

            printDebug("CloudDownloadWorker: Done — $downloaded downloaded, $linked linked, $failed failed")
            if (env.isCancelled()) WorkerResult.Retry("stopped mid-run") else WorkerResult.Success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printDebug("CloudDownloadWorker: Failed: ${e.message}")
            WorkerResult.Retry("download pass failed: ${e.message}")
        }
    }

    private fun ensureChannel(): String {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_STATUS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    applicationContext.getString(Res.string.cloud_download_remote),
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
            .setContentTitle(applicationContext.getString(Res.string.cloud_download_low_space_title))
            .setContentText(
                applicationContext.getString(Res.string.cloud_download_low_space_text, accountLabel)
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

    private fun reportProgress(data: kotlinx.serialization.json.JsonObject) {
        WorkSignals.reportProgress(PROGRESS_WORK_NAME, data)
    }

    companion object {
        const val WORKER_NAME = "CloudDownloadWorker"
        const val PROGRESS_WORK_NAME = WORKER_NAME
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

        suspend fun schedule(scheduler: BackgroundTaskScheduler, intervalMinutes: Long, wifiOnly: Boolean) {
            scheduler.enqueue(
                id = WORK_NAME,
                trigger = TaskTrigger.Periodic(
                    intervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes)
                ),
                workerClassName = WORKER_NAME,
                constraints = Constraints(
                    requiresNetwork = true,
                    requiresUnmeteredNetwork = wifiOnly,
                    backoffPolicy = BackoffPolicy.EXPONENTIAL,
                    backoffDelayMs = TimeUnit.MINUTES.toMillis(5),
                ),
                policy = ExistingPolicy.UPDATE,
            )
        }

        /** Runs a manual "Download all" for one account ([configId] <= 0 means every enabled account). */
        suspend fun triggerNow(scheduler: BackgroundTaskScheduler, configId: Long = -1L) {
            val uniqueName = if (configId > 0L) "${WORK_NAME_ONCE}_$configId" else WORK_NAME_ONCE
            scheduler.enqueue(
                id = uniqueName,
                trigger = TaskTrigger.OneTime(),
                workerClassName = WORKER_NAME,
                constraints = Constraints(requiresNetwork = true),
                inputJson = workDataJson(KEY_MANUAL to true, KEY_CONFIG_ID to configId),
                policy = ExistingPolicy.REPLACE,
            )
        }

        /**
         * One download pass on app open. Non-manual: the per-account
         * `downloadRemoteEnabled`/`syncEnabled` gating and the metered-network
         * check apply exactly like the periodic worker.
         */
        suspend fun triggerOnAppOpen(scheduler: BackgroundTaskScheduler) {
            scheduler.enqueue(
                id = WORK_NAME_APP_OPEN,
                trigger = TaskTrigger.OneTime(),
                workerClassName = WORKER_NAME,
                constraints = Constraints(requiresNetwork = true),
                policy = ExistingPolicy.KEEP,
            )
        }

        fun cancel(scheduler: BackgroundTaskScheduler) {
            scheduler.cancel(WORK_NAME)
        }
    }
}

@Worker(name = CloudDownloadWorker.WORKER_NAME)
class CloudDownloadWorkerAdapter : GalleryWorkerAdapter(CloudDownloadWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.cloudDownloadWorker.doWork(input, env)
}
