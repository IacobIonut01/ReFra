/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.webdav

import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.data.entity.providerTakenTimestamp
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


/**
 * Pins the WebDAV capture-time resolution behind issue #1476: Nextcloud returns
 * `creationdate` = Unix epoch for every file (31 Dec 1969 in the UI) while the
 * real EXIF capture time arrives via `nc:metadata-photos-original_date_time`.
 */
class WebDavCaptureTimeTest {

    @Test
    fun epochCreationDateAndNoPhotoTime_resolvesNull() {
        val result = resolveWebDavCaptureTime(
            creationDate = "1970-01-01T00:00:00+00:00",
            photoCaptureTimeSeconds = null,
            lastModifiedMillis = 1795195761000L,
            defaultZone = ZoneOffset.UTC
        )

        assertNull(result.takenTimestampMillis)
        assertNull(result.dateTimeOriginal)
    }

    @Test
    fun photoCaptureTime_formatsExifWallClock() {
        val result = resolveWebDavCaptureTime(
            creationDate = "",
            photoCaptureTimeSeconds = 1341059531L,
            lastModifiedMillis = 1795195750000L,
            defaultZone = ZoneOffset.ofHours(3)
        )

        assertEquals(1341048731000L, result.takenTimestampMillis)
        assertEquals("2012:06:30 12:32:11", result.dateTimeOriginal)
    }

    @Test
    fun photoCaptureTime_formatsExifWallClock_inUtc() {
        val result = resolveWebDavCaptureTime(
            creationDate = "",
            photoCaptureTimeSeconds = 1341059531L,
            lastModifiedMillis = 1795195750000L,
            defaultZone = ZoneOffset.UTC
        )

        assertEquals(1341059531000L, result.takenTimestampMillis)
        assertEquals("2012:06:30 12:32:11", result.dateTimeOriginal)
    }

    @Test
    fun photoCaptureTimeEqualToMtime_isIgnored() {
        val result = resolveWebDavCaptureTime(
            creationDate = "1970-01-01T00:00:00+00:00",
            photoCaptureTimeSeconds = 1795195761L,
            lastModifiedMillis = 1795195761000L,
            defaultZone = ZoneOffset.UTC
        )

        assertNull(result.takenTimestampMillis)
        assertNull(result.dateTimeOriginal)
    }

    @Test
    fun zeroPhotoCaptureTime_fallsBackToCreationDate() {
        val result = resolveWebDavCaptureTime(
            creationDate = "2021-05-06T12:34:56Z",
            photoCaptureTimeSeconds = 0L,
            lastModifiedMillis = 1795195761000L,
            defaultZone = ZoneOffset.UTC
        )

        assertEquals(1620304496000L, result.takenTimestampMillis)
        assertNull(result.dateTimeOriginal)
    }

    @Test
    fun nullPhotoCaptureTime_usesCreationDate() {
        val result = resolveWebDavCaptureTime(
            creationDate = "2021-05-06T12:34:56Z",
            photoCaptureTimeSeconds = null,
            lastModifiedMillis = 1795195761000L,
            defaultZone = ZoneOffset.UTC
        )

        assertEquals(1620304496000L, result.takenTimestampMillis)
        assertNull(result.dateTimeOriginal)
    }

    @Test
    fun providerTakenTimestamp_treatsZeroAsAbsent() {
        val zero = CloudMediaEntity(
            remoteId = "a.jpg",
            providerType = ProviderType.NEXTCLOUD,
            serverConfigId = 1L,
            label = "a.jpg",
            mimeType = "image/jpeg",
            timestamp = 1795195761000L,
            takenTimestamp = 0L
        )
        assertNull(zero.providerTakenTimestamp)

        val real = zero.copy(takenTimestamp = 1341059531000L)
        assertEquals(1341059531000L, real.providerTakenTimestamp)
    }
}
