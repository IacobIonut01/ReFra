/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.entity.CloudMediaEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * The remote byte range used to resolve capture times for cloud media
 * must respect the same network policy as other cloud transfers: never while
 * effectively offline, and on metered networks only for accounts that did not
 * restrict transfers to unmetered.
 */
class CaptureTimeIndexGateTest {

    @Test
    fun offlineBlocksFetch() {
        assertFalse(remoteCaptureFetchAllowed(effectiveOffline = true, unmetered = true, accountWifiOnly = false))
        assertFalse(remoteCaptureFetchAllowed(effectiveOffline = true, unmetered = false, accountWifiOnly = false))
    }

    @Test
    fun meteredHonoursAccountWifiOnly() {
        assertFalse(remoteCaptureFetchAllowed(effectiveOffline = false, unmetered = false, accountWifiOnly = true))
        assertTrue(remoteCaptureFetchAllowed(effectiveOffline = false, unmetered = false, accountWifiOnly = false))
    }

    @Test
    fun unmeteredAlwaysAllowed() {
        assertTrue(remoteCaptureFetchAllowed(effectiveOffline = false, unmetered = true, accountWifiOnly = true))
        assertTrue(remoteCaptureFetchAllowed(effectiveOffline = false, unmetered = true, accountWifiOnly = false))
    }

    @Test
    fun immichProviderTimeIsEmbedded() {
        assertTrue(providerCaptureTimeIsEmbedded(entity(ProviderType.IMMICH, taken = 1_700_000_000_000L)))
        assertFalse(providerCaptureTimeIsEmbedded(entity(ProviderType.IMMICH, taken = null)))
        assertFalse(providerCaptureTimeIsEmbedded(entity(ProviderType.IMMICH, taken = 0L)))
    }

    @Test
    fun webDavCreationDateIsNotEmbedded() {
        // #1294: a generic WebDAV creationdate is the server's file-creation
        // time, not embedded EXIF — the probe must still run.
        assertFalse(
            providerCaptureTimeIsEmbedded(
                entity(ProviderType.WEBDAV, taken = 1_700_000_000_000L, dateTimeOriginal = null)
            )
        )
    }

    @Test
    fun nextcloudPhotoDateIsEmbedded() {
        assertTrue(
            providerCaptureTimeIsEmbedded(
                entity(
                    ProviderType.NEXTCLOUD,
                    taken = 1_700_000_000_000L,
                    dateTimeOriginal = "2012:06:30 12:32:11"
                )
            )
        )
        assertFalse(
            providerCaptureTimeIsEmbedded(
                entity(ProviderType.NEXTCLOUD, taken = 1_700_000_000_000L, dateTimeOriginal = null)
            )
        )
    }

    private fun entity(
        providerType: ProviderType,
        taken: Long?,
        dateTimeOriginal: String? = null
    ) = CloudMediaEntity(
        remoteId = "a.jpg",
        providerType = providerType,
        serverConfigId = 1L,
        label = "a.jpg",
        mimeType = "image/jpeg",
        timestamp = 1_700_000_000_000L,
        takenTimestamp = taken,
        dateTimeOriginal = dateTimeOriginal
    )
}
