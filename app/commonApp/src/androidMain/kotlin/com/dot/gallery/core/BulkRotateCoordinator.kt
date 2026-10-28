/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.presentation.components.FloatingProgressRegistry
import com.dot.gallery.core.presentation.components.FloatingProgressTask
import com.dot.gallery.core.presentation.components.MediaRotationTracker
import com.dot.gallery.core.presentation.components.PendingMediaRotation
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.workers.RotateBatchProgress
import com.dot.gallery.core.workers.RotateMediaWorker
import com.dot.gallery.core.workers.rotateImages
import com.dot.gallery.domain.model.Media
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.rotate_batch_done
import com.dot.gallery.generated.resources.rotate_batch_failed
import com.dot.gallery.generated.resources.rotate_batch_partial
import com.dot.gallery.generated.resources.rotate_batch_running
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.UUID
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton

/**
 * Runs a multi-item rotation as one [RotateMediaWorker] batch and mirrors its progress into the
 * floating progress pill and the per-cell rotation animation. Owns an app-lifetime scope so the
 * feedback survives the selection closing or the user leaving the screen.
 */
@Singleton
class BulkRotateCoordinator @Inject constructor(
    private val scheduler: BackgroundTaskScheduler,
    private val progressRegistry: FloatingProgressRegistry,
    private val rotationTracker: MediaRotationTracker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Starts the batch and returns its run id. Each pair carries the item's forced-copy flag. */
    fun rotate(items: List<Pair<Media, Boolean>>, degrees: Int): String? {
        if (items.isEmpty()) return null
        val runId = "rotate_batch_${UUID.randomUUID()}"
        val ids = items.map { it.first.id }
        rotationTracker.queue(ids, degrees)
        scope.launch {
            val label = getString(Res.string.rotate_batch_running)
            val running = FloatingProgressTask(
                id = runId,
                icon = Icons.AutoMirrored.Outlined.RotateRight,
                label = label,
                completed = 0,
                total = items.size,
            )
            progressRegistry.post(running)
            var last: RotateBatchProgress? = null
            try {
                withContext(Dispatchers.IO) { scheduler.rotateImages(runId, items, degrees) }
                WorkSignals.observe(runId, RotateMediaWorker.WORKER_NAME).first { info ->
                    RotateBatchProgress.from(info.progress)?.let { progress ->
                        if (progress != last) {
                            last = progress
                            applyPhases(ids, progress)
                            progressRegistry.post(running.copy(completed = progress.done))
                        }
                    }
                    info.isFinished || last?.isTerminal == true
                }
                // A cancelled or crashed run never reports a terminal payload: whatever is
                // still unfinished counts as failed.
                val final = last?.takeIf { it.isTerminal } ?: interruptedBatch(last, ids.size)
                applyPhases(ids, final)
                progressRegistry.post(summaryTask(running, final))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                printError(
                    tag = "worker.rotate",
                    message = "Batch rotation could not be tracked",
                    throwable = e,
                    ctx = mapOf("items" to "${ids.size}"),
                )
                ids.forEach(rotationTracker::markFailed)
                progressRegistry.post(
                    running.copy(
                        label = getString(Res.string.rotate_batch_failed),
                        state = FloatingProgressTask.State.Failed,
                    )
                )
            } finally {
                withContext(NonCancellable) {
                    delay(SUMMARY_VISIBLE_MS)
                    progressRegistry.dismiss(runId)
                    // Cells that were off-screen never ran their settle animation.
                    ids.forEach(rotationTracker::settle)
                    WorkSignals.clear(runId)
                }
            }
        }
        return runId
    }

    private fun applyPhases(ids: List<Long>, progress: RotateBatchProgress) {
        batchPhases(progress, ids.size).forEachIndexed { index, phase ->
            val id = ids[index]
            when (phase) {
                PendingMediaRotation.Phase.Queued -> Unit
                PendingMediaRotation.Phase.Processing -> rotationTracker.markProcessing(id)
                PendingMediaRotation.Phase.Done -> rotationTracker.markDone(id)
                PendingMediaRotation.Phase.Failed -> rotationTracker.markFailed(id)
            }
        }
    }

    private suspend fun summaryTask(
        running: FloatingProgressTask,
        final: RotateBatchProgress,
    ): FloatingProgressTask {
        val succeeded = final.total - final.failed.size
        return when {
            final.failed.isEmpty() -> running.copy(
                label = getPluralString(Res.plurals.rotate_batch_done, succeeded, succeeded),
                completed = final.total,
                state = FloatingProgressTask.State.Succeeded,
            )
            succeeded > 0 -> running.copy(
                label = getString(Res.string.rotate_batch_partial, succeeded, final.failed.size),
                completed = final.total,
                state = FloatingProgressTask.State.Failed,
            )
            else -> running.copy(
                label = getString(Res.string.rotate_batch_failed),
                state = FloatingProgressTask.State.Failed,
            )
        }
    }

    private companion object {
        const val SUMMARY_VISIBLE_MS = 2_500L
    }
}

/** Terminal stand-in for a run that stopped early: every item not confirmed done failed. */
internal fun interruptedBatch(last: RotateBatchProgress?, count: Int): RotateBatchProgress {
    val done = last?.done ?: 0
    return RotateBatchProgress(
        status = RotateMediaWorker.Status.FAILED,
        total = count,
        done = count,
        current = -1,
        failed = (0 until count).filterTo(mutableSetOf()) { it >= done || it in last?.failed.orEmpty() },
    )
}

/** Maps a batch progress payload to the animation phase of each of its [count] items. */
internal fun batchPhases(progress: RotateBatchProgress, count: Int): List<PendingMediaRotation.Phase> =
    List(count) { index ->
        when {
            index in progress.failed -> PendingMediaRotation.Phase.Failed
            index < progress.done -> PendingMediaRotation.Phase.Done
            index == progress.current -> PendingMediaRotation.Phase.Processing
            else -> PendingMediaRotation.Phase.Queued
        }
    }
