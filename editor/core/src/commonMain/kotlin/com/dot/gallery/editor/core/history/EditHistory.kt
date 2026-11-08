package com.dot.gallery.editor.core.history

import com.dot.gallery.editor.core.time.checkedAdd

/**
 * Undo/redo history over immutable document values.
 *
 * Two ways to record work:
 * - [execute] applies a transform atomically as one undoable step.
 * - [begin], then any number of [update] calls replacing the live draft,
 *   then [commit] lands the whole gesture as a single undoable step.
 *   [cancel] restores the value captured at [begin].
 *
 * [current] always shows the effective value — the draft while one is open.
 * While a draft is open [canUndo]/[canRedo] report false: the live draft is
 * not a committed step yet. Dirty state is value-equality against the
 * [markSaved] checkpoint, so undoing back to the saved value reports clean.
 * A new committed edit clears the redo stack; a no-op commit, a cancelled
 * draft, an [update] that leaves the value unchanged and a [markSaved] that
 * repeats the current checkpoint do not.
 *
 * Not thread-safe: all calls must happen on the owning (UI) thread.
 * [revision] is a strictly increasing counter bumped on every observable
 * state change; the next value is checked for overflow *before* any
 * mutation, so an overflow leaves the history untouched.
 */
class EditHistory<T : Any>(initial: T, private val capacity: Int = 100) {

    private class Entry<T : Any>(val label: String, val value: T)

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    // Oldest first; the tail is the most recent committed step.
    private val undoStack = ArrayDeque<Entry<T>>()
    private val redoStack = ArrayDeque<Entry<T>>()

    private var currentValue: T = initial
    private var savedValue: T = initial
    private var draftStartValue: T? = null
    private var draftLabel: String? = null
    private var revisionValue: Long = 0L

    val current: T get() = currentValue
    val canUndo: Boolean get() = !isDrafting && undoStack.isNotEmpty()
    val canRedo: Boolean get() = !isDrafting && redoStack.isNotEmpty()
    val isDirty: Boolean get() = currentValue != savedValue
    val isDrafting: Boolean get() = draftStartValue != null
    val revision: Long get() = revisionValue

    /** Opens a draft gesture. Nested drafts are rejected. */
    fun begin(label: String) {
        check(!isDrafting) { "nested draft: \"$label\" started while \"$draftLabel\" is open" }
        val newRevision = nextRevision()
        draftStartValue = currentValue
        draftLabel = label
        revisionValue = newRevision
    }

    /**
     * Replaces the draft value; requires an open draft. An [value] equal to
     * the current effective value is a no-op and does not bump [revision].
     */
    fun update(value: T) {
        check(isDrafting) { "update() requires an open draft" }
        if (value == currentValue) return
        val newRevision = nextRevision()
        currentValue = value
        revisionValue = newRevision
    }

    /**
     * Lands the open draft as one undoable step and returns true, or closes the
     * draft without recording anything (and keeps the redo stack) when the
     * draft value equals the value captured at [begin].
     */
    fun commit(): Boolean {
        val start = draftStartValue
        check(start != null) { "commit() requires an open draft" }
        val newRevision = nextRevision()
        val label = draftLabel.orEmpty()
        draftStartValue = null
        draftLabel = null
        revisionValue = newRevision
        if (currentValue == start) return false
        pushUndo(label, start)
        redoStack.clear()
        return true
    }

    /** Restores the pre-gesture value and keeps the redo stack. */
    fun cancel() {
        val start = draftStartValue
        check(start != null) { "cancel() requires an open draft" }
        val newRevision = nextRevision()
        currentValue = start
        draftStartValue = null
        draftLabel = null
        revisionValue = newRevision
    }

    /**
     * Applies [transform] to the current value as one undoable step. If the
     * transform throws, nothing is mutated — value, stacks and revision are all
     * unchanged. A transform returning an equal value records nothing and
     * returns false.
     */
    fun execute(label: String, transform: (T) -> T): Boolean {
        check(!isDrafting) { "execute() is not allowed during a draft" }
        val newValue = transform(currentValue)
        if (newValue == currentValue) return false
        val newRevision = nextRevision()
        pushUndo(label, currentValue)
        currentValue = newValue
        redoStack.clear()
        revisionValue = newRevision
        return true
    }

    fun undo(): Boolean {
        check(!isDrafting) { "undo() is not allowed during a draft" }
        val entry = undoStack.lastOrNull() ?: return false
        val newRevision = nextRevision()
        undoStack.removeLast()
        redoStack.addLast(Entry(entry.label, currentValue))
        currentValue = entry.value
        revisionValue = newRevision
        return true
    }

    fun redo(): Boolean {
        check(!isDrafting) { "redo() is not allowed during a draft" }
        val entry = redoStack.lastOrNull() ?: return false
        val newRevision = nextRevision()
        redoStack.removeLast()
        // Re-applied steps rejoin the undo stack; the bounded total cannot grow
        // past capacity because redo entries were originally pushed there.
        undoStack.addLast(Entry(entry.label, currentValue))
        currentValue = entry.value
        revisionValue = newRevision
        return true
    }

    /**
     * Records the current value as the clean checkpoint. Calling it when the
     * checkpoint already equals the current value is a no-op.
     */
    fun markSaved() {
        check(!isDrafting) { "markSaved() is not allowed during a draft" }
        if (savedValue == currentValue) return
        val newRevision = nextRevision()
        savedValue = currentValue
        revisionValue = newRevision
    }

    /**
     * Checkpoints an explicit snapshot as the saved state — for example an
     * exported copy captured before further edits landed. Unlike [markSaved]
     * this is allowed during a draft: the open draft and [current] are left
     * untouched, so dirtiness is still judged purely by value equality.
     */
    fun markSaved(value: T) {
        if (savedValue == value) return
        val newRevision = nextRevision()
        savedValue = value
        revisionValue = newRevision
    }

    private fun pushUndo(label: String, value: T) {
        undoStack.addLast(Entry(label, value))
        while (undoStack.size > capacity) {
            undoStack.removeFirst()
        }
    }

    /** Checked next revision; throws before any state is mutated. */
    private fun nextRevision(): Long = checkedAdd(revisionValue, 1L)
}
