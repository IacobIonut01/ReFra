/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import com.dot.gallery.core.metadata.MetadataDirectory
import com.dot.gallery.core.metadata.MetadataTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataViewStateTest {

    private val directories = listOf(
        MetadataDirectory(
            name = "Exif IFD0",
            tags = listOf(MetadataTag("Make", "Google"), MetadataTag("Model", "Pixel 6")),
        ),
        MetadataDirectory(
            name = "GPS",
            tags = listOf(MetadataTag("GPS Latitude", "51° 19' 18\"")),
        ),
    )

    @Test
    fun blankQueryKeepsEverything() {
        assertEquals(directories, directories.filterMetadata("  "))
    }

    @Test
    fun queryMatchesTagNamesAndValuesCaseInsensitively() {
        assertEquals(
            listOf(directories[0].copy(tags = listOf(MetadataTag("Model", "Pixel 6")))),
            directories.filterMetadata("pixel"),
        )
        assertEquals(listOf("GPS"), directories.filterMetadata("latitude").map { it.name })
    }

    @Test
    fun directoryNameMatchKeepsAllItsTags() {
        assertEquals(listOf(directories[1]), directories.filterMetadata("gps"))
    }

    @Test
    fun noMatchLeavesNothing() {
        assertTrue(directories.filterMetadata("aperture").isEmpty())
    }

    @Test
    fun fieldCountSumsEveryDirectory() {
        assertEquals(3, MetadataViewState(isLoading = false, directories = directories).fieldCount)
    }

    @Test
    fun clipboardTextHasAHeadingPerDirectory() {
        assertEquals(
            "Exif IFD0\nMake: Google\nModel: Pixel 6\n\nGPS\nGPS Latitude: 51° 19' 18\"",
            directories.toClipboardText(),
        )
    }
}
