/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Coroutine context element carrying a feature scope (`library`, `editor`,
 * `cloud.immich`, `worker.upload`) through suspend calls. [AppLog] reads it
 * when writing an entry so work launched from a screen keeps that screen's
 * attribution even after it moves onto a worker or IO dispatcher:
 *
 * ```
 * viewModelScope.launch(logScopeContext("library")) { … }
 * // or
 * withLogScope("media-ops") { copyFiles(…) }
 * ```
 *
 * The scope also rides a [ThreadLocal] ([ambientScope]) so plain non-suspend
 * log calls (`printError`, `printDebug`, …) inside the block pick it up too —
 * no call-site changes needed.
 */
class LogScope(val scope: String) : AbstractCoroutineContextElement(LogScope) {
    companion object Key : CoroutineContext.Key<LogScope>
}

private val ambientScope = ThreadLocal<String?>()

/** The ambient scope visible to non-suspend callers, if any. */
internal fun currentAmbientLogScope(): String? = ambientScope.get()

/** Context element bundle for `launch`/`async`: ambient `LogScope` + thread-local propagation. */
fun logScopeContext(scope: String): CoroutineContext =
    LogScope(scope) + ambientScope.asContextElement(scope)

suspend fun <T> withLogScope(scope: String, block: suspend () -> T): T =
    withContext(logScopeContext(scope)) { block() }

/** The ambient scope for the calling suspend context, if any. */
suspend fun currentLogScope(): String? = coroutineContext[LogScope]?.scope
