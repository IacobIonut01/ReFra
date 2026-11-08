/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class BirthDatePickerTest {

    @Test
    fun pickerMillisKeepTheCalendarDayWestOfUtc() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        try {
            val august21Utc = LocalDate.of(2013, 8, 21)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
            assertEquals("2013-08-21", birthDateFromPickerMillis(august21Utc))
            assertEquals(august21Utc, pickerMillisFromBirthDate("2013-08-21"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun blankAndGarbageDatesDoNotBecomeMillis() {
        assertNull(pickerMillisFromBirthDate(""))
        assertNull(pickerMillisFromBirthDate("   "))
        assertNull(pickerMillisFromBirthDate("not-a-date"))
    }
}
