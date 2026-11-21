/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.domain.model.CaptureTimeOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith


/**
 * `toUriMedia` builds an `android.net.Uri`, so it can only run on device —
 * this pins the issue #1476 contract that a stored epoch-0 `takenTimestamp`
 * surfaces as no capture time and the modified-time fallback origin.
 */
@RunWith(AndroidJUnit4::class)
class CloudMediaEntityCaptureTimeTest {

    @Test
    fun entityWithZeroTakenTimestamp_fallsBackToModified() {
        val media = CloudMediaEntity(
            remoteId = "a.jpg",
            providerType = ProviderType.NEXTCLOUD,
            serverConfigId = 1L,
            label = "a.jpg",
            mimeType = "image/jpeg",
            timestamp = 1795195761000L,
            takenTimestamp = 0L
        ).toUriMedia()

        assertNull(media.takenTimestamp)
        assertEquals(CaptureTimeOrigin.MODIFIED_FALLBACK, media.captureTimeOrigin)
    }
}
