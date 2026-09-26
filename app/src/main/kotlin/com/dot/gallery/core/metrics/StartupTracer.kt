/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.metrics

import android.os.SystemClock
import android.util.Log
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.core.logging.LogLevel

/**
 * Lightweight startup telemetry. All spans are logged with tag [TAG]
 * so they can be filtered with `adb logcat -s StartupPerf`.
 *
 * **Only active on debug/staging builds.** On release builds every method
 * is a no-op and [trace] simply executes the block with zero overhead.
 *
 * Usage:
 * ```
 * val t = StartupTracer.begin("EncryptedDB.create")
 * // … work …
 * StartupTracer.end(t)
 * ```
 *
 * Or inline:
 * ```
 * val result = StartupTracer.trace("loadLibrary") { System.loadLibrary("sqlcipher") }
 * ```
 *
 * At any point call [dump] to print a summary of all recorded spans.
 */
object StartupTracer {

    private const val TAG = "StartupPerf"

    @JvmField
    val ENABLED = BuildConfig.BUILD_TYPE != "release"

    private val NOOP_SPAN = Span("", 0L)

    data class Span(
        val label: String,
        val startMs: Long,
        var endMs: Long = 0L,
        val threadName: String = Thread.currentThread().name
    ) {
        val durationMs get() = if (endMs > 0) endMs - startMs else -1L
    }

    private val spans = mutableListOf<Span>()
    private val appStartMs = SystemClock.elapsedRealtime()

    fun begin(label: String): Span {
        if (!ENABLED) return NOOP_SPAN
        val span = Span(label = label, startMs = SystemClock.elapsedRealtime())
        synchronized(spans) { spans.add(span) }
        // Rule exception (AGENTS.md §2): raw Log kept for the tag-filtered logcat stream; AppLog persists alongside.
        runCatching { Log.d(TAG, "▶ $label [thread=${Thread.currentThread().name}]") }
        AppLog.log(LogLevel.DEBUG, "app.startup", "▶ $label [thread=${Thread.currentThread().name}]")
        return span
    }

    fun end(span: Span) {
        if (!ENABLED) return
        span.endMs = SystemClock.elapsedRealtime()
        // Rule exception (AGENTS.md §2): raw Log kept for the tag-filtered logcat stream; AppLog persists alongside.
        runCatching { Log.d(TAG, "◀ ${span.label} → ${span.durationMs}ms [thread=${span.threadName}]") }
        AppLog.log(LogLevel.DEBUG, "app.startup", "◀ ${span.label} → ${span.durationMs}ms [thread=${span.threadName}]")
    }

    inline fun <T> trace(label: String, block: () -> T): T {
        if (!ENABLED) return block()
        val span = begin(label)
        return try {
            block()
        } finally {
            end(span)
        }
    }

    fun dump() {
        if (!ENABLED) return
        val totalMs = SystemClock.elapsedRealtime() - appStartMs
        synchronized(spans) {
            // Rule exception (AGENTS.md §2): raw Log kept for the tag-filtered logcat stream; AppLog persists alongside.
            runCatching { Log.i(TAG, "═══════════════════════════════════════════════") }
            AppLog.log(LogLevel.INFO, "app.startup", "═══════════════════════════════════════════════")
            runCatching { Log.i(TAG, " STARTUP TELEMETRY (${totalMs}ms since process start)") }
            AppLog.log(LogLevel.INFO, "app.startup", " STARTUP TELEMETRY (${totalMs}ms since process start)")
            runCatching { Log.i(TAG, "═══════════════════════════════════════════════") }
            AppLog.log(LogLevel.INFO, "app.startup", "═══════════════════════════════════════════════")
            spans.sortedBy { it.startMs }.forEach { s ->
                val offset = s.startMs - appStartMs
                val dur = if (s.durationMs >= 0) "${s.durationMs}ms" else "RUNNING"
                runCatching { Log.i(TAG, "  +${offset}ms  ${s.label}  $dur  [${s.threadName}]") }
                AppLog.log(LogLevel.INFO, "app.startup", "  +${offset}ms  ${s.label}  $dur  [${s.threadName}]")
            }
            runCatching { Log.i(TAG, "═══════════════════════════════════════════════") }
            AppLog.log(LogLevel.INFO, "app.startup", "═══════════════════════════════════════════════")
        }
    }
}
