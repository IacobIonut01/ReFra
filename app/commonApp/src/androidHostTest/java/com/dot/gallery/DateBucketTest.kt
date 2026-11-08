package com.dot.gallery

import com.dot.gallery.domain.date.DateBucket
import com.dot.gallery.domain.date.dateBucketOf
import com.dot.gallery.domain.date.isRelativeDateLabel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for [dateBucketOf].
 *
 * media dated in the future (e.g. April 2039) was grouped under
 * "Today" because every non-positive day distance mapped to the Today label.
 */
class DateBucketTest {

    private val today = LocalDate(2026, 10, 24)

    @Test
    fun pastWeekGetsRelativeLabels() {
        assertEquals(DateBucket.TODAY, dateBucketOf(today, today))
        assertEquals(DateBucket.YESTERDAY, dateBucketOf(today.minus(DatePeriod(days = 1)), today))
        assertEquals(DateBucket.WEEKDAY, dateBucketOf(today.minus(DatePeriod(days = 6)), today))
        assertEquals(DateBucket.DATE, dateBucketOf(today.minus(DatePeriod(days = 7)), today))
        assertEquals(DateBucket.EXTENDED_DATE, dateBucketOf(LocalDate(2025, 4, 28), today))
    }

    @Test
    fun futureDatesAreNeverToday() {
        assertEquals(DateBucket.DATE, dateBucketOf(today.plus(DatePeriod(days = 1)), today))
        assertEquals(DateBucket.DATE, dateBucketOf(LocalDate(2026, 12, 31), today))
        assertEquals(DateBucket.EXTENDED_DATE, dateBucketOf(LocalDate(2039, 4, 1), today))
    }

    @Test
    fun futureDatesGetMonthHeaders() {
        assertTrue(isRelativeDateLabel(0))
        assertTrue(isRelativeDateLabel(6))
        assertFalse(isRelativeDateLabel(7))
        assertFalse(isRelativeDateLabel(-1))
        assertFalse(isRelativeDateLabel(-4_000))
    }
}
