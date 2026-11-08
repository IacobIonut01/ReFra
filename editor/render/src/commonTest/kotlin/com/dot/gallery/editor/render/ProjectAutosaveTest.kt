package com.dot.gallery.editor.render

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The debounced-autosave contract: committed payloads persist after the
 * debounce, drafts (a null snapshot) never arm a write, equal payloads
 * do not re-arm, [ProjectAutosave.flush] lands a pending write now, and
 * [ProjectAutosave.reset] lets an identical document re-persist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectAutosaveTest {

    /** [tick] republishes like the session's position clock. */
    private data class State(
        val doc: Int?,
        val drafting: Boolean = false,
        val tick: Int = 0
    )

    private class Harness(scope: TestScope) {
        val state = MutableStateFlow(State(null))
        val writes = mutableListOf<Int>()
        val autosave = ProjectAutosave<State, Int>(
            scope = scope,
            state = state,
            debounceMs = 500,
            snapshot = { s -> if (s.drafting) null else s.doc },
            persist = { writes += it }
        )
    }

    @Test
    fun `a committed payload persists after the debounce`() = runTest {
        val h = Harness(this)
        h.autosave.start()
        h.state.value = State(doc = 1)
        runCurrent()
        // Nothing yet — the write is still inside the debounce window.
        assertEquals(emptyList(), h.writes)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(1), h.writes)
        h.autosave.cancel()
    }

    @Test
    fun `rapid commits coalesce into one write of the latest`() = runTest {
        val h = Harness(this)
        h.autosave.start()
        h.state.value = State(doc = 1)
        runCurrent()
        advanceTimeBy(300)
        h.state.value = State(doc = 2)
        runCurrent()
        advanceTimeBy(300)
        h.state.value = State(doc = 3)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        // Two partial windows then one full — only the last doc lands.
        assertEquals(listOf(3), h.writes)
        h.autosave.cancel()
    }

    @Test
    fun `a draft never persists, even mid-gesture`() = runTest {
        val h = Harness(this)
        h.autosave.start()
        h.state.value = State(doc = 1)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(1), h.writes)
        // A draft republishing a different live value must not write.
        h.state.value = State(doc = 2, drafting = true)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(1), h.writes)
        // The commit lands when the draft closes.
        h.state.value = State(doc = 2)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(1, 2), h.writes)
        h.autosave.cancel()
    }

    @Test
    fun `an unchanged document does not re-arm the timer`() = runTest {
        val h = Harness(this)
        h.autosave.start()
        h.state.value = State(doc = 1)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        // The 125 Hz position tick republishes fresh states that
        // snapshot to the same document — nothing re-writes.
        repeat(10) { tick ->
            h.state.value = State(doc = 1, tick = tick + 1)
            runCurrent()
            advanceTimeBy(1_000)
        }
        assertEquals(listOf(1), h.writes)
        h.autosave.cancel()
    }

    @Test
    fun `flush lands the pending write immediately`() = runTest {
        val h = Harness(this)
        h.autosave.start()
        h.state.value = State(doc = 7)
        runCurrent()
        h.autosave.flush()
        assertEquals(listOf(7), h.writes)
        // A second flush with nothing pending is a no-op.
        h.autosave.flush()
        assertEquals(listOf(7), h.writes)
        h.autosave.cancel()
    }

    @Test
    fun `reset re-persists an identical document`() = runTest {
        val h = Harness(this)
        h.autosave.start()
        h.state.value = State(doc = 1)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(1), h.writes)
        // A fresh source cleared its file — the same document must
        // persist again despite comparing equal to the last write.
        // StateFlow conflates identical values, so a null beat forces
        // the re-emission the session's own republish would produce.
        h.autosave.reset()
        h.state.value = State(doc = null)
        runCurrent()
        h.state.value = State(doc = 1)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(1, 1), h.writes)
        h.autosave.cancel()
    }

    @Test
    fun `cancel abandons a pending write`() = runTest {
        val h = Harness(this)
        h.autosave.start()
        h.state.value = State(doc = 9)
        runCurrent()
        h.autosave.cancel()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(emptyList(), h.writes)
    }
}
