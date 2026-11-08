/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.date

/**
 * Formats [this] epoch-seconds timestamp with an LDML pattern
 * ("EEEE, MMMM d, y" style — already-resolved pattern, not an ICU skeleton).
 *
 * Android: `android.text.format.DateFormat`. wasmJs: `Intl.DateTimeFormat`
 * with a token mapper covering the app's pattern subset
 * (E/M/d/y/H/h/m/s/a + literals).
 */
expect fun Long.formatEpochSec(pattern: String): String

/**
 * Resolves an ICU date skeleton ("MMMMd", "yMMMEEEd") into a concrete LDML
 * pattern for the current locale. wasmJs passes the skeleton through — its
 * formatter accepts the same token subset.
 */
expect fun systemDatePattern(skeleton: String): String

fun Long.getDate(format: CharSequence): String =
    formatEpochSec(format.toString())

fun Long.getMonth(): String {
    val currentYear = todayLocalDate().year
    val mediaDate = epochSecToLocalDate()
    return if (currentYear != mediaDate.year)
        "${formatEpochSec("MMMM")} ${mediaDate.year}"
    else formatEpochSec("MMMM")
}

fun Long.getYear(): String = epochSecToLocalDate().year.toString()

fun Long.getMediaAppBarDate(format: CharSequence, extendedFormat: CharSequence): String =
    if (epochSecToLocalDate().year != todayLocalDate().year) {
        formatEpochSec(extendedFormat.toString())
    } else {
        formatEpochSec(format.toString())
    }

/**
 * Validates a user-entered LDML date pattern the way `SimpleDateFormat(pattern)` would:
 * every unquoted ASCII letter must be a known pattern symbol. Quoted literal runs
 * ('text') are skipped; an unmatched quote is an error.
 */
private val ldmlPatternLetters = "GyYuUQqMLlwWdDFgEecabBhHKkjJmsSAzZOvVXx".toSet()

fun isValidDatePattern(pattern: String): Boolean {
    var i = 0
    var quoted = false
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '\'') {
            // '' is a literal quote inside or outside quoting; toggles otherwise.
            if (i + 1 < pattern.length && pattern[i + 1] == '\'') { i += 2; continue }
            quoted = !quoted; i++; continue
        }
        if (!quoted && c.isLetter() && c !in ldmlPatternLetters) return false
        i++
    }
    return !quoted
}

/** Canonical "full" timestamp pattern shared by UI and data layers. */
const val FULL_DATE_FORMAT = "EEEE, MMMM d, yyyy, hh:mm a"
