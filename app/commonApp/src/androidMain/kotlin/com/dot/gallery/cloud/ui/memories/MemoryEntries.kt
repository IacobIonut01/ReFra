/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.memories

import androidx.compose.runtime.Immutable
import com.dot.gallery.cloud.core.MemoryInfo
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.definedTimestamp
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.min

/** One memory on the Memories page, from the device library or from a photo server. */
@Immutable
data class MemoryEntry(
    val key: String,
    val isLocal: Boolean,
    val onThisDay: Boolean,
    val year: Int,
    val assetCount: Int,
    val media: List<UriMedia>,
    /** Words for a memory without a year, such as a server's "trip". */
    val typeLabel: String,
    /** The day a "On this day" memory recalls. */
    val date: LocalDate?,
)

internal fun MemoryInfo.toEntry(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) = MemoryEntry(
    key = accountKey,
    isLocal = false,
    onThisDay = isOnThisDay(this, today, zone),
    year = year,
    assetCount = assetCount,
    media = media,
    typeLabel = type.replace("_", " ").replaceFirstChar { it.uppercase() },
    date = year.takeIf { it > 0 }?.let { runCatching { today.withYear(it) }.getOrNull() },
)

/** Whether [memory] is one of today's "On this day" memories rather than an earlier one. */
internal fun isOnThisDay(memory: MemoryInfo, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    memory.type == "on_this_day" && memory.createdAt > 0L &&
        Instant.ofEpochMilli(memory.createdAt).atZone(zone).toLocalDate() == today

/** A device memory before it is tied to [UriMedia]: a past year, or this day in a past year. */
internal data class LocalMemoryGroup<M : Media>(
    val year: Int,
    val onThisDay: Boolean,
    val total: Int,
    val highlights: List<M>,
)

/** "On this day" widens to this many days either side when the exact day has too few photos. */
internal const val ON_THIS_DAY_WINDOW_DAYS = 3
internal const val ON_THIS_DAY_MIN_ITEMS = 3
/** A past year needs this many photos to become a memory. */
internal const val YEAR_MEMORY_MIN_ITEMS = 5
internal const val MEMORY_HIGHLIGHTS = 20

private fun annualDistance(first: MonthDay, second: MonthDay): Long {
    val distance = ChronoUnit.DAYS.between(first.atYear(2000), second.atYear(2000)).absoluteValue
    return min(distance, 366L - distance)
}

/** Up to [count] items spread evenly across [items], keeping their order. */
internal fun <T> spread(items: List<T>, count: Int): List<T> {
    if (items.size <= count) return items
    return List(count) { index -> items[(index.toLong() * (items.size - 1) / (count - 1)).toInt()] }
}

/**
 * Builds device memories from [media]: "On this day" for every past year with photos taken on
 * today's date (widened to [ON_THIS_DAY_WINDOW_DAYS] when the exact day has too few), then one
 * memory per past year with at least [YEAR_MEMORY_MIN_ITEMS] photos. Each holds up to
 * [MEMORY_HIGHLIGHTS] photos spread across its span, newest first. Newest years first.
 */
internal fun <M : Media> groupLocalMemories(
    media: List<M>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<LocalMemoryGroup<M>> {
    val todayMonthDay = MonthDay.from(today)
    val past = media
        .map { it to Instant.ofEpochSecond(it.definedTimestamp).atZone(zone).toLocalDate() }
        .filter { (_, date) -> date.year < today.year }
        .sortedByDescending { (item, _) -> item.definedTimestamp }

    var onThisDay = past.filter { (_, date) -> MonthDay.from(date) == todayMonthDay }
    if (onThisDay.size < ON_THIS_DAY_MIN_ITEMS) {
        onThisDay = past.filter { (_, date) ->
            annualDistance(MonthDay.from(date), todayMonthDay) <= ON_THIS_DAY_WINDOW_DAYS
        }
    }

    fun groups(dated: List<Pair<M, LocalDate>>, isOnThisDay: Boolean, minItems: Int) = dated
        .groupBy { (_, date) -> date.year }
        .filterValues { it.size >= minItems }
        .toSortedMap(compareByDescending { it })
        .map { (year, items) ->
            LocalMemoryGroup(
                year = year,
                onThisDay = isOnThisDay,
                total = items.size,
                highlights = spread(items.map { it.first }, MEMORY_HIGHLIGHTS),
            )
        }

    return groups(onThisDay, isOnThisDay = true, minItems = 1) +
        groups(past, isOnThisDay = false, minItems = YEAR_MEMORY_MIN_ITEMS)
}

internal fun LocalMemoryGroup<UriMedia>.toEntry(today: LocalDate) = MemoryEntry(
    key = "local/${if (onThisDay) "on_this_day" else "year"}/$year",
    isLocal = true,
    onThisDay = onThisDay,
    year = year,
    assetCount = total,
    media = highlights,
    typeLabel = "",
    date = if (onThisDay) runCatching { today.withYear(year) }.getOrNull() else null,
)
