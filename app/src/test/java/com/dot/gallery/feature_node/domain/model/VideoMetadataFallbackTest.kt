/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for #1267: on Android 12 the isolated metadata service's
 * MediaMetadataRetriever leg takes the whole IPC reply down, so videos must be
 * probed in-process and merged without clobbering whatever the service returned.
 */
class VideoMetadataFallbackTest {

    @Test
    fun videoProbeNeeded_requiresAllCoreFields() {
        assertFalse(videoProbeNeeded(hasDuration = true, hasWidth = true, hasHeight = true))
    }

    @Test
    fun videoProbeNeeded_missingDurationTriggersProbe() {
        assertTrue(videoProbeNeeded(hasDuration = false, hasWidth = true, hasHeight = true))
    }

    @Test
    fun videoProbeNeeded_missingDimensionsTriggerProbe() {
        assertTrue(videoProbeNeeded(hasDuration = true, hasWidth = false, hasHeight = true))
        assertTrue(videoProbeNeeded(hasDuration = true, hasWidth = true, hasHeight = false))
    }

    @Test
    fun mergeMissing_backfillsOnlyAbsentFields() {
        val isolated = VideoProbe(durationMs = 5_000L, videoWidth = 1920, videoHeight = 1080)
        val probe = VideoProbe(
            durationMs = 9_999L,
            videoWidth = 640,
            videoHeight = 480,
            frameRate = 30f,
            bitRate = 8_000_000
        )

        val merged = isolated.mergeMissing(probe)

        // Service values win where present
        assertEquals(5_000L, merged.durationMs)
        assertEquals(1920, merged.videoWidth)
        assertEquals(1080, merged.videoHeight)
        // Probe fills what the isolated reply lacked
        assertEquals(30f, merged.frameRate)
        assertEquals(8_000_000, merged.bitRate)
    }

    @Test
    fun mergeMissing_nullProbeLeavesProbeUnchanged() {
        val probe = VideoProbe(durationMs = 3_000L)
        assertSame(probe, probe.mergeMissing(null))
    }

    @Test
    fun mergeMissing_emptyIsolatedResultTakesAllProbeFields() {
        val probe = VideoProbe(durationMs = 4_200L, videoWidth = 1280, videoHeight = 720)

        val merged = VideoProbe().mergeMissing(probe)

        assertEquals(probe, merged)
    }

    @Test
    fun isEmpty_detectsUselessProbe() {
        assertTrue(VideoProbe().isEmpty)
        assertFalse(VideoProbe(durationMs = 1L).isEmpty)
        assertFalse(VideoProbe(bitRate = 100).isEmpty)
    }

    @Test
    fun videoProbe_nullableFieldsDefaultToNull() {
        val probe = VideoProbe()
        assertNull(probe.durationMs)
        assertNull(probe.videoWidth)
        assertNull(probe.videoHeight)
        assertNull(probe.frameRate)
        assertNull(probe.bitRate)
    }
}
