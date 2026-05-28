package com.dot.gallery.core

import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.CollectionWithCount
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.util.MediaGroupType
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.MergedSubfolderAlbum
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MediaDistributor {

    /**
     * Pull-to-refresh
     */
    val isRefreshing: StateFlow<Boolean>
    suspend fun invalidate()

    /**
     * Common
     */
    val hasPermission: MutableStateFlow<Boolean>
    val dateFormatsFlow: StateFlow<Triple<String, String, String>>
    var groupByMonth: Boolean
    val groupSimilarMedia: StateFlow<Boolean>
    val enabledGroupTypes: StateFlow<Set<MediaGroupType>>
    val mergeAlbumsByName: StateFlow<Boolean>

    /**
     * Settings
     */
    val settingsFlow: StateFlow<TimelineSettings?>

    /**
     * Albums
     */
    val albumsFlow: StateFlow<AlbumState>
    val blacklistedAlbumsFlow: StateFlow<List<IgnoredAlbum>>
    val pinnedAlbumsFlow: StateFlow<List<PinnedAlbum>>
    val lockedAlbumsFlow: StateFlow<List<LockedAlbum>>
    val mergedSubfolderAlbumsFlow: StateFlow<List<MergedSubfolderAlbum>>

    /**
     * Media
     */
    val timelineMediaFlow: SharedFlow<MediaState<Media.UriMedia>>
    fun albumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>>
    val favoritesMediaFlow: SharedFlow<MediaState<Media.UriMedia>>
    val trashMediaFlow: SharedFlow<MediaState<Media.UriMedia>>

    /**
     * Media Metadata
     */
    val metadataFlow: Flow<MediaMetadataState>
    val locationsMediaFlow: Flow<List<LocationMedia>>
    val geoMediaFlow: Flow<List<GeoMedia>>

    /**
     * Vault
     */
    val vaultsMediaFlow: StateFlow<VaultState>
    fun vaultMediaFlow(vault: Vault?): StateFlow<MediaState<Media.UriMedia>>

    /**
     * Collections
     */
    val collectionsFlow: StateFlow<List<CollectionWithCount>>
    val collectionAlbumIdsFlow: StateFlow<Set<Long>>
    fun collectionAlbumIdsInCollection(collectionId: Long): Flow<List<Long>>
    fun collectionMediaFlow(collectionId: Long): StateFlow<MediaState<Media.UriMedia>>

    /**
     * Search
     */
    val imageEmbeddingsFlow: StateFlow<List<ImageEmbedding>>


    fun locationBasedMedia(
        gpsLocationNameCity: String,
        gpsLocationNameCountry: String
    ): Flow<MediaState<Media.UriMedia>>
}