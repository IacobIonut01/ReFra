/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.date.epochSecToLocalDate
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.TimelineDateSource
import com.dot.gallery.domain.model.isBigHeaderKey
import com.dot.gallery.domain.model.timestampFor
import com.dot.gallery.domain.util.isFavorite
import com.dot.gallery.domain.util.isVideo

/** A place named in the timeline: the city a photo was taken in, with its country for lookups. */
@Immutable
data class TimelinePlace(val city: String, val country: String)

/**
 * One calendar month of the timeline.
 *
 * @param key `year * 100 + month`, the same key [MonthSegments] uses.
 * @param places Cities of the month, most photographed first.
 * @param highlights The month's best photos, hero first (see [pickMonthHighlights]).
 */
@Immutable
data class TimelineMonth(
    val key: Int,
    val year: Int,
    /** Month and year, "October 2026". */
    val label: String,
    /** The month name alone, "October", for months of the current year. */
    val name: String,
    val photoCount: Int,
    val videoCount: Int,
    val places: List<TimelinePlace>,
    val highlights: List<Media>,
    val mediaIds: Set<Long>,
    /** The month's first item in timeline order, where its slideshow starts. */
    val firstMediaId: Long = mediaIds.firstOrNull() ?: -1L,
) {
    val itemCount: Int get() = photoCount + videoCount

    /** The month as its header names it: the year is left out for the current year. */
    fun title(currentYear: Int): String = if (year == currentYear) name else label
}

/**
 * Month and day facts the timeline decorates its headers with, built once per media or metadata
 * change off the main thread.
 */
@Immutable
class TimelineIndex(
    /** Months, in the timeline's order. */
    val months: List<TimelineMonth>,
    private val monthByKey: Map<Int, TimelineMonth>,
    private val monthKeyByHeaderKey: Map<String, Int>,
    private val placeByHeaderKey: Map<String, TimelinePlace>,
) {
    fun month(key: Int): TimelineMonth? = monthByKey[key]

    /** The month a `header_big_*` month header introduces. */
    fun monthForHeader(headerKey: String): TimelineMonth? =
        monthKeyByHeaderKey[headerKey]?.let(monthByKey::get)

    /** The most photographed city of a day header's media. */
    fun placeForHeader(headerKey: String): TimelinePlace? = placeByHeaderKey[headerKey]

    companion object {
        val Empty = TimelineIndex(emptyList(), emptyMap(), emptyMap(), emptyMap())
    }
}

/** Months with fewer items keep the plain header instead of a highlight card. */
const val MONTH_HIGHLIGHT_MIN_ITEMS = 12

/** Hero plus two highlights on the card; the months view tile shows four. */
const val MONTH_HIGHLIGHT_COUNT = 4

fun monthKeyOf(timestampSec: Long): Int {
    val date = timestampSec.epochSecToLocalDate()
    return date.year * 100 + date.monthNumber
}

/** "October 2026": the month name with its year, as the scroller and month views show it. */
fun monthLabelOf(timestampSec: Long): String =
    "${timestampSec.formatEpochSec("LLLL")} ${timestampSec.epochSecToLocalDate().year}"

/**
 * Builds the [TimelineIndex] for [media] (the flat list, every group member included) and the
 * [mappedData] the grid draws.
 */
