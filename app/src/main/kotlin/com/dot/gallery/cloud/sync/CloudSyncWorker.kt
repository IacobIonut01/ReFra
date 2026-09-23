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
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.core.capabilities.SyncDelta
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudMediaLocalState
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.dao.SyncStateDao
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.data.entity.SyncStateEntity
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val registry: ProviderRegistry,
    private val configDao: CloudServerConfigDao,
    private val syncStateDao: SyncStateDao,
    private val cloudMediaDao: CloudMediaDao,
    private val smartScanScheduler: SmartScanScheduler
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        printDebug("CloudSyncWorker: Starting sync...")
        try {
            // One-shot runs (e.g. app-open sync) may skip the interval gate while
            // still honouring per-account network/availability policies.
            val ignoreInterval = inputData.getBoolean(KEY_IGNORE_INTERVAL, false)
            var mediaChanged = false
            var retryNeeded = false
            val isMetered = runCatching {
                (applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager)?.isActiveNetworkMetered ?: false
            }.getOrDefault(false)
            val configs = configDao.getAll().first()
            for (config in configs) {
                if (!config.isActive || !config.syncEnabled) continue

                // Resolve the provider for THIS specific account (configId), not the first
                // instance of its type — otherwise two accounts of the same provider type
                // (e.g. two Immich servers) would both sync against whichever registered first.
                val provider = registry.getByConfigId(config.id) as? RemoteMediaProvider ?: continue
                if (!provider.isAvailable || (isMetered && !CloudCellularPolicy.allowsSync(config))) continue
                val syncProvider = provider as? SyncCapableProvider ?: continue
                val previousState = syncStateDao.get(config.providerType, config.id)
                val syncStartedAt = System.currentTimeMillis()
                if (!ignoreInterval && !isCloudSyncDue(
                        lastSyncTimestamp = previousState?.lastSyncTimestamp ?: 0L,
                        intervalMinutes = config.syncIntervalMinutes,
                        now = syncStartedAt
                    )) continue

                // Providers without a real deletion channel can't spot vanished remote
                // files in a delta; ask them for a full index on a slower cadence so the
                // cache still self-heals (deletions propagate within ~24h).
                val reconcileIndex = isCloudReconcileDue(
                    lastReconcileCursor = previousState?.lastSyncCursor,
                    now = syncStartedAt
                )

                printDebug("CloudSyncWorker: Syncing ${config.providerType.displayName} #${config.id} (reconcile=$reconcileIndex)...")
                val syncResult = syncIncrementally(
                    lastWatermark = previousState?.lastSyncTimestamp ?: 0L,
                    nextWatermark = syncStartedAt,
                    fetch = { syncProvider.getSyncDelta(it, reconcileIndex) },
                    persist = { delta ->
                        printDebug("CloudSyncWorker: ${delta.items.size} changes, ${delta.deletedRemoteIds.size} deletions for ${config.providerType.displayName} #${config.id}")
                        applyCloudSyncDelta(applicationContext, cloudMediaDao, config, delta)
                    },
                    advanceWatermark = { timestamp ->
                        // Update last sync timestamp; record when a full-index reconcile last
                        // ran so providers with an expensive index stay on the slow cadence.
                        syncStateDao.upsert(
                            (previousState ?: SyncStateEntity(
                                providerType = config.providerType,
                                serverConfigId = config.id
                            )).copy(
                                lastSyncTimestamp = timestamp,
                                lastSyncCursor = if (reconcileIndex) {
                                    timestamp.toString()
                                } else {
                                    previousState?.lastSyncCursor
                                },
                                lastError = null
                            )
                        )
                    }
                )
                syncResult.onSuccess { delta ->
                    mediaChanged = mediaChanged || delta.changedCount > 0
                    printDebug("CloudSyncWorker: Done syncing ${config.providerType.displayName}")
                }.onFailure { error ->
                    retryNeeded = true
                    printDebug("CloudSyncWorker: Sync failed for ${config.providerType.displayName} #${config.id}: ${error.message}")
                }
            }
            if (mediaChanged) smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
            return if (retryNeeded) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printDebug("CloudSyncWorker: Failed: ${e.message}")
            return Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "cloud_sync"
        private const val WORK_NAME_ONCE = "cloud_sync_once"
        private const val KEY_IGNORE_INTERVAL = "ignore_sync_interval"

        /**
         * Runs one remote-delta sync pass immediately (subject to network). Used by
         * the app-open sync trigger; [ignoreInterval] skips the per-account
         * [isCloudSyncDue] gate while keeping metered-network/availability checks.
         */
        fun syncNow(workManager: WorkManager, ignoreInterval: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_IGNORE_INTERVAL to ignoreInterval))
                .build()
            workManager.enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun schedule(
            workManager: WorkManager,
            intervalMinutes: Long = 60,
            wifiOnly: Boolean = true
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}

