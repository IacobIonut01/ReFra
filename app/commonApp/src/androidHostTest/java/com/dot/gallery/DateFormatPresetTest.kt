/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery

import com.dot.gallery.domain.date.DateFormatPreset
import com.dot.gallery.domain.date.SystemDateFormatField
import com.dot.gallery.domain.date.iso8601Pattern
import com.dot.gallery.domain.date.isValidDatePattern
import com.dot.gallery.domain.date.splitDateHeader
import com.dot.gallery.domain.date.withLiteralZoneOffset
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DateFormatPresetTest {

    /** 2026-11-22T14:30:05Z. */
    private val instant = Instant.ofEpochSecond(1_795_357_805L)

    @Test
    fun everyPresetIsAcceptedByTheEditor() {
        DateFormatPreset.entries.forEach { preset ->
            assertTrue(preset.name, isValidDatePattern(preset.pattern))
        }
    }

    @Test
    fun presetsFormatAsTheirStandards() {
        val expected = mapOf(
            DateFormatPreset.ISO_8601_DATE to "2026-11-22",
            DateFormatPreset.ISO_8601_DATE_TIME to "2026-11-22T15:30",
            DateFormatPreset.RFC_3339 to "2026-11-22 15:30:05+01:00",
        )
        DateFormatPreset.entries.forEach { preset ->
            assertEquals(preset.name, expected.getValue(preset), formatLikeAndroid(preset.pattern, 3600))
            assertEquals(preset.name, expected.getValue(preset), formatWithJavaTime(preset.pattern, 3600))
        }
    }

    @Test
    fun rfc3339WritesZForUtcAndSignedOffsetsOtherwise() {
        val pattern = DateFormatPreset.RFC_3339.pattern
        assertEquals("2026-11-22 14:30:05Z", formatLikeAndroid(pattern, 0))
        assertEquals("2026-11-22 09:00:05-05:30", formatLikeAndroid(pattern, -19_800))
        assertEquals(formatWithJavaTime(pattern, -19_800), formatLikeAndroid(pattern, -19_800))
    }

    @Test
    fun offsetRunLengthsFollowLdml() {
        assertEquals("'+05'", withLiteralZoneOffset("X", 18_000))
        assertEquals("'+0530'", withLiteralZoneOffset("X", 19_800))
        assertEquals("'+0530'", withLiteralZoneOffset("XX", 19_800))
        assertEquals("'+05:30'", withLiteralZoneOffset("XXX", 19_800))
        assertEquals("'+00:00'", withLiteralZoneOffset("xxx", 0))
        assertEquals("'Z'", withLiteralZoneOffset("XXX", 0))
    }

    @Test
    fun quotedOffsetLettersAndPlainPatternsAreLeftAlone() {
        val plain = DateFormatPreset.ISO_8601_DATE_TIME.pattern
        assertSame(plain, withLiteralZoneOffset(plain, 3600))
        assertEquals("'X marks' HH '+01'", withLiteralZoneOffset("'X marks' HH X", 3600))
    }

    @Test
    fun iso8601EverywhereKeepsTimesWhereTheFieldShowsThem() {
        val dateOnly = DateFormatPreset.ISO_8601_DATE.pattern
        val dateTime = DateFormatPreset.ISO_8601_DATE_TIME.pattern
        assertEquals(dateOnly, SystemDateFormatField.WEEKLY.iso8601Pattern)
        assertEquals(dateOnly, SystemDateFormatField.DEFAULT.iso8601Pattern)
        assertEquals(dateOnly, SystemDateFormatField.EXTENDED.iso8601Pattern)
        assertEquals(dateTime, SystemDateFormatField.HEADER.iso8601Pattern)
        assertEquals(dateTime, SystemDateFormatField.EXTENDED_HEADER.iso8601Pattern)
        assertEquals(dateTime, SystemDateFormatField.EXIF.iso8601Pattern)
    }

    @Test
    fun oneLineHeaderIsShownOnce() {
        assertEquals("2026-11-22T15:30" to null, splitDateHeader("2026-11-22T15:30"))
        assertEquals("November 22" to "3:30 PM", splitDateHeader("November 22\n3:30 PM"))
    }

    /**
     * The Android path: offset letters rewritten to a literal, then a formatter that does not
     * need to know them (SimpleDateFormat stands in for `android.text.format.DateFormat`).
     */
    private fun formatLikeAndroid(pattern: String, offsetSeconds: Int): String {
        val format = SimpleDateFormat(withLiteralZoneOffset(pattern, offsetSeconds), Locale.US)
        format.timeZone = TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(offsetSeconds))
        return format.format(Date.from(instant))
    }

    /** The desktop path: `java.time` understands the offset letters itself. */
    private fun formatWithJavaTime(pattern: String, offsetSeconds: Int): String =
        DateTimeFormatter.ofPattern(pattern, Locale.US)
            .format(instant.atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds)))
}
