/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.glide

import com.bumptech.glide.request.target.Target
import com.dot.gallery.core.decoder.SketchHeifDecoder
import com.dot.gallery.core.image.thumbnail.HeavyThumbFormat
import com.dot.gallery.feature.viewer.components.media.HEIF_PLATFORM_PREVIEW_EXTRA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for #1385: grid-sized HEIC must reach the platform thumbnail. */
class HeifPlatformThumbnailRoutingTest {

    @Test
    fun gridSizedPlatformDecodableHeicDefersToPlatformThumbnail() {
        assertTrue(defer(width = 256, height = 256))
        assertTrue(defer(width = 360, height = 512))
    }

    @Test
    fun overTenBitHeifKeepsSoftwareDecode() {
        assertFalse(defer(width = 256, height = 256, heavyFormat = HeavyThumbFormat.HEIF_SW))
    }

    @Test
    fun largeOrUnboundedRequestsKeepFullDecode() {
        assertFalse(defer(width = 513, height = 256))
        assertFalse(defer(width = 1080, height = 1080))
        assertFalse(defer(width = Target.SIZE_ORIGINAL, height = Target.SIZE_ORIGINAL))
        assertFalse(defer(width = 0, height = 256))
    }

    @Test
    fun nonMediaStoreUrisKeepFullDecode() {
        assertFalse(defer(width = 256, height = 256, isMediaStoreUri = false))
    }

    @Test
    fun deferralMatchesMediaStoreLoaderScope() {
        // A deferred request must be one the MediaStore loader accepts, or nothing serves it.
        for (size in listOf(1, 256, 512, 513, Target.SIZE_ORIGINAL)) {
            assertEquals(
                MediaStoreThumbnailModelLoader.coversSize(size, size, maxDimension = CAP),
                defer(width = size, height = size),
            )
        }
    }

    @Test
    fun viewerPreviewExtraMatchesHeifDecoderKey() {
        assertEquals(SketchHeifDecoder.Factory.PLATFORM_PREVIEW_EXTRA, HEIF_PLATFORM_PREVIEW_EXTRA)
    }

    private fun defer(
        width: Int,
        height: Int,
        isMediaStoreUri: Boolean = true,
        heavyFormat: HeavyThumbFormat? = null,
    ) = shouldDeferHeifToPlatformThumbnail(isMediaStoreUri, width, height, heavyFormat, CAP)

    private companion object {
        // Explicit cap: the device-derived default reads display metrics, unavailable on the JVM.
        const val CAP = 512
    }
}
