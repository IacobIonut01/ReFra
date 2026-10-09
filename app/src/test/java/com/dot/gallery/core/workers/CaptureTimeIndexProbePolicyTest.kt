/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import com.dot.gallery.cloud.core.ProviderType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Path-based providers only expose filesystem timestamps (WebDAV `creationdate`,
 * mtime). Those must not suppress the embedded DateTimeOriginal probe. Immich
 * already sends EXIF-derived capture time as `localDateTime`, so a populated
 * takenTimestamp from Immich is authoritative.
 */
class CaptureTimeIndexProbePolicyTest {

    @Test
    fun immichTakenTimestampSkipsEmbeddedProbe() {
        assertTrue(
            skipEmbeddedCaptureProbe(ProviderType.IMMICH, takenTimestamp = 1_400_000_000_000L)
        )
    }

    @Test
    fun webdavTakenTimestampStillProbesEmbeddedMetadata() {
        assertFalse(
            skipEmbeddedCaptureProbe(ProviderType.WEBDAV, takenTimestamp = 1_700_000_000_000L)
        )
    }

    @Test
    fun nextcloudOwnCloudSmbNfsTakenTimestampStillProbes() {
        for (type in listOf(
            ProviderType.NEXTCLOUD,
            ProviderType.OWNCLOUD,
            ProviderType.SMB,
            ProviderType.NFS
        )) {
            assertFalse(
                "expected $type creationdate/mtime to stay a fallback, not skip EXIF",
                skipEmbeddedCaptureProbe(type, takenTimestamp = 1_700_000_000_000L)
            )
        }
    }

    @Test
    fun missingTakenTimestampNeverSkips() {
        for (type in ProviderType.remoteTypes()) {
            assertFalse(skipEmbeddedCaptureProbe(type, takenTimestamp = null))
        }
    }
}
