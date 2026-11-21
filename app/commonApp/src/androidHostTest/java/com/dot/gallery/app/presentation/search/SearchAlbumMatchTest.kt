/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.search

import com.dot.gallery.feature.search.matchesAlbumQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * #1468: album-name queries (incl. the Screenshots chip) must also match the
 * remote folder inside a cloud item's relativePath — cloud items carry the
 * provider name in albumLabel, so label-only matching hid WebDAV-only media.
 */
class SearchAlbumMatchTest {

    @Test
    fun localLabelMatch() {
        assertTrue(matchesAlbumQuery("Screenshots", "Screenshots", isCloud = false, query = "Screenshots"))
    }

    @Test
    fun cloudFolderMatch() {
        assertTrue(matchesAlbumQuery("WebDAV", "WebDAV/Screenshots", isCloud = true, query = "Screenshots"))
        assertTrue(matchesAlbumQuery("WebDAV", "WebDAV/DCIM/Screenshots", isCloud = true, query = "Screenshots"))
    }

    @Test
    fun cloudFolderPrefixDoesNotMatch() {
        assertFalse(matchesAlbumQuery("WebDAV", "WebDAV/ScreenshotsOld", isCloud = true, query = "Screenshots"))
    }

    @Test
    fun localFolderPathDoesNotMatch() {
        assertFalse(matchesAlbumQuery("Camera", "DCIM/Screenshots/", isCloud = false, query = "Screenshots"))
    }

    @Test
    fun providerOnlyRelativePathNeverFolderMatches() {
        assertFalse(matchesAlbumQuery("Immich", "Immich", isCloud = true, query = "2024"))
    }

    @Test
    fun comparisonIsCaseSensitive() {
        assertFalse(matchesAlbumQuery("Screenshots", "Screenshots", isCloud = false, query = "SCREENSHOTS"))
        assertFalse(matchesAlbumQuery("WebDAV", "WebDAV/Screenshots", isCloud = true, query = "screenshots"))
    }
}
