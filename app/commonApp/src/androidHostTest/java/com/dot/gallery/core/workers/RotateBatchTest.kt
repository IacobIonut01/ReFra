/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import com.dot.gallery.core.batchPhases
import com.dot.gallery.core.interruptedBatch
import com.dot.gallery.core.presentation.components.PendingMediaRotation.Phase
import com.dot.gallery.core.work.WorkInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A multi-select rotate runs as one sequential worker batch whose progress is keyed by its run
 * id, so the floating pill and the per-cell animation can follow each item.
 */
class RotateBatchTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val items = listOf(
        RotateItemInput("content://media/1", "image/jpeg", "a.jpg", forceCopy = false, sourceDateModified = 10L),
        RotateItemInput("content://media/2", "image/x-adobe-dng", "b.dng", forceCopy = true, sourceDateModified = 20L),
        RotateItemInput("content://media/3", null, null, forceCopy = false, sourceDateModified = 0L),
    )

    @Test
    fun batchInputRoundTripsEveryItemInOrder() {
        val input = WorkInput(rotateBatchInputJson(runId = "rotate_batch_x", degrees = 90, items = items))

        assertEquals("rotate_batch_x", input.getString(RotateMediaWorker.KEY_RUN_ID))
        assertEquals(90, input.getInt(RotateMediaWorker.KEY_ROTATION_DEGREES, 0))
        val decoded = input.getObjectArray(RotateMediaWorker.KEY_ITEMS)!!.map { RotateItemInput.from(it) }
        assertEquals(items, decoded)
    }

    @Test
    fun itemWithoutUriIsRejected() {
        assertNull(RotateItemInput.from(WorkInput("""{"label":"x.jpg"}""")))
    }

    @Test
    fun singleItemInputIsNotTreatedAsBatch() {
        assertNull(WorkInput("""{"media_uri":"content://media/1"}""").getObjectArray(RotateMediaWorker.KEY_ITEMS))
    }

    @Test
    fun progressPayloadRoundTrips() {
        val progress = RotateBatchProgress(
            status = RotateMediaWorker.Status.ROTATING,
            total = 5,
            done = 3,
            current = 3,
            failed = setOf(1),
        )
        assertEquals(progress, RotateBatchProgress.from(WorkInput.of(progress.toJson())))
        assertFalse(progress.isTerminal)
        assertTrue(progress.copy(status = RotateMediaWorker.Status.COMPLETED).isTerminal)
    }

    @Test
    fun singleItemStagePayloadIsNotBatchProgress() {
        assertNull(RotateBatchProgress.from(WorkInput("""{"status":2,"message":"Saving"}""")))
    }

    @Test
    fun phasesFollowDoneCurrentAndFailedItems() {
        val progress = RotateBatchProgress(
            status = RotateMediaWorker.Status.ROTATING,
            total = 4,
            done = 2,
            current = 2,
            failed = setOf(0),
        )
        assertEquals(
            listOf(Phase.Failed, Phase.Done, Phase.Processing, Phase.Queued),
            batchPhases(progress, 4),
        )
    }

    @Test
    fun interruptedRunFailsEveryUnconfirmedItem() {
        val last = RotateBatchProgress(
            status = RotateMediaWorker.Status.ROTATING,
            total = 4,
            done = 2,
            current = 2,
            failed = setOf(1),
        )
        val final = interruptedBatch(last, 4)

        assertTrue(final.isTerminal)
        assertEquals(setOf(1, 2, 3), final.failed)
        assertEquals(listOf(Phase.Done, Phase.Failed, Phase.Failed, Phase.Failed), batchPhases(final, 4))
        assertEquals(setOf(0, 1, 2), interruptedBatch(null, 3).failed)
    }

    @Test
    fun singleItemInputCarriesItsRunIdForViewerProgress() {
        // Progress used to be reported under the worker name, so the viewer observing the
        // enqueue id never saw a stage.
        val input = WorkInput(rotateSingleInputJson(runId = "rotate_abc", degrees = 90, item = items[0]))

        assertEquals("rotate_abc", input.getString(RotateMediaWorker.KEY_RUN_ID))
        assertEquals(items[0], RotateItemInput.from(input))
        assertNull(input.getObjectArray(RotateMediaWorker.KEY_ITEMS))
    }

    @Test
    fun checkpointSurvivesRestartAndClears() {
        val dir = tmp.newFolder("rotate_batch")
        RotateBatchCheckpoint(dir, "rotate_batch_1").apply {
            markAttempt(0)
            markCompleted(0)
            markAttempt(1)
        }
        // A re-run of the same batch (new worker instance) skips item 0 instead of rotating it
        // again, and reports item 1 (the process died on it) as failed instead of looping.
        val resumed = RotateBatchCheckpoint(dir, "rotate_batch_1")
        assertEquals(RotateBatchCheckpoint.State(completed = setOf(0), interrupted = setOf(1)), resumed.read())
        assertEquals(RotateBatchCheckpoint.State(emptySet(), emptySet()), RotateBatchCheckpoint(dir, "rotate_batch_2").read())

        resumed.clear()
        assertEquals(RotateBatchCheckpoint.State(emptySet(), emptySet()), RotateBatchCheckpoint(dir, "rotate_batch_1").read())
    }

    @Test
    fun checkpointNameCannotEscapeItsDirectory() {
        val dir = tmp.newFolder("rotate_batch")
        RotateBatchCheckpoint(dir, "../../evil").markCompleted(1)
        assertEquals(listOf("evil.idx"), dir.list()!!.toList())
    }
}
