/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.data.repository

import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.CloudMapMarker
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.CloudServerInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.MemoryInfo
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SharedLinkInfo
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumCopyResult
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumShare
import com.dot.gallery.cloud.core.capabilities.RemoteNameConflictPolicy
import com.dot.gallery.cloud.core.capabilities.SyncDelta
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.domain.model.Media
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface CloudRepository {

    val hasConfiguredProviders: Boolean
    val connectionStates: StateFlow<Map<ProviderType, ConnectionState>>
    val peopleInvalidation: SharedFlow<Unit>

    // Server management
    suspend fun testConnection(type: ProviderType, config: CloudServerConfig): Result<CloudServerInfo>
    suspend fun connect(type: ProviderType, config: CloudServerConfig): Result<Unit>
    suspend fun disconnect(type: ProviderType)
    fun notifyProviderConnected(type: ProviderType, state: ConnectionState)

    // Aggregated assets from all providers
    fun getAllRemoteAssets(page: Int = 0, pageSize: Int = 100): Flow<Resource<List<CloudMediaEntity>>>
    fun getRemoteAssets(type: ProviderType, page: Int = 0, pageSize: Int = 100): Flow<Resource<List<CloudMediaEntity>>>
    fun getRemoteFavorites(): Flow<Resource<List<CloudMediaEntity>>>
    fun getRemoteTrashed(): Flow<Resource<List<CloudMediaEntity>>>

    // Albums from all providers
    fun getAllRemoteAlbums(): Flow<Resource<List<CloudAlbum>>>
    fun getAlbumMedia(
        type: ProviderType,
        configId: Long,
        albumId: String
    ): Flow<Resource<List<CloudMediaEntity>>>

    // Album management — routed to the owning account; unsupported when the
    // provider lacks ALBUM_WRITE or doesn't implement the op (failure Result).
    suspend fun renameAlbum(
        type: ProviderType,
        configId: Long,
        remoteAlbumId: String,
        newName: String
    ): Result<CloudAlbum>
    suspend fun deleteRemoteAlbum(
        type: ProviderType,
        configId: Long,
        remoteAlbumId: String
    ): Result<Unit>
    suspend fun removeFromAlbum(
        type: ProviderType,
        configId: Long,
        remoteAlbumId: String,
        assetIds: List<String>
    ): Result<Unit>
    suspend fun updateAlbumUsers(
        type: ProviderType,
        configId: Long,
        remoteAlbumId: String,
        users: List<RemoteAlbumShare>
    ): Result<CloudAlbum>

    // People from all providers
    fun getAllPeople(): Flow<Resource<List<PersonInfo>>>
    fun getPersonMedia(
        type: ProviderType,
        configId: Long,
        personId: String
    ): Flow<Resource<List<Media>>>

    // Map markers from all providers
    fun getAllMapMarkers(): Flow<Resource<List<CloudMapMarker>>>

    // Smart search across all providers
    suspend fun smartSearch(query: String): Result<List<Media>>

    /**
     * Server-side reverse image search anchored at one of the provider's own assets.
     * Only valid when the owning account declares SMART_SEARCH capability.
     */
    suspend fun smartSearchByAsset(
        type: ProviderType,
        configId: Long,
        remoteId: String
    ): Result<List<Media>>

    /**
     * Global media ids of cloud assets whose synced server tags match [query]
     * (name or value contains). Empty when no tag-capable account is synced.
     */
    suspend fun findTagMediaIds(query: String): List<Long>

    // Share links
    suspend fun createShareLink(
        type: ProviderType,
        configId: Long,
        assetIds: List<String>,
        expiresAt: Long? = null
    ): Result<String>

    // Sync
    suspend fun uploadAsset(type: ProviderType, localMedia: Media, targetPath: String? = null): Result<CloudMediaEntity>
    suspend fun copyAssetToAlbum(
        type: ProviderType,
        configId: Long,
        remoteAlbumId: String,
        localMedia: Media,
        conflictPolicy: RemoteNameConflictPolicy = RemoteNameConflictPolicy.KEEP_BOTH,
        checksum: String? = null,
        continuationRemoteId: String? = null
    ): RemoteAlbumCopyResult
    suspend fun downloadAsset(type: ProviderType, remoteId: String): Result<android.net.Uri>
    suspend fun getSyncDelta(
        type: ProviderType,
        timestamp: Long,
        reconcileIndex: Boolean = false
    ): Result<SyncDelta>

    /**
     * Pull-to-refresh entry point: fetches the full delta with a complete-index
     * reconcile for every active account, persists upserts + deletions into
     * `cloud_media` (so Room-backed flows update the timeline/albums instantly), and
     * advances each account's sync watermark so the periodic worker doesn't redo it.
     * Returns the number of changed rows across all accounts.
     */
    suspend fun syncAllRemoteChanges(): Result<Int>

    // Search
    suspend fun search(query: String): Result<List<CloudMediaEntity>>

    // Delete a single remote asset from a specific account and drop it from the local cache.
    suspend fun deleteAsset(type: ProviderType, configId: Long, remoteId: String): Result<Unit>

    // Archive
    suspend fun toggleArchive(
        type: ProviderType,
        configId: Long,
        remoteId: String,
        archived: Boolean
    ): Result<Unit>
    fun getRemoteArchived(
        type: ProviderType,
        configId: Long
    ): Flow<Resource<List<CloudMediaEntity>>>
    suspend fun getCachedArchivedAsync(): List<CloudMediaEntity>

    // Shared links management
    fun getSharedLinks(type: ProviderType): Flow<Resource<List<SharedLinkInfo>>>
    fun getSharedLinks(
        type: ProviderType,
        configId: Long
    ): Flow<Resource<List<SharedLinkInfo>>>
    suspend fun deleteSharedLink(type: ProviderType, configId: Long, linkId: String): Result<Unit>
    suspend fun updateSharedLink(
        type: ProviderType,
        configId: Long,
        linkId: String,
        updates: Map<String, Any>
    ): Result<Unit>

    // People editing
    suspend fun updatePersonName(
        type: ProviderType,
        configId: Long,
        personId: String,
        name: String
    ): Result<Unit>
    suspend fun updatePersonBirthDate(
        type: ProviderType,
        configId: Long,
        personId: String,
        birthDate: String
    ): Result<Unit>

    // Trash bulk operations
    suspend fun emptyTrash(type: ProviderType): Result<Unit>
    suspend fun restoreAllTrash(type: ProviderType): Result<Unit>

    // Memories
    fun getMemories(
        type: ProviderType,
        configId: Long
    ): Flow<Resource<List<MemoryInfo>>>

    // Cache
    fun getCachedMedia(): Flow<List<CloudMediaEntity>>
    suspend fun getCachedMediaAsync(): List<CloudMediaEntity>
    fun getCachedFavorites(): Flow<List<CloudMediaEntity>>
    suspend fun getCachedFavoritesAsync(): List<CloudMediaEntity>
    fun getCachedTrashed(): Flow<List<CloudMediaEntity>>
    suspend fun getCachedTrashedAsync(): List<CloudMediaEntity>
    fun getCachedMediaByProvider(type: ProviderType): Flow<List<CloudMediaEntity>>
    suspend fun clearCache(type: ProviderType)
    suspend fun clearAllCache()
}
