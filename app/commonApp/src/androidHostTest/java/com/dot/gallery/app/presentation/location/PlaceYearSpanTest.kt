/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.location

import com.dot.gallery.domain.model.EncryptedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class PlaceYearSpanTest {

    private fun media(id: Long, modifiedSeconds: Long, takenMillis: Long? = null) = EncryptedMedia(
        id = id,
        label = "m$id",
        bytes = ByteArray(0),
        path = "/path/$id",
        relativePath = "/",
        albumID = 1L,
        albumLabel = "album",
        timestamp = modifiedSeconds,
        takenTimestamp = takenMillis,
        fullDate = "",
        mimeType = "image/jpeg",
        favorite = 0,
        trashed = 0,
        size = 0L,
    )

    @Test
    fun emptyPlaceHasNoYears() {
        assertNull(placeYearSpan(emptyList(), ZoneOffset.UTC))
    }

    @Test
    fun spanUsesCaptureTimeBeforeModifiedTime() {
        val y2019 = 1_560_000_000L
        val y2026 = 1_780_000_000L
        val span = placeYearSpan(
            listOf(
                media(1, modifiedSeconds = y2026),
                // Modified in 2026 but captured in 2019: the capture year wins.
                media(2, modifiedSeconds = y2026, takenMillis = y2019 * 1000),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(2019 to 2026, span)
    }

    @Test
    fun singleYearPlaceStartsAndEndsTheSameYear() {
        assertEquals(2026 to 2026, placeYearSpan(listOf(media(1, 1_780_000_000L)), ZoneOffset.UTC))
    }
}
