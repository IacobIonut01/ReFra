/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.core.os.ConfigurationCompat
import com.dot.gallery.domain.date.SystemDateFormatField
import com.dot.gallery.domain.date.systemDatePattern
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale

fun getMonth(extendedFormat: String, defaultFormat: String, date: String): String {
    return try {
        val dateFormatExtended = SimpleDateFormat(extendedFormat, ComposeLocale.getCurrentAndroid()).parse(date)
        val cal = Calendar.getInstance(ComposeLocale.getCurrentAndroid()).apply { timeInMillis = dateFormatExtended!!.time }
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG_FORMAT, ComposeLocale.getCurrentAndroid())!!
        val year = cal.get(Calendar.YEAR)
        "$month $year"
    } catch (e: ParseException) {
        try {
            val dateFormat = SimpleDateFormat(defaultFormat, ComposeLocale.getCurrentAndroid()).parse(date)
            val cal = Calendar.getInstance(ComposeLocale.getCurrentAndroid()).apply { timeInMillis = dateFormat!!.time }
            cal.getDisplayName(Calendar.MONTH, Calendar.LONG_FORMAT, ComposeLocale.getCurrentAndroid())!!
        } catch (e: ParseException) {
            ""
        }
    }
}

fun ComposeLocale.Companion.getCurrentAndroid(): Locale {
    return ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0] ?: Locale.getDefault()
}

/**
 * Returns the locale-correct time pattern for the current system, honoring the
 * device's 12/24-hour preference. e.g. "h:mm a" or "H:mm".
 */
fun systemTimePattern(context: Context): String {
    val locale = ComposeLocale.getCurrentAndroid()
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    return DateFormat.getBestDateTimePattern(locale, skeleton)
}

/**
 * Derives a system-following pattern for the given [field], combining a
 * locale-correct date portion with the device 12/24-hour time where relevant.
 */
fun systemDateTimePattern(context: Context, field: SystemDateFormatField): String {
    return when (field) {
        SystemDateFormatField.WEEKLY -> systemDatePattern("EEEE")
        SystemDateFormatField.DEFAULT -> systemDatePattern("MMMMEEEEd")
        SystemDateFormatField.EXTENDED -> systemDatePattern("yMMMEEEd")
        SystemDateFormatField.HEADER ->
            systemDatePattern("MMMMd") + "\n" + systemTimePattern(context)
        SystemDateFormatField.EXTENDED_HEADER ->
            systemDatePattern("yMMMMd") + "\n" + systemTimePattern(context)
        SystemDateFormatField.EXIF ->
            systemDatePattern("yMMMMd") + " • " + systemTimePattern(context)
    }
}

/**
 * Resolves a stored date-format preference for non-composable consumers
 * (ViewModels, MediaDistributorImpl flows). A blank stored value means
 * "follow the system" (see ) and must never reach grouping/formatting
 * code, where it formats every date as "" and collapses all groups into one.
 */
fun resolvedDateFormat(context: Context, raw: String, field: SystemDateFormatField): String =
    if (raw.isBlank()) systemDateTimePattern(context, field) else raw
