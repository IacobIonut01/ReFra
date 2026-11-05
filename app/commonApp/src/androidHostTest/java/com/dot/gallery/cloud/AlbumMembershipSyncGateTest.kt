/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.CloudAuthToken
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.CloudServerInfo
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.sync.albumMembershipSyncSupported
import com.dot.gallery.core.Resource
import com.dot.gallery.data.entity.CloudMediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for #1371: the upload album-sync pass must not run on
 * path-based providers. WebDAV/SMB/NFS declare [ProviderCapability.ALBUM_WRITE]
 * (createAlbum = mkdir at the share root) but their addToAlbum is unsupported,
 * so the pass only ever created stray empty root folders. The pass is now gated
 * on [ProviderCapability.ALBUM_MEMBERSHIP], declared only by membership-list
 * providers (Immich).
 */
class AlbumMembershipSyncGateTest {

    @Test
    fun pathProviderWithAlbumWriteIsStillExcluded() {
        // WebDAV declares ALBUM_WRITE but not ALBUM_MEMBERSHIP — the bug's case.
        val provider = FakeProvider(
            ProviderType.WEBDAV,
            setOf(
                ProviderCapability.REMOTE_ASSETS,
                ProviderCapability.REMOTE_ALBUMS,
                ProviderCapability.SYNC,
                ProviderCapability.ALBUM_WRITE,
            )
        )
        assertFalse(albumMembershipSyncSupported(provider))
    }

    @Test
    fun providerWithoutAlbumWriteIsExcluded() {
        val provider = FakeProvider(ProviderType.SMB, setOf(ProviderCapability.SYNC))
        assertFalse(albumMembershipSyncSupported(provider))
    }

    @Test
    fun membershipProviderIsIncluded() {
        val provider = FakeProvider(
            ProviderType.IMMICH,
            setOf(
                ProviderCapability.SYNC,
                ProviderCapability.ALBUM_WRITE,
                ProviderCapability.ALBUM_MEMBERSHIP,
            )
        )
        assertTrue(albumMembershipSyncSupported(provider))
    }

    private class FakeProvider(
        type: ProviderType,
        override val capabilities: Set<ProviderCapability>
    ) : RemoteMediaProvider {
        override val providerType = type
        override val displayName = "fake"
        override val isAvailable = true
        override val connectionState = MutableStateFlow(ConnectionState.CONNECTED)

        override suspend fun testConnection(config: CloudServerConfig) =
            Result.failure<CloudServerInfo>(UnsupportedOperationException())
        override suspend fun authenticate(config: CloudServerConfig) =
            Result.failure<CloudAuthToken>(UnsupportedOperationException())
        override fun getRemoteAssets(page: Int, pageSize: Int) = emptyMedia()
        override fun getRemoteAlbums(): Flow<Resource<List<CloudAlbum>>> =
            flowOf(Resource.Success(emptyList()))
        override fun getRemoteAlbumMedia(albumId: String) = emptyMedia()
        override fun getRemoteFavorites() = emptyMedia()
        override fun getRemoteTrashed() = emptyMedia()
        override suspend fun toggleFavorite(remoteId: String, favorite: Boolean) = Result.success(Unit)
        override suspend fun toggleArchive(remoteId: String, archived: Boolean) = Result.success(Unit)
        override suspend fun trashAsset(remoteId: String) = Result.success(Unit)
        override suspend fun restoreAsset(remoteId: String) = Result.success(Unit)
        override suspend fun deleteAsset(remoteId: String) = Result.success(Unit)
        override suspend fun emptyTrash() = Result.success(Unit)
        override suspend fun restoreAllTrash() = Result.success(Unit)
        override suspend fun createAlbum(name: String) =
            Result.failure<CloudAlbum>(UnsupportedOperationException())
        override suspend fun addToAlbum(albumId: String, assetIds: List<String>) = Result.success(Unit)
        override suspend fun search(query: String) = Result.success(emptyList<CloudMediaEntity>())
        override fun getRemoteArchived() = emptyMedia()
        override suspend fun getStorageInfo() =
            Result.failure<CloudStorageInfo>(UnsupportedOperationException())
        override fun getThumbnailUrl(remoteId: String, size: ThumbnailSize) = ""
        override fun getOriginalUrl(remoteId: String) = ""
        override fun getAuthHeaders() = emptyMap<String, String>()
        override fun configure(config: CloudServerConfig) = Unit

        private fun emptyMedia(): Flow<Resource<List<CloudMediaEntity>>> =
            flowOf(Resource.Success(emptyList()))
    }
}
