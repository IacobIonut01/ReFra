/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.core.AlbumMediaLoadMode
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.PendingRemovalScope
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.model.ImageEmbedding
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.LockedAlbum
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import com.dot.gallery.domain.model.PinnedAlbum
import com.dot.gallery.domain.model.TimelineSettings
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.util.MediaGroupType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [MediaDistributor] for docs previews — the demo photo library lives
 * entirely in [DocsGalleryData]; mutations (favorite toggles, pins, ignores,
 * collection edits) rewrite these flows so live previews react, but nothing
 * persists or touches the device. `MockedMediaDistributor` (androidMain) is the
 * coverage reference only — this is a plain common implementation.
 */
class DocsMediaDistributor(
    initialMedia: List<UriMedia> = DocsGalleryData.mediaItems(),
    private val initialCloudAlbums: List<Album> = emptyList(),
    initialCollections: List<CollectionWithCount> = DocsGalleryData.collections(initialMedia),
) : MediaDistributor {

    private val media = MutableStateFlow(initialMedia)

    private fun stateFor(items: List<UriMedia>) = DocsGalleryData.mediaStateFor(items)

    override val isRefreshing: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun invalidate() {}

    override val hasPermission: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val dateFormatsFlow: StateFlow<Triple<String, String, String>> =
        MutableStateFlow(Triple("EEE, MMM d", "EEE, MMM d, yyyy", "EEEE"))
    override var groupByMonth: Boolean = false
    override var groupByYear: Boolean = false
    override val groupSimilarMedia: StateFlow<Boolean> = MutableStateFlow(false)
    override val enabledGroupTypes: StateFlow<Set<MediaGroupType>> =
        MutableStateFlow(MediaGroupType.entries.toSet())
    override val mergeAlbumsByName: StateFlow<Boolean> = MutableStateFlow(false)

    override val settingsFlow: StateFlow<TimelineSettings?> = MutableStateFlow(null)

    private val albums = MutableStateFlow(DocsGalleryData.albums(initialMedia))
    private val ignored = MutableStateFlow(DocsGalleryData.ignoredAlbums())
    private val pinned = MutableStateFlow<List<PinnedAlbum>>(emptyList())
    private val collections = MutableStateFlow(initialCollections)
    private val vaults = MutableStateFlow(VaultState(vaults = DocsGalleryData.vaults(), isLoading = false))

    private val albumState = MutableStateFlow(
        AlbumState(
            albums = albums.value,
            albumsWithBlacklisted = albums.value,
            albumsUnpinned = albums.value,
            albumsCloud = initialCloudAlbums,
            collections = collections.value,
            isLoading = false,
        )
    )
    override val albumsFlow: StateFlow<AlbumState> = albumState
    override val blacklistedAlbumsFlow: StateFlow<List<IgnoredAlbum>> = ignored
    override val pinnedAlbumsFlow: StateFlow<List<PinnedAlbum>> = pinned
    override val lockedAlbumsFlow: StateFlow<List<LockedAlbum>> = MutableStateFlow(emptyList())
    override val mergedSubfolderAlbumsFlow: StateFlow<List<MergedSubfolderAlbum>> =
        MutableStateFlow(emptyList())

    private val timelineMedia = MutableStateFlow(stateFor(initialMedia))
    override val timelineMediaFlow: StateFlow<MediaState<UriMedia>> = timelineMedia

    override fun albumTimelineMediaFlow(
        albumId: Long,
        loadMode: AlbumMediaLoadMode
    ): Flow<MediaState<UriMedia>> = media.map { list ->
        stateFor(list.filter { it.albumID == albumId || albumId == -1L })
    }

    private val favorites = MutableStateFlow(stateFor(initialMedia.filter { it.favorite == 1 }))
    override val favoritesMediaFlow: StateFlow<MediaState<UriMedia>> = favorites
    override val trashMediaFlow: StateFlow<MediaState<UriMedia>> =
        MutableStateFlow(stateFor(emptyList()))

    override val pendingRemovalIds: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
    private val _favoriteOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    override val favoriteOverrides: StateFlow<Map<Long, Boolean>> = _favoriteOverrides

    override fun markPendingRemoval(ids: Collection<Long>, scope: PendingRemovalScope) {}
    override fun unmarkPendingRemoval(ids: Collection<Long>) {}

    override fun setFavoriteOverride(mediaId: Long, favorite: Boolean) {
        _favoriteOverrides.update { it + (mediaId to favorite) }
        media.update { list -> list.map { m -> if (m.id == mediaId) m.copy(favorite = if (favorite) 1 else 0) else m } }
        timelineMedia.update { stateFor(media.value) }
        favorites.update { stateFor(media.value.filter { it.favorite == 1 }) }
    }

    override fun clearFavoriteOverride(mediaId: Long) {
        _favoriteOverrides.update { it - mediaId }
    }

    override val cloudSyncStates: StateFlow<Map<Long, SyncState>> = MutableStateFlow(emptyMap())

    override val metadataFlow: Flow<MediaMetadataState> = MutableStateFlow(MediaMetadataState())
    override val locationsMediaFlow: Flow<List<LocationMedia>> = MutableStateFlow(emptyList())
    override val geoMediaFlow: Flow<List<GeoMedia>> = MutableStateFlow(emptyList())

    override val vaultsMediaFlow: StateFlow<VaultState> = vaults
    override fun vaultMediaFlow(vault: Vault?): StateFlow<MediaState<UriMedia>> =
        MutableStateFlow(MediaState(isLoading = false))

    override val collectionsFlow: StateFlow<List<CollectionWithCount>> = collections
    override val collectionAlbumIdsFlow: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
    override fun collectionAlbumIdsInCollection(collectionId: Long): Flow<List<Long>> =
        flowOf(emptyList())
    override fun collectionMediaFlow(collectionId: Long): StateFlow<MediaState<UriMedia>> =
        MutableStateFlow(
            stateFor(
                when (collectionId) {
                    DocsGalleryData.COLLECTION_TRIP -> media.value.filter { it.id in DocsGalleryData.tripMediaIds }
                    DocsGalleryData.COLLECTION_ALL -> media.value
                    else -> emptyList()
                }
            )
        )

    override val imageEmbeddingsFlow: StateFlow<List<ImageEmbedding>> = MutableStateFlow(emptyList())

    override fun locationBasedMedia(
        gpsLocationNameCity: String,
        gpsLocationNameCountry: String,
        latitude: Double?,
        longitude: Double?,
        additionalMediaIds: Flow<Set<Long>>,
    ): Flow<MediaState<UriMedia>> = flowOf(stateFor(emptyList()))

    /* ------- in-memory mutations the docs scenes drive ------- */

    fun setPinned(albumId: Long, pinnedValue: Boolean) {
        pinned.update { list ->
            if (pinnedValue) list.filterNot { it.id == albumId } + PinnedAlbum(albumId)
            else list.filter { it.id != albumId }
        }
        republishAlbums()
    }

    fun ignore(albumId: Long, label: String) {
        ignored.update { it + IgnoredAlbum(id = albumId, label = label, albumIds = listOf(albumId), matchedAlbums = listOf(label)) }
        republishAlbums()
    }

    fun addVault(vault: Vault) {
        vaults.update { it.copy(vaults = it.vaults + vault, isLoading = false) }
    }

    private fun republishAlbums() {
        val list = albums.value.map { album ->
            album.copy(isPinned = pinned.value.any { it.id == album.id })
        }
        albumState.value = AlbumState(
            albums = list,
            albumsWithBlacklisted = list,
            albumsUnpinned = list.filter { !it.isPinned },
            albumsCloud = initialCloudAlbums,
            albumsPinned = list.filter { it.isPinned },
            collections = collections.value,
            isLoading = false,
        )
    }
}
