/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.backup.DataHealthReport
import com.dot.gallery.core.backup.backupJson
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.SystemConstraint
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import java.io.File

@Singleton
class DataHealthStore @Inject constructor(
    private val context: Context
) {
    suspend fun isAutoCleanEnabled(): Boolean =
        context.activeDataStore.data.first()[AUTO_CLEAN_KEY] ?: true

    suspend fun setAutoCleanEnabled(enabled: Boolean) {
        context.activeDataStore.edit { it[AUTO_CLEAN_KEY] = enabled }
    }

    suspend fun readReport(): DataHealthReport? =
        context.activeDataStore.data.first()[REPORT_KEY]?.let {
            runCatching { backupJson.decodeFromString(DataHealthReport.serializer(), it) }.getOrNull()
        }

    suspend fun writeReport(report: DataHealthReport) {
        context.activeDataStore.edit {
            it[REPORT_KEY] = backupJson.encodeToString(DataHealthReport.serializer(), report)
        }
    }

    private companion object {
        val AUTO_CLEAN_KEY = booleanPreferencesKey("auto_clean_orphans")
        val REPORT_KEY = stringPreferencesKey("backup_health_v1")
    }
}

/**
 * Reconciles derived per-media rows with the current media mirror. It never deletes user
 * definitions (categories, collections, people, albums, vaults) and never touches `cloud_media`.
 */
class DataHealthWorker @Inject constructor(
    private val context: Context,
    private val database: InternalDatabase,
    private val cloudMediaDao: CloudMediaDao,
    private val store: DataHealthStore
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withContext(Dispatchers.IO) {
            val manual = input.getBoolean(KEY_MANUAL, false)
            if (!manual && !store.isAutoCleanEnabled()) {
                return@withContext WorkerResult.Success()
            }
            val report = runCatching { reconcile(clean = true) }
                .getOrElse {
                    printError("worker.data-health", "cleanup failed", it)
                    DataHealthReport(
                        checkedAt = System.currentTimeMillis(),
                        cleanRun = true,
                        error = it.message
                    )
                }
            store.writeReport(report)
            WorkSignals.recordOutput(
                WORKER_NAME,
                workData(
                    "removed" to report.total,
                    "categories" to report.categories,
                    "metadata" to report.metadata,
                    "faces" to report.faces
                )
            )
            WorkerResult.Success()
        }

    private suspend fun reconcile(clean: Boolean): DataHealthReport {
        val localMedia = database.getMediaDao().getMedia()
        // Never prune against an unindexed or failed mirror: that would delete valid rows.
        if (localMedia.isEmpty()) {
            return DataHealthReport(
                checkedAt = System.currentTimeMillis(),
                cleanRun = clean,
                error = "Library is not indexed yet"
            )
        }
        val cloudMedia = cloudMediaDao.getAllAsync()
        val validIds = HashSet<Long>(localMedia.size + cloudMedia.size)
        localMedia.forEach { validIds += it.id }
        cloudMedia.forEach { validIds += it.globalMediaId }

        var categories = 0
        var metadata = 0
        var embeddings = 0
        var captureTime = 0
        var faces = 0
        var collections = 0
        var editBackups = 0
        var staleVaultCredentials = 0

        if (clean) {
            val categoryDao = database.getCategoryDao()
            categories += categoryDao.cleanupCategoriesForDeletedMedia()

            val metadataDao = database.getMetadataDao()
            metadata += metadataDao.deleteMissingCore()
            metadata += metadataDao.deleteMissingVideo()
            metadata += metadataDao.deleteMissingFlags()

            embeddings += database.getImageEmbeddingDao().deleteOrphans()

            val captureDao = database.getMediaCaptureTimeDao()
            val staleCapture = captureDao.getAll()
                .map { it.mediaId }
                .filterNot { it in validIds }
            if (staleCapture.isNotEmpty()) {
                captureDao.deleteByIds(staleCapture)
                captureTime += staleCapture.size
            }

            val faceDao = database.getDetectedFaceDao()
            faces += faceDao.deleteOrphans()
            faces += faceDao.deleteOrphanExclusions()
            faces += faceDao.deleteOrphanLinks()
            faces += faceDao.deleteOrphanSuppressions()

            val collectionDao = database.getCollectionDao()
            collections += collectionDao.cleanupAllOrphanedCollectionMedia()
            collections += collectionDao.cleanupAllOrphanedExcludedMedia()

            val editDao = database.getEditHistoryDao()
            editDao.getOrphans().forEach { orphan ->
                File(orphan.backupPath).delete()
                editDao.deleteOrphan(orphan.mediaId)
                editBackups++
            }

            database.getScannedMediaDao().removeStaleEntries()

            staleVaultCredentials += clearStaleVaultCredentials()
        }

        return DataHealthReport(
            checkedAt = System.currentTimeMillis(),
            cleanRun = clean,
            categories = categories,
            metadata = metadata,
            embeddings = embeddings,
            captureTime = captureTime,
            faces = faces,
            collections = collections,
            editBackups = editBackups,
            staleVaultCredentials = staleVaultCredentials
        )
    }

    /** Removes credential/lockout keys whose vault no longer exists. */
    private suspend fun clearStaleVaultCredentials(): Int {
        val vaultIds = database.getVaultDao().getVaults().first()
            .map { it.uuid.toString() }
            .toSet()
        val keep = vaultIds + VaultPasswordManager.GATE_UUID.toString()
        val prefixes = listOf(
            "vault_password_",
            "vault_attempts_",
            "vault_lockout_until_"
        )
        val prefs = context.activeDataStore.data.first()
        val stale = prefs.asMap().keys.map { it.name }.filter { name ->
            val prefix = prefixes.firstOrNull { name.startsWith(it) } ?: return@filter false
            name.removePrefix(prefix) !in keep
        }
        if (stale.isEmpty()) return 0
        context.activeDataStore.edit { editable ->
            stale.forEach { editable.remove(stringPreferencesKey(it)) }
        }
        return stale.size
    }

    companion object {
        const val WORKER_NAME = "DataHealthWorker"
        const val KEY_MANUAL = "manual"
        private const val TAG = "DataHealth"
        private const val PERIODIC_ID = "DataHealthWorker_periodic"
        private const val ONE_SHOT_ID = "DataHealthWorker_run"
    }
}

