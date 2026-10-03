/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image

import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.dao.withPreservedCaptureTime
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.repository.needsCaptureTimeFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCaptureTimeTest {
    private fun image(
        provider: ProviderType = ProviderType.WEBDAV,
        remoteId: String = "image.jpg",
        configId: Long = 1L
    ) = CloudMediaEntity(
        remoteId = remoteId,
        providerType = provider,
        serverConfigId = configId,
        originalUrl = "https://example.test/image.jpg",
        mimeType = "image/jpeg",
        size = 1234L,
        timestamp = 2000L
    )

    private val media = image()

    @Test
    fun onlyUndatedWebDavOriginalImagesAreEligible() {
        for (provider in listOf(ProviderType.WEBDAV, ProviderType.OWNCLOUD, ProviderType.NEXTCLOUD)) {
            assertTrue(image(provider = provider).needsCaptureTimeFrom(media.originalUrl))
        }
        assertFalse(media.needsCaptureTimeFrom("https://example.test/preview.jpg"))
        assertFalse(media.copy(takenTimestamp = 1000L).needsCaptureTimeFrom(media.originalUrl))
        assertFalse(media.copy(mimeType = "video/mp4").needsCaptureTimeFrom(media.originalUrl))
        assertFalse(image(provider = ProviderType.IMMICH).needsCaptureTimeFrom(media.originalUrl))
        assertFalse(media.copy(originalUrl = "").needsCaptureTimeFrom(""))
    }

    @Test
    fun changedRevisionOrIdentityDoesNotInheritDate() {
        val previous = media.copy(takenTimestamp = 1000L)
        for (incoming in listOf(
            media.copy(size = 999L),
            media.copy(timestamp = 3000L),
            media.copy(originalUrl = "https://other.test/image.jpg"),
            media.copy(mimeType = "video/mp4"),
            image(remoteId = "other.jpg"),
            image(configId = 2L),
            image(provider = ProviderType.OWNCLOUD)
        )) {
            assertNull(incoming.withPreservedCaptureTime(previous).takenTimestamp)
        }
    }

    @Test
    fun incomingDateAndOtherProvidersKeepTheirExistingBehavior() {
        val previous = media.copy(takenTimestamp = 1000L)
        assertEquals(500L, media.copy(takenTimestamp = 500L).withPreservedCaptureTime(previous).takenTimestamp)
        val other = image(provider = ProviderType.IMMICH)
        assertNull(other.withPreservedCaptureTime(other.copy(takenTimestamp = 1000L)).takenTimestamp)
        assertNull(media.withPreservedCaptureTime(null).takenTimestamp)
    }
}
