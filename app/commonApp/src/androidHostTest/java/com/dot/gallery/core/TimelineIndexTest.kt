/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.core.presentation.components.TimelinePlace
import com.dot.gallery.core.presentation.components.buildTimelineIndex
import com.dot.gallery.core.presentation.components.monthKeyOf
import com.dot.gallery.core.presentation.components.pickMonthHighlights
import com.dot.gallery.core.presentation.components.rankPlaces
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.model.EncryptedMedia
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.TimelineDateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineIndexTest {

    @Test
    fun monthsCountPhotosAndVideosAndRankCities() {
        val october = listOf(
            media(1, day(2026, 10, 31)),
            media(2, day(2026, 10, 31), video = true),
            media(3, day(2026, 10, 27)),
        )
        val september = listOf(media(4, day(2026, 9, 15)))
        val metadata = mapOf(
            1L to meta(1, "Sintra", "Portugal"),
            2L to meta(2, "Lisbon", "Portugal"),
            3L to meta(3, "Lisbon", "Portugal"),
        )
        val index = buildTimelineIndex(
            media = october + september,
            mappedData = emptyList<MediaItem<EncryptedMedia>>(),
            metadata = metadata,
            dateSource = TimelineDateSource.MODIFIED_TIME,
            monthLabel = { "m" },
            monthName = { "n" },
        )

        assertEquals(listOf(202610, 202609), index.months.map { it.key })
        val oct = index.month(202610)!!
        assertEquals(2, oct.photoCount)
        assertEquals(1, oct.videoCount)
        assertEquals(
            listOf(TimelinePlace("Lisbon", "Portugal"), TimelinePlace("Sintra", "Portugal")),
            oct.places,
        )
        assertEquals(setOf(1L, 2L, 3L), oct.mediaIds)
        assertTrue(index.month(202609)!!.places.isEmpty())
    }

    @Test
    fun bigHeaderMapsToTheMonthOfTheMediaAfterIt() {
        val first = media(1, day(2026, 10, 31))
        val second = media(2, day(2026, 9, 15))
        val mapped = listOf<MediaItem<EncryptedMedia>>(
            MediaItem.Header("header_big_October_1", "October", setOf(1L)),
            MediaItem.Header("header_day1", "Thu, 31 Oct", setOf(1L)),
            MediaItem.MediaViewItem("media_1", first),
            MediaItem.Header("header_big_September_1", "September", setOf(2L)),
            MediaItem.Header("header_day2", "Tue, 15 Sep", setOf(2L)),
            MediaItem.MediaViewItem("media_2", second),
        )
        val index = buildTimelineIndex(
            media = listOf(first, second),
            mappedData = mapped,
            metadata = mapOf(2L to meta(2, "Banff", "Canada")),
            dateSource = TimelineDateSource.MODIFIED_TIME,
            monthLabel = { "m" },
            monthName = { "n" },
        )

        assertEquals(202610, index.monthForHeader("header_big_October_1")?.key)
        assertEquals(202609, index.monthForHeader("header_big_September_1")?.key)
        assertNull(index.placeForHeader("header_day1"))
        assertEquals(TimelinePlace("Banff", "Canada"), index.placeForHeader("header_day2"))
        assertNull(index.monthForHeader("header_day2"))
    }

    @Test
    fun highlightsPreferFavouritesSpreadDaysAndSkipScreenshots() {
        val items = listOf(
            media(1, day(2026, 10, 1), size = 10),
            media(2, day(2026, 10, 1), size = 9),
            media(3, day(2026, 10, 2), size = 1, favorite = true),
            media(4, day(2026, 10, 3), size = 100, path = "/DCIM/Screenshots/4.png"),
            media(5, day(2026, 10, 4), size = 5),
        )
        val picks = pickMonthHighlights(items, emptyMap(), TimelineDateSource.MODIFIED_TIME, count = 3)

        assertEquals(listOf(3L, 1L, 5L), picks.map { it.id })
    }

    @Test
    fun landscapeHighlightLeads() {
        val items = listOf(media(1, day(2026, 10, 1)), media(2, day(2026, 10, 2)))
        val metadata = mapOf(
            1L to meta(1, null, null, width = 3000, height = 4000),
            2L to meta(2, null, null, width = 4000, height = 2000),
        )
        val picks = pickMonthHighlights(items, metadata, TimelineDateSource.MODIFIED_TIME, count = 2)

        assertEquals(2L, picks.first().id)
    }

    @Test
    fun videosFillInOnlyWhenPhotosRunOut() {
        val items = listOf(
            media(1, day(2026, 10, 1), video = true, size = 1000),
            media(2, day(2026, 10, 2)),
        )
        val picks = pickMonthHighlights(items, emptyMap(), TimelineDateSource.MODIFIED_TIME, count = 2)

        assertEquals(listOf(2L, 1L), picks.map { it.id })
    }

    @Test
    fun placesIgnoreBlankCities() {
        val metadata = mapOf(1L to meta(1, "  ", "Romania"), 2L to meta(2, "Cluj", "Romania"))

        assertEquals(listOf(TimelinePlace("Cluj", "Romania")), rankPlaces(listOf(1L, 2L), metadata))
    }

    @Test
    fun monthKeyIsYearAndMonth() {
        assertEquals(202602, monthKeyOf(day(2026, 2, 14)))
    }

    private fun day(year: Int, month: Int, day: Int): Long =
        java.time.LocalDateTime.of(year, month, day, 12, 0)
            .atZone(java.time.ZoneId.systemDefault()).toEpochSecond()

    private fun media(
        id: Long,
        timestamp: Long,
        video: Boolean = false,
        favorite: Boolean = false,
        size: Long = 0,
        path: String = "/DCIM/Camera/$id.jpg",
    ) = EncryptedMedia(
        id = id,
        label = "m$id",
        bytes = ByteArray(0),
        path = path,
        relativePath = path.substringBeforeLast('/'),
        albumID = 1L,
        albumLabel = "Camera",
        timestamp = timestamp,
        fullDate = "",
        mimeType = if (video) "video/mp4" else "image/jpeg",
        favorite = if (favorite) 1 else 0,
        trashed = 0,
        size = size,
        duration = if (video) "1000" else null,
    )

    private fun meta(
        id: Long,
        city: String?,
        country: String?,
        width: Int = 0,
        height: Int = 0,
    ) = MediaMetadata(
        mediaId = id,
        imageDescription = null,
        dateTimeOriginal = null,
        manufacturerName = null,
        modelName = null,
        aperture = null,
        exposureTime = null,
        iso = null,
        gpsLatitude = null,
        gpsLongitude = null,
        gpsLocationName = null,
        gpsLocationNameCountry = country,
        gpsLocationNameCity = city,
        imageWidth = width,
        imageHeight = height,
        imageResolutionX = null,
        imageResolutionY = null,
        resolutionUnit = null,
        durationMs = null,
        videoWidth = null,
        videoHeight = null,
        frameRate = null,
        bitRate = null,
        isNightMode = false,
        isPanorama = false,
        isPhotosphere = false,
        isLongExposure = false,
        isMotionPhoto = false,
    )
}
