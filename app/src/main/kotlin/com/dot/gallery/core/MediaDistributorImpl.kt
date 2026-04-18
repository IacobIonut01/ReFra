package com.dot.gallery.core

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.Settings.Misc.DEFAULT_DATE_FORMAT
import com.dot.gallery.core.Settings.Misc.EXTENDED_DATE_FORMAT
import com.dot.gallery.core.Settings.Misc.WEEKLY_DATE_FORMAT
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.model.shouldIgnore
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.EventHandler
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.domain.util.mapPinned
import com.dot.gallery.feature_node.domain.util.removeBlacklisted
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.dot.gallery.feature_node.presentation.util.mediaFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

val LocalMediaDistributor = compositionLocalOf<MediaDistributor> {
    error("No MediaDistributor provided!!! This is likely due to a missing Hilt injection in the Composable hierarchy.")
}

@Singleton
class MediaDistributorImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val eventHandler: EventHandler,
    workManager: WorkManager
) : MediaDistributor {
    
    private val sharingMethod = SharingStarted.WhileSubscribed(5_000L)
    private val prioritySharingMethod = SharingStarted.Eagerly

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Album Media Sort preference flow
     */
    private val albumMediaSortFlow: StateFlow<Settings.Album.LastSort> = 
        Settings.Album.getAlbumMediaSortFlow(context)
            .stateIn(appScope, SharingStarted.Eagerly, Settings.Album.LastSort(OrderType.Descending, FilterKind.DATE))

    /**
     * Common
     */
    override val hasPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val dateFormatsFlow: StateFlow<Triple<String, String, String>> = combine(
        repository.getSetting(DEFAULT_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT),
        repository.getSetting(EXTENDED_DATE_FORMAT, Constants.EXTENDED_DATE_FORMAT),
        repository.getSetting(WEEKLY_DATE_FORMAT, Constants.WEEKLY_DATE_FORMAT)
    ) { defaultDateFormat, extendedDateFormat, weeklyDateFormat ->
        Triple(defaultDateFormat, extendedDateFormat, weeklyDateFormat)
    }.stateIn(
        scope = appScope,
        started = sharingMethod,
        initialValue = Triple(
            first = Constants.DEFAULT_DATE_FORMAT,
            second = Constants.EXTENDED_DATE_FORMAT,
            third = Constants.WEEKLY_DATE_FORMAT
        )
    )
    override var groupByMonth: Boolean
        get() = settingsFlow.value?.groupTimelineByMonth == true
        set(value) {
            appScope.launch {
                settingsFlow.value?.copy(groupTimelineByMonth = value)?.let {
                    repository.updateTimelineSettings(it)
                }
            }
        }

    override val groupSimilarMedia: StateFlow<Boolean> =
        repository.getSetting(Settings.Misc.GROUP_SIMILAR_MEDIA, true)
            .stateIn(appScope, sharingMethod, true)

    /**
     * Settings
     */
    override val settingsFlow: StateFlow<TimelineSettings?> = repository.getTimelineSettings()
        .stateIn(
            scope = appScope,
            started = sharingMethod,
            initialValue = TimelineSettings()
        )

    /**
     * Albums
     */
    override val blacklistedAlbumsFlow: StateFlow<List<IgnoredAlbum>> =
        repository.getBlacklistedAlbums()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptyList()
            )

    override val pinnedAlbumsFlow: StateFlow<List<PinnedAlbum>> =
        repository.getPinnedAlbums()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptyList()
            )

    private var albumOrder: MediaOrder
        get() = settingsFlow.value?.albumMediaOrder ?: MediaOrder.Date(OrderType.Descending)
        set(value) {
            appScope.launch {
                settingsFlow.value?.copy(albumMediaOrder = value)?.let {
                    repository.updateTimelineSettings(it)
                }
            }
        }

    private val albumThumbnails = repository.getAlbumThumbnails()
        .stateIn(
            scope = appScope,
            started = prioritySharingMethod,
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val albumsFlow: StateFlow<AlbumState> = hasPermission.flatMapLatest { granted ->
        if (!granted) flowOf(AlbumState())
        else combine(
            repository.getAlbums(mediaOrder = albumOrder),
            pinnedAlbumsFlow,
            blacklistedAlbumsFlow,
            settingsFlow,
            albumThumbnails
        ) { result, pinnedAlbums, blacklistedAlbums, settings, thumbnails ->
            val newOrder = settings?.albumMediaOrder ?: albumOrder
            val data = newOrder.sortAlbums(result.data ?: emptyList()).map { album ->
                val thumbnail = thumbnails.find { it.albumId == album.id }
                if (thumbnail == null) return@map album
                album.copy(uri = thumbnail.thumbnailUri)
            }
            val cleanData = data.removeBlacklisted(blacklistedAlbums).mapPinned(pinnedAlbums)

            AlbumState(
                albums = cleanData,
                albumsWithBlacklisted = data,
                albumsUnpinned = cleanData.filter { !it.isPinned },
                albumsPinned = cleanData.filter { it.isPinned }.sortedBy { it.label },
                isLoading = false,
                error = if (result is Resource.Error) result.message ?: "An error occurred" else ""
            )
        }
    }.stateIn(appScope, started = prioritySharingMethod, AlbumState())

    /**
     * Media
     */
    override val timelineMediaFlow: SharedFlow<MediaState<Media.UriMedia>> =
        mediaFlow(-1L, null, triggerDatabaseUpdate = true)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    override val albumsTimelinesMediaFlow: StateFlow<Map<Long, MediaState<Media.UriMedia>>> =
        hasPermission.flatMapLatest { granted ->
            if (!granted) flowOf(emptyMap())
            else combine(
                repository.mediaFlow(-1L, null),
                albumsFlow,
                settingsFlow,
                blacklistedAlbumsFlow,
                dateFormatsFlow,
                albumMediaSortFlow,
                groupSimilarMedia
            ) { values ->
                val allMediaResult = values[0] as Resource<List<Media.UriMedia>>
                val albumState = values[1] as AlbumState
                val settings = values[2] as TimelineSettings?
                val blacklistedAlbums = values[3] as List<IgnoredAlbum>
                val dateFormats = values[4] as Triple<String, String, String>
                val albumSort = values[5] as Settings.Album.LastSort
                val shouldGroupSimilar = values[6] as Boolean

                val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
                val allMedia = allMediaResult.data ?: emptyList()
                val albumIds = albumState.albums.mapTo(HashSet()) { it.id }

                val sorter = when (albumSort.kind) {
                    FilterKind.DATE -> MediaOrder.Date(albumSort.orderType)
                    FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(albumSort.orderType)
                    FilterKind.NAME -> MediaOrder.Label(albumSort.orderType)
                }

                val mediaByAlbum = allMedia.groupBy { it.albumID }
                val result = HashMap<Long, MediaState<Media.UriMedia>>(albumIds.size)
                for (albumId in albumIds) {
                    val albumMedia = mediaByAlbum[albumId] ?: continue
                    val filtered = albumMedia.toMutableList().apply {
                        removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media, albumId) } }
                    }
                    result[albumId] = mapMediaToItem(
                        data = sorter.sortMedia(filtered),
                        error = "",
                        albumId = albumId,
                        groupByMonth = settings?.groupTimelineByMonth == true,
                        groupSimilarMedia = shouldGroupSimilar,
                        defaultDateFormat = defaultDateFormat,
                        extendedDateFormat = extendedDateFormat,
                        weeklyDateFormat = weeklyDateFormat
                    )
                }
                result
            }
        }.stateIn(appScope, sharingMethod, emptyMap())

    override fun albumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>> =
        albumsTimelinesMediaFlow.map { it[albumId] ?: MediaState() }
            .stateIn(appScope, sharingMethod, MediaState())


    override val favoritesMediaFlow: SharedFlow<MediaState<Media.UriMedia>> =
        mediaFlow(-1L, Constants.Target.TARGET_FAVORITES)

    override val trashMediaFlow: SharedFlow<MediaState<Media.UriMedia>> =
        mediaFlow(-1L, Constants.Target.TARGET_TRASH)


    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun mediaFlow(albumId: Long, target: String?, triggerDatabaseUpdate: Boolean = false) = hasPermission.flatMapLatest { granted ->
        if (!granted) flowOf(MediaState(
            error = "No permission to access media",
            isLoading = false
        ))
        else combine(
            repository.mediaFlow(albumId, target),
            settingsFlow,
            blacklistedAlbumsFlow,
            dateFormatsFlow,
            albumMediaSortFlow,
            groupSimilarMedia
        ) { values ->
            val result = values[0] as Resource<List<Media.UriMedia>>
            val settings = values[1] as TimelineSettings?
            val blacklistedAlbums = values[2] as List<IgnoredAlbum>
            val dateFormats = values[3] as Triple<String, String, String>
            val albumSort = values[4] as Settings.Album.LastSort
            val shouldGroupSimilar = values[5] as Boolean
            
            val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
            
            if (result is Resource.Error) return@combine MediaState(
                error = result.message ?: "",
                isLoading = false
            )
            // Use custom sort for album timelines, default sort for favorites/trash
            val sorter = if (target == null && albumId > 0) {
                when (albumSort.kind) {
                    FilterKind.DATE -> MediaOrder.Date(albumSort.orderType)
                    FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(albumSort.orderType)
                    FilterKind.NAME -> MediaOrder.Label(albumSort.orderType)
                }
            } else {
                MediaOrder.Default
            }
            val data = (result.data ?: emptyList()).toMutableList().apply {
                removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media, albumId) } }
            }
            mapMediaToItem(
                data = sorter.sortMedia(data),
                error = result.message ?: "",
                albumId = albumId,
                groupByMonth = settings?.groupTimelineByMonth == true,
                groupSimilarMedia = shouldGroupSimilar,
                defaultDateFormat = defaultDateFormat,
                extendedDateFormat = extendedDateFormat,
                weeklyDateFormat = weeklyDateFormat
            )
        }
    }.mapLatest {
        if (triggerDatabaseUpdate) {
            eventHandler.pushEvent(UIEvent.UpdateDatabase)
        }
        it
    }.shareIn(
        scope = appScope,
        started = sharingMethod,
        replay = 1
    )

    /**
     * Media Metadata
     */
    override val metadataFlow: Flow<MediaMetadataState> = combine(
        repository.getMetadata(),
        workManager.getWorkInfosForUniqueWorkFlow("MetadataCollection")
            .map { it.lastOrNull()?.state == WorkInfo.State.RUNNING },
        workManager.getWorkInfosForUniqueWorkFlow("MetadataCollection")
            .map { it.lastOrNull()?.progress?.getInt("progress", 0) ?: 0 }
    ) { metadata, isRunning, progress ->
        MediaMetadataState(
            metadata = metadata,
            isLoading = isRunning,
            isLoadingProgress = progress
        )
    }

    override fun locationBasedMedia(
        gpsLocationNameCity: String,
        gpsLocationNameCountry: String
    ): Flow<MediaState<Media.UriMedia>> = combine(
        repository.getMetadata(),
        repository.getCompleteMedia()
    ) { metadata, media ->
        val matchingMediaIds = metadata
            .filter {
                it.gpsLocationNameCity == gpsLocationNameCity &&
                        it.gpsLocationNameCountry == gpsLocationNameCountry
            }
            .mapTo(HashSet()) { it.mediaId }
        val filteredMedia = media.data.orEmpty().filter {
            it.id in matchingMediaIds
        }
        return@combine mapMediaToItem(
            data = filteredMedia,
            error = media.message ?: "",
            albumId = -1L,
            defaultDateFormat = dateFormatsFlow.value.first,
            extendedDateFormat = dateFormatsFlow.value.second,
            weeklyDateFormat = dateFormatsFlow.value.third
        )
    }

    override val locationsMediaFlow: Flow<List<LocationMedia>> = combine(
        repository.getMetadata(),
        timelineMediaFlow
    ) { metadata, timelineState ->
        val mediaById = HashMap<Long, Media.UriMedia>(timelineState.media.size)
        for (m in timelineState.media) { mediaById[m.id] = m }
        metadata
            .filter { it.gpsLocationNameCity != null && it.gpsLocationNameCountry != null }
            .groupBy { "${it.gpsLocationNameCity}, ${it.gpsLocationNameCountry}" }
            .mapNotNull { (location, items) ->
                items.mapNotNull { mediaById[it.mediaId] }
                    .maxByOrNull { it.definedTimestamp }
                    ?.let { media -> LocationMedia(media = media, location = location) }
            }
            .sortedBy { it.location }
    }

    override val geoMediaFlow: Flow<List<GeoMedia>> = combine(
        repository.getMetadata(),
        timelineMediaFlow
    ) { metadata, timelineState ->
        val mediaById = HashMap<Long, Media.UriMedia>(timelineState.media.size)
        for (m in timelineState.media) { mediaById[m.id] = m }
        metadata
            .filter { it.gpsLatitude != null && it.gpsLongitude != null }
            .mapNotNull { meta ->
                mediaById[meta.mediaId]?.let { media ->
                    GeoMedia(
                        mediaId = meta.mediaId,
                        latitude = meta.gpsLatitude!!,
                        longitude = meta.gpsLongitude!!,
                        locationCity = meta.gpsLocationNameCity,
                        locationCountry = meta.gpsLocationNameCountry,
                        media = media
                    )
                }
            }
    }

    /**
     * Vault
     */
    override val vaultsMediaFlow: StateFlow<VaultState> = repository.getVaults()
        .map { VaultState(it.data ?: emptyList(), isLoading = false) }
        .stateIn(appScope, started = sharingMethod, VaultState())

    override fun vaultMediaFlow(vault: Vault?): StateFlow<MediaState<Media.UriMedia>> = combine(
        repository.getEncryptedMedia(vault),
        settingsFlow,
        dateFormatsFlow
    ) { result, settings, (defaultDateFormat, extendedDateFormat, weeklyDateFormat) ->
        mapMediaToItem(
            data = result.data ?: emptyList(),
            error = result.message ?: "",
            albumId = -1L,
            groupByMonth = settings?.groupTimelineByMonth == true,
            defaultDateFormat = defaultDateFormat,
            extendedDateFormat = extendedDateFormat,
            weeklyDateFormat = weeklyDateFormat
        )
    }.stateIn(appScope, sharingMethod, MediaState())

    /**
     * Search
     */
    override val imageEmbeddingsFlow: StateFlow<List<ImageEmbedding>> =
        repository.getImageEmbeddings()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptyList()
            )


}