/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import com.dot.gallery.core.insight.CodeFormat
import com.dot.gallery.core.insight.CodeInsight
import com.dot.gallery.core.insight.CodePayload
import com.dot.gallery.core.insight.ImageQuad
import com.dot.gallery.core.insight.Insight
import com.dot.gallery.core.insight.InsightKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InsightControllerTest {

    private class FakeEngine(
        private val results: Map<String, List<Insight>> = emptyMap(),
        private val analyzeMs: Long = 30,
    ) : InsightEngine<String> {
        val calls = mutableListOf<Pair<String, Boolean>>()
        var cancelled = 0
        var released = 0

        override suspend fun analyze(target: String, thorough: Boolean): List<Insight> {
            calls += target to thorough
            try {
                delay(analyzeMs)
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
            return results[target].orEmpty()
        }

        override fun release() {
            released++
        }
    }

    private fun code(text: String) = CodeInsight(
        format = CodeFormat.QR_CODE,
        text = text,
        payload = CodePayload.Text(text),
        quad = ImageQuad(0.1f, 0.1f, 0.4f, 0.1f, 0.4f, 0.4f, 0.1f, 0.4f),
    )

    private fun key(id: Long) = InsightKey(id, "v$id")

    private fun TestScope.controller(engine: FakeEngine) = InsightController(
        engine = engine,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun fastSwipingAnalysesOnlyTheSettledPage() = runTest {
        val engine = FakeEngine(mapOf("p19" to listOf(code("hello"))))
        val controller = controller(engine)
        // 20 settles 15 ms apart: every one is replaced before the 120 ms settle delay ends.
        repeat(20) { page ->
            controller.request(key(page.toLong()), "p$page", InsightTrigger.AUTO)
            advanceTimeBy(15)
        }
        advanceUntilIdle()
        assertEquals(listOf("p19" to false), engine.calls)
        assertEquals(1, controller.analysesStarted)
        assertEquals(key(19), controller.state.value.key)
        assertEquals(1, controller.state.value.insights.size)
        controller.dispose()
    }

    @Test
    fun leavingAPageCancelsItsScanAndDropsTheResult() = runTest {
        val engine = FakeEngine(mapOf("a" to listOf(code("a"))), analyzeMs = 100)
        val controller = controller(engine)
        controller.request(key(1), "a", InsightTrigger.AUTO)
        advanceTimeBy(150) // inside analyze
        runCurrent()
        controller.request(key(2), "b", InsightTrigger.AUTO)
        advanceUntilIdle()
        assertEquals(1, engine.cancelled)
        assertEquals(key(2), controller.state.value.key)
        assertTrue(controller.state.value.insights.isEmpty())
        controller.dispose()
    }

    @Test
    fun emptyQuickPassFallsBackToThoroughPassAfterDelay() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.request(key(1), "a", InsightTrigger.AUTO)
        advanceTimeBy(InsightController.DEFAULT_SETTLE_DELAY_MS + 40)
        runCurrent()
        assertEquals(listOf("a" to false), engine.calls)
        advanceUntilIdle()
        assertEquals(listOf("a" to false, "a" to true), engine.calls)
        controller.dispose()
    }

    @Test
    fun cachedResultsComeBackWithoutAnalysing() = runTest {
        val engine = FakeEngine(mapOf("a" to listOf(code("a"))))
        val controller = controller(engine)
        controller.request(key(1), "a", InsightTrigger.AUTO)
        advanceUntilIdle()
        controller.request(key(2), "b", InsightTrigger.AUTO)
        advanceUntilIdle()
        val before = engine.calls.size
        controller.request(key(1), "a", InsightTrigger.AUTO)
        assertEquals(key(1), controller.state.value.key)
        assertEquals(1, controller.state.value.insights.size)
        advanceUntilIdle()
        assertEquals(before, engine.calls.size)
        controller.dispose()
    }

    @Test
    fun aRewrittenFileIsAnalysedAgain() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.request(InsightKey(1, "100:5"), "a", InsightTrigger.AUTO)
        advanceUntilIdle()
        controller.request(InsightKey(1, "200:6"), "a", InsightTrigger.AUTO)
        advanceUntilIdle()
        assertEquals(4, engine.calls.size)
        controller.dispose()
    }

    @Test
    fun passiveModeNeverStartsWork() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.request(key(1), "a", InsightTrigger.PASSIVE)
        advanceUntilIdle()
        assertTrue(engine.calls.isEmpty())
        assertEquals(key(1), controller.state.value.key)
        controller.dispose()
    }

    @Test
    fun manualScanSkipsTheDelayAndReportsNothingFound() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        val nothing = async { controller.nothingFound.first() }
        controller.request(key(1), "a", InsightTrigger.MANUAL)
        assertTrue(controller.state.value.manualScanRunning)
        advanceTimeBy(70)
        runCurrent()
        // Both passes ran back to back, with no settle or thorough delay.
        assertEquals(listOf("a" to false, "a" to true), engine.calls)
        advanceUntilIdle()
        assertEquals(key(1), nothing.await())
        assertFalse(controller.state.value.manualScanRunning)
        controller.dispose()
    }

    @Test
    fun cancelStopsAManualScanAndKeepsTheKey() = runTest {
        val engine = FakeEngine(analyzeMs = 100)
        val controller = controller(engine)
        controller.request(key(1), "a", InsightTrigger.MANUAL)
        advanceTimeBy(50)
        runCurrent()
        controller.cancel()
        advanceUntilIdle()
        assertEquals(1, engine.cancelled)
        assertFalse(controller.state.value.manualScanRunning)
        assertEquals(key(1), controller.state.value.key)
        controller.dispose()
    }

    @Test
    fun engineErrorsAreReportedAndShowNothing() = runTest {
        var reported: Throwable? = null
        val engine = object : InsightEngine<String> {
            override suspend fun analyze(target: String, thorough: Boolean): List<Insight> =
                throw IllegalStateException("boom")

            override fun release() = Unit
        }
        val controller = InsightController(engine, StandardTestDispatcher(testScheduler), onError = { reported = it })
        controller.request(key(1), "a", InsightTrigger.AUTO)
        advanceUntilIdle()
        assertEquals("boom", reported?.message)
        assertTrue(controller.state.value.insights.isEmpty())
        controller.dispose()
    }

    @Test
    fun disposeCancelsReleasesAndIgnoresLaterRequests() = runTest {
        val engine = FakeEngine(analyzeMs = 100)
        val controller = controller(engine)
        controller.request(key(1), "a", InsightTrigger.AUTO)
        advanceTimeBy(150)
        runCurrent()
        controller.dispose()
        advanceUntilIdle()
        assertEquals(1, engine.cancelled)
        assertEquals(1, engine.released)
        controller.request(key(2), "b", InsightTrigger.AUTO)
        advanceUntilIdle()
        assertEquals(1, engine.calls.size)
        assertNull(controller.state.value.key)
    }
}
