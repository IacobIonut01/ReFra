package com.dot.gallery.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dot.gallery.core.decryption.VaultDecryptStore
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.feature_node.presentation.frameextract.FrameSourceCleanup
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.hilt.android.EntryPointAccessors
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Periodically deletes orphaned decrypted temp files created for large encrypted media streaming.
 * Files targeted: managed vault_stream_, vault_dec_, and decrypted shared_ exports older than
 * [maxAgeHours].
 */
@HiltWorker
class TempVaultCleanupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withLogScope("worker.temp-vault-cleanup") { runCatching {
        val maxAgeHours = inputData.getLong(KEY_MAX_AGE_HOURS, DEFAULT_MAX_AGE_HOURS)
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(maxAgeHours)
        val cacheDir = appContext.cacheDir ?: return@runCatching Result.success()
        var deletedCount = 0
        // Managed decrypted store: sweep expired/orphaned canonical files under cacheDir/vault_tmp.
        deletedCount += VaultDecryptStore.sweep(appContext)
        // Clean every worker-managed decrypted/export file from cacheDir. shared_ files preserve
        // their media extension so they intentionally do not end in .tmp.
        cacheDir.listFiles()?.forEach { f ->
            if (isManagedDecryptedTempFile(f) && f.lastModified() < cutoff) {
                if (f.delete()) deletedCount++
            }
        }
        // Also clean any leftover managed files from filesDir (legacy location before fix).
        val filesDir = appContext.filesDir
        if (filesDir != null) {
            filesDir.listFiles()?.forEach { f ->
                if (isManagedDecryptedTempFile(f) && f.lastModified() < cutoff) {
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
        FrameSourceCleanup.sweep(appContext)
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
    } }

    companion object {
        private const val TEMP_PREFIX = "vault_stream_"
        private const val TEMP_DEC_PREFIX = "vault_dec_"
        private const val TEMP_META_VID_PREFIX = "vault_meta_vid_"
        private const val TEMP_VID_STREAM_PREFIX = "vault_vid_stream_"
        private const val TEMP_VID_PREFIX = "vault_vid_"
        private const val SHARED_DECRYPTED_PREFIX = "shared_"
        private const val DEFAULT_MAX_AGE_HOURS = 12L
        private const val UNIQUE_WORK = "TempVaultCleanup"
        private const val PREFS_NAME = "vault_cleanup_prefs"
        private const val KEY_LEGACY_CLEANUP_DONE = "legacy_filesdir_cleanup_done"
        const val KEY_MAX_AGE_HOURS = "maxAgeHours"

        // Player/frame-extract spill files: "{mediaId}.temp<rand>.tmp" and "{id}.enc.temp<rand>.tmp"
        private val TEMP_PLAYER_PATTERN = Regex("^\\d+(\\.enc)?\\.temp.*\\.tmp$")

        internal fun isManagedDecryptedTempFile(file: File): Boolean = file.isFile && when {
            file.name.startsWith(SHARED_DECRYPTED_PREFIX) -> true
            file.name.startsWith(TEMP_PREFIX) -> file.name.endsWith(".tmp")
            file.name.startsWith(TEMP_DEC_PREFIX) -> file.name.endsWith(".tmp")
            file.name.startsWith(TEMP_META_VID_PREFIX) -> file.name.endsWith(".tmp")
            file.name.startsWith(TEMP_VID_STREAM_PREFIX) -> file.name.endsWith(".tmp")
            file.name.startsWith(TEMP_VID_PREFIX) -> file.name.endsWith(".tmp")
            else -> TEMP_PLAYER_PATTERN.matches(file.name)
        }

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

        /**
         * One-time cleanup for users upgrading from versions that leaked vault_dec_*.tmp
         * files into filesDir. Deletes ALL such files unconditionally (no age cutoff)
         * since they are all orphaned. Guarded by a SharedPreferences flag so it only
         * runs once per install.
         */
        fun runLegacyFilesdirCleanup(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_LEGACY_CLEANUP_DONE, false)) return

            val filesDir = context.filesDir ?: return
            var deletedCount = 0
            var freedBytes = 0L
            filesDir.listFiles()?.forEach { f ->
                if (isManagedDecryptedTempFile(f)) {
                    freedBytes += f.length()
                    if (f.delete()) deletedCount++
                }
            }
            // Also sweep vault subfolders for any orphaned .tmp files
            filesDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    dir.listFiles()?.forEach { f ->
                        if (isManagedDecryptedTempFile(f)) {
                            freedBytes += f.length()
                            if (f.delete()) deletedCount++
                        }
                    }
                }
            }
            if (deletedCount > 0) {
                val freedMB = freedBytes / (1024 * 1024)
                printDebug("Legacy vault temp cleanup: deleted $deletedCount files, freed ${freedMB}MB")
            }
            prefs.edit().putBoolean(KEY_LEGACY_CLEANUP_DONE, true).apply()
        }
    }
}
