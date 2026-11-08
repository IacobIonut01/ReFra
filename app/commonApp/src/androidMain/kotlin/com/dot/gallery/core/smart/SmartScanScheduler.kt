/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import android.content.Context
import com.dot.gallery.core.work.isFinished
import com.dot.gallery.core.work.workDataJson
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.SystemConstraint
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.dao.SmartScanDao
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.data.entity.SmartScanPhaseEntity
import com.dot.gallery.data.entity.SmartScanRunEntity
import com.dot.gallery.data.dao.SmartScanScheduleResult
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.data.entity.SmartScanTrigger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import me.tatarka.inject.annotations.Inject
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.workers.SmartScanWorker
import com.dot.gallery.core.presentation.util.mediaStoreVersion
import com.dot.gallery.core.Settings
import com.dot.gallery.core.logging.printWarn


/**
 * [chargingOnly] is the "Scan only while charging" setting: with it off, background heavy phases
 * still wait for a battery that is not low, but no longer for the charger.
 */
internal fun smartScanConstraintsFor(
    phases: Collection<SmartScanPhase>,
    userVisible: Boolean,
    chargingOnly: Boolean = true,
): Constraints =
    Constraints(
        // Low storage disallowed by default — ALLOW_LOW_STORAGE is the opt-out.
        systemConstraints = if (userVisible) emptySet()
            else setOf(SystemConstraint.REQUIRE_BATTERY_NOT_LOW),
        requiresCharging = chargingOnly && !userVisible && phases.any(SMART_SCAN_HEAVY_PHASES::contains),
        // User-visible runs execute in the foreground service so a manual scan
        // isn't suspended when the app backgrounds.
        isHeavyTask = userVisible,
    )

private val SMART_SCAN_HEAVY_PHASES = setOf(
    SmartScanPhase.SEARCH_INDEX,
    SmartScanPhase.CATEGORY_CLASSIFICATION,
    SmartScanPhase.FACE_INDEX
)

