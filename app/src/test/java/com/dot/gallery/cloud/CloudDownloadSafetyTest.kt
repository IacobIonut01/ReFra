/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import android.net.Uri
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.core.capabilities.SyncDelta
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.canonicalBackupChecksum
import com.dot.gallery.cloud.sync.isLocalCopyCandidate
import com.dot.gallery.cloud.sync.localCopyMediaStoreId
import com.dot.gallery.cloud.sync.sanitizeDownloadPathSegment
import com.dot.gallery.cloud.sync.sanitizeDownloadSubPath
import com.dot.gallery.feature_node.domain.model.Media
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * JVM guards for the download-safety fixes behind #1270: no remote asset-store
 * layout may leak into user-visible MediaStore folders, and a REMOTE_ONLY row is
 * only re-downloaded when no verified identical local copy exists.
 */
class CloudDownloadSafetyTest {

    private object FakeSyncProvider : SyncCapableProvider {
        override val providerType = ProviderType.WEBDAV
        override val displayName = "WebDAV"
        override val isAvailable = false
        override val capabilities = emptySet<ProviderCapability>()
        override suspend fun uploadAsset(
            localMedia: Media,
            targetPath: String?
        ): Result<CloudMediaEntity> = Result.failure(UnsupportedOperationException())

        override suspend fun downloadAsset(remoteId: String): Result<Uri> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getSyncDelta(timestamp: Long, reconcileIndex: Boolean) =
            Result.failure<SyncDelta>(UnsupportedOperationException())

        override suspend fun bulkUploadCheck(hashes: List<String>) =
            Result.failure<Map<String, Boolean>>(UnsupportedOperationException())
    }

    private fun entity(relativePath: String) = CloudMediaEntity(
        remoteId = "r1",
        providerType = ProviderType.WEBDAV,
        serverConfigId = 7L,
        relativePath = relativePath
    )

    // region sanitizeDownloadSubPath

    @Test
    fun blankSubPathFallsBack() {
        assertEquals("Immich (me)", sanitizeDownloadSubPath("", "Immich (me)"))
        assertEquals("Immich (me)", sanitizeDownloadSubPath("///", "Immich (me)"))
        assertEquals("Cloud", sanitizeDownloadSubPath("", ""))
    }

    @Test
    fun userFolderSubPathSurvives() {
        assertEquals("Camera", sanitizeDownloadSubPath("Camera", "Fallback"))
        assertEquals("Photos/2024", sanitizeDownloadSubPath("/Photos/2024/", "Fallback"))
        // A hex-looking leaf alone is not an asset-store shard: only a
        // UUID-followed-by-2-hex-segment adjacency is rejected.
        assertEquals("MyAlbum/4f", sanitizeDownloadSubPath("MyAlbum/4f", "Fallback"))
        assertEquals("upload/photos", sanitizeDownloadSubPath("upload/photos", "Fallback"))
        // A bare UUID segment without a shard child is harmless.
        assertEquals(
            "e2f0c95f-f60b-41bd-af75-1c3d0f6b5a01",
            sanitizeDownloadSubPath("e2f0c95f-f60b-41bd-af75-1c3d0f6b5a01", "Fallback")
        )
    }

    @Test
    fun immichAssetStorePathsFallBack() {
        val uid = "e2f0c95f-f60b-41bd-af75-1c3d0f6b5a01"
        assertEquals(
            "Fallback",
            sanitizeDownloadSubPath("upload/$uid/ed/4f", "Fallback")
        )
        assertEquals(
            "Fallback",
            sanitizeDownloadSubPath("/upload/$uid/00", "Fallback")
        )
        assertEquals(
            "Fallback",
            sanitizeDownloadSubPath("photos/upload/$uid/ed", "Fallback")
        )
    }

    @Test
    fun traversalSegmentsAreDropped() {
        assertEquals("a/b", sanitizeDownloadSubPath("a/../b", "Fallback"))
        assertEquals("b", sanitizeDownloadSubPath("./b", "Fallback"))
        assertEquals("Fallback", sanitizeDownloadSubPath("..", "Fallback"))
    }

