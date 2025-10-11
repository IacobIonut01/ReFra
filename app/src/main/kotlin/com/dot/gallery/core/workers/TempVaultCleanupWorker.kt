package com.dot.gallery.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.hilt.android.EntryPointAccessors
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Periodically deletes orphaned decrypted temp files created for large encrypted media streaming.
 * Files targeted: cacheDir/vault_stream_*.tmp older than [maxAgeHours].
 */
@HiltWorker
class TempVaultCleanupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val maxAgeHours = inputData.getLong(KEY_MAX_AGE_HOURS, DEFAULT_MAX_AGE_HOURS)
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(maxAgeHours)
        val cacheDir = appContext.cacheDir ?: return@runCatching Result.success()
        var deletedCount = 0
        cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith(TEMP_PREFIX) && f.name.endsWith(".tmp")) {
                if (f.lastModified() < cutoff) {
                    if (f.delete()) deletedCount++
                }
            }
        }
        // Sidecar metadata purge ( >7 days )
        val sidecarDir = File(cacheDir, "meta_sidecar")
        if (sidecarDir.isDirectory) {
            val metaCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            sidecarDir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < metaCutoff) {
                    if (f.delete()) deletedCount++
                }
            }
        }
        printDebug("TempVaultCleanupWorker removed $deletedCount stale temp/meta files")
        runCatching {
            val ep = EntryPointAccessors.fromApplication(appContext, com.dot.gallery.core.metrics.MetricsCollectorEntryPoint::class.java)
            val snap = ep.metrics().snapshot()
            printDebug("Metrics: decrypt=${snap.decryptInvocations} waiters=${snap.decryptCoalescedWaiters} lruHit=${snap.lruHits}/${snap.lruHits + snap.lruMisses} sidecar R/W=${snap.sidecarReads}/${snap.sidecarWrites}")
        }
        Result.success()
    }.getOrElse { e ->
        printDebug("TempVaultCleanupWorker failed: ${e.message}")
        Result.failure()
    }

    companion object {
        private const val TEMP_PREFIX = "vault_stream_"
        private const val DEFAULT_MAX_AGE_HOURS = 12L
        private const val UNIQUE_WORK = "TempVaultCleanup"
        const val KEY_MAX_AGE_HOURS = "maxAgeHours"

        fun schedule(workManager: WorkManager, maxAgeHours: Long = DEFAULT_MAX_AGE_HOURS) {
            val req = PeriodicWorkRequestBuilder<TempVaultCleanupWorker>(12, TimeUnit.HOURS)
                .addTag(UNIQUE_WORK)
                .setInputData(
                    androidx.work.workDataOf(
                        KEY_MAX_AGE_HOURS to maxAgeHours
                    )
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }
    }
}