/**
 * Persists a [SyncDelta] into Room: upserts changed rows, applies provider-reported
 * deletions, and prunes cached rows against a complete remote index. When the account
 * has `syncRemoteDeletions` enabled, pruned rows that carry an app-owned local copy
 * (`appLocalCopy`) also have that copy removed — user files are never touched because
 * the DAO only surfaces app-flagged copies. Shared by the periodic worker and the
 * pull-to-refresh path (`CloudRepository.syncAllRemoteChanges`).
 */
internal suspend fun applyCloudSyncDelta(
    context: Context,
    cloudMediaDao: CloudMediaDao,
    config: CloudServerConfigEntity,
    delta: SyncDelta
) {
    if (delta.items.isNotEmpty()) cloudMediaDao.insertAll(delta.items)
    val prunedLocalCopies = mutableListOf<CloudMediaLocalState>()
    if (delta.deletedRemoteIds.isNotEmpty()) {
        prunedLocalCopies += cloudMediaDao.deleteByRemoteIds(
            config.providerType, config.id, delta.deletedRemoteIds
        )
    }
    delta.completeRemoteIds?.let { remoteIds ->
        prunedLocalCopies += cloudMediaDao.deleteMissingRemoteMedia(
            config.id, config.providerType, remoteIds
        )
    }
    if (config.syncRemoteDeletions && prunedLocalCopies.isNotEmpty()) {
        deleteAppLocalCopies(context, prunedLocalCopies)
    }
}

internal fun isCloudSyncDue(
    lastSyncTimestamp: Long,
    intervalMinutes: Int,
    now: Long
): Boolean = lastSyncTimestamp <= 0L ||
    now - lastSyncTimestamp >= intervalMinutes.coerceAtLeast(15) * 60_000L

internal const val CLOUD_RECONCILE_INTERVAL_MS = 24L * 60L * 60_000L

/**
 * Full-index reconciles are expensive for providers without a real delta channel, so
 * they run on a slower cadence than regular syncs. [lastReconcileCursor] stores the
 * epoch-millis of the last successful reconcile in `sync_state.lastSyncCursor`; a
 * missing/legacy value reconciles immediately (first sync always covers the index).
 */
internal fun isCloudReconcileDue(
    lastReconcileCursor: String?,
    now: Long,
    intervalMs: Long = CLOUD_RECONCILE_INTERVAL_MS
): Boolean = lastReconcileCursor?.toLongOrNull()?.let { now - it >= intervalMs } ?: true

internal suspend fun <T> syncIncrementally(
    lastWatermark: Long,
    nextWatermark: Long,
    fetch: suspend (Long) -> Result<T>,
    persist: suspend (T) -> Unit,
    advanceWatermark: suspend (Long) -> Unit
): Result<T> {
    val fetched = try {
        fetch(lastWatermark)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        return Result.failure(error)
    }
    val changes = fetched.getOrElse { error ->
        if (error is CancellationException) throw error
        return Result.failure(error)
    }
    return try {
        persist(changes)
        advanceWatermark(nextWatermark)
        Result.success(changes)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
