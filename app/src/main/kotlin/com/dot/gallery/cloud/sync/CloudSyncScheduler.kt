/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.content.Context
import androidx.work.WorkManager
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Minimum gap between app-open sync triggers; foreground oscillations don't rescan. */
private const val APP_OPEN_SYNC_THROTTLE_MS = 60_000L

@Singleton
class CloudSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val configDao: CloudServerConfigDao
) {

    private val lastAppOpenTriggerAt = AtomicLong(0L)

    /**
     * "Sync when the app is opened" (#1241): enqueues one-time sync, upload, and
     * download passes whenever the app comes to the foreground, so local photo
     * changes and remote changes are picked up immediately instead of waiting for
     * the periodic interval. The one-time workers keep their periodic semantics —
     * only active, sync/download-enabled accounts run, and per-account network and
     * charging policies still apply.
     */
    suspend fun syncOnAppOpen() {
        val now = System.currentTimeMillis()
        val previous = lastAppOpenTriggerAt.get()
        if (now - previous < APP_OPEN_SYNC_THROTTLE_MS) return
        if (!Settings.Misc.getSyncOnAppOpen(context).first()) return
        val configs = configDao.getAll().first()
        if (configs.none { it.isActive && (it.syncEnabled || it.downloadRemoteEnabled) }) return
        if (!lastAppOpenTriggerAt.compareAndSet(previous, now)) return
        printDebug("CloudSyncScheduler: app opened — enqueueing one-time sync/upload/download")
        CloudSyncWorker.syncNow(workManager, ignoreInterval = true)
        CloudUploadWorker.triggerOnAppOpen(workManager)
        CloudDownloadWorker.triggerOnAppOpen(workManager)
    }

    suspend fun reconcile() {
        val plan = cloudSyncSchedulePlan(configDao.getAll().first())
        if (plan == null) {
            printDebug("CloudSyncScheduler: No sync-enabled configs, canceling workers")
            CloudSyncWorker.cancel(workManager)
            CloudUploadWorker.cancel(workManager)
            CloudDownloadWorker.cancel(workManager)
            return
        }

        printDebug(
            "CloudSyncScheduler: Scheduling sync + upload " +
                "(interval=${plan.intervalMinutes}, syncWifiOnly=${plan.syncWifiOnly}, " +
                "uploadWifiOnly=${plan.uploadWifiOnly}, " +
                "downloadWifiOnly=${plan.downloadWifiOnly})"
        )
        CloudSyncWorker.schedule(
            workManager,
            intervalMinutes = plan.intervalMinutes,
            wifiOnly = plan.syncWifiOnly
        )
        CloudUploadWorker.schedule(
            workManager,
            intervalMinutes = plan.intervalMinutes,
            wifiOnly = plan.uploadWifiOnly
        )
        if (plan.downloadWifiOnly != null) {
            CloudDownloadWorker.schedule(
                workManager,
                intervalMinutes = plan.intervalMinutes,
                wifiOnly = plan.downloadWifiOnly
            )
        } else {
            CloudDownloadWorker.cancel(workManager)
        }
    }
}

internal data class CloudSyncSchedulePlan(
    val intervalMinutes: Long,
    val syncWifiOnly: Boolean,
    val uploadWifiOnly: Boolean,
    /** Non-null when at least one account enabled remote downloads; the value is the shared wifiOnly constraint. */
    val downloadWifiOnly: Boolean?
)

/**
 * Single source of truth for whether cloud sync and backup may use a metered network.
 * The account's Wi-Fi-only switch governs remote sync. Uploads additionally honour the
 * per-media cellular overrides exposed by Backup Options.
 */
internal object CloudCellularPolicy {
    fun allowsSync(config: CloudServerConfigEntity): Boolean = !config.wifiOnly

    fun allowsUpload(config: CloudServerConfigEntity, mimeType: String): Boolean =
        !config.wifiOnly || if (mimeType.startsWith("video/")) {
            config.cellularVideos
        } else {
            config.cellularPhotos
        }

    fun allowsAnyUpload(config: CloudServerConfigEntity): Boolean =
        !config.wifiOnly || config.cellularPhotos || config.cellularVideos
}

internal fun cloudSyncSchedulePlan(
    configs: List<CloudServerConfigEntity>
): CloudSyncSchedulePlan? {
    val syncConfigs = configs.filter { it.isActive && it.syncEnabled }
    val downloadConfigs = configs.filter { it.isActive && it.downloadRemoteEnabled }
    if (syncConfigs.isEmpty() && downloadConfigs.isEmpty()) return null
    // Shared WorkManager constraints must be permissive enough for every account. Each worker
    // applies the same policy again per account (and, for uploads, per media type).
    return CloudSyncSchedulePlan(
        intervalMinutes = (syncConfigs + downloadConfigs)
            .minOf { it.syncIntervalMinutes }.toLong().coerceAtLeast(15L),
        syncWifiOnly = syncConfigs.none(CloudCellularPolicy::allowsSync),
        uploadWifiOnly = syncConfigs.none(CloudCellularPolicy::allowsAnyUpload),
        downloadWifiOnly = if (downloadConfigs.isEmpty()) null
            else downloadConfigs.none(CloudCellularPolicy::allowsSync)
    )
}

internal fun cloudSyncScheduleChanged(
    oldConfig: CloudServerConfigEntity,
    newConfig: CloudServerConfigEntity
): Boolean = oldConfig.isActive != newConfig.isActive ||
    oldConfig.syncEnabled != newConfig.syncEnabled ||
    oldConfig.syncIntervalMinutes != newConfig.syncIntervalMinutes ||
    oldConfig.wifiOnly != newConfig.wifiOnly ||
    oldConfig.cellularPhotos != newConfig.cellularPhotos ||
    oldConfig.cellularVideos != newConfig.cellularVideos ||
    oldConfig.requireCharging != newConfig.requireCharging ||
    oldConfig.syncAlbums != newConfig.syncAlbums ||
    oldConfig.downloadRemoteEnabled != newConfig.downloadRemoteEnabled
