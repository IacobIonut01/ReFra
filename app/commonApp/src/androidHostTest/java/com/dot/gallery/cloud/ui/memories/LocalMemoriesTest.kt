/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.memories

import com.dot.gallery.domain.model.EncryptedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LocalMemoriesTest {

    private val today = LocalDate.of(2026, 10, 5)
    private var nextId = 1L

    private fun photosOn(date: LocalDate, count: Int) = (0 until count).map { index ->
        EncryptedMedia(
            id = nextId++,
            label = "photo",
            bytes = byteArrayOf(),
            path = "",
            relativePath = "",
            albumID = 0L,
            albumLabel = "",
            timestamp = date.atTime(10, index).toEpochSecond(ZoneOffset.UTC),
            fullDate = "",
            mimeType = "image/jpeg",
            favorite = 0,
            trashed = 0,
            size = 0L,
        )
    }

    @Test
    fun onThisDayGroupsPastYearsOnTodaysDate() {
        val media = photosOn(LocalDate.of(2023, 10, 5), 4) +
            photosOn(LocalDate.of(2021, 10, 5), 2) +
            photosOn(today, 9)

        val onThisDay = groupLocalMemories(media, today, ZoneOffset.UTC).filter { it.onThisDay }

        assertEquals(listOf(2023, 2021), onThisDay.map { it.year })
        assertEquals(listOf(4, 2), onThisDay.map { it.total })
    }

    @Test
    fun onThisDayWidensToNearbyDaysWhenTheDayIsSparse() {
        val media = photosOn(LocalDate.of(2024, 10, 3), 2) + photosOn(LocalDate.of(2024, 10, 20), 5)

        val onThisDay = groupLocalMemories(media, today, ZoneOffset.UTC).filter { it.onThisDay }

        assertEquals(listOf(2024), onThisDay.map { it.year })
        assertEquals(2, onThisDay.single().total)
    }

    @Test
    fun everyPastYearWithEnoughPhotosIsAnEarlierMemory() {
        val media = photosOn(LocalDate.of(2025, 3, 1), YEAR_MEMORY_MIN_ITEMS) +
            photosOn(LocalDate.of(2024, 6, 1), YEAR_MEMORY_MIN_ITEMS - 1) +
            photosOn(LocalDate.of(2022, 1, 1), 30) +
            photosOn(today.minusDays(40), 50)

        val earlier = groupLocalMemories(media, today, ZoneOffset.UTC).filterNot { it.onThisDay }

        assertEquals(listOf(2025, 2022), earlier.map { it.year })
        assertEquals(30, earlier.last().total)
        assertEquals(MEMORY_HIGHLIGHTS, earlier.last().highlights.size)
    }

    @Test
    fun highlightsSpreadAcrossTheWholeRun() {
        val items = (1..100).toList()

        val picked = spread(items, 5)

        assertEquals(listOf(1, 25, 50, 75, 100), picked)
        assertTrue(spread(items.take(3), 5) == items.take(3))
    }
}
