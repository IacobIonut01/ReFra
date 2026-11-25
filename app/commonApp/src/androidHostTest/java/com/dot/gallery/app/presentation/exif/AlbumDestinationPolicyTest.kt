/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.exif

import com.dot.gallery.domain.model.resolveAlbumAbsolutePath
import com.dot.gallery.feature.exif.ui.isAlbumMoveDestinationEnabled
import com.dot.gallery.feature.exif.ui.isAlbumCopyDestinationEnabled
import com.dot.gallery.feature.exif.ui.resolveNewAlbumMovePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.feature.exif.ui.AlbumDestinationSource


class AlbumDestinationPolicyTest {

    @Test
    fun matchingMediaStoreVolumesEnableExistingAlbum() {
        assertTrue(
            isAlbumMoveDestinationEnabled(
                hasFullMediaAccess = false,
                albumVolume = "external_primary",
                albumRelativePath = "Pictures/Target/",
                sources = listOf(
                    AlbumDestinationSource("external_primary", "DCIM/Camera/", "image/jpeg"),
                    AlbumDestinationSource("external_primary", "Pictures/Source/", "image/jpeg"),
                ),
                hasAllFilesAccess = false,
            )
        )
    }

    @Test
    fun anyDifferentMediaStoreVolumeDisablesBatchWithoutFullAccess() {
        assertFalse(
            isAlbumMoveDestinationEnabled(
                hasFullMediaAccess = false,
                albumVolume = "external_primary",
                albumRelativePath = "Pictures/Target/",
                sources = listOf(
                    AlbumDestinationSource("external_primary", "DCIM/Camera/", "image/jpeg"),
                    AlbumDestinationSource("71f8-2c0a", "Pictures/Source/", "image/jpeg"),
                ),
                hasAllFilesAccess = false,
            )
        )
    }

    @Test
    fun androidMediaDestinationIsDisabledWithoutFullAccess() {
        val regularSource = listOf(
            AlbumDestinationSource("external_primary", "DCIM/Camera/", "image/jpeg"),
        )

        assertFalse(
            isAlbumMoveDestinationEnabled(
                false,
                "external_primary",
                "Android/media/example.app/Pictures/",
                regularSource,
                hasAllFilesAccess = false,
            )
        )
        assertFalse(
            isAlbumCopyDestinationEnabled(
                hasFullMediaAccess = false,
                albumRelativePath = "Android/media/example.app/Pictures/",
                hasAllFilesAccess = false,
            )
        )
    }

    @Test
    fun fullAccessAllowsCrossVolumeButNeverCloudDestination() {
        val sources = listOf(AlbumDestinationSource("external_primary", "DCIM/Camera/", "image/jpeg"))

        assertTrue(
            isAlbumMoveDestinationEnabled(true, "71f8-2c0a", "Pictures/Target/", sources, hasAllFilesAccess = true)
        )
        assertFalse(
            isAlbumMoveDestinationEnabled(
                true,
                "IMMICH",
                "cloud/IMMICH/Album",
                sources,
                hasAllFilesAccess = true,
                isCloudAlbum = true,
            )
        )
        assertTrue(
            isAlbumMoveDestinationEnabled(
                true,
                "external_primary",
                "cloud/LocalAlbum/",
                sources,
                hasAllFilesAccess = true,
                isCloudAlbum = false,
            )
        )
    }

    @Test
    fun restrictedSourceStaysEnabledForWritableAlbum() {
        // A WhatsApp image (Android/media/com.whatsapp/...) can be copied into an existing album,
        // and moved there through the copy + delete-request path.
        val restrictedSource = listOf(
            AlbumDestinationSource(
                "external_primary",
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/",
                "image/jpeg",
            ),
        )

        assertTrue(
            isAlbumCopyDestinationEnabled(
                hasFullMediaAccess = false,
                albumRelativePath = "Pictures/Target/",
                hasAllFilesAccess = false,
            )
        )
        assertTrue(
            isAlbumMoveDestinationEnabled(
                false,
                "external_primary",
                "Pictures/Target/",
                restrictedSource,
                hasAllFilesAccess = false,
            )
        )
    }

    @Test
    fun copyToRestrictedOrCloudAlbumStaysDisabled() {
        assertFalse(
            isAlbumCopyDestinationEnabled(
                hasFullMediaAccess = false,
                albumRelativePath = "Android/media/example.app/Pictures/",
                hasAllFilesAccess = false,
            )
        )
        assertFalse(
            isAlbumCopyDestinationEnabled(
                hasFullMediaAccess = true,
                albumRelativePath = "cloud/IMMICH/Album",
                isCloudAlbum = true,
                hasAllFilesAccess = true,
            )
        )
        assertTrue(
            isAlbumCopyDestinationEnabled(
                hasFullMediaAccess = true,
                albumRelativePath = "Android/media/example.app/Pictures/",
                hasAllFilesAccess = true,
            )
        )
    }

