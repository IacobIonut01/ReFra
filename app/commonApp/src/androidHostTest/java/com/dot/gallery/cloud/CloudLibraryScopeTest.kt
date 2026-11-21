/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.cloud.core.CloudLibraryScope
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudLibraryRoots
import com.dot.gallery.cloud.core.encode
import com.dot.gallery.cloud.core.isInCloudLibrary
import com.dot.gallery.cloud.core.libraryScope
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.data.entity.CloudUploadPrefEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLibraryScopeTest {

    private fun config(
        uploadBasePath: String = "",
        uploadVideosPath: String = ""
    ) = CloudServerConfigEntity(
        id = 1L,
        providerType = ProviderType.WEBDAV,
        serverUrl = "http://127.0.0.1:18081",
        uploadBasePath = uploadBasePath,
        uploadVideosPath = uploadVideosPath
    )

    private fun pref(
        albumLabel: String,
        customPath: String = ""
    ) = CloudUploadPrefEntity(
        serverConfigId = 1L,
        albumId = albumLabel.hashCode().toLong(),
        providerType = ProviderType.WEBDAV,
        albumLabel = albumLabel,
        uploadEnabled = true,
        customPath = customPath
    )

    @Test
    fun encodeDecodeRoundTrips() {
        assertEquals("", CloudLibraryScope.WholeAccount.encode())
        assertEquals(CloudLibraryScope.WholeAccount, CloudLibraryScope.decode(""))
        assertEquals("backup:", CloudLibraryScope.BackupFolders.encode())
        assertEquals(CloudLibraryScope.BackupFolders, CloudLibraryScope.decode("backup:"))
        assertEquals("folder:Photos/Family", CloudLibraryScope.Folder("Photos/Family").encode())
        assertEquals(CloudLibraryScope.Folder("Photos/Family"), CloudLibraryScope.decode("folder:Photos/Family"))
    }

    @Test
    fun decodeTreatsUnknownAndEmptyFolderValuesAsWholeAccount() {
        assertEquals(CloudLibraryScope.WholeAccount, CloudLibraryScope.decode("Camera,Pictures"))
        assertEquals(CloudLibraryScope.WholeAccount, CloudLibraryScope.decode("folder:../"))
        assertEquals(CloudLibraryScope.WholeAccount, CloudLibraryScope.decode("folder:"))
    }

    @Test
    fun libraryScopeReadsSyncFoldersColumn() {
        assertEquals(CloudLibraryScope.WholeAccount, config().copy(syncFolders = "").libraryScope)
        assertEquals(CloudLibraryScope.BackupFolders, config().copy(syncFolders = "backup:").libraryScope)
        assertEquals(
            CloudLibraryScope.Folder("Photos"),
            config().copy(syncFolders = "folder:Photos").libraryScope
        )
        assertEquals(CloudLibraryScope.WholeAccount, config().copy(syncFolders = "Camera,Pictures").libraryScope)
    }

    @Test
    fun wholeAccountRootsAtServerRoot() {
        assertEquals(listOf(""), cloudLibraryRoots(CloudLibraryScope.WholeAccount, config(), emptyList(), "Photos"))
    }

    @Test
    fun backupFoldersWithBlankBaseUsesPerAlbumTargets() {
        val roots = cloudLibraryRoots(
            CloudLibraryScope.BackupFolders,
            config(),
            listOf(pref("Screenshots"), pref("Camera")),
            "Photos"
        )
        assertEquals(listOf("Camera", "Screenshots"), roots)
    }

    @Test
    fun backupFoldersWithBaseCollapsesToBaseRoot() {
        val roots = cloudLibraryRoots(
            CloudLibraryScope.BackupFolders,
            config(uploadBasePath = "ReFra", uploadVideosPath = "ReFra/Videos"),
            listOf(pref("Screenshots"), pref("Camera")),
            "Photos"
        )
        assertEquals(listOf("ReFra"), roots)
    }

    @Test
    fun backupFoldersWithVideosPathKeepsBothRoots() {
        val roots = cloudLibraryRoots(
            CloudLibraryScope.BackupFolders,
            config(uploadVideosPath = "Videos"),
            listOf(pref("Camera")),
            "Photos"
        )
        assertEquals(listOf("Camera", "Videos"), roots)
    }

    @Test
    fun backupFoldersIncludesCustomPathOverride() {
        val roots = cloudLibraryRoots(
            CloudLibraryScope.BackupFolders,
            config(),
            listOf(pref("Camera", customPath = "Shared/Phone")),
            "Photos"
        )
        assertEquals(listOf("Shared/Phone"), roots)
    }

    @Test
    fun backupFoldersWithoutPrefsOrPathsFallsBackToDefaultFolder() {
        val roots = cloudLibraryRoots(
            CloudLibraryScope.BackupFolders,
            config(),
            emptyList(),
            "Photos"
        )
        assertEquals(listOf("Photos"), roots)
    }

    @Test
    fun folderScopeUsesChosenPath() {
        assertEquals(
            listOf("Photos/Family"),
            cloudLibraryRoots(CloudLibraryScope.Folder("Photos/Family"), config(), emptyList(), "Photos")
        )
    }

    @Test
    fun isInCloudLibraryMatchesRoots() {
        val roots = listOf("Screenshots")
        assertTrue(isInCloudLibrary("Screenshots/a.png", roots))
        assertTrue(isInCloudLibrary("Screenshots/Sub/b.png", roots))
        assertFalse(isInCloudLibrary("ScreenshotsOld/a.png", roots))
        assertFalse(isInCloudLibrary("Other/a.png", roots))
        assertTrue(isInCloudLibrary("anything/at/all.png", listOf("")))
    }
}
