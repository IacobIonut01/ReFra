/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.work

import dev.brewkits.kmpworkmanager.KmpWorkManager
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.QueriedTask
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import com.dot.gallery.injection.AppComponent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Bridges the kotlin-inject graph to kmpworkmanager's KSP-generated factory.
 * The generated [dev.brewkits.kmpworkmanager.generated.AndroidWorkerFactoryGenerated]
 * can only construct no-arg workers, so each worker exposes a no-arg `@Worker` adapter
 * that resolves its injected implementation through this holder (populated by the
 * Application at startup).
 */
object WorkComponentHolder {
    @Volatile
    lateinit var component: AppComponent
        private set

    fun install(component: AppComponent) {
        this.component = component
    }
}

/** The app-wide scheduler; initialized by the Application via `KmpWorkManager.initialize`. */
fun workScheduler(): BackgroundTaskScheduler =
    NetworkConstraintGuard.wrap(KmpWorkManager.getInstance().backgroundTaskScheduler)

/**
 * Typed view over a worker's JSON input — replaces `androidx.work.Data`.
 * Unknown/missing keys behave like `Data`: typed getters return defaults, `getString`
 * returns null.
 */
class WorkInput private constructor(
    @PublishedApi
    internal val json: JsonObject
) {

    constructor(raw: String?) : this(
        raw?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: EMPTY_JSON
    )

    fun getString(key: String): String? =
        json[key]?.jsonPrimitive?.takeIf { it.isString }?.content

    fun getInt(key: String, defaultValue: Int): Int =
        json[key]?.jsonPrimitive?.intOrNull ?: defaultValue

    fun getLong(key: String, defaultValue: Long): Long =
        json[key]?.jsonPrimitive?.longOrNull ?: defaultValue

    fun getIntOrNull(key: String): Int? = json[key]?.jsonPrimitive?.intOrNull

    fun getLongOrNull(key: String): Long? = json[key]?.jsonPrimitive?.longOrNull

    fun getFloat(key: String, defaultValue: Float): Float =
        json[key]?.jsonPrimitive?.floatOrNull ?: defaultValue

    fun getDouble(key: String, defaultValue: Double): Double =
        json[key]?.jsonPrimitive?.doubleOrNull ?: defaultValue

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        json[key]?.jsonPrimitive?.booleanOrNull ?: defaultValue

    fun getStringArray(key: String): Array<String>? =
        (json[key] as? JsonArray)?.map { it.jsonPrimitive.content }?.toTypedArray()

    fun getLongArray(key: String): LongArray? =
        (json[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.longOrNull }?.toLongArray()

    fun getIntArray(key: String): IntArray? =
        (json[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull }?.toIntArray()

    /** Array of nested objects (e.g. per-item batch inputs); non-object entries are skipped. */
    fun getObjectArray(key: String): List<WorkInput>? =
        (json[key] as? JsonArray)?.mapNotNull { element -> (element as? JsonObject)?.let { WorkInput(it) } }

    inline fun <reified T> hasKeyWithValueOfType(key: String): Boolean {
        val element = json[key] ?: return false
        return when (T::class) {
            String::class -> element is JsonPrimitive && element.isString
            Int::class -> element is JsonPrimitive && element.intOrNull != null
            Long::class -> element is JsonPrimitive && element.longOrNull != null
            Float::class -> element is JsonPrimitive && element.floatOrNull != null
            Double::class -> element is JsonPrimitive && element.doubleOrNull != null
            Boolean::class -> element is JsonPrimitive && element.booleanOrNull != null
            Array<String>::class -> element is JsonArray && element.all { it is JsonPrimitive && it.isString }
            else -> false
        }
    }

    val isEmpty: Boolean get() = json.isEmpty()

    companion object {
        private val EMPTY_JSON = JsonObject(emptyMap())
        private val EMPTY = WorkInput(EMPTY_JSON)

        val empty: WorkInput get() = EMPTY

        fun of(json: JsonObject?): WorkInput =
            json?.let { WorkInput(it) } ?: EMPTY
    }
}

/** `workDataOf` equivalent — serializes to the JSON string `enqueue(inputJson=)` takes. */
fun workDataJson(vararg pairs: Pair<String, Any?>): String =
    workData(*pairs).toString()

fun workData(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    for ((key, value) in pairs) {
        when (value) {
            null -> continue
            is String -> put(key, value)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Float -> put(key, value)
            is Double -> put(key, value)
            is Boolean -> put(key, value)
            is Array<*> -> put(key, JsonArray(value.map { JsonPrimitive(it?.toString()) }))
            is IntArray -> put(key, JsonArray(value.map { JsonPrimitive(it) }))
            is LongArray -> put(key, JsonArray(value.map { JsonPrimitive(it) }))
            is JsonElement -> put(key, value)
            else -> put(key, value.toString())
        }
    }
}

/** `WorkInfo.State.isFinished` equivalent — a task that can no longer run or emit events. */
val TaskState.isFinished: Boolean
    get() = this is TaskState.Succeeded || this is TaskState.Failed ||
        this is TaskState.Cancelled || this is TaskState.Unknown

/**
 * `getWorkInfosByTagFlow(tag)` equivalent — kmpworkmanager exposes no reactive tag stream,
 * so the tagged task set is re-queried on an interval while the returned flow is collected
 * (these flows are only collected by UI screens while visible).
 */
fun BackgroundTaskScheduler.taggedTasksFlow(
    tag: String,
    pollMs: Long = 1_000L,
): Flow<List<QueriedTask>> = flow {
    while (currentCoroutineContext().isActive) {
        emit(queryTasks(tags = setOf(tag)))
        delay(pollMs)
    }
}

/** Lightweight `WorkInfo` stand-in for observation sites: state + progress + output. */
data class WorkInfoLite(
    val state: TaskState,
    val progress: WorkInput,
    val output: WorkInput?,
) {
    val isFinished: Boolean
        get() = state is TaskState.Succeeded || state is TaskState.Failed || state is TaskState.Cancelled
    val succeeded: Boolean get() = state is TaskState.Succeeded
    val failed: Boolean get() = state is TaskState.Failed
}

/**
 * In-process channel carrying the typed progress payloads workers emit (the kmpworker
 * `WorkerProgress` type only models percent/message/step — our payloads are arbitrary
 * key/value JSON) and their terminal outputs. State comes from the scheduler's own
 * `observeTaskState`; progress/output ride [WorkSignals].
 */
object WorkSignals {
    private val _progress = MutableStateFlow<Map<String, JsonObject>>(emptyMap())
    private val _outputs = MutableStateFlow<Map<String, JsonObject>>(emptyMap())

    /** Worker-side: publish a progress payload under [workName] (the enqueue id / unique name). */
    fun reportProgress(workName: String, data: JsonObject) {
        _progress.update { it + (workName to data) }
    }

    /** Adapter-side: record a terminal output payload under the worker's registered name. */
    fun recordOutput(workerName: String, data: JsonObject?) {
        if (data != null) _outputs.update { it + (workerName to data) }
    }

    fun clear(workName: String) {
        _progress.update { it - workName }
    }

    /** Latest progress payload reported under [workName], or null if none yet. */
    fun latestProgress(workName: String): JsonObject? = _progress.value[workName]

    /** Latest terminal output recorded under [workerName], or null if none yet. */
    fun latestOutput(workerName: String): JsonObject? = _outputs.value[workerName]

    fun progressFlow(workName: String): Flow<JsonObject?> =
        _progress.map { it[workName] }.distinctUntilChanged()

    fun outputFlow(workerName: String): Flow<JsonObject?> =
        _outputs.map { it[workerName] }.distinctUntilChanged()

    /**
     * `getWorkInfosForUniqueWorkFlow(name)` equivalent — combines scheduler state,
     * latest progress payload, and terminal output into a single [WorkInfoLite] stream.
     */
    fun observe(workName: String, workerName: String = workName): Flow<WorkInfoLite> = combine(
        workScheduler().observeTaskState(workName),
        progressFlow(workName),
        outputFlow(workerName),
    ) { state, progress, output ->
        WorkInfoLite(state, WorkInput.of(progress), output?.let(WorkInput::of))
    }
}

/**
 * Business-logic worker constructed by kotlin-inject. Invoked by its no-arg `@Worker`
 * adapter (see *WorkerAdapter classes) which resolves the instance through
 * [WorkComponentHolder]. Not bound to kmpworkmanager's `AndroidWorker` interface so the
 * implementation stays directly unit-testable — call [doWork] with a fabricated
 * [WorkInput]/[WorkerEnvironment].
 */
interface GalleryWorker {
    suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult
}

/**
 * Base for the `@Worker`-annotated no-arg adapters: records terminal output into
 * [WorkSignals] so observers can read `outputData`-equivalents.
 */
abstract class GalleryWorkerAdapter(private val workName: String) :
    dev.brewkits.kmpworkmanager.background.domain.AndroidWorker {

    protected abstract suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult

    final override suspend fun doWork(input: String?, env: WorkerEnvironment): WorkerResult {
        val result = run(WorkInput(input), env)
        if (result is WorkerResult.Success) WorkSignals.recordOutput(workName, result.data)
        return result
    }
}

/**
 * `setProgress(workDataOf(...))` equivalent — emits a typed payload for [workName] and
 * mirrors a coarse percent into the kmpworker progress listener so TaskProgressBus
 * subscribers stay informed.
 */
suspend fun WorkerEnvironment.setProgress(workName: String, data: JsonObject, percent: Int? = null) {
    WorkSignals.reportProgress(workName, data)
    percent?.let { pct ->
        progressListener?.onProgressUpdate(
            dev.brewkits.kmpworkmanager.background.domain.WorkerProgress(pct.coerceIn(0, 100))
        )
    }
}