    @Test
    fun imageIntoMoviesIsDisabledWithoutAllFilesAccessAndEnabledWithIt() {
        val imageSource = listOf(AlbumDestinationSource("external_primary", "DCIM/Camera/", "image/jpeg"))

        assertFalse(
            isAlbumCopyDestinationEnabled(
                hasFullMediaAccess = true,
                albumRelativePath = "Movies/Trips/",
                sources = imageSource,
                hasAllFilesAccess = false,
            )
        )
        assertFalse(
            isAlbumMoveDestinationEnabled(
                hasFullMediaAccess = true,
                albumVolume = "external_primary",
                albumRelativePath = "Movies/Trips/",
                sources = imageSource,
                hasAllFilesAccess = false,
            )
        )
        assertTrue(
            isAlbumCopyDestinationEnabled(
                hasFullMediaAccess = true,
                albumRelativePath = "Movies/Trips/",
                sources = imageSource,
                hasAllFilesAccess = true,
            )
        )
    }

    @Test
    fun imageIntoDownloadIsEnabled() {
        val imageSource = listOf(AlbumDestinationSource("external_primary", "DCIM/Camera/", "image/jpeg"))

        assertTrue(
            isAlbumCopyDestinationEnabled(
                hasFullMediaAccess = true,
                albumRelativePath = "Download/",
                sources = imageSource,
                hasAllFilesAccess = true,
            )
        )
        assertTrue(
            isAlbumMoveDestinationEnabled(
                hasFullMediaAccess = true,
                albumVolume = "external_primary",
                albumRelativePath = "Download/",
                sources = imageSource,
                hasAllFilesAccess = true,
            )
        )
    }

    @Test
    fun moveDisablesExactSourceFolderButAllowsDifferentSameNamedFolder() {
        val sources = listOf(AlbumDestinationSource("external_primary", "DCIM/Camera/", "image/jpeg"))

        assertFalse(
            isAlbumMoveDestinationEnabled(
                false,
                "external_primary",
                "DCIM/Camera/",
                sources,
                hasAllFilesAccess = false,
            )
        )
        assertTrue(
            isAlbumMoveDestinationEnabled(
                false,
                "external_primary",
                "Pictures/Camera/",
                sources,
                hasAllFilesAccess = false,
            )
        )
        assertTrue(
            isAlbumMoveDestinationEnabled(
                false,
                "external_primary",
                "DCIM/Camera/",
                sources + AlbumDestinationSource("external_primary", "Pictures/Camera/", "image/jpeg"),
                hasAllFilesAccess = false,
            )
        )
        assertFalse(
            isAlbumMoveDestinationEnabled(
                false,
                "external_primary",
                "dcim//camera/",
                sources,
                hasAllFilesAccess = false,
            )
        )
    }

    @Test
    fun cloudOnlySelectionCanTargetWritableLocalAlbum() {
        assertTrue(
            isAlbumMoveDestinationEnabled(
                false,
                "external_primary",
                "Pictures/Target/",
                emptyList(),
                hasAllFilesAccess = false,
            )
        )
    }

    @Test
    fun newAlbumUsesPicturesWithoutAllFilesAccess() {
        assertEquals("Pictures/Receipts", resolveNewAlbumMovePath("Receipts", false))
        assertEquals("Receipts", resolveNewAlbumMovePath("Receipts", true))
    }

    @Test
    fun albumAbsolutePathUsesFilesystemRootInsteadOfMediaStoreVolumeName() {
        assertEquals(
            "/storage/emulated/0/Pictures/Target/",
            resolveAlbumAbsolutePath(
                pathToThumbnail = "/storage/emulated/0/Pictures/Target/photo.jpg",
                relativePath = "Pictures/Target/",
                storageVolume = "external_primary",
            )
        )
    }

    @Test
    fun virtualParentAbsolutePathUsesItsRelativePath() {
        assertEquals(
            "/storage/emulated/0/Pictures/Parent/",
            resolveAlbumAbsolutePath(
                pathToThumbnail = "/storage/emulated/0/Pictures/Parent/Child/photo.jpg",
                relativePath = "Pictures/Parent/",
                storageVolume = "external_primary",
            )
        )
    }

    @Test
    fun sdAlbumAbsolutePathRetainsMountedFilesystemRoot() {
        assertEquals(
            "/storage/71F8-2C0A/Pictures/Target/",
            resolveAlbumAbsolutePath(
                pathToThumbnail = "/storage/71F8-2C0A/Pictures/Target/photo.jpg",
                relativePath = "Pictures/Target/",
                storageVolume = "71f8-2c0a",
            )
        )
    }

    @Test
    fun unresolvedSdAlbumDoesNotFallBackToPrimaryStorage() {
        assertEquals(
            "",
            resolveAlbumAbsolutePath(
                pathToThumbnail = "",
                relativePath = "Pictures/Target/",
                storageVolume = "71f8-2c0a",
            )
        )
    }
}
