/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.date

import android.text.format.DateFormat
import androidx.core.os.ConfigurationCompat
import android.content.res.Resources
import java.util.Locale
import java.util.TimeZone

private fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0] ?: Locale.getDefault()

/**
 * `DateFormat.format` prints the zone-offset letters `X`/`x` verbatim, so the RFC 3339 preset
 * gets its offset written in as a literal first.
 */
actual fun Long.formatEpochSec(pattern: String): String {
    val millis = this * 1000L
    val offsetSeconds = TimeZone.getDefault().getOffset(millis) / 1000
    return DateFormat.format(withLiteralZoneOffset(pattern, offsetSeconds), millis).toString()
}

actual fun systemDatePattern(skeleton: String): String =
    DateFormat.getBestDateTimePattern(currentLocale(), skeleton)
