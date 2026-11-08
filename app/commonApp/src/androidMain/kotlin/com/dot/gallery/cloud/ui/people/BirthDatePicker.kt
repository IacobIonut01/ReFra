/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.cloud.ui.people

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The material date picker reports UTC midnights. Formatting that instant in the
 * device zone stores the previous calendar day west of UTC.
 */
fun birthDateFromPickerMillis(utcMidnightMillis: Long): String =
    Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()

/** Inverse of [birthDateFromPickerMillis]. Null when [isoDate] is not `yyyy-MM-dd`. */
fun pickerMillisFromBirthDate(isoDate: String): Long? {
    if (isoDate.isBlank()) return null
    return runCatching {
        LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()
}
