/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.cloud.sync.targetPathCandidates
import com.dot.gallery.data.entity.CloudUploadPrefEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #1301: older backups made under legacy path schemes must still verify —
 * candidates include the resolved path, the bare album folder, and the
 * provider default (null).
 */
class FreeUpSpacePathCandidatesTest {

    private fun pref(albumLabel: String, customPath: String = "") =
        CloudUploadPrefEntity(serverConfigId = 1, albumId = 1, albumLabel = albumLabel,
            providerType = com.dot.gallery.cloud.core.ProviderType.WEBDAV, customPath = customPath)

    @Test
    fun includesLegacyAlbumOnlyAndDefault() {
        val c = targetPathCandidates(config = null, preference = pref("Camera"), isVideo = false)
        assertEquals(listOf("Camera", null), c)
    }

    @Test
    fun deduplicates() {
        val c = targetPathCandidates(config = null, preference = pref("Camera"), isVideo = false)
        assertEquals(c.size, c.toSet().size)
    }

    @Test
    fun customPathKeepsFallbacks() {
        val c = targetPathCandidates(config = null, preference = pref("Camera", "Backup/Photos"), isVideo = false)
        assertEquals(listOf("Backup/Photos", "Camera", null), c)
    }
}