fun <T : Media> buildTimelineIndex(
    media: List<T>,
    mappedData: List<MediaItem<T>>,
    metadata: Map<Long, MediaMetadata>,
    dateSource: TimelineDateSource,
    monthLabel: (Long) -> String = ::monthLabelOf,
    monthName: (Long) -> String = { it.formatEpochSec("LLLL") },
): TimelineIndex {
    if (media.isEmpty()) return TimelineIndex.Empty

    val byMonth = LinkedHashMap<Int, MutableList<T>>()
    val firstTimestamp = HashMap<Int, Long>()
    for (item in media) {
        val timestamp = item.timestampFor(dateSource)
        val key = monthKeyOf(timestamp)
        byMonth.getOrPut(key) { ArrayList() }.add(item)
        firstTimestamp.getOrPut(key) { timestamp }
    }
    val months = byMonth.map { (key, items) ->
        var videos = 0
        for (item in items) if (item.isVideo) videos++
        TimelineMonth(
            key = key,
            year = key / 100,
            label = monthLabel(firstTimestamp.getValue(key)),
            name = monthName(firstTimestamp.getValue(key)),
            photoCount = items.size - videos,
            videoCount = videos,
            places = rankPlaces(items.map { it.id }, metadata),
            highlights = pickMonthHighlights(items, metadata, dateSource),
            mediaIds = items.mapTo(LinkedHashSet(items.size)) { it.id },
            firstMediaId = items.first().id,
        )
    }

    val monthKeyByHeaderKey = HashMap<String, Int>()
    val placeByHeaderKey = HashMap<String, TimelinePlace>()
    val pendingBigHeaders = ArrayList<String>(2)
    for (item in mappedData) {
        when (item) {
            is MediaItem.Header -> if (item.key.isBigHeaderKey) {
                pendingBigHeaders.add(item.key)
            } else {
                rankPlaces(item.data, metadata).firstOrNull()?.let { placeByHeaderKey[item.key] = it }
            }
            is MediaItem.MediaViewItem -> if (pendingBigHeaders.isNotEmpty()) {
                val key = monthKeyOf(item.media.timestampFor(dateSource))
                for (header in pendingBigHeaders) monthKeyByHeaderKey[header] = key
                pendingBigHeaders.clear()
            }
        }
    }

    return TimelineIndex(
        months = months,
        monthByKey = months.associateBy { it.key },
        monthKeyByHeaderKey = monthKeyByHeaderKey,
        placeByHeaderKey = placeByHeaderKey,
    )
}

/** Cities of [ids], most frequent first; ties keep the order they first appear in. */
fun rankPlaces(ids: Iterable<Long>, metadata: Map<Long, MediaMetadata>): List<TimelinePlace> {
    if (metadata.isEmpty()) return emptyList()
    val counts = LinkedHashMap<TimelinePlace, Int>()
    for (id in ids) {
        val entry = metadata[id] ?: continue
        val city = entry.gpsLocationNameCity?.trim().orEmpty()
        if (city.isEmpty()) continue
        val place = TimelinePlace(city, entry.gpsLocationNameCountry?.trim().orEmpty())
        counts[place] = (counts[place] ?: 0) + 1
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key }
}

/**
 * The month's best photos, hero first.
 *
 * Favourites come first, then the largest photos (by pixel count, falling back to file size).
 * Screenshots are skipped, and videos are used only when the month has too few photos. Picks are
 * spread over different days before a day gets a second one, and a landscape pick leads when there
 * is one, because the hero is wider than it is tall.
 */
fun <T : Media> pickMonthHighlights(
    items: List<T>,
    metadata: Map<Long, MediaMetadata>,
    dateSource: TimelineDateSource,
    count: Int = MONTH_HIGHLIGHT_COUNT,
): List<T> {
    if (items.isEmpty() || count <= 0) return emptyList()
    val stills = items.filter { !it.isVideo && !it.isScreenshot }
    val pool = if (stills.size >= count) stills else stills + items.filter { it !in stills }
    val ranked = pool.sortedWith(
        compareBy<T> { it.isVideo || it.isScreenshot }
            .thenByDescending { it.isFavorite }
            .thenByDescending { metadata[it.id]?.pixelCount ?: 0L }
            .thenByDescending { it.size }
    )
    val picks = ArrayList<T>(count)
    val days = HashSet<Long>()
    for (item in ranked) {
        if (picks.size == count) break
        if (days.add(item.timestampFor(dateSource).epochSecToLocalDate().toEpochDays().toLong())) picks.add(item)
    }
    for (item in ranked) {
        if (picks.size == count) break
        if (item !in picks) picks.add(item)
    }
    val landscape = picks.indexOfFirst { metadata[it.id]?.isLandscape == true }
    if (landscape > 0) picks.add(0, picks.removeAt(landscape))
    return picks
}

private val Media.isScreenshot: Boolean
    get() = path.contains("/Screenshots/", ignoreCase = true) ||
        relativePath.contains("Screenshots", ignoreCase = true)

private val MediaMetadata.pixelCount: Long
    get() = imageWidth.toLong() * imageHeight.toLong()

private val MediaMetadata.isLandscape: Boolean
    get() = imageWidth > imageHeight
