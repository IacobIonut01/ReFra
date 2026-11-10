/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Cancels a background OCR run that an interactive (viewer) request pre-empted. Only that run's own
 * job is cancelled, so the caller's coroutine stays active and can read the photo again later.
 */
class OcrPreemptedException : CancellationException("OCR pre-empted by the viewer")

/**
 * Runs [block] again after [backoffMillis] each time the viewer pre-empts it, at most [maxAttempts]
 * times in all, and returns null if the last attempt is pre-empted too. Any other cancellation, and a
 * pre-emption that arrives after the caller itself was cancelled, propagates unchanged.
 */
suspend fun <T> retryWhenPreempted(
    maxAttempts: Int,
    backoffMillis: Long,
    block: suspend () -> T,
): T? {
    require(maxAttempts > 0 && backoffMillis >= 0)
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (preempted: OcrPreemptedException) {
            currentCoroutineContext().ensureActive()
            if (attempt < maxAttempts - 1) delay(backoffMillis)
        }
    }
    return null
}
