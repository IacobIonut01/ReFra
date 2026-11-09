/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import com.dot.gallery.core.util.SynchronizedRefinedThumbnailLoadTracker
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class ThumbnailMotionTest {

    @Test
    fun movingGridUsesMotionTierForThumbnailWithoutRefinedLoad() {
        assertTrue(shouldUseMotionThumbnail(isMoving = true, hasLoadedRefined = false))
    }

    @Test
    fun movingGridKeepsCompletedRefinedThumbnail() {
        assertFalse(shouldUseMotionThumbnail(isMoving = true, hasLoadedRefined = true))
    }

    @Test
    fun idleGridUsesRefinedTier() {
        assertFalse(shouldUseMotionThumbnail(isMoving = false, hasLoadedRefined = false))
    }

    @Test
    fun heavyCodecRefinedRequestBridgesFromCachedMotionBitmap() {
        // #1385: heavy codecs used to refine with no bridge, flashing the placeholder.
        assertEquals(MotionThumbnailBridge.CACHED_ONLY, motionThumbnailBridge(isHeavyCodec = true))
    }

    @Test
    fun regularCodecRefinedRequestBridgesFromMotionBitmap() {
        assertEquals(MotionThumbnailBridge.DECODE, motionThumbnailBridge(isHeavyCodec = false))
    }

    @Test
    fun refinedLoadTrackerRemembersCompletedThumbnail() {
        val tracker = RefinedThumbnailLoadTracker(capacity = 2)
        val key = RefinedThumbnailKey(model = "media-1", signature = "version-1")

        tracker.markLoaded(key)

        assertTrue(tracker.hasLoaded(key))
    }

    @Test
    fun refinedLoadTrackerSeparatesModelAndSignatureChanges() {
        val tracker = RefinedThumbnailLoadTracker(capacity = 3)
        tracker.markLoaded(RefinedThumbnailKey(model = "media-1", signature = "version-1"))

        assertFalse(tracker.hasLoaded(RefinedThumbnailKey("media-2", "version-1")))
        assertFalse(tracker.hasLoaded(RefinedThumbnailKey("media-1", "version-2")))
    }

    @Test
    fun refinedLoadTrackerEvictsLeastRecentlyUsedThumbnail() {
        val tracker = RefinedThumbnailLoadTracker(capacity = 2)
        val media1 = RefinedThumbnailKey(model = "media-1", signature = "version-1")
        val media2 = RefinedThumbnailKey(model = "media-2", signature = "version-1")
        val media3 = RefinedThumbnailKey(model = "media-3", signature = "version-1")
        tracker.markLoaded(media1)
        tracker.markLoaded(media2)
        tracker.hasLoaded(media1)

        tracker.markLoaded(media3)

        assertTrue(tracker.hasLoaded(media1))
        assertFalse(tracker.hasLoaded(media2))
        assertTrue(tracker.hasLoaded(media3))
    }

    @Test
    fun sharedTrackerSurvivesConcurrentLoadCallbacks() {
        // Glide Compose fires load callbacks on its decode threads while composition reads on main.
        val tracker = SynchronizedRefinedThumbnailLoadTracker(capacity = 16)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val jobs = (0 until 8).map { worker ->
                pool.submit {
                    repeat(20_000) { i ->
                        val key = RefinedThumbnailKey(model = "media-${(worker * 31 + i) % 64}", signature = "v")
                        if (i % 2 == 0) tracker.markLoaded(key) else tracker.hasLoaded(key)
                    }
                }
            }
            jobs.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
