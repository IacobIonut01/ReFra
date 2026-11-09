/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.runtime.Stable
import com.dot.gallery.core.insight.Insight
import com.dot.gallery.core.insight.InsightKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Schedules photo analysis for one viewer screen.
 *
 * - One job at a time: every request cancels the previous one, so fast swiping never piles up
 *   work, and a result is only published while its key is still the requested one.
 * - Automatic requests wait [settleDelayMs] first, so a page that is only passed through is
 *   never analysed. If the quick pass finds nothing, the thorough pass runs after a further
 *   [thoroughDelayMs] of the photo staying on screen.
 * - Results (including "nothing") are kept in a small LRU, so swiping back is instant.
 * - All analysis runs on [dispatcher]; callers on the main thread only post requests.
 */
@Stable
class InsightController<T>(
    private val engine: InsightEngine<T>,
    private val dispatcher: CoroutineDispatcher,
    private val settleDelayMs: Long = DEFAULT_SETTLE_DELAY_MS,
    private val thoroughDelayMs: Long = DEFAULT_THOROUGH_DELAY_MS,
    private val cacheSize: Int = DEFAULT_CACHE_SIZE,
    private val onError: (Throwable) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Any()
    private val cache = object : LinkedHashMap<InsightKey, List<Insight>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<InsightKey, List<Insight>>?) =
            size > cacheSize
    }

    private var job: Job? = null
    private var jobKey: InsightKey? = null
    private var generation = 0L
    private var disposed = false

    private val _state = MutableStateFlow(InsightState())
    val state: StateFlow<InsightState> = _state.asStateFlow()

    private val _nothingFound = MutableSharedFlow<InsightKey>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits when a scan the person asked for found nothing. */
    val nothingFound: SharedFlow<InsightKey> = _nothingFound

    /** Number of analyses started; for tests and diagnostics. */
    var analysesStarted: Int = 0
        private set

    fun request(key: InsightKey, target: T, trigger: InsightTrigger) {
        synchronized(lock) {
            if (disposed) return
            val cached = cache[key]
            if (cached != null) {
                cancelLocked()
                _state.value = InsightState(key, cached)
                if (trigger == InsightTrigger.MANUAL && cached.isEmpty()) _nothingFound.tryEmit(key)
                return
            }
            if (trigger == InsightTrigger.AUTO && jobKey == key && job?.isActive == true) return
            cancelLocked()
            val manual = trigger == InsightTrigger.MANUAL
            _state.value = InsightState(key, emptyList(), manualScanRunning = manual)
            if (trigger == InsightTrigger.PASSIVE) return
            val myGeneration = generation
            jobKey = key
            job = scope.launch {
                try {
                    if (!manual) delay(settleDelayMs)
                    analysesStarted++
                    var found = engine.analyze(target, thorough = false)
                    if (found.isEmpty()) {
                        if (!manual) delay(thoroughDelayMs)
                        ensureActive()
                        found = engine.analyze(target, thorough = true)
                    }
                    ensureActive()
                    publish(myGeneration, key, found, manual)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    onError(e)
                    publish(myGeneration, key, emptyList(), manual = false)
                }
            }
        }
    }

    /** Stops any scan and clears what is shown (page left, setting turned off, cutout started). */
    fun clear() {
        synchronized(lock) {
            cancelLocked()
            _state.value = InsightState()
        }
    }

    /** Cancels a running scan without clearing cached results. */
    fun cancel() {
        synchronized(lock) {
            val key = _state.value.key
            cancelLocked()
            _state.value = InsightState(key, key?.let { cache[it] }.orEmpty())
        }
    }

    /** Releases pooled memory (on low memory); the next scan allocates again. */
    fun trimMemory() {
        scope.launch { engine.release() }
    }

    fun dispose() {
        synchronized(lock) {
            if (disposed) return
            disposed = true
            cancelLocked()
            cache.clear()
            _state.value = InsightState()
        }
        scope.launch { engine.release() }.invokeOnCompletion { scope.cancel() }
    }

    private fun publish(myGeneration: Long, key: InsightKey, found: List<Insight>, manual: Boolean) {
        synchronized(lock) {
            if (disposed || myGeneration != generation) return
            cache[key] = found
            _state.value = InsightState(key, found)
            jobKey = null
            job = null
            if (manual && found.isEmpty()) _nothingFound.tryEmit(key)
        }
    }

    private fun cancelLocked() {
        generation++
        job?.cancel()
        job = null
        jobKey = null
    }

    companion object {
        const val DEFAULT_SETTLE_DELAY_MS = 120L
        const val DEFAULT_THOROUGH_DELAY_MS = 400L
        const val DEFAULT_CACHE_SIZE = 64
    }
}
