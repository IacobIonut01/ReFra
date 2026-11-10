/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.ocr.catalog.OcrCatalogModel
import com.dot.gallery.core.ocr.catalog.OcrModelRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Owns the ONNX sessions: the detector plus an LRU of [MAX_RECOGNIZER_SESSIONS] recognizers, created
 * lazily and closed on [release] (low memory, the switch turned off, packs changed). One inference runs
 * at a time; an interactive request cancels a running background one, and cancellation terminates
 * the native run through `RunOptions.setTerminate`.
 */
class OcrSessionManager(private val source: OcrModelSource) {
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private val executionMutex = Mutex()
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectorSession: OrtSession? = null
    private val recognizerSessions = LinkedHashMap<String, OrtSession>(4, 0.75f, true)

    @Volatile
    private var activeRequest: Pair<OcrRequestPriority, Job?>? = null

    suspend fun <T> run(
        model: OcrCatalogModel,
        priority: OcrRequestPriority,
        input: FloatBuffer,
        shape: LongArray,
        transform: (OrtSession.Result) -> T,
    ): T {
        activeRequest?.let { (activePriority, job) ->
            if (priority == OcrRequestPriority.INTERACTIVE && activePriority != OcrRequestPriority.INTERACTIVE) {
                job?.cancel(OcrPreemptedException())
            }
        }
        return executionMutex.withLock {
            coroutineScope {
                currentCoroutineContext().ensureActive()
                // Pre-emption cancels only this run's scope: the caller sees an OcrPreemptedException
                // and its own job stays active, so a background indexer can read the photo again.
                activeRequest = priority to coroutineContext[Job]
                val session = session(model)
                val runOptions = OrtSession.RunOptions().apply {
                    // Give the arena's memory back after every run: it otherwise keeps the largest
                    // detector input it has seen (about 600 MB after one 2048 px photo on a Pixel 10 Pro).
                    addRunConfigEntry("memory.enable_memory_arena_shrinkage", "cpu:0")
                }
                val watcher = launch(Dispatchers.Default) {
                    try {
                        awaitCancellation()
                    } finally {
                        runCatching { runOptions.setTerminate(true) }
                            .onFailure { printWarn(TAG, "Could not terminate an OCR run", mapOf("error" to it.toString())) }
                    }
                }
                try {
                    OnnxTensor.createTensor(environment, input, shape).use { tensor ->
                        val name = model.tensorContract.inputs.single().name
                        withContext(Dispatchers.Default) { session.run(mapOf(name to tensor), runOptions) }.use(transform)
                    }
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    throw e
                } finally {
                    watcher.cancelAndJoin()
                    runOptions.close()
                    activeRequest = null
                }
            }
        }
    }

    /**
     * Closes every session once the running inference (if any) has finished, without waiting for it.
     * For callers on the main thread (low-memory callbacks): [release] waits for the execution lock,
     * which an inference holds for its whole run.
     */
    fun releaseAsync() {
        releaseScope.launch { release() }
    }

    /** Closes every session; the next run opens them again. */
    suspend fun release() = executionMutex.withLock {
        detectorSession?.close()
        detectorSession = null
        recognizerSessions.values.forEach(OrtSession::close)
        recognizerSessions.clear()
    }

    private fun session(model: OcrCatalogModel): OrtSession = when (model.role) {
        OcrModelRole.DETECTOR -> detectorSession ?: create(model).also { detectorSession = it }
        OcrModelRole.RECOGNIZER -> recognizerSessions[model.id] ?: create(model).also { session ->
            recognizerSessions[model.id] = session
            while (recognizerSessions.size > MAX_RECOGNIZER_SESSIONS) {
                val eldest = recognizerSessions.entries.iterator().next()
                recognizerSessions.remove(eldest.key)
                eldest.value.close()
            }
        }
    }

    private fun create(model: OcrCatalogModel): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(3)
            setInterOpNumThreads(1)
        }
        return try {
            source.open(model).use { handle ->
                // ORT copies the model bytes into the session, so a mapped document can be closed afterwards.
                val session = when (handle) {
                    is OcrModelHandle.Path -> environment.createSession(handle.path, options)
                    is OcrModelHandle.Buffer -> environment.createSession(handle.buffer, options)
                }
                check(session.inputNames == model.tensorContract.inputs.mapTo(linkedSetOf()) { it.name }) {
                    "Unexpected inputs for ${model.id}"
                }
                session
            }
        } finally {
            options.close()
        }
    }

    private companion object {
        const val TAG = "ml.ocr"
        const val MAX_RECOGNIZER_SESSIONS = 2
    }
}
