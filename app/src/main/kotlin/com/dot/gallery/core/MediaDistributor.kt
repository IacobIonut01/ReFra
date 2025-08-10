package com.dot.gallery.core

import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MediaDistributor {

    /**
     * Common
     */
    val hasPermission: MutableStateFlow<Boolean>
    val dateFormatsFlow: StateFlow<Triple<String, String, String>>
    var groupByMonth: Boolean

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

    /**
     * Media
     */
    val timelineMediaFlow: SharedFlow<MediaState<Media.UriMedia>>
    val albumsTimelinesMediaFlow: StateFlow<Map<Long, MediaState<Media.UriMedia>>>
    fun albumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>>
    val favoritesMediaFlow: SharedFlow<MediaState<Media.UriMedia>>
    val trashMediaFlow: SharedFlow<MediaState<Media.UriMedia>>

    /**
     * Media Metadata
     */
    val metadataFlow: StateFlow<MediaMetadataState>

    /**
     * Vault
     */
    val vaultsMediaFlow: StateFlow<VaultState>
    fun vaultMediaFlow(vault: Vault?): StateFlow<MediaState<Media.UriMedia>>

    /**
     * Search
     */
    val imageEmbeddingsFlow: StateFlow<List<ImageEmbedding>>


}