/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.core.presentation.components.resolutionBadgeLabel
import com.dot.gallery.data.entity.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolutionBadgeTest {

    private fun meta(
        width: Int = 0,
        height: Int = 0,
        videoWidth: Int? = null,
        videoHeight: Int? = null,
    ) = MediaMetadata(
        mediaId = 1L,
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
        gpsLocationNameCountry = null,
        gpsLocationNameCity = null,
        imageWidth = width,
        imageHeight = height,
        imageResolutionX = null,
        imageResolutionY = null,
        resolutionUnit = null,
        durationMs = null,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        frameRate = null,
        bitRate = null,
        isNightMode = false,
        isPanorama = false,
        isPhotosphere = false,
        isLongExposure = false,
        isMotionPhoto = false,
    )

    @Test
    fun missingMetadataGivesNoLabel() {
        assertNull(resolutionBadgeLabel(null, isVideo = false, minPhotoMegapixels = 24))
        assertNull(resolutionBadgeLabel(null, isVideo = true, minPhotoMegapixels = 24))
    }

    @Test
    fun largePhotoGetsRoundedMegapixels() {
        assertEquals(
            "50MP",
            resolutionBadgeLabel(meta(width = 8160, height = 6120), isVideo = false, minPhotoMegapixels = 24)
        )
    }

    @Test
    fun photoBelowThresholdHasNoLabel() {
        val metadata = meta(width = 4080, height = 3072)
        assertNull(resolutionBadgeLabel(metadata, isVideo = false, minPhotoMegapixels = 24))
        assertEquals(
            "13MP",
            resolutionBadgeLabel(metadata, isVideo = false, minPhotoMegapixels = 12)
        )
    }

    @Test
    fun roundedMegapixelsDecideTheThreshold() {
        assertEquals(
            "24MP",
            resolutionBadgeLabel(meta(width = 6000, height = 3999), isVideo = false, minPhotoMegapixels = 24)
        )
    }

    @Test
    fun photoWithoutDimensionsHasNoLabel() {
        assertNull(
            resolutionBadgeLabel(meta(width = 0, height = 2160), isVideo = false, minPhotoMegapixels = 1)
        )
    }

    @Test
    fun landscapeVideoIs4K() {
        assertEquals(
            "4K",
            resolutionBadgeLabel(
                meta(videoWidth = 3840, videoHeight = 2160),
                isVideo = true,
                minPhotoMegapixels = 24
            )
        )
    }

    @Test
    fun portraitVideoIs4K() {
        assertEquals(
            "4K",
            resolutionBadgeLabel(
                meta(videoWidth = 2160, videoHeight = 3840),
                isVideo = true,
                minPhotoMegapixels = 24
            )
        )
    }

    @Test
    fun veryLargeVideoIs8K() {
        assertEquals(
            "8K",
            resolutionBadgeLabel(
                meta(videoWidth = 7680, videoHeight = 4320),
                isVideo = true,
                minPhotoMegapixels = 24
            )
        )
    }

    @Test
    fun hdVideoHasNoLabel() {
        assertNull(
            resolutionBadgeLabel(
                meta(videoWidth = 1920, videoHeight = 1080),
                isVideo = true,
                minPhotoMegapixels = 24
            )
        )
    }

    @Test
    fun videoFallsBackToImageDimensions() {
        assertEquals(
            "4K",
            resolutionBadgeLabel(
                meta(width = 3840, height = 2160),
                isVideo = true,
                minPhotoMegapixels = 24
            )
        )
    }

    @Test
    fun videoLabelIgnoresThePhotoThreshold() {
        assertEquals(
            "4K",
            resolutionBadgeLabel(
                meta(videoWidth = 3840, videoHeight = 2160),
                isVideo = true,
                minPhotoMegapixels = 100
            )
        )
    }
}
