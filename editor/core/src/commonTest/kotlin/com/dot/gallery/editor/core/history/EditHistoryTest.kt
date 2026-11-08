package com.dot.gallery.editor.core.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EditHistoryTest {

    private fun history(capacity: Int = 100) = EditHistory("v0", capacity)

    @Test
    fun `initial state is clean with no history`() {
        val h = history()
        assertEquals("v0", h.current)
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertFalse(h.isDirty)
        assertFalse(h.isDrafting)
        assertEquals(0L, h.revision)
        assertFailsWith<IllegalArgumentException> { EditHistory("x", 0) }
    }

    @Test
    fun `execute undo and redo round trip`() {
        val h = history()
        assertTrue(h.execute("edit") { it + "+a" })
        assertEquals("v0+a", h.current)
        assertTrue(h.isDirty)
        assertTrue(h.undo())
        assertEquals("v0", h.current)
        assertTrue(h.canRedo)
        assertTrue(h.redo())
        assertEquals("v0+a", h.current)
        assertFalse(h.canRedo)
    }

    @Test
    fun `a gesture commits as one undo step despite many updates`() {
        val h = history()
        h.begin("drag")
        assertTrue(h.isDrafting)
        repeat(100) { h.update("drag-$it") }
        assertEquals("drag-99", h.current)
        assertTrue(h.commit())
        assertFalse(h.isDrafting)
        // One undoable step: a single undo returns to the pre-gesture value.
        assertTrue(h.undo())
        assertEquals("v0", h.current)
        assertFalse(h.canUndo)
    }

    @Test
    fun `cancel restores pre-gesture value and keeps redo`() {
        val h = history()
        h.execute("edit") { it + "+a" }
        h.undo()
        h.begin("drag")
        h.update("half-done")
        h.cancel()
        assertEquals("v0", h.current)
        assertTrue(h.canRedo) // the +a step is still redoable
        assertTrue(h.redo())
        assertEquals("v0+a", h.current)
    }

    @Test
    fun `no-op commit records nothing and retains redo`() {
        val h = history()
        h.execute("edit") { it + "+a" }
        h.undo()
        assertTrue(h.canRedo)
        h.begin("drag")
        h.update("v0") // lands back on the same value
        assertFalse(h.commit())
        assertEquals("v0", h.current)
        assertFalse(h.canUndo)
        assertTrue(h.canRedo)
    }

    @Test
    fun `new branch clears redo`() {
        val h = history()
        h.execute("a") { it + "+a" }
        h.undo()
        assertTrue(h.canRedo)
        h.execute("b") { it + "+b" }
        assertFalse(h.canRedo)
    }

    @Test
    fun `capacity evicts the oldest steps`() {
        val h = EditHistory("v0", capacity = 2)
        h.execute("a") { it + "a" }
        h.execute("b") { it + "b" }
        h.execute("c") { it + "c" }
        assertTrue(h.undo())
        assertTrue(h.undo())
        // The "a" step was evicted; only two undos are available.
        assertFalse(h.canUndo)
        assertEquals("v0a", h.current)
    }

    @Test
    fun `dirty state follows the saved checkpoint`() {
        val h = history()
        h.execute("a") { it + "a" }
        assertTrue(h.isDirty)
        h.markSaved()
        assertFalse(h.isDirty)
        h.execute("b") { it + "b" }
        assertTrue(h.isDirty)
        h.undo()
        assertFalse(h.isDirty) // back at the saved value
        h.undo()
        assertTrue(h.isDirty) // before the saved value
    }

    @Test
    fun `throwing transform leaves everything unchanged`() {
        val h = history()
        h.execute("a") { it + "a" }
        val revision = h.revision
        val current = h.current
        val failure = assertFailsWith<IllegalStateException> {
            h.execute("boom") { throw IllegalStateException("boom") }
        }
        assertEquals("boom", failure.message)
        assertEquals(current, h.current)
        assertEquals(revision, h.revision)
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun `operations requiring no draft are rejected during one`() {
        val h = history()
        h.begin("drag")
        val revision = h.revision
        assertFailsWith<IllegalStateException> { h.begin("again") }
        assertFailsWith<IllegalStateException> { h.execute("x") { it } }
        assertFailsWith<IllegalStateException> { h.undo() }
        assertFailsWith<IllegalStateException> { h.redo() }
        assertFailsWith<IllegalStateException> { h.markSaved() }
        assertEquals(revision, h.revision)
        h.cancel()
        assertFalse(h.isDrafting)
    }

    @Test
    fun `draft operations without a draft are rejected`() {
        val h = history()
        assertFailsWith<IllegalStateException> { h.update("x") }
        assertFailsWith<IllegalStateException> { h.commit() }
        assertFailsWith<IllegalStateException> { h.cancel() }
    }

    @Test
    fun `no-op execute records nothing`() {
        val h = history()
        assertFalse(h.execute("same") { it })
        assertFalse(h.canUndo)
        assertEquals("v0", h.current)
    }

    @Test
    fun `canUndo and canRedo are false while drafting`() {
        val h = history()
        h.execute("a") { it + "+a" }
        h.execute("b") { it + "+b" }
        h.undo()
        assertTrue(h.canUndo)
        assertTrue(h.canRedo)

        h.begin("drag")
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)

        h.cancel()
        assertTrue(h.canUndo)
        assertTrue(h.canRedo)
    }

    @Test
    fun `update with the same value does not bump revision`() {
        val h = history()
        h.begin("drag")
        val afterBegin = h.revision
        h.update("v0") // equals current value
        assertEquals(afterBegin, h.revision)
        h.update("v1")
        assertTrue(h.revision > afterBegin)
        h.update("v1") // equal again: no bump
        assertEquals(h.revision, afterBegin + 1)
        h.cancel()
    }

    @Test
    fun `markSaved on an unchanged checkpoint is a no-op`() {
        val h = history()
        assertFalse(h.isDirty)
        val revision = h.revision
        h.markSaved()
        assertEquals(revision, h.revision)
        // But saving after a real change still bumps.
        h.execute("a") { it + "+a" }
        h.markSaved()
        assertTrue(h.revision > revision)
    }

    @Test
    fun `markSaved with an explicit value checkpoints a snapshot`() {
        val h = history()
        h.execute("edit") { it + "+a" }
        h.execute("edit") { it + "+b" }
        // An exported copy captured "+a" — the later "+b" stays dirty.
        h.markSaved("v0+a")
        assertTrue(h.isDirty)
        assertEquals("v0+a+b", h.current)
        h.undo()
        assertFalse(h.isDirty)
    }

    @Test
    fun `markSaved with a value is allowed during a draft and preserves it`() {
        val h = history()
        h.begin("drag")
        h.update("live")
        h.markSaved("live")
        assertTrue(h.isDrafting)
        assertEquals("live", h.current)
        assertFalse(h.isDirty)
        h.commit()
        assertFalse(h.isDirty)
        assertTrue(h.undo())
        assertTrue(h.isDirty)
    }

    @Test
    fun `markSaved with the current checkpoint is a no-op`() {
        val h = history()
        val revision = h.revision
        h.markSaved("v0")
        assertEquals(revision, h.revision)
    }

    @Test
    fun `revision increments on every observable change`() {
        val h = history()
        val r0 = h.revision
        h.begin("g")
        val r1 = h.revision
        h.update("x")
        val r2 = h.revision
        h.commit()
        val r3 = h.revision
        h.undo()
        val r4 = h.revision
        h.redo()
        val r5 = h.revision
        h.markSaved()
        val r6 = h.revision
        assertTrue(r0 < r1 && r1 < r2 && r2 < r3 && r3 < r4 && r4 < r5 && r5 < r6)
        assertNotEquals(r0, r6)
    }
}
