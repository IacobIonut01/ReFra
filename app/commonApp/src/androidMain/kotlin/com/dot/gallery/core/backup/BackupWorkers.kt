/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import android.content.Context
import android.net.Uri
import com.dot.gallery.core.backup.ConfigBackupManager.ExportResult
import com.dot.gallery.core.backup.ConfigBackupManager.ImportResult
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.isFinished
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import com.dot.gallery.core.work.workScheduler
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import java.util.UUID

/**
 * Owns the single active export/restore job. The journal in [BackupJobStore] is the durable
 * source of truth; this runtime adds in-process result objects and low-latency state updates.
 */
@Singleton
class BackupJobRuntime @Inject constructor(
    private val store: BackupJobStore,
    private val scheduler: BackgroundTaskScheduler
) : BackupJobController {
    private val _job = MutableStateFlow(store.load())
    override val job: StateFlow<BackupJobSnapshot?> = _job.asStateFlow()

    private val _lastImportResult = MutableStateFlow<ImportResult?>(null)
    override val lastImportResult: StateFlow<ImportResult?> = _lastImportResult.asStateFlow()

    private val _lastExportResult = MutableStateFlow<ExportResult?>(null)
    override val lastExportResult: StateFlow<ExportResult?> = _lastExportResult.asStateFlow()

    private val _lastExport = MutableStateFlow(store.loadLast(BackupJobKind.EXPORT))
    override val lastExport: StateFlow<BackupJobSnapshot?> = _lastExport.asStateFlow()
    private val _lastRestore = MutableStateFlow(store.loadLast(BackupJobKind.RESTORE))
    override val lastRestore: StateFlow<BackupJobSnapshot?> = _lastRestore.asStateFlow()

    @Volatile
    private var lastPersistAt = 0L

    override fun startExport(destination: Uri, selection: BackupSelection, password: String?) {
        val now = System.currentTimeMillis()
        val job = BackupJobSnapshot(
            id = "${BackupExportWorker.TAG}_${UUID.randomUUID()}",
            kind = BackupJobKind.EXPORT,
            status = BackupJobStatus.RUNNING,
            createdAt = now,
            updatedAt = now,
            encrypted = !password.isNullOrEmpty(),
            destination = destination.toString(),
            selection = selection
        )
        _lastExportResult.value = null
        store.save(job)
        store.saveSecret(password)
        _job.value = job
        enqueue(job)
    }

    override fun startRestore(source: Uri, selection: BackupSelection, password: String?) {
        val now = System.currentTimeMillis()
        val job = BackupJobSnapshot(
            id = "${BackupRestoreWorker.TAG}_${UUID.randomUUID()}",
            kind = BackupJobKind.RESTORE,
            status = BackupJobStatus.RUNNING,
            createdAt = now,
            updatedAt = now,
            encrypted = !password.isNullOrEmpty(),
            source = source.toString(),
            selection = selection
        )
        _lastImportResult.value = null
        store.save(job)
        store.saveSecret(password)
        _job.value = job
        enqueue(job)
    }

    override fun cancel() {
        val current = _job.value ?: return
        if (current.isFinished) return
        runCatching { scheduler.cancel(current.id) }
        update(current.copy(status = BackupJobStatus.CANCELLED, updatedAt = System.currentTimeMillis()))
        store.clearSecret()
    }

    override fun clearFinished() {
        val current = _job.value ?: return
        if (!current.isFinished) return
        store.clear()
        _job.value = null
    }

    /** Called from `GalleryApp.onCreate` after the startup gate; re-enqueues a killed job. */
    suspend fun recoverActiveJob() {
        val job = store.load() ?: return
        _job.value = job
        if (job.status != BackupJobStatus.RUNNING) return
        val state = runCatching { scheduler.observeTaskState(job.id).first() }.getOrNull()
        if (state == null || state.isFinished) {
            enqueue(job, replace = true)
        }
    }

    internal fun onProgress(jobId: String, section: BackupSection, current: Int, total: Int) {
        val snapshot = _job.value ?: return
        if (snapshot.id != jobId) return
        val next = snapshot.copy(
            progress = snapshot.progress + (section to BackupJobProgress(section, current, total)),
            updatedAt = System.currentTimeMillis()
        )
        _job.value = next
        val now = System.currentTimeMillis()
        if (now - lastPersistAt > 500L || current >= total) {
            lastPersistAt = now
            store.save(next)
        }
    }

    internal fun completeRestore(jobId: String, result: ImportResult) {
        val snapshot = _job.value ?: return
        if (snapshot.id != jobId) return
        _lastImportResult.value = result
        val status = if (result.issues.isEmpty()) {
            BackupJobStatus.COMPLETED
        } else {
            BackupJobStatus.PARTIAL
        }
        finish(
            snapshot.copy(
                status = status,
                updatedAt = System.currentTimeMillis(),
                sectionResults = result.sectionResults,
                issueCount = result.issues.size,
                pendingFavoriteCount = result.pendingLocalFavoriteUris.size
            )
        )
        store.clearSecret()
    }

    internal fun completeExport(jobId: String, result: ExportResult) {
        val snapshot = _job.value ?: return
        if (snapshot.id != jobId) return
        _lastExportResult.value = result
        finish(
            snapshot.copy(
                status = BackupJobStatus.COMPLETED,
                updatedAt = System.currentTimeMillis(),
                exportedCount = result.sectionCounts.values.sum(),
                payloadBytes = result.payloadBytes
            )
        )
        store.clearSecret()
    }

    internal fun fail(jobId: String, message: String) {
        val snapshot = _job.value ?: return
        if (snapshot.id != jobId) return
        finish(
            snapshot.copy(
                status = BackupJobStatus.FAILED,
                updatedAt = System.currentTimeMillis(),
                error = message
            )
        )
        store.clearSecret()
    }

    internal fun cancelled(jobId: String) {
        val snapshot = _job.value ?: return
        if (snapshot.id != jobId) return
        finish(
            snapshot.copy(
                status = BackupJobStatus.CANCELLED,
                updatedAt = System.currentTimeMillis()
            )
        )
        store.clearSecret()
    }

    private fun update(snapshot: BackupJobSnapshot) {
        store.save(snapshot)
        _job.value = snapshot
    }

    private fun finish(snapshot: BackupJobSnapshot) {
        store.save(snapshot)
        store.saveLast(snapshot)
        _job.value = snapshot
        when (snapshot.kind) {
            BackupJobKind.EXPORT -> _lastExport.value = snapshot
            BackupJobKind.RESTORE -> _lastRestore.value = snapshot
        }
    }

    private fun enqueue(job: BackupJobSnapshot, replace: Boolean = false) {
        val worker = when (job.kind) {
            BackupJobKind.EXPORT -> BackupExportWorker.WORKER_NAME
            BackupJobKind.RESTORE -> BackupRestoreWorker.WORKER_NAME
        }
        workSchedulerEnqueue(
            id = job.id,
            workerName = worker,
            jobId = job.id,
            replace = replace
        )
    }

    private fun workSchedulerEnqueue(
        id: String,
        workerName: String,
        jobId: String,
        replace: Boolean
    ) {
        // kmpworkmanager's enqueue is suspend; a fire-and-forget scope is provided by the caller
        // through startWork(), which runs on the main dispatcher from the UI.
        pendingEnqueues += PendingEnqueue(id, workerName, jobId, replace)
        drainEnqueues()
    }

    private val pendingEnqueues = ArrayDeque<PendingEnqueue>()
    private var draining = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun drainEnqueues() {
        if (draining) return
        draining = true
        val pending = pendingEnqueues.removeFirstOrNull() ?: run {
            draining = false
            return
        }
        scope.launch {
            runCatching {
                scheduler.enqueue(
                    id = pending.id,
                    trigger = TaskTrigger.OneTime(),
                    workerClassName = pending.workerName,
                    constraints = Constraints(isHeavyTask = true),
                    inputJson = workDataJson(BackupWorkerKeys.JOB_ID to pending.jobId),
                    policy = if (pending.replace) ExistingPolicy.REPLACE else ExistingPolicy.KEEP,
                    tags = setOf(BackupWorkerKeys.TAG)
                )
            }.onFailure {
                printError("app.backup", "could not enqueue backup job", it)
                fail(pending.jobId, it.message ?: "Could not start the backup job")
            }
            draining = false
            drainEnqueues()
        }
    }

    private data class PendingEnqueue(
        val id: String,
        val workerName: String,
        val jobId: String,
        val replace: Boolean
    )
}

