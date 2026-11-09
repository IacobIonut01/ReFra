/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class MediaStoreThumbnailModelLoaderTest {

    @Test
    fun embeddedFrontCameraThumbnailBypassesPlatformLoader() {
        assertTrue(
            shouldBypassPlatformThumbnail(
                hasEmbeddedThumbnail = true,
                lensModel = "Pixel 10 Pro front camera 2.713mm f/2.2",
            )
        )
    }

    @Test
    fun frontCameraWithoutEmbeddedThumbnailUsesPlatformLoader() {
        assertFalse(
            shouldBypassPlatformThumbnail(
                hasEmbeddedThumbnail = false,
                lensModel = "Pixel 10 Pro front camera 2.713mm f/2.2",
            )
        )
    }

    @Test
    fun capFollowsMediaProviderThumbnailSize() {
        // MediaProvider caches thumbnails at half the display's short edge.
        assertEquals(540, MediaStoreThumbnailModelLoader.platformThumbnailCap(1080, 2340))
        assertEquals(720, MediaStoreThumbnailModelLoader.platformThumbnailCap(3120, 1440))
        // Never below the historical 512 px bound.
        assertEquals(512, MediaStoreThumbnailModelLoader.platformThumbnailCap(720, 1600))
    }

    @Test
    fun thumbnailThatFillsCellIsAccepted() {
        // 4:3 thumbnail in a 540 box covering a 360 px square cell (3-column grid).
        assertTrue(MediaStoreThumbnailModelLoader.platformThumbnailFills(540, 405, 360, 360))
        // Within the 10% tolerance.
        assertTrue(MediaStoreThumbnailModelLoader.platformThumbnailFills(540, 405, 445, 445))
    }

    @Test
    fun thumbnailThatWouldBeStretchedIsRejected() {
        // The same 540x405 thumbnail centre-cropped into a 512 px square needs a 1.26x stretch.
        assertFalse(MediaStoreThumbnailModelLoader.platformThumbnailFills(540, 405, 512, 512))
        assertFalse(MediaStoreThumbnailModelLoader.platformThumbnailFills(0, 0, 256, 256))
    }

    @Test
    fun embeddedRearCameraThumbnailUsesPlatformLoader() {
        assertFalse(
            shouldBypassPlatformThumbnail(
                hasEmbeddedThumbnail = true,
                lensModel = "Pixel 10 Pro back camera 6.9mm f/1.68",
            )
        )
    }
}
