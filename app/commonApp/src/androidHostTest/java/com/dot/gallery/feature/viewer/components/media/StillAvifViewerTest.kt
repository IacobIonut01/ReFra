/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.components.media

import com.dot.gallery.cloud.image.CloudSubsamplingMode
import com.dot.gallery.cloud.image.resolveCloudSubsamplingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillAvifViewerTest {

    @Test
    fun stillAvifIsNotTreatedAsAnimated() {
        assertFalse(
            viewerMarksAnimated(
                isApng = false,
                isJxl = false,
                isAvisMime = false,
                sniffedAnimatedAvif = false,
                isHeifSequenceMime = false,
                apiAtLeast31 = true,
            )
        )
    }

    @Test
    fun avisMimeAndSniffedSequenceStayAnimatedOnApi31() {
        assertTrue(
            viewerMarksAnimated(
                isApng = false,
                isJxl = false,
                isAvisMime = true,
                sniffedAnimatedAvif = false,
                isHeifSequenceMime = false,
                apiAtLeast31 = true,
            )
        )
        assertTrue(
            viewerMarksAnimated(
                isApng = false,
                isJxl = false,
                isAvisMime = false,
                sniffedAnimatedAvif = true,
                isHeifSequenceMime = false,
                apiAtLeast31 = true,
            )
        )
        assertTrue(
            viewerMarksAnimated(
                isApng = false,
                isJxl = false,
                isAvisMime = false,
                sniffedAnimatedAvif = false,
                isHeifSequenceMime = true,
                apiAtLeast31 = true,
            )
        )
    }

    @Test
    fun sequencesStayStillBelowApi31SoTheRegionDecoderCanTile() {
        assertFalse(
            viewerMarksAnimated(
                isApng = false,
                isJxl = false,
                isAvisMime = true,
                sniffedAnimatedAvif = true,
                isHeifSequenceMime = true,
                apiAtLeast31 = false,
            )
        )
        assertTrue(
            viewerMarksAnimated(
                isApng = true,
                isJxl = false,
                isAvisMime = false,
                sniffedAnimatedAvif = false,
                isHeifSequenceMime = false,
                apiAtLeast31 = false,
            )
        )
        assertTrue(
            viewerMarksAnimated(
                isApng = false,
                isJxl = true,
                isAvisMime = false,
                sniffedAnimatedAvif = false,
                isHeifSequenceMime = false,
                apiAtLeast31 = false,
            )
        )
    }

    @Test
    fun stillAvifUsesTheHeifRegionDecoder() {
        assertTrue(viewerUsesHeifTiles(isHeif = false, isAvif = true, isAnimated = false))
        assertTrue(viewerUsesHeifTiles(isHeif = true, isAvif = false, isAnimated = false))
        assertFalse(viewerUsesHeifTiles(isHeif = false, isAvif = true, isAnimated = true))
        assertFalse(viewerUsesHeifTiles(isHeif = true, isAvif = false, isAnimated = true))
        assertFalse(viewerUsesHeifTiles(isHeif = false, isAvif = false, isAnimated = false))
    }

    @Test
    fun stillAvifCloudOriginalUsesTheHeifDecoder() {
        assertEquals(
            CloudSubsamplingMode.HEIF,
            resolveCloudSubsamplingMode(
                isJxl = false,
                hasCustomRegionDecoder = false,
                isHeif = false,
                isSpecialFormat = false,
                isAnimated = false,
                isAnimatedRaster = false,
                isAvif = true,
            )
        )
        assertEquals(
            CloudSubsamplingMode.NONE,
            resolveCloudSubsamplingMode(
                isJxl = false,
                hasCustomRegionDecoder = false,
                isHeif = false,
                isSpecialFormat = false,
                isAnimated = true,
                isAnimatedRaster = false,
                isAvif = true,
            )
        )
    }
}
