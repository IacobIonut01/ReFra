/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.date

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** How a media date is labelled in timeline section headers. */
enum class DateBucket { TODAY, YESTERDAY, WEEKDAY, DATE, EXTENDED_DATE }

/**
 * Picks the header label kind for [mediaDate] relative to [today]. Only past
 * dates within the last week get relative labels; future dates (e.g. a camera
 * clock set years ahead) always get an absolute date, with the year when it
 * differs from the current one.
 */
fun dateBucketOf(mediaDate: LocalDate, today: LocalDate): DateBucket =
    when (mediaDate.daysUntil(today)) {
        0 -> DateBucket.TODAY
        1 -> DateBucket.YESTERDAY
        in 2..6 -> DateBucket.WEEKDAY
        else -> if (mediaDate.year != today.year) DateBucket.EXTENDED_DATE else DateBucket.DATE
    }

/** True when [daysUntilToday] maps to a Today/Yesterday/weekday label rather than a date. */
fun isRelativeDateLabel(daysUntilToday: Int): Boolean = daysUntilToday in 0..6
