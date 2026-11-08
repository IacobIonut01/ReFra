/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.memories

import com.dot.gallery.cloud.core.MemoryInfo
import com.dot.gallery.cloud.core.ProviderType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MemoriesOnThisDayTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val todayNoon = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun memory(type: String, createdAt: Long) = MemoryInfo(
        id = "m",
        type = type,
        year = 2023,
        providerType = ProviderType.IMMICH,
        serverConfigId = 1L,
        createdAt = createdAt,
    )

    @Test
    fun todaysOnThisDayMemoryLeadsThePage() {
        assertTrue(isOnThisDay(memory("on_this_day", todayNoon), today, ZoneOffset.UTC))
    }

    @Test
    fun olderOnThisDayMemoryIsAnEarlierMemory() {
        val yesterday = todayNoon - 24L * 60 * 60 * 1000
        assertFalse(isOnThisDay(memory("on_this_day", yesterday), today, ZoneOffset.UTC))
    }

    @Test
    fun otherMemoryTypesAndUndatedMemoriesAreEarlierMemories() {
        assertFalse(isOnThisDay(memory("trip", todayNoon), today, ZoneOffset.UTC))
        assertFalse(isOnThisDay(memory("on_this_day", 0L), today, ZoneOffset.UTC))
    }
}
