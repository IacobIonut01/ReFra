/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printError
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExecutionRecord
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.QueriedTask
import dev.brewkits.kmpworkmanager.background.domain.ScheduleResult
import dev.brewkits.kmpworkmanager.background.domain.TaskChain
import dev.brewkits.kmpworkmanager.background.domain.TaskRequest
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import kotlinx.coroutines.flow.Flow

/**
 * Offline-variant safety net for WorkManager's network constraint tracking.
 *
 * The offline build strips ACCESS_NETWORK_STATE from the merged manifest, but androidx.work
 * reads ConnectivityManager state unconditionally in NetworkStateTracker24.readSystemState()
 * whenever a WorkSpec carries a network constraint — a library-internal call that throws
 * SecurityException inside a WorkManager coroutine, which app code cannot guard. To keep the
 * permission stripped without crashing, this guard
 *  - drops requiresNetwork / requiresUnmeteredNetwork from every task scheduled through the
 *    app scheduler while the permission is absent (tasks still run; network work simply
 *    fails fast without INTERNET, and the cloud workers early-return on empty configs), and
 *  - deletes already-persisted network-constrained WorkSpecs before KmpWorkManager
 *    initializes — e.g. ones left behind by a previous online install under the same
 *    application id, which would otherwise crash at startup before any app code runs — and
 *    installs a database trigger that clears required_network_type on every later INSERT or
 *    UPDATE, covering paths the decorator cannot reach (kmpworkmanager's own persisted task
 *    store, reschedules on the raw NativeTaskScheduler).
 */
object NetworkConstraintGuard {

    private const val WORK_MANAGER_DB = "androidx.work.workdb"
    private const val STRIP_TRIGGER = "refra_strip_network_constraints"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var wrapped: BackgroundTaskScheduler? = null

    /**
     * Installs the application context for [wrap] and, while ACCESS_NETWORK_STATE is
     * absent, sanitizes the persisted WorkManager database. Must run before
     * `KmpWorkManager.initialize` — WorkManager schedules persisted specs during init.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (!hasAccessNetworkState(context)) {
            sanitizeWorkDatabase(context)
        }
    }

    /**
     * Re-runs the database sanitization — purge plus write-time trigger — after
     * `KmpWorkManager.initialize` (whose init may create the database and reschedule
     * internally persisted tasks) and once more after first content.
     */
    fun rearm() {
        val context = appContext ?: return
        if (!hasAccessNetworkState(context)) {
            sanitizeWorkDatabase(context)
        }
    }

    /** Wraps [scheduler] in the constraint-stripping decorator while the permission is absent. */
    fun wrap(scheduler: BackgroundTaskScheduler): BackgroundTaskScheduler {
        val context = appContext ?: return scheduler
        if (hasAccessNetworkState(context)) return scheduler
        return wrapped ?: ConstraintStrippingScheduler(scheduler).also { wrapped = it }
    }

    private fun hasAccessNetworkState(context: Context): Boolean =
        context.packageManager.checkPermission(
            Manifest.permission.ACCESS_NETWORK_STATE,
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED

    private fun sanitizeWorkDatabase(context: Context) {
        val dbFile = context.getDatabasePath(WORK_MANAGER_DB)
        if (!dbFile.exists()) return
        runCatching {
            SQLiteDatabase.openDatabase(
                dbFile.path, null, SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                // FK cascade removes each spec's WorkTag/WorkName/SystemIdInfo/Dependency rows.
                db.execSQL("PRAGMA foreign_keys=ON")
                // Network-constrained WorkSpecs can also be written AFTER this point through
                // paths app code cannot wrap — kmpworkmanager's own persisted task store
                // (exact alarms, chain continuations enqueued via the raw NativeTaskScheduler
                // with constraints stored by an older build). The trigger clears the flag at
                // write time so no constrained spec can ever exist, regardless of writer.
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS $STRIP_TRIGGER " +
                        "AFTER INSERT ON WorkSpec WHEN NEW.required_network_type <> 0 BEGIN " +
                        "UPDATE WorkSpec SET required_network_type = 0 WHERE id = NEW.id; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS ${STRIP_TRIGGER}_update " +
                        "AFTER UPDATE OF required_network_type ON WorkSpec " +
                        "WHEN NEW.required_network_type <> 0 BEGIN " +
                        "UPDATE WorkSpec SET required_network_type = 0 WHERE id = NEW.id; END"
                )
                db.execSQL("DELETE FROM WorkSpec WHERE required_network_type <> 0")
            }
        }.onFailure {
            printError(
                "worker.network-guard",
                "workdb network-constraint sanitization failed",
                it
            )
        }
    }
}

