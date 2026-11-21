/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.cloud.sync.FREE_UP_SPACE_ANY_AGE_CUTOFF
import com.dot.gallery.cloud.sync.FreeUpSpaceScanOutcome
import com.dot.gallery.cloud.sync.freeUpSpaceScanOutcome
import com.dot.gallery.cloud.sync.passesFreeUpSpaceAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scan message must distinguish "nothing was old enough", "nothing is in a
 * backed-up album" and "backed-up items failed remote verification" — the old
 * single "none verified" string hid all three behind one message.
 */
class FreeUpSpaceScanOutcomeTest {

    private fun outcome(totalLocal: Int, backedUpLocal: Int, verifiedCount: Int = 0) =
        freeUpSpaceScanOutcome(verifiedCount, totalLocal, backedUpLocal)

    @Test
    fun verifiedWins() {
        assertEquals(
            FreeUpSpaceScanOutcome.VERIFIED,
            outcome(totalLocal = 4, backedUpLocal = 4, verifiedCount = 2)
        )
    }

    @Test
    fun nothingMatchesWhenNoCandidates() {
        assertEquals(
            FreeUpSpaceScanOutcome.NOTHING_MATCHES,
            outcome(totalLocal = 0, backedUpLocal = 0)
        )
    }

    @Test
    fun nothingBackedUpWhenNoEnabledDestination() {
        assertEquals(
            FreeUpSpaceScanOutcome.NOTHING_BACKED_UP,
            outcome(totalLocal = 3, backedUpLocal = 0)
        )
    }

    @Test
    fun noneVerifiedWhenBackedUpButUnmatched() {
        assertEquals(
            FreeUpSpaceScanOutcome.NONE_VERIFIED,
            outcome(totalLocal = 3, backedUpLocal = 2)
        )
    }

    @Test
    fun anyAgeAcceptsItemFromOneMinuteAgo() {
        val nowMs = 1_800_000_000_000L
        val oneMinuteAgoSeconds = (nowMs - 60_000L) / 1000L
        assertTrue(
            passesFreeUpSpaceAge(oneMinuteAgoSeconds, FREE_UP_SPACE_ANY_AGE_CUTOFF, nowMs)
        )
    }

    @Test
    fun thirtyDaysRejectsRecentAndAcceptsOlder() {
        val nowMs = 1_800_000_000_000L
        val oneMinuteAgoSeconds = (nowMs - 60_000L) / 1000L
        val thirtyOneDaysAgoSeconds = (nowMs - 31L * 86_400_000L) / 1000L
        assertFalse(passesFreeUpSpaceAge(oneMinuteAgoSeconds, 30, nowMs))
        assertTrue(passesFreeUpSpaceAge(thirtyOneDaysAgoSeconds, 30, nowMs))
    }

    @Test
    fun ninetyDaysRejectsEightyNineDays() {
        val nowMs = 1_800_000_000_000L
        val eightyNineDaysAgoSeconds = (nowMs - 89L * 86_400_000L) / 1000L
        assertFalse(passesFreeUpSpaceAge(eightyNineDaysAgoSeconds, 90, nowMs))
    }
}