@Worker(name = DataHealthWorker.WORKER_NAME)
class DataHealthWorkerAdapter : GalleryWorkerAdapter(DataHealthWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.dataHealthWorker.doWork(input, env)
}

/** Runs reconciliation now (used by the hub's "Clean now") and returns the work id. */
suspend fun BackgroundTaskScheduler.runDataHealthNow(): String {
    val id = "${DataHealthWorker.WORKER_NAME}_run_${System.currentTimeMillis()}"
    enqueue(
        id = id,
        trigger = TaskTrigger.OneTime(),
        workerClassName = DataHealthWorker.WORKER_NAME,
        constraints = Constraints(
            isHeavyTask = false,
            systemConstraints = setOf(SystemConstraint.REQUIRE_BATTERY_NOT_LOW)
        ),
        inputJson = workDataJson(DataHealthWorker.KEY_MANUAL to true),
        policy = ExistingPolicy.KEEP,
        tags = setOf("DataHealth")
    )
    return id
}

@Singleton
class DataHealthControllerImpl @Inject constructor(
    private val store: DataHealthStore,
    private val scheduler: BackgroundTaskScheduler
) : com.dot.gallery.core.backup.DataHealthController {
    private val _report = kotlinx.coroutines.flow.MutableStateFlow<DataHealthReport?>(null)
    override val report: kotlinx.coroutines.flow.StateFlow<DataHealthReport?> =
        _report.asStateFlow()

    private val _autoClean = kotlinx.coroutines.flow.MutableStateFlow(true)
    override val autoClean: kotlinx.coroutines.flow.StateFlow<Boolean> = _autoClean.asStateFlow()

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
            .launch { refresh() }
    }

    override suspend fun refresh() {
        _report.value = store.readReport()
        _autoClean.value = store.isAutoCleanEnabled()
    }

    override suspend fun setAutoClean(enabled: Boolean) {
        store.setAutoCleanEnabled(enabled)
        _autoClean.value = enabled
    }

    override suspend fun cleanNow() {
        val id = scheduler.runDataHealthNow()
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(60_000) {
                com.dot.gallery.core.work.WorkSignals
                    .observe(id, DataHealthWorker.WORKER_NAME)
                    .filter { it.isFinished }
                    .first()
            }
        }
        refresh()
    }
}

/** Debounced one-shot after a media-library update. */
suspend fun BackgroundTaskScheduler.runDataHealthAfterSync() {
    enqueue(
        id = "DataHealthWorker_after_sync",
        trigger = TaskTrigger.OneTime(),
        workerClassName = DataHealthWorker.WORKER_NAME,
        constraints = Constraints(
            isHeavyTask = false,
            systemConstraints = setOf(SystemConstraint.REQUIRE_BATTERY_NOT_LOW)
        ),
        inputJson = workDataJson(DataHealthWorker.KEY_MANUAL to false),
        policy = ExistingPolicy.KEEP,
        tags = setOf("DataHealth")
    )
}

/** Schedules the daily automatic pass. */
suspend fun BackgroundTaskScheduler.scheduleDataHealthPeriodic() {
    enqueue(
        id = "DataHealthWorker_periodic",
        trigger = TaskTrigger.Periodic(intervalMs = 24L * 60 * 60 * 1000),
        workerClassName = DataHealthWorker.WORKER_NAME,
        constraints = Constraints(
            isHeavyTask = false,
            systemConstraints = setOf(SystemConstraint.REQUIRE_BATTERY_NOT_LOW)
        ),
        inputJson = workDataJson(DataHealthWorker.KEY_MANUAL to false),
        policy = ExistingPolicy.UPDATE,
        tags = setOf("DataHealth")
    )
}
