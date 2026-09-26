@file:Suppress("KotlinConstantConditions")

package com.dot.gallery.feature_node.presentation.util

import android.util.Log
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.core.logging.LogLevel

private const val TAG = "GalleryInfo"

// Repo rule (AGENTS.md §2): all app logging goes through these helpers — no
// direct android.util.Log.*, printStackTrace(), or println elsewhere in app
// code. android.util.Log.* throws ("not mocked") on the JVM — the logcat call
// must never break the caller or the AppLog path, so it is fire-and-forget.
private fun logcat(block: () -> Int) {
    runCatching { block() }
}

fun printInfo(message: Any) {
    val text = message.toString()
    logcat { Log.i(TAG, text) }
    AppLog.log(LogLevel.INFO, TAG, text)
}

fun printInfo(tag: String, message: String) {
    logcat { Log.i(tag, message) }
    AppLog.log(LogLevel.INFO, tag, message)
}

fun printDebug(message: Any) {
    printDebug(message.toString())
}

fun printDebug(message: String) {
    if (BuildConfig.BUILD_TYPE != "release") {
        logcat { Log.d(TAG, message) }
    }
    AppLog.log(LogLevel.DEBUG, TAG, message)
}

/** Tagged debug log — the tag prefix (e.g. `decode.heif`) sets the By-area scope. */
fun printDebug(tag: String, message: String) {
    if (BuildConfig.BUILD_TYPE != "release") {
        logcat { Log.d(tag, message) }
    }
    AppLog.log(LogLevel.DEBUG, tag, message)
}

fun printError(message: String) {
    logcat { Log.e(TAG, message) }
    AppLog.log(LogLevel.ERROR, TAG, message)
}

/**
 * Structured error log. [ctx] carries debug context (counts, sizes, types);
 * [scope] names the feature domain (`cloud.immich`, `worker.upload`,
 * `media-ops`) for the By-area grouping. Values are anonymized on write.
 */
fun printError(
    tag: String,
    message: String,
    throwable: Throwable? = null,
    ctx: Map<String, String>? = null,
    scope: String? = null,
) {
    logcat { Log.e(tag, message, throwable) }
    AppLog.log(LogLevel.ERROR, tag, message, throwable, ctx, scope)
}

fun printWarning(message: String) {
    logcat { Log.w(TAG, message) }
    AppLog.log(LogLevel.WARN, TAG, message)
}

fun printWarn(
    tag: String,
    message: String,
    ctx: Map<String, String>? = null,
    scope: String? = null,
) {
    logcat { Log.w(tag, message) }
    AppLog.log(LogLevel.WARN, tag, message, null, ctx, scope)
}

/** Records a user-facing action as a breadcrumb + EVENT log entry. */
fun logEvent(action: String, ctx: Map<String, String>? = null, scope: String? = null) {
    AppLog.breadcrumb(action, ctx, scope)
}
