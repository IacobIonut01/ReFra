/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import android.content.res.Resources
import android.os.Parcelable
import android.text.format.DateFormat
import androidx.core.os.ConfigurationCompat
import kotlinx.parcelize.Parcelize
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

fun Long.getDateExt(): DateExt {
    val mediaDate = Calendar.getInstance(Locale.US)
    mediaDate.timeInMillis = this * 1000L
    return DateExt(
        month = mediaDate.getDisplayName(Calendar.MONTH, Calendar.LONG_FORMAT, Locale.US)!!,
        day = mediaDate.get(Calendar.DAY_OF_MONTH),
        year = mediaDate.get(Calendar.YEAR)
    )
}

fun getDateHeader(startDate: DateExt, endDate: DateExt): String {
    return if (startDate.year == endDate.year) {
        if (startDate.month == endDate.month) {
            if (startDate.day == endDate.day) {
                "${startDate.month} ${startDate.day}, ${startDate.year}"
            } else "${startDate.month} ${startDate.day} - ${endDate.day}, ${startDate.year}"
        } else
            "${startDate.month} ${startDate.day} - ${endDate.month} ${endDate.day}, ${startDate.year}"
    } else {
        "${startDate.month} ${startDate.day}, ${startDate.year} - ${endDate.month} ${endDate.day}, ${endDate.year}"
    }
}

fun getMonth(extendedFormat: String, defaultFormat: String, date: String): String {
    return try {
        val dateFormatExtended = SimpleDateFormat(extendedFormat, Locale.US).parse(date)
        val cal = Calendar.getInstance(Locale.US).apply { timeInMillis = dateFormatExtended!!.time }
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG_FORMAT, Locale.US)!!
        val year = cal.get(Calendar.YEAR)
        "$month $year"
    } catch (e: ParseException) {
        try {
            val dateFormat = SimpleDateFormat(defaultFormat, Locale.US).parse(date)
            val cal = Calendar.getInstance(Locale.US).apply { timeInMillis = dateFormat!!.time }
            cal.getDisplayName(Calendar.MONTH, Calendar.LONG_FORMAT, Locale.US)!!
        } catch (e: ParseException) {
            ""
        }
    }
}

fun Long.getDate(
    format: CharSequence,
): String {
    val mediaDate = Calendar.getInstance(Locale.US)
    mediaDate.timeInMillis = this * 1000L
    return DateFormat.format(format, mediaDate).toString()
}

fun Long.getDate(
    format: String,
    weeklyFormat: String,
    extendedFormat: String,
    stringToday: String,
    stringYesterday: String
): String {
    val locale = ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0] ?: Locale.getDefault()
    val currentDate = Calendar.getInstance(locale)
    currentDate.timeInMillis = System.currentTimeMillis()
    val mediaDate = Calendar.getInstance(locale)
    mediaDate.timeInMillis = this * 1000L
    val different: Long = System.currentTimeMillis() - mediaDate.timeInMillis
    val secondsInMilli: Long = 1000
    val minutesInMilli = secondsInMilli * 60
    val hoursInMilli = minutesInMilli * 60
    val daysInMilli = hoursInMilli * 24

    val daysDifference = (different / daysInMilli.toFloat()).roundToInt()

    return when (daysDifference) {
        0 -> {
            if (currentDate.get(Calendar.DATE) != mediaDate.get(Calendar.DATE)) {
                stringYesterday
            } else {
                stringToday
            }
        }

        1 -> {
            stringYesterday
        }

        else -> {
            if (daysDifference in 2..6) {
                DateFormat.format(weeklyFormat, mediaDate).toString()
            } else {
                if (currentDate.get(Calendar.YEAR) > mediaDate.get(Calendar.YEAR)) {
                    DateFormat.format(extendedFormat, mediaDate).toString()
                } else DateFormat.format(format, mediaDate).toString()
            }
        }
    }
}

fun Long.getMonth(): String {
    val currentDate = Calendar.getInstance(Locale.US).apply { timeInMillis = System.currentTimeMillis() }
    val mediaDate = Calendar.getInstance(Locale.US).apply { timeInMillis = this@getMonth * 1000L }
    val month = mediaDate.getDisplayName(Calendar.MONTH, Calendar.LONG_FORMAT, Locale.US)!!
    val year = mediaDate.get(Calendar.YEAR)
    return if (currentDate.get(Calendar.YEAR) != mediaDate.get(Calendar.YEAR))
        "$month $year"
    else month
}

fun Long.formatMinSec(): String {
    return if (this == 0L) {
        "00:00"
    } else {
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(this),
            TimeUnit.MILLISECONDS.toSeconds(this) -
                    TimeUnit.MINUTES.toSeconds(
                        TimeUnit.MILLISECONDS.toMinutes(this)
                    )
        )
    }
}

fun String?.formatMinSec(): String {
    return when (val value = this?.toLong()) {
        null -> ""
        else -> value.formatMinSec()
    }
}

@Parcelize
data class DateExt(val month: String, val day: Int, val year: Int): Parcelable