internal object BackupWorkerKeys {
    const val JOB_ID = "jobId"
    const val TAG = "BackupJob"
    const val SECTION = "section"
    const val CURRENT = "current"
    const val TOTAL = "total"
}

// =================================================================================================
// Restore worker
// =================================================================================================

class BackupRestoreWorker @Inject constructor(
    private val context: Context,
    private val store: BackupJobStore,
    private val runtime: BackupJobRuntime,
    private val backupManager: ConfigBackupManager
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withContext(Dispatchers.IO) {
            val jobId = input.getString(BackupWorkerKeys.JOB_ID)
                ?: return@withContext WorkerResult.Failure("missing job id")
            val job = store.load()?.takeIf { it.id == jobId }
                ?: return@withContext WorkerResult.Failure("job not found")
            val source = job.source?.let { Uri.parse(it) }
                ?: return@withContext WorkerResult.Failure("missing source")
            val password = store.readSecret()
            try {
                val result = backupManager.importBackup(source, job.selection, password) {
                        section, current, total ->
                    if (env.isCancelled()) {
                        throw CancellationException("Backup restore cancelled")
                    }
                    WorkSignals.reportProgress(
                        jobId,
                        workData(
                            BackupWorkerKeys.SECTION to section.name,
                            BackupWorkerKeys.CURRENT to current,
                            BackupWorkerKeys.TOTAL to total
                        )
                    )
                    runtime.onProgress(jobId, section, current, total)
                }.getOrElse { throw it }
                runtime.completeRestore(jobId, result)
                WorkSignals.recordOutput(
                    BackupRestoreWorker.WORKER_NAME,
                    workData(
                        "restored" to result.sectionResults.values.sumOf { it.restored },
                        "skipped" to result.sectionResults.values.sumOf { it.skipped },
                        "issues" to result.issues.size
                    )
                )
                WorkerResult.Success()
            } catch (e: CancellationException) {
                runtime.cancelled(jobId)
                WorkerResult.Failure("cancelled")
            } catch (e: BackupPasswordException) {
                runtime.fail(jobId, "Incorrect backup password")
                WorkerResult.Failure("Incorrect backup password")
            } catch (e: Exception) {
                printError("app.backup", "restore failed", e)
                runtime.fail(jobId, e.message ?: "Restore failed")
                WorkerResult.Failure(e.message ?: "Restore failed")
            } finally {
                store.clearSecret()
            }
        }

    companion object {
        const val WORKER_NAME = "BackupRestoreWorker"
        const val TAG = "BackupRestore"
    }
}

