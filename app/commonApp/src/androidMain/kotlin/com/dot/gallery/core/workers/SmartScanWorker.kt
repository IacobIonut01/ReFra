/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import com.dot.gallery.core.smart.SmartScanPhaseContext
import com.dot.gallery.core.smart.SmartScanPhaseResult
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.core.smart.SmartScanProgress
import com.dot.gallery.data.dao.SmartScanDao
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.data.entity.SmartScanPhaseEntity
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.core.logging.withLogScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.presentation.util.remove
import com.dot.gallery.core.smart.SmartScanProcessorRegistry
import me.tatarka.inject.annotations.Inject
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printWarn


class SmartScanWorker @Inject constructor(
    private val dao: SmartScanDao,
    private val processors: SmartScanProcessorRegistry,
) : GalleryWorker {
    private lateinit var runId: String
    private lateinit var owner: String
    private val activePhases = ConcurrentHashMap.newKeySet<SmartScanPhase>()
    private val activeWorkItemCounts = ConcurrentHashMap<SmartScanPhase, Int>()
    private val lastProgressPersistedAt = ConcurrentHashMap<SmartScanPhase, Long>()
    private val publishMutex = Mutex()
    @Volatile private var userVisible = false

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult = withLogScope("worker.smart-scan") {
        runId = input.getString(KEY_RUN_ID).orEmpty()
        owner = java.util.UUID.randomUUID().toString()
        if (runId.isBlank()) return@withLogScope WorkerResult.Failure("missing_run_id")
        val initial = dao.getRun(runId)
            ?: return@withLogScope WorkerResult.Failure("run_not_found")
        userVisible = initial.userVisible
        val now = System.currentTimeMillis()
        dao.recoverExpiredFeatureLeases(now)
        dao.recoverExpiredPhaseLeases(now)
        dao.recoverExpiredRunLeases(now)
        if (dao.claimRunLease(runId, owner, now, now + LEASE_MILLIS) != 1) {
            return@withLogScope resultFor(initial.status)
        }
        // userVisible runs are scheduled with isHeavyTask=true, so kmpworkmanager already
        // elevated this execution to a foreground service — no manual setForeground call.

        try {
            dispatch()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { persistInterruption() }
            throw cancelled
        } catch (error: Throwable) {
            val code = error.javaClass.simpleName.ifBlank { "dispatcher_failed" }
            printError(
                tag = "SmartScanWorker",
                message = "Smart scan dispatch failed",
                throwable = error,
                ctx = mapOf("run" to runId.take(8)),
            )
            withContext(NonCancellable) {
                activePhases.forEach { phase ->
                    dao.finishPhaseOwned(runId, phase, owner, SmartScanStatus.FAILED, System.currentTimeMillis(), code)
                }
                dao.finishRunOwned(runId, owner, SmartScanStatus.FAILED, System.currentTimeMillis(), code)
            }
            WorkerResult.Failure(code)
        }
    }

    private suspend fun dispatch(): WorkerResult {
        while (currentCoroutineContext().isActive) {
            val run = dao.getRun(runId) ?: return WorkerResult.Failure("run gone")
            if (run.status !in setOf(SmartScanStatus.QUEUED, SmartScanStatus.RUNNING)) {
                return resultFor(run.status)
            }
            val phases = dao.getPhases(runId).associateBy { it.phase }
            val sourcePhase = phases[SmartScanPhase.SOURCE_SYNC]
            val sourceRevision = processors.processorFor(SmartScanPhase.SOURCE_SYNC).revision
            if (sourcePhase != null && SmartScanPlan.shouldRequeueForRevision(
                    sourcePhase.status,
                    sourcePhase.processorRevision,
                    sourceRevision
                )
            ) {
                dao.requeueTerminalPhase(runId, SmartScanPhase.SOURCE_SYNC, System.currentTimeMillis())
                continue
            }
            if (sourcePhase?.status in RUNNABLE_STATUSES) {
                dispatchPhase(SmartScanPhase.SOURCE_SYNC)
                continue
            }
            val branches = SmartScanPlan.executionBranches(run.requestedFeatures).filter { branch ->
                branch.any { phases[it]?.status in RUNNABLE_STATUSES }
            }
            if (branches.isEmpty()) break
            coroutineScope {
                branches.map { branch ->
                    async {
                        var index = 0
                        while (index < branch.size) {
                            val phase = branch[index]
                            if (dao.getPhase(runId, phase)?.status in RUNNABLE_STATUSES) dispatchPhase(phase)
                            if (dao.getPhase(runId, phase)?.status !in RUNNABLE_STATUSES) index++
                        }
                    }
                }.awaitAll()
            }
        }

        return finishRun()
    }

    private suspend fun dispatchPhase(phase: SmartScanPhase) {
        val run = dao.getRun(runId) ?: error("run_not_found")
        val processor = processors.processorFor(phase)
        val now = System.currentTimeMillis()
        check(dao.claimPhaseLease(runId, phase, owner, now, now + LEASE_MILLIS, processor.revision) == 1) {
            "phase_lease_unavailable"
        }
        activePhases.add(phase)
        activeWorkItemCounts.remove(phase)
        lastProgressPersistedAt.remove(phase)
        try {
            check(dao.updateCurrentPhase(runId, owner, phase, run.sourceSnapshot, now) == 1) {
                "run_lease_lost"
            }
            publishAggregate()
            val phaseFullRefresh = run.fullRefresh
            val outcome = runProcessorWithHeartbeat(processor, run.sourceSnapshot, phaseFullRefresh)
            finishPhase(phase, outcome)
            if (!phaseFullRefresh && dao.getRun(runId)?.fullRefresh == true) {
                dao.requeueTerminalPhase(runId, phase, System.currentTimeMillis())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val code = error.javaClass.simpleName.ifBlank { "phase_failed" }
            printError(
                tag = "SmartScanWorker",
                message = "Phase threw: ${phase.name}",
                throwable = error,
                ctx = mapOf("run" to runId.take(8), "phase" to phase.name, "code" to code),
                scope = "smart-scan",
            )
            withContext(NonCancellable) {
                dao.finishPhaseOwned(runId, phase, owner, SmartScanStatus.FAILED, System.currentTimeMillis(), code)
                publishAggregate()
            }
        } finally {
            activePhases.remove(phase)
            activeWorkItemCounts.remove(phase)
            lastProgressPersistedAt.remove(phase)
        }
    }

    private suspend fun finishRun(): WorkerResult {
        val terminalPhases = dao.getPhases(runId)
            .filter { it.phase in SmartScanPlan.phasesFor(dao.getRun(runId)?.requestedFeatures ?: 0) }
        val requestedFeaturePhases = terminalPhases.filter { it.phase != SmartScanPhase.SOURCE_SYNC }
        val status = SmartScanPlan.terminalStatus(requestedFeaturePhases.map { it.status })
        val error = requestedFeaturePhases.firstNotNullOfOrNull { it.lastErrorCode }
        val finished = dao.finishRunIfComplete(runId, owner, status, System.currentTimeMillis(), error)
        if (finished != 1) {
            val current = dao.getRun(runId) ?: return WorkerResult.Failure("run gone")
            return if (current.status in setOf(SmartScanStatus.QUEUED, SmartScanStatus.RUNNING)) {
                WorkerResult.Retry(reason = "run still active")
            } else {
                resultFor(current.status)
            }
        }
        publishAggregate()
        return resultFor(status)
    }

    private suspend fun runProcessorWithHeartbeat(
        processor: com.dot.gallery.core.smart.SmartScanPhaseProcessor,
        sourceSnapshot: String,
        fullRefresh: Boolean
    ): SmartScanPhaseResult = coroutineScope {
        val heartbeat = launch {
            while (isActive) {
                delay(HEARTBEAT_MILLIS)
                renewLeases(processor.phase)
            }
        }
        try {
            processor.process(
                SmartScanPhaseContext(runId, owner, sourceSnapshot, fullRefresh) { progress ->
                    persistProgress(processor.phase, progress)
                }
            )
        } finally {
            heartbeat.cancelAndJoin()
        }
    }

    private suspend fun finishPhase(phase: SmartScanPhase, result: SmartScanPhaseResult) {
        persistProgress(phase, result.progress, force = true)
        val now = System.currentTimeMillis()
        when (result) {
            is SmartScanPhaseResult.Completed -> {
                result.sourceSnapshot?.let { snapshot ->
                    dao.updateCurrentPhase(runId, owner, phase, snapshot, now)
                }
                dao.finishPhaseOwned(runId, phase, owner, SmartScanStatus.SUCCEEDED, now)
            }
            is SmartScanPhaseResult.Partial -> {
                printWarn(
                    tag = "SmartScanWorker",
                    message = "Phase partial: ${phase.name} (${result.errorCode})",
                    ctx = mapOf("run" to runId.take(8), "phase" to phase.name, "code" to result.errorCode),
                    scope = "smart-scan",
                )
                dao.finishPhaseOwned(runId, phase, owner, SmartScanStatus.PARTIAL, now, result.errorCode)
            }
            is SmartScanPhaseResult.Blocked -> {
                printWarn(
                    tag = "SmartScanWorker",
                    message = "Phase blocked: ${phase.name} (${result.errorCode})",
                    ctx = mapOf("run" to runId.take(8), "phase" to phase.name, "code" to result.errorCode),
                    scope = "smart-scan",
                )
                dao.finishPhaseOwned(runId, phase, owner, SmartScanStatus.BLOCKED, now, result.errorCode)
            }
            is SmartScanPhaseResult.Failed -> {
                printWarn(
                    tag = "SmartScanWorker",
                    message = "Phase failed: ${phase.name} (${result.errorCode})",
                    ctx = mapOf("run" to runId.take(8), "phase" to phase.name, "code" to result.errorCode),
                    scope = "smart-scan",
                )
                dao.finishPhaseOwned(runId, phase, owner, SmartScanStatus.FAILED, now, result.errorCode)
            }
        }
        publishAggregate()
    }

    private suspend fun persistProgress(
        phase: SmartScanPhase,
        progress: SmartScanProgress,
        force: Boolean = false
    ) {
        currentCoroutineContext().ensureActive()
        val now = System.currentTimeMillis()
        if (phase != SmartScanPhase.SOURCE_SYNC) activeWorkItemCounts[phase] = progress.total
        val updateInterval = if (userVisible) FOREGROUND_PROGRESS_UPDATE_MILLIS else BACKGROUND_PROGRESS_UPDATE_MILLIS
        if (!force && now - (lastProgressPersistedAt[phase] ?: 0L) < updateInterval) return
        userVisible = userVisible || dao.getRun(runId)?.userVisible == true
        renewLeases(phase)
        dao.updatePhaseSummary(
            runId,
            phase,
            progress.total,
            progress.processed,
            progress.succeeded,
            progress.skipped,
            progress.failed,
            now
        )
        lastProgressPersistedAt[phase] = now
        publishAggregate()
    }

    private suspend fun publishAggregate() = publishMutex.withLock {
        val phases = dao.getPhases(runId)
        val aggregate = SmartScanPlan.aggregate(phases.map { it.toProgress() })
        val current = phases.firstOrNull { it.phase in activePhases && it.status == SmartScanStatus.RUNNING }
            ?: phases.firstOrNull { it.status == SmartScanStatus.RUNNING }
        val activePhase = current?.phase
        val overallPercent = (SmartScanPlan.overallProgress(phases) * 100).toInt()
        val now = System.currentTimeMillis()
        dao.updateRunSummary(
            runId,
            aggregate.total,
            aggregate.processed,
            aggregate.succeeded,
            aggregate.skipped,
            aggregate.failed,
            now
        )
        WorkSignals.reportProgress(
            WORK_NAME,
            workData(
                KEY_PROGRESS to overallPercent,
                KEY_TOTAL to aggregate.total,
                KEY_PROCESSED to aggregate.processed,
                KEY_PHASE to activePhase?.storedValue
            )
        )
    }

    private suspend fun renewLeases(phase: SmartScanPhase) {
        val now = System.currentTimeMillis()
        check(dao.renewRunLease(runId, owner, now, now + LEASE_MILLIS) == 1) { "run_lease_lost" }
        check(dao.renewPhaseLease(runId, phase, owner, now, now + LEASE_MILLIS) == 1) { "phase_lease_lost" }
    }

    private suspend fun persistInterruption() {
        val run = dao.getRun(runId) ?: return
        val now = System.currentTimeMillis()
        dao.releaseFeatureLeases(runId, owner, now)
        if (run.status == SmartScanStatus.CANCELLED) return
        activePhases.forEach { phase -> dao.releasePhaseLease(runId, phase, owner, now) }
        dao.releaseRunLease(runId, owner, now)
    }

    private fun resultFor(status: SmartScanStatus): WorkerResult = when (status) {
        SmartScanStatus.SUCCEEDED, SmartScanStatus.PARTIAL, SmartScanStatus.BLOCKED -> WorkerResult.Success()
        SmartScanStatus.QUEUED, SmartScanStatus.RUNNING -> WorkerResult.Retry(reason = "run still active")
        SmartScanStatus.FAILED, SmartScanStatus.INTERRUPTED, SmartScanStatus.CANCELLED -> WorkerResult.Failure(status.name)
    }

    private fun SmartScanPhaseEntity.toProgress() = SmartScanProgress(
        totalMedia,
        processedMedia,
        succeededMedia,
        skippedMedia,
        failedMedia
    )

    companion object {
        const val WORK_NAME = "smart_scan_worker"
        const val WORKER_NAME = "SmartScanWorker"
        const val KEY_RUN_ID = "run_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        const val KEY_PROCESSED = "processed"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR_CODE = "error_code"
        private const val CHANNEL_ID = "smart_scan_media_processing"
        private const val NOTIFICATION_ID = 0x5343414E
        private const val LEASE_MILLIS = 5 * 60 * 1000L
        private const val HEARTBEAT_MILLIS = 60 * 1000L
        private const val FOREGROUND_PROGRESS_UPDATE_MILLIS = 1_500L
        private const val BACKGROUND_PROGRESS_UPDATE_MILLIS = 5_000L
        private val RUNNABLE_STATUSES = setOf(SmartScanStatus.QUEUED, SmartScanStatus.RUNNING)

        fun runTag(runId: String) = "smart_scan_run_$runId"
    }
}

@Worker(name = SmartScanWorker.WORKER_NAME)
class SmartScanWorkerAdapter : GalleryWorkerAdapter(SmartScanWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.smartScanWorker.doWork(input, env)
}