@Singleton
class SmartScanSchedulerImpl @Inject constructor(
    private val scheduler: BackgroundTaskScheduler,
    private val dao: SmartScanDao,
    private val database: InternalDatabase,
    private val processors: () -> SmartScanProcessorRegistry,
    private val appContext: Context
) : SmartScanScheduler {
    private val schedulingMutex = Mutex()

    override suspend fun automatic(features: Int): SmartScanScheduleResult =
        schedule(features, SmartScanTrigger.AUTOMATIC, userVisible = false, fullRefresh = false)

    override suspend fun automaticIfNeeded(features: Int): SmartScanScheduleResult? = schedulingMutex.withLock {
        val expanded = SmartScanPlan.expandedFeatures(features)
        val phases = SmartScanPlan.phasesFor(expanded)
        val featurePhases = phases.filterNot { it == SmartScanPhase.SOURCE_SYNC }
        val registry = processors()
        val expectedRevisions = featurePhases.associateWith { registry.processorFor(it).revision }
        val latestSourcePhase = dao.getLatestSuccessfulPhase(SmartScanPhase.SOURCE_SYNC)
        val currentSourceSnapshot = latestSourcePhase?.let { dao.getRun(it.runId)?.sourceSnapshot }
        val latestRevisions = buildMap {
            featurePhases.forEach { phase ->
                val latest = dao.getLatestCurrentPhase(phase)
                val latestRevision = latest?.processorRevision
                val phaseSourceSnapshot = latest?.let { dao.getRun(it.runId)?.sourceSnapshot }
                if (SmartScanPlan.isPhaseCheckpointCurrent(
                        expectedRevisions.getValue(phase),
                        latestRevision,
                        currentSourceSnapshot,
                        phaseSourceSnapshot
                    )
                ) {
                    put(phase, requireNotNull(latestRevision))
                }
            }
        }
        val mediaVersion = appContext.mediaStoreVersion
        val latestSourceSnapshot = coroutineScope {
            val media = async { database.getMediaDao().getMedia() }
            val cloud = async { database.getCloudMediaDao().getAllCachedAsync() }
            smartSourceSnapshot(mediaVersion, media.await(), cloud.await())
        }
        val mediaCurrent = database.getMediaDao().isMediaVersionUpToDate(mediaVersion) &&
            latestSourcePhase?.processorRevision == registry.processorFor(SmartScanPhase.SOURCE_SYNC).revision &&
            currentSourceSnapshot == latestSourceSnapshot
        if (SmartScanPlan.isAutomaticScanCurrent(mediaCurrent, expectedRevisions, latestRevisions)) null
        else scheduleLocked(expanded, SmartScanTrigger.AUTOMATIC, userVisible = false, fullRefresh = false)
    }

    override suspend fun manual(features: Int): SmartScanScheduleResult =
        schedule(features, SmartScanTrigger.MANUAL, userVisible = true, fullRefresh = false)

    override suspend fun all(userVisible: Boolean): SmartScanScheduleResult = schedule(
        features = SmartScanFeature.ALL_MASK,
        trigger = if (userVisible) SmartScanTrigger.MANUAL else SmartScanTrigger.AUTOMATIC,
        userVisible = userVisible,
        fullRefresh = false
    )

    override suspend fun fullRefresh(): SmartScanScheduleResult = schedule(
        features = SmartScanFeature.ALL_MASK,
        trigger = SmartScanTrigger.MANUAL,
        userVisible = true,
        fullRefresh = true
    )

    /**
     * Run only the grouping phase over already-indexed faces — no media re-scan.
     * The run still reports the PERSONS feature so the dispatcher's branch plan
     * accepts FACE_CLUSTER; every other planned phase is simply absent from the
     * run's phase rows and is skipped.
     */
    override suspend fun regroupFaces(): SmartScanScheduleResult = schedulingMutex.withLock {
        scheduleLocked(
            features = SmartScanFeature.PERSONS.bit,
            trigger = SmartScanTrigger.MANUAL,
            userVisible = true,
            fullRefresh = false,
            onlyPhases = setOf(SmartScanPhase.FACE_CLUSTER)
        )
    }

    override suspend fun resumeActiveRun(): Boolean = schedulingMutex.withLock {
        val run = dao.getActiveRun() ?: return@withLock false
        val state = run.workId?.let { scheduler.observeTaskState(it).first() }
        if (state != null && !state.isFinished) return@withLock true

        if (!dao.prepareRunRecovery(run.runId, UUID.randomUUID().toString(), System.currentTimeMillis())) {
            return@withLock false
        }
        return@withLock enqueueRunLocked(run.runId, replace = true)
    }

    override suspend fun retryFailed(runId: String?): SmartScanScheduleResult? {
        val failed = if (runId != null) dao.getRun(runId) else dao.getLatestRetryableRun()
        failed ?: return null
        if (failed.status !in RETRYABLE_STATUSES) return null
        return schedule(
            features = failed.requestedFeatures,
            trigger = SmartScanTrigger.RECOVERY,
            userVisible = failed.userVisible,
            fullRefresh = failed.fullRefresh
        )
    }

    override suspend fun cancel(runId: String): Boolean = schedulingMutex.withLock {
        dao.cancelRun(runId, System.currentTimeMillis()) ?: return@withLock false
        scheduler.cancelByTag(SmartScanWorker.runTag(runId))
        true
    }

    private suspend fun schedule(
        features: Int,
        trigger: SmartScanTrigger,
        userVisible: Boolean,
        fullRefresh: Boolean
    ): SmartScanScheduleResult = schedulingMutex.withLock {
        scheduleLocked(features, trigger, userVisible, fullRefresh)
    }

    private suspend fun scheduleLocked(
        features: Int,
        trigger: SmartScanTrigger,
        userVisible: Boolean,
        fullRefresh: Boolean,
        onlyPhases: Set<SmartScanPhase>? = null
    ): SmartScanScheduleResult {
        require(features and SmartScanFeature.ALL_MASK != 0) { "At least one Smart Scan feature is required" }
        require(features and SmartScanFeature.ALL_MASK.inv() == 0) { "Unknown Smart Scan feature bits" }

        val expanded = SmartScanPlan.expandedFeatures(features)
        val plannedPhases = SmartScanPlan.phasesFor(expanded)
            .filter { onlyPhases == null || it in onlyPhases }
        val previousActiveWorkId = dao.getActiveRun()?.workId
        val now = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()
        val work = workRequest(runId, plannedPhases, userVisible)
        val run = SmartScanRunEntity(
            runId = runId,
            trigger = trigger,
            requestedFeatures = expanded,
            userVisible = userVisible,
            fullRefresh = fullRefresh,
            workId = work.id,
            requestedAt = now,
            updatedAt = now
        )
        val phases = plannedPhases.map { phase ->
            SmartScanPhaseEntity(runId = runId, phase = phase, updatedAt = now)
        }
        val scheduled = dao.coalesceOrCreate(run, phases)
        val scheduledWorkId = requireNotNull(scheduled.workId)
        val existingState = scheduler.observeTaskState(scheduledWorkId).first()
        if (existingState != TaskState.Unknown && !existingState.isFinished) return scheduled
        val scheduledRun = requireNotNull(dao.getRun(scheduled.runId))
        val actualWorkId = if (existingState.isFinished && existingState != TaskState.Unknown)
            UUID.randomUUID().toString() else scheduledWorkId
        if (actualWorkId != scheduledWorkId) {
            dao.attachWork(scheduled.runId, actualWorkId, System.currentTimeMillis())
        }
        val actualWork = if (scheduled.runId == runId && actualWorkId == work.id) {
            work
        } else {
            workRequest(
                scheduled.runId,
                SmartScanPlan.phasesFor(scheduledRun.requestedFeatures),
                scheduledRun.userVisible,
                actualWorkId
            )
        }
        val promoted = scheduled.runId != runId && actualWorkId == work.id
        if (promoted) {
            previousActiveWorkId?.let { value ->
                runCatching { scheduler.cancel(value) }
            }
        }
        scheduler.enqueue(
            id = actualWork.id,
            trigger = actualWork.trigger,
            workerClassName = SmartScanWorker.WORKER_NAME,
            constraints = actualWork.constraints,
            inputJson = actualWork.inputJson,
            policy = if (promoted) ExistingPolicy.REPLACE else ExistingPolicy.KEEP,
            tags = actualWork.tags,
        )
        return scheduled
    }

    private suspend fun enqueueRunLocked(runId: String, replace: Boolean): Boolean {
        val run = dao.getRun(runId) ?: return false
        val work = workRequest(runId, SmartScanPlan.phasesFor(run.requestedFeatures), run.userVisible)
        if (dao.attachWork(runId, work.id, System.currentTimeMillis()) != 1) return false
        scheduler.enqueue(
            id = work.id,
            trigger = work.trigger,
            workerClassName = SmartScanWorker.WORKER_NAME,
            constraints = work.constraints,
            inputJson = work.inputJson,
            policy = if (replace) ExistingPolicy.REPLACE else ExistingPolicy.KEEP,
            tags = work.tags,
        )
        return true
    }

    /**
     * A pending Smart Scan task description. The task id is a random UUID string — kept
     * random (rather than `runWorkName`) so a superseded run's enqueued task can't be
     * re-observed as a live one when `attachWork` moves the run to a fresh task id.
     */
    private class ScanWork(
        val id: String,
        val inputJson: String,
        val constraints: Constraints,
        val tags: Set<String>,
    ) {
        val trigger = TaskTrigger.OneTime()
    }

    private suspend fun workRequest(
        runId: String,
        phases: Collection<SmartScanPhase>,
        userVisible: Boolean,
        id: String = UUID.randomUUID().toString()
    ): ScanWork = ScanWork(
        id = id,
        inputJson = workDataJson(SmartScanWorker.KEY_RUN_ID to runId),
        constraints = smartScanConstraintsFor(phases, userVisible, scanOnlyWhileCharging()),
        tags = setOf(SmartScanWorker.WORK_NAME, SmartScanWorker.runTag(runId)),
    )

    private suspend fun scanOnlyWhileCharging(): Boolean =
        runCatching { Settings.SmartFeatures.scanOnlyWhileCharging(appContext).first() }
            .onFailure {
                printWarn("smart.scan", "reading the charging preference failed; keeping charging-only: ${it.message}")
            }
            .getOrDefault(true)

    companion object {
        const val WORK_NAME = "smart_scan_dispatcher"
        private val RETRYABLE_STATUSES = setOf(
            SmartScanStatus.FAILED,
            SmartScanStatus.PARTIAL,
            SmartScanStatus.BLOCKED,
            SmartScanStatus.INTERRUPTED
        )
    }
}
