/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline

import com.dot.gallery.app.presentation.timeline.components.timelineBackupProgressFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineBackupProgressPolicyTest {
    @Test
    fun unknownTotalRemainsIndeterminate() {
        assertNull(timelineBackupProgressFraction(4, 0))
    }

    @Test
    fun invalidTotalRemainsIndeterminate() {
        assertNull(timelineBackupProgressFraction(4, -1))
    }

    @Test
    fun knownCountsProduceTheActualFraction() {
        assertEquals(0.4f, timelineBackupProgressFraction(4, 10))
    }

    @Test
    fun negativeProgressCannotUnderfillTheTrack() {
        assertEquals(0f, timelineBackupProgressFraction(-1, 10))
    }

    @Test
    fun staleProgressCannotOverfillTheTrack() {
        assertEquals(1f, timelineBackupProgressFraction(Int.MAX_VALUE, 10))
    }
}
