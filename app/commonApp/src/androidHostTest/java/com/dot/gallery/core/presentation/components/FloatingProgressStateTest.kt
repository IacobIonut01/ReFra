/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingProgressStateTest {

    private fun task(id: String, completed: Int = 0) =
        FloatingProgressTask(id = id, icon = Icons.Outlined.Info, label = id, completed = completed, total = 4)

    @Test
    fun postReplacesTaskInPlaceAndDismissRemovesIt() {
        val registry = FloatingProgressRegistry()
        registry.post(task("a"))
        registry.post(task("b"))
        registry.post(task("a", completed = 2))

        assertEquals(listOf("a", "b"), registry.tasks.value.map { it.id })
        assertEquals(2, registry.tasks.value.first().completed)
        assertEquals(0.5f, registry.tasks.value.first().fraction)

        registry.dismiss("a")
        assertEquals(listOf("b"), registry.tasks.value.map { it.id })
    }

    @Test
    fun unknownTotalHasNoFraction() {
        assertNull(task("a").copy(total = 0).fraction)
    }

    @Test
    fun completedRotationBumpsRevisionOnceAndSettleKeepsIt() {
        val tracker = MediaRotationTracker()
        tracker.queue(listOf(1L, 2L), degrees = 90)
        tracker.markProcessing(1L)
        assertEquals(PendingMediaRotation.Phase.Processing, tracker.entry(1L)?.pending?.phase)

        tracker.markDone(1L)
        tracker.markDone(1L)
        assertEquals(1, tracker.entry(1L)?.revision)

        tracker.settle(1L)
        assertNull(tracker.entry(1L)?.pending)
        // The thumbnail keeps its new signature after the animation ends.
        assertEquals(1, tracker.entry(1L)?.revision)

        tracker.markFailed(2L)
        assertEquals(0, tracker.entry(2L)?.revision)
        assertEquals(PendingMediaRotation.Phase.Failed, tracker.entry(2L)?.pending?.phase)
    }

    @Test
    fun phaseChangesIgnoreUntrackedMedia() {
        val tracker = MediaRotationTracker()
        tracker.markDone(7L)
        tracker.settle(7L)
        assertNull(tracker.entry(7L))
    }
}