    // endregion

    // region sanitizeDownloadPathSegment

    @Test
    fun albumNameSegmentsAreCleaned() {
        assertEquals("My Album", sanitizeDownloadPathSegment("My Album"))
        assertEquals("a-b", sanitizeDownloadPathSegment("a/b"))
        assertEquals("a-b", sanitizeDownloadPathSegment("a\\b"))
        assertEquals("Album", sanitizeDownloadPathSegment(".."))
        assertEquals("Album", sanitizeDownloadPathSegment("..."))
        assertEquals("Album", sanitizeDownloadPathSegment("   "))
        assertEquals("hidden", sanitizeDownloadPathSegment(".hidden"))
    }

    // endregion

    // region isLocalCopyCandidate

    @Test
    fun candidateRequiresNameAndKnownSizeMatch() {
        assertTrue(isLocalCopyCandidate("IMG_1.jpg", 100L, "IMG_1.jpg", 100L))
        assertFalse(isLocalCopyCandidate("IMG_1.jpg", 100L, "IMG_1.jpg", 101L))
        // Unknown remote size defers entirely to the SHA-1 verify step.
        assertTrue(isLocalCopyCandidate("IMG_1.jpg", 0L, "IMG_1.jpg", 999L))
        assertFalse(isLocalCopyCandidate("IMG_1.jpg", 0L, "other.jpg", 999L))
        assertFalse(isLocalCopyCandidate("IMG_1.jpg", 100L, null, 100L))
        assertFalse(isLocalCopyCandidate("", 100L, "IMG_1.jpg", 100L))
    }

    // endregion

    // region canonicalBackupChecksum

    @Test
    fun sha1HexChecksumsNormalizeToLowercase() {
        val hex = "0123456789abcdef0123456789abcdef01234567"
        assertEquals(hex, canonicalBackupChecksum(hex))
        assertEquals(hex, canonicalBackupChecksum(hex.uppercase()))
    }

    @Test
    fun immichBase64ChecksumConvertsToHex() {
        val bytes = ByteArray(20) { it.toByte() }
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val expectedHex = bytes.joinToString("") { "%02x".format(it) }
        assertEquals(expectedHex, canonicalBackupChecksum(base64))
    }

    @Test
    fun unrecognizedChecksumPassesThrough() {
        assertEquals("not-a-checksum", canonicalBackupChecksum("not-a-checksum"))
        // Base64 that doesn't decode to 20 bytes is not a SHA-1: pass through.
        assertEquals("aGk=", canonicalBackupChecksum("aGk="))
    }

    // endregion

    // region localCopyMediaStoreId

    @Test
    fun mediaStoreUriYieldsRowId() {
        assertEquals(
            42L,
            localCopyMediaStoreId("content://media/external_primary/images/media/42")
        )
        assertNull(localCopyMediaStoreId("file:///data/user/0/app/cache/dl.tmp"))
        assertNull(localCopyMediaStoreId(""))
    }

    // endregion

    // region downloadSubPath default

    @Test
    fun defaultSubPathMirrorsRemoteFolders() = runTest {
        assertEquals("Photos/2024", FakeSyncProvider.downloadSubPath(entity("Photos/2024"), "NAS"))
        assertEquals("Camera", FakeSyncProvider.downloadSubPath(entity("/Camera/"), "NAS"))
    }

    @Test
    fun defaultSubPathFallsBackToAccountLabel() = runTest {
        assertEquals("NAS", FakeSyncProvider.downloadSubPath(entity(""), "NAS"))
        assertEquals("NAS", FakeSyncProvider.downloadSubPath(entity("/"), "NAS"))
        // A whitespace-padded path is NOT the hook's job — sanitizeDownloadSubPath in
        // the writer collapses it to the fallback downstream.
        assertEquals(" / ", FakeSyncProvider.downloadSubPath(entity(" / "), "NAS"))
    }

    // endregion
}
