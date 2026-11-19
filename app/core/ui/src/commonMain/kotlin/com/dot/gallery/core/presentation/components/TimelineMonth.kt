/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable
import com.dot.gallery.domain.model.Media


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
