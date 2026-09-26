/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core

import android.util.Log
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.core.logging.LogLevel

/**
 * Lightweight tracing for the cloud media pipeline (all providers: SMB/NFS, WebDAV, Immich, …).
 *
 * Logs to the `CloudTrace` tag and is a no-op on release builds, so the instrumentation can stay
 * permanently in place. Use [time]/[timeNullable] to record how long each stage takes — the slow
 * SMB loads are easiest to diagnose by seeing which stage (network read vs decode) dominates.
 *
 * Filter logs with: `adb logcat -s CloudTrace`
 */
object CloudTrace {
    const val TAG = "CloudTrace"

    /** Always on for non-release builds (debug + staging). */
    private val buildEnabled: Boolean = BuildConfig.BUILD_TYPE != "release"

    /**
     * Enabled on non-release builds, or whenever the user turns on "Verbose logging" in the
     * cloud Advanced settings (so release builds can be diagnosed on demand).
     */
    val enabled: Boolean get() = buildEnabled || CloudRuntimeSettings.verboseLoggingEnabled

    private val appLogTag: String = if ('.' in TAG) TAG else "cloud.trace"

    fun d(message: String) {
        if (enabled) {
            // Rule exception (AGENTS.md §2): raw Log kept for the `adb logcat -s CloudTrace` stream; AppLog persists alongside.
            runCatching { Log.d(TAG, message) }
            AppLog.log(LogLevel.DEBUG, appLogTag, message)
        }
    }

    fun w(message: String, t: Throwable? = null) {
        if (enabled) {
            // Rule exception (AGENTS.md §2): raw Log kept for the `adb logcat -s CloudTrace` stream; AppLog persists alongside.
            runCatching { Log.w(TAG, message, t) }
            AppLog.log(LogLevel.WARN, appLogTag, message, t)
        }
    }

    /** Human-readable byte size, e.g. "14.0 MB". */
    fun bytes(n: Long): String = when {
        n < 0 -> "?"
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
        else -> "%.1f MB".format(n / (1024.0 * 1024.0))
    }

    /** Times [block], logging "<label> took <ms>ms" (annotating failures). */
    inline fun <T> time(label: String, block: () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        var failure: Throwable? = null
        try {
            return block()
        } catch (t: Throwable) {
            failure = t
            throw t
        } finally {
            val ms = (System.nanoTime() - start) / 1_000_000
            if (failure == null) d("$label took ${ms}ms")
            else w("$label FAILED after ${ms}ms: ${failure.javaClass.simpleName}: ${failure.message}")
        }
    }
}
