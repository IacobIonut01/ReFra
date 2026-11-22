/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.date

import kotlin.math.abs

/** Standard patterns offered as one-tap choices beside every date format field. */
enum class DateFormatPreset(val pattern: String) {
    /** ISO 8601 calendar date: 2026-11-22. */
    ISO_8601_DATE("yyyy-MM-dd"),

    /** ISO 8601 local date and time: 2026-11-22T14:30. */
    ISO_8601_DATE_TIME("yyyy-MM-dd'T'HH:mm"),

    /** RFC 3339 timestamp with its UTC offset: 2026-11-22 14:30:05+01:00. */
    RFC_3339("yyyy-MM-dd HH:mm:ssXXX"),
}

/**
 * The ISO 8601 pattern "Use ISO 8601 everywhere" writes to this field: a date for the grid's
 * group titles, a date and time where the field already shows a time.
 */
val SystemDateFormatField.iso8601Pattern: String
    get() = when (this) {
        SystemDateFormatField.WEEKLY,
        SystemDateFormatField.DEFAULT,
        SystemDateFormatField.EXTENDED -> DateFormatPreset.ISO_8601_DATE.pattern
        SystemDateFormatField.HEADER,
        SystemDateFormatField.EXTENDED_HEADER,
        SystemDateFormatField.EXIF -> DateFormatPreset.ISO_8601_DATE_TIME.pattern
    }

/**
 * Replaces each unquoted run of the LDML zone-offset letters `X` and `x` in [pattern] with the
 * quoted literal offset for [offsetSeconds], for formatters that print those letters verbatim
 * (`android.text.format.DateFormat`). `X` writes `Z` for UTC; `x` always writes digits. Run
 * length picks the shape: 1 `+01` (minutes only when non-zero), 2 and 4 `+0100`, 3 and 5
 * `+01:00`. A pattern without them is returned unchanged.
 */
fun withLiteralZoneOffset(pattern: String, offsetSeconds: Int): String {
    if (pattern.none { it == 'X' || it == 'x' }) return pattern
    val out = StringBuilder(pattern.length + 8)
    var quoted = false
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '\'') {
            quoted = !quoted
            out.append(c)
            i++
            continue
        }
        if (!quoted && (c == 'X' || c == 'x')) {
            var j = i
            while (j < pattern.length && pattern[j] == c) j++
            out.append('\'').append(formatZoneOffset(offsetSeconds, j - i, utcAsZ = c == 'X')).append('\'')
            i = j
            continue
        }
        out.append(c)
        i++
    }
    return out.toString()
}

private fun formatZoneOffset(offsetSeconds: Int, count: Int, utcAsZ: Boolean): String {
    if (offsetSeconds == 0 && utcAsZ) return "Z"
    val sign = if (offsetSeconds < 0) '-' else '+'
    val totalMinutes = abs(offsetSeconds) / 60
    val hours = (totalMinutes / 60).toString().padStart(2, '0')
    val minutes = (totalMinutes % 60).toString().padStart(2, '0')
    return when (count) {
        1 -> if (minutes == "00") "$sign$hours" else "$sign$hours$minutes"
        2, 4 -> "$sign$hours$minutes"
        else -> "$sign$hours:$minutes"
    }
}