/** Clears the flags that map onto androidx.work's NetworkType constraint. */
internal fun Constraints.withoutNetworkFlags(): Constraints =
    if (requiresNetwork || requiresUnmeteredNetwork) {
        copy(requiresNetwork = false, requiresUnmeteredNetwork = false)
    } else {
        this
    }

internal fun TaskRequest.withoutNetworkFlags(): TaskRequest =
    copy(constraints = constraints?.withoutNetworkFlags())

/**
 * [BackgroundTaskScheduler] decorator that clears network flags on every scheduled task so
 * androidx.work never instantiates its ConnectivityManager-reading NetworkStateTracker.
 * Used only when the merged manifest lacks ACCESS_NETWORK_STATE (the offline variant).
 */
internal class ConstraintStrippingScheduler(
    private val delegate: BackgroundTaskScheduler
) : BackgroundTaskScheduler {

    private fun Constraints.sanitized(): Constraints = withoutNetworkFlags().also {
        if (it !== this) {
            printDebug(
                "worker.network-guard",
                "dropped network constraint — ACCESS_NETWORK_STATE not declared"
            )
        }
    }

    override suspend fun enqueue(
        id: String,
        trigger: TaskTrigger,
        workerClassName: String,
        constraints: Constraints,
        inputJson: String?,
        policy: ExistingPolicy,
        tags: Set<String>,
        deadlineMs: Long?
    ): ScheduleResult = delegate.enqueue(
        id = id,
        trigger = trigger,
        workerClassName = workerClassName,
        constraints = constraints.sanitized(),
        inputJson = inputJson,
        policy = policy,
        tags = tags,
        deadlineMs = deadlineMs
    )

    // TaskChain binds the scheduler it was built on, so chain steps created here carry
    // sanitized constraints and chain.enqueue() routes back through enqueueChain below.
    override fun beginWith(task: TaskRequest): TaskChain =
        TaskChain(this, listOf(task.withoutNetworkFlags()))

    override fun beginWith(tasks: List<TaskRequest>): TaskChain =
        TaskChain(this, tasks.map(TaskRequest::withoutNetworkFlags))

    // TaskChain keeps its steps internal, so then()-appended steps arrive unsanitized even
    // when the chain was built through beginWith above. Read them back via the getter —
    // compile-time-internal, but public on the JVM — and rebuild the chain through the
    // public API when any step still carries a network constraint, since WorkManager would
    // crash scheduling such a step and the startup purge could never help.
    override suspend fun enqueueChain(chain: TaskChain, id: String?, policy: ExistingPolicy) =
        delegate.enqueueChain(chain.withoutNetworkFlags(), id, policy)

    private fun TaskChain.withoutNetworkFlags(): TaskChain {
        val steps = stepsOrNull() ?: return this
        if (steps.flatten().none { it.constraints?.hasNetworkFlags() == true }) return this
        var rebuilt: TaskChain? = null
        for (step in steps) {
            val sanitized = step.map(TaskRequest::withoutNetworkFlags)
            rebuilt = rebuilt?.then(sanitized) ?: TaskChain(delegate, sanitized)
        }
        return rebuilt ?: this
    }

    @Suppress("UNCHECKED_CAST")
    private fun TaskChain.stepsOrNull(): List<List<TaskRequest>>? = runCatching {
        javaClass.getMethod("getSteps\$kmpworker_release").invoke(this)
    }.getOrNull() as? List<List<TaskRequest>>

    private fun Constraints.hasNetworkFlags(): Boolean =
        requiresNetwork || requiresUnmeteredNetwork

    override fun cancel(id: String) = delegate.cancel(id)
    override fun cancelAll() = delegate.cancelAll()
    override fun cancelByTag(tag: String) = delegate.cancelByTag(tag)
    override fun cancelByWorkerClass(workerClassName: String) =
        delegate.cancelByWorkerClass(workerClassName)

    override fun flushPendingProgress() = delegate.flushPendingProgress()
    override suspend fun getExecutionHistory(limit: Int): List<ExecutionRecord> =
        delegate.getExecutionHistory(limit)

    override suspend fun clearExecutionHistory() = delegate.clearExecutionHistory()
    override fun observeTaskState(id: String): Flow<TaskState> = delegate.observeTaskState(id)
    override suspend fun queryTasks(
        tags: Set<String>,
        workerClassNames: Set<String>,
        states: Set<TaskState.Kind>
    ): List<QueriedTask> = delegate.queryTasks(tags, workerClassNames, states)
}