@Worker(name = BackupRestoreWorker.WORKER_NAME)
class BackupRestoreWorkerAdapter : GalleryWorkerAdapter(BackupRestoreWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.backupRestoreWorker.doWork(input, env)
}

// =================================================================================================
// Export worker
// =================================================================================================

class BackupExportWorker @Inject constructor(
    private val context: Context,
    private val store: BackupJobStore,
    private val runtime: BackupJobRuntime,
    private val backupManager: ConfigBackupManager
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withContext(Dispatchers.IO) {
            val jobId = input.getString(BackupWorkerKeys.JOB_ID)
                ?: return@withContext WorkerResult.Failure("missing job id")
            val job = store.load()?.takeIf { it.id == jobId }
                ?: return@withContext WorkerResult.Failure("job not found")
            val destination = job.destination?.let { Uri.parse(it) }
                ?: return@withContext WorkerResult.Failure("missing destination")
            val password = store.readSecret()
            try {
                val result = backupManager.exportBackup(destination, job.selection, password) {
                        section, current, total ->
                    if (env.isCancelled()) {
                        throw CancellationException("Backup export cancelled")
                    }
                    WorkSignals.reportProgress(
                        jobId,
                        workData(
                            BackupWorkerKeys.SECTION to section.name,
                            BackupWorkerKeys.CURRENT to current,
                            BackupWorkerKeys.TOTAL to total
                        )
                    )
                    runtime.onProgress(jobId, section, current, total)
                }.getOrElse { throw it }
                runtime.completeExport(jobId, result)
                WorkSignals.recordOutput(
                    BackupExportWorker.WORKER_NAME,
                    workData("exported" to result.sectionCounts.values.sum())
                )
                WorkerResult.Success()
            } catch (e: CancellationException) {
                runtime.cancelled(jobId)
                WorkerResult.Failure("cancelled")
            } catch (e: Exception) {
                printError("app.backup", "export failed", e)
                runtime.fail(jobId, e.message ?: "Export failed")
                WorkerResult.Failure(e.message ?: "Export failed")
            } finally {
                store.clearSecret()
            }
        }

    companion object {
        const val WORKER_NAME = "BackupExportWorker"
        const val TAG = "BackupExport"
    }
}

@Worker(name = BackupExportWorker.WORKER_NAME)
class BackupExportWorkerAdapter : GalleryWorkerAdapter(BackupExportWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.backupExportWorker.doWork(input, env)
}
