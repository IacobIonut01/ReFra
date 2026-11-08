/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.util

import com.dot.gallery.domain.date.DateExt
import com.dot.gallery.domain.date.DateGrouper
import com.dot.gallery.domain.date.epochSecToLocalDate
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.domain.date.getDateExt
import com.dot.gallery.domain.date.getDateHeader
import com.dot.gallery.domain.date.getMonth
import com.dot.gallery.domain.date.getYear
import com.dot.gallery.domain.date.isRelativeDateLabel
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.TimelineDateSource
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.timestampFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Viewer page order.
 *
 * [groupedIds] is the date-header walk (stacked siblings stay behind their
 * representative). With date headers off, the grid lists [sourceIds] in the
 * active sort, and the pager has to follow that same list.
 */
fun pagerIdsForOpenGrid(
    sourceIds: List<Long>,
    groupedIds: List<Long>,
    dateHeadersEnabled: Boolean,
): List<Long> = if (dateHeadersEnabled) groupedIds else sourceIds.distinct()

suspend fun <T : Media> mapMediaToItem(
    data: List<T>,
    error: String,
    albumId: Long,
    groupByMonth: Boolean = false,
    groupByYear: Boolean = false,
    withMonthHeader: Boolean = true,
    groupSimilarMedia: Boolean = false,
    dateHeadersEnabled: Boolean = true,
    enabledGroupTypes: Set<MediaGroupType> = MediaGroupType.entries.toSet(),
    cloudGroupKeyOverrides: Map<Long, String> = emptyMap(),
    cloudBackups: Map<Long, List<UriMedia>> = emptyMap(),
    defaultDateFormat: String,
    extendedDateFormat: String,
    weeklyDateFormat: String,
    dateSource: TimelineDateSource = TimelineDateSource.CAPTURE_TIME
) = withContext(Dispatchers.Default) {
    val estimatedSize = data.size + (data.size / 20) // ~1 header per 20 items
    val mappedData = ArrayList<MediaItem<T>>(estimatedSize)
    val mappedDataWithMonthly = if (withMonthHeader) ArrayList<MediaItem<T>>(estimatedSize) else mutableListOf()
    val mappedDataWithYearly = if (withMonthHeader) ArrayList<MediaItem<T>>(estimatedSize) else mutableListOf()
    val monthHeaderList = HashSet<String>()
    val yearHeaderList = HashSet<String>()
    val headers = ArrayList<MediaItem.Header<T>>(estimatedSize / 20 + 1)
    val pagerMediaList = if (groupSimilarMedia) ArrayList<T>(data.size) else mutableListOf()
    val mediaGroupsMap = if (groupSimilarMedia) HashMap<Long, List<T>>() else mutableMapOf()

    // DateGrouper pre-computes locale, todayStartMillis, and currentYear once,
    // then reuses a single Calendar per item instead of allocating 4 new ones.
    val dateGrouper = if (!groupByMonth && !groupByYear) DateGrouper(
        format = defaultDateFormat,
        weeklyFormat = weeklyDateFormat,
        extendedFormat = extendedDateFormat,
        /** Localized in composition */
        stringToday = "Today",
        stringYesterday = "Yesterday"
    ) else null
    val groupedData = data.groupBy {
        val timestamp = it.timestampFor(dateSource)
        when {
            groupByYear -> timestamp.getYear()
            groupByMonth -> timestamp.getMonth()
            else -> dateGrouper!!.classify(timestamp)
        }
    }
    val hasCloudOverrides = cloudGroupKeyOverrides.isNotEmpty()
    groupedData.forEach { (date, data) ->
        val dateHeader = MediaItem.Header<T>("header_$date", date, data.mapTo(HashSet(data.size)) { it.id })
        headers.add(dateHeader)
        val groupedMedia = if (groupSimilarMedia) {
            // Use pre-computed override keys for cloud items so they group with local counterparts
            val groups = if (hasCloudOverrides) {
                data.groupBy { cloudGroupKeyOverrides[it.id] ?: it.groupKey }
            } else {
                data.groupBy { it.groupKey }
            }
            groups.values.flatMap { group ->
                if (group.size > 1) {
                    val groupType = group.classifyGroupType()
                    if (groupType in enabledGroupTypes) {
                        val representative = group.selectRepresentative()
                        pagerMediaList.add(representative)
                        // Store members ordered so the representative (JPG over its RAW/DNG
                        // sibling) is first — keeps the opened stack and multi-file share aligned
                        // with the grid cover.
                        mediaGroupsMap[representative.id] = group.sortedByRepresentative()
                        return@flatMap listOf(
                            MediaItem.MediaViewItem(
                                key = "media_${representative.id}",
                                media = representative,
                                stackCount = group.size,
                                isCloudGroup = groupType == MediaGroupType.CLOUD_LOCAL
                            )
                        )
                    }
                }
                group.map { media ->
                    pagerMediaList.add(media)
                    MediaItem.MediaViewItem("media_${media.id}", media)
                }
            }
        } else {
            data.map {
                MediaItem.MediaViewItem("media_${it.id}", it)
            }
        }
        if (groupByYear) {
            mappedData.add(dateHeader)
            mappedData.addAll(groupedMedia)
            mappedDataWithYearly.add(dateHeader)
            mappedDataWithYearly.addAll(groupedMedia)
        } else if (groupByMonth) {
            mappedData.add(dateHeader)
            mappedData.addAll(groupedMedia)
            mappedDataWithMonthly.add(dateHeader)
            mappedDataWithMonthly.addAll(groupedMedia)
        } else {
            // The group key is a localized date string only for items a week or
            // older — Today/Yesterday/weekday label keys get no big month header.
            val firstTimestamp = data.first().timestampFor(dateSource)
            val month = if (dateGrouper != null && !isRelativeDateLabel(dateGrouper.daysUntilToday(firstTimestamp))) {
                if (firstTimestamp.epochSecToLocalDate().year == dateGrouper.currentYear) {
                    firstTimestamp.formatEpochSec("MMMM")
                } else {
                    firstTimestamp.formatEpochSec("MMMMyyyy")
                }
            } else ""
            if (month.isNotEmpty() && !monthHeaderList.contains(month)) {
                monthHeaderList.add(month)
                val bigMonthHeader = MediaItem.Header<T>(
                    "header_big_${month}_${data.size}",
                    month,
                    dateHeader.data
                )
                if (mappedData.isNotEmpty()) {
                    mappedData.add(bigMonthHeader)
                }
                if (withMonthHeader && mappedDataWithMonthly.isNotEmpty()) {
                    mappedDataWithMonthly.add(bigMonthHeader)
                }
            }
            mappedData.add(dateHeader)
            if (withMonthHeader) {
                mappedDataWithMonthly.add(dateHeader)
            }
            mappedData.addAll(groupedMedia)
            if (withMonthHeader) {
                mappedDataWithMonthly.addAll(groupedMedia)
            }
        }
        if (!groupByYear && withMonthHeader) {
            val year = data.firstOrNull()?.timestampFor(dateSource)?.getYear() ?: ""
            if (year.isNotEmpty() && !yearHeaderList.contains(year)) {
                yearHeaderList.add(year)
                if (mappedDataWithYearly.isNotEmpty()) {
                    mappedDataWithYearly.add(
                        MediaItem.Header(
                            "header_big_${year}_${data.size}",
                            year,
                            dateHeader.data
                        )
                    )
                }
            }
            mappedDataWithYearly.add(dateHeader)
            mappedDataWithYearly.addAll(groupedMedia)
        }
    }
    MediaState(
        isLoading = false,
        error = error,
        media = data,
        pagerMedia = run {
            val groupedPager = (if (groupSimilarMedia) pagerMediaList else data).distinctBy { it.id }
            val ids = pagerIdsForOpenGrid(
                sourceIds = data.map { it.id },
                groupedIds = groupedPager.map { it.id },
                dateHeadersEnabled = dateHeadersEnabled,
            )
            // `putIfAbsent` is JVM-only; getOrPut keeps the first occurrence, same semantics.
            val byId = LinkedHashMap<Long, T>(data.size)
            for (item in data) byId.getOrPut(item.id) { item }
            ids.mapNotNull { byId[it] }
        },
        mediaGroups = mediaGroupsMap,
        headers = headers,
        mappedMedia = mappedData,
        mappedMediaWithMonthly = if (withMonthHeader) mappedDataWithMonthly else emptyList(),
        mappedMediaWithYearly = if (withMonthHeader) mappedDataWithYearly else emptyList(),
        cloudBackups = cloudBackups,
        dateHeader = data.dateHeader(albumId, dateSource),
        dateSource = dateSource
    )
}

private fun List<Media>.dateHeader(
    albumId: Long,
    dateSource: TimelineDateSource
): String =
    if (albumId != -1L && isNotEmpty()) {
        val startDate: DateExt = last().timestampFor(dateSource).getDateExt()
        val endDate: DateExt = first().timestampFor(dateSource).getDateExt()
        getDateHeader(startDate, endDate)
    } else ""
