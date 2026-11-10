/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.engine.OcrPreemptedException
import com.dot.gallery.core.ocr.engine.retryWhenPreempted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OcrPreemptionTest {
    /**
     * The session manager cancels only the scope of the running inference. The pre-emption must reach
     * the indexer through the engine's dispatcher switch and the per-item timeout as a retryable
     * [OcrPreemptedException], leaving the indexer's own coroutine active (it used to end the run).
     */
    @Test
    fun preemptedRunIsRetriedAndTheCallerStaysActive() = runBlocking {
        var attempts = 0
        val result = retryWhenPreempted(maxAttempts = 3, backoffMillis = 0) {
            withTimeout(10_000) {
                withContext(Dispatchers.Default) {
                    attempts++
                    if (attempts == 1) inferenceCancelledByViewer() else "read"
                }
            }
        }
        assertEquals("read", result)
        assertEquals(2, attempts)
        assertTrue(isActive)
    }

    @Test
    fun returnsNullWhenEveryAttemptIsPreempted() = runTest {
        var attempts = 0
        val result = retryWhenPreempted<String>(maxAttempts = 3, backoffMillis = 1_000) {
            attempts++
            throw OcrPreemptedException()
        }
        assertNull(result)
        assertEquals(3, attempts)
    }

    @Test
    fun otherCancellationIsNotRetried() = runTest {
        var attempts = 0
        try {
            retryWhenPreempted<String>(maxAttempts = 3, backoffMillis = 0) {
                attempts++
                throw CancellationException("worker stopped")
            }
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertTrue(expected !is OcrPreemptedException)
        }
        assertEquals(1, attempts)
    }

    /** Mirrors OcrSessionManager.run: the viewer cancels the running scope's job with the pre-emption. */
    private suspend fun inferenceCancelledByViewer(): String = coroutineScope {
        val runScope = coroutineContext[Job]!!
        launch(Dispatchers.Default) { runScope.cancel(OcrPreemptedException()) }
        awaitCancellation()
    }
}
