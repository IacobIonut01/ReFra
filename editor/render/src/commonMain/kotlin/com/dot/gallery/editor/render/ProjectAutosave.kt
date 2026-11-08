package com.dot.gallery.editor.render

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Debounced autosave for a session's committed state. [snapshot] maps a
 * state emission to the payload worth persisting — returning null for a
 * live draft, an opening session or an absent document means tool and
 * gesture drafts can never be written out. Committed payloads debounce
 * for [debounceMs]; equal payloads do not re-arm the timer, so a 125 Hz
 * position tick republishing the same recipe costs nothing.
 *
 * [persist] runs on the owning scope — callers hop to a worker inside
 * it. A payload captured while committed is still committed when the
 * debounce lands inside a later draft, so the in-flight write is never
 * interrupted.
 */
class ProjectAutosave<S : Any, P : Any>(
    private val scope: CoroutineScope,
    private val state: StateFlow<S>,
    private val debounceMs: Long = 500,
    private val snapshot: (S) -> P?,
    private val persist: suspend (P) -> Unit
) {
    private var collectJob: Job? = null
    private var writeJob: Job? = null
    private var pending: P? = null
    private var lastWritten: P? = null
    private var started = false

    /** Starts collecting; idempotent. */
    fun start() {
        if (started) return
        started = true
        collectJob = scope.launch {
            state.collect { state ->
                val payload = snapshot(state) ?: return@collect
                if (payload != lastWritten && payload != pending) {
                    pending = payload
                    writeJob?.cancel()
                    writeJob = scope.launch {
                        delay(debounceMs)
                        val armed = pending ?: return@launch
                        pending = null
                        persist(armed)
                        lastWritten = armed
                    }
                }
            }
        }
    }

    /**
     * Forces the pending write to land now — the host calls this from
     * its stop path so the last committed edit is not left inside the
     * debounce window when the process can be killed.
     */
    suspend fun flush() {
        writeJob?.cancel()
        writeJob = null
        val armed = pending ?: return
        pending = null
        persist(armed)
        lastWritten = armed
    }

    /**
     * Drops the write-through memory — a host clearing its persisted
     * file on a fresh source must call this, or an identical next
     * document would compare equal to [lastWritten] and never be
     * re-persisted.
     */
    fun reset() {
        writeJob?.cancel()
        writeJob = null
        pending = null
        lastWritten = null
    }

    /** Stops collecting; a pending write is abandoned un-persisted. */
    fun cancel() {
        collectJob?.cancel()
        writeJob?.cancel()
        collectJob = null
        writeJob = null
        pending = null
    }
}
