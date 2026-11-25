/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultRestorePathTest {

    @Test
    fun typedOriginalIsRestoredInPlace() {
        assertEquals(
            "Pictures/ReFraDocsPreview",
            vaultRestoreRelativePath("image/jpeg", "Pictures/ReFraDocsPreview/", false),
        )
        assertEquals(
            "Movies/Trips",
            vaultRestoreRelativePath("video/mp4", "Movies/Trips/", false),
        )
    }

    @Test
    fun nonTypedOriginalFallsBackToDefaultFolder() {
        assertEquals("Pictures/Restored", vaultRestoreRelativePath("image/jpeg", "Download/", false))
        assertEquals("Movies/Restored", vaultRestoreRelativePath("video/mp4", "Download/", false))
        assertEquals("Pictures/Restored", vaultRestoreRelativePath("image/jpeg", "", false))
        assertEquals("Pictures/Restored", vaultRestoreRelativePath("image/jpeg", "cloud/IMMICH/", false))
    }

    @Test
    fun filesOriginalFallsBackToDefaultFolderEvenWithAllFilesAccess() {
        assertEquals(
            "Pictures/Restored",
            vaultRestoreRelativePath("application/pdf", "Download/", true),
        )
        assertEquals(
            "Pictures/Restored",
            vaultRestoreRelativePath("application/pdf", "Download/", false),
        )
    }
}
