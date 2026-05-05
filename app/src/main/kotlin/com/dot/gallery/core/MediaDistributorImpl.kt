package com.dot.gallery.core

import android.content.Context
import android.media.MediaScannerConnection
import androidx.compose.runtime.compositionLocalOf
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.Settings.Misc.DEFAULT_DATE_FORMAT
import com.dot.gallery.core.Settings.Misc.EXTENDED_DATE_FORMAT
import com.dot.gallery.core.Settings.Misc.WEEKLY_DATE_FORMAT
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumGroup
import com.dot.gallery.feature_node.domain.model.AlbumGroupMember
import com.dot.gallery.feature_node.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.AlbumThumbnail
import com.dot.gallery.feature_node.domain.model.CollectionWithCount
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.MergedSubfolderAlbum
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
import com.dot.gallery.feature_node.domain.util.MediaGroupType
import com.dot.gallery.feature_node.domain.util.mapLocked
import com.dot.gallery.feature_node.domain.util.mapPinned
import com.dot.gallery.feature_node.domain.util.removeBlacklisted
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.dot.gallery.feature_node.presentation.util.mediaFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
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
     * Tracks media IDs that have already been submitted for a MediaStore rescan
     * to avoid redundant scanning of the same files.
     */
    private val rescanRequestedIds = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Pull-to-refresh
     */
    override val isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun invalidate() {
        isRefreshing.value = true
        withContext(Dispatchers.IO) {
            context.contentResolver.notifyChange(
                MediaStore.Files.getContentUri("external"), null
            )
        }
        delay(1500)
        isRefreshing.value = false
    }

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

    override val enabledGroupTypes: StateFlow<Set<MediaGroupType>> = combine(
        repository.getSetting(Settings.Misc.GROUP_RAW_JPG, true),
        repository.getSetting(Settings.Misc.GROUP_EDITED_COPIES, true),
        repository.getSetting(Settings.Misc.GROUP_BURST_SEQUENCES, true)
    ) { rawJpg, editedCopies, burstSequences ->
        buildSet {
            if (rawJpg) add(MediaGroupType.RAW_JPG)
            if (editedCopies) add(MediaGroupType.EDITS)
            if (burstSequences) add(MediaGroupType.BURST)
        }
    }.stateIn(appScope, sharingMethod, MediaGroupType.entries.toSet())

    override val mergeAlbumsByName: StateFlow<Boolean> =
        repository.getSetting(Settings.Album.MERGE_ALBUMS_BY_NAME, true)
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

    override val lockedAlbumsFlow: StateFlow<List<LockedAlbum>> =
        repository.getLockedAlbums()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptyList()
            )

    override val mergedSubfolderAlbumsFlow: StateFlow<List<MergedSubfolderAlbum>> =
        repository.getMergedSubfolderAlbums()
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

    private val albumGroupsFlow: StateFlow<List<AlbumGroup>> =
        repository.getAllAlbumGroups()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptyList()
            )

    private val albumGroupMembersFlow: StateFlow<List<AlbumGroupMember>> =
        repository.getAllGroupMembers()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptyList()
            )

    /**
     * Collections
     */
    override val collectionsFlow: StateFlow<List<CollectionWithCount>> =
        repository.getCollectionsWithCount()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptyList()
            )

    override val collectionAlbumIdsFlow: StateFlow<Set<Long>> =
        repository.getAllAlbumIdsInCollections()
            .map { it.toSet() }
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptySet()
            )

    override fun collectionAlbumIdsInCollection(collectionId: Long): Flow<List<Long>> =
        repository.getAlbumIdsInCollection(collectionId)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val albumsFlow: StateFlow<AlbumState> = hasPermission.flatMapLatest { granted ->
        if (!granted) flowOf(AlbumState())
        else combine(
            repository.getAlbums(mediaOrder = albumOrder),
            pinnedAlbumsFlow,
            blacklistedAlbumsFlow,
            lockedAlbumsFlow,
            settingsFlow,
            albumThumbnails,
            albumGroupsFlow,
            albumGroupMembersFlow,
            mergeAlbumsByName,
            mergedSubfolderAlbumsFlow,
            collectionsFlow,
            collectionAlbumIdsFlow,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val result = values[0] as Resource<List<Album>>
            @Suppress("UNCHECKED_CAST")
            val pinnedAlbums = values[1] as List<PinnedAlbum>
            @Suppress("UNCHECKED_CAST")
            val blacklistedAlbums = values[2] as List<IgnoredAlbum>
            @Suppress("UNCHECKED_CAST")
            val lockedAlbums = values[3] as List<LockedAlbum>
            val settings = values[4] as TimelineSettings?
            @Suppress("UNCHECKED_CAST")
            val thumbnails = values[5] as List<AlbumThumbnail>
            @Suppress("UNCHECKED_CAST")
            val groups = values[6] as List<AlbumGroup>
            @Suppress("UNCHECKED_CAST")
            val groupMembers = values[7] as List<AlbumGroupMember>
            val shouldMerge = values[8] as Boolean
            @Suppress("UNCHECKED_CAST")
            val mergedSubfolders = values[9] as List<MergedSubfolderAlbum>
            @Suppress("UNCHECKED_CAST")
            val collections = values[10] as List<CollectionWithCount>
            @Suppress("UNCHECKED_CAST")
            val collectionAlbumIds = values[11] as Set<Long>
            val newOrder = settings?.albumMediaOrder ?: albumOrder
            val data = newOrder.sortAlbums(result.data ?: emptyList()).map { album ->
                val thumbnail = thumbnails.find { it.albumId == album.id }
                if (thumbnail == null) return@map album
                album.copy(uri = thumbnail.thumbnailUri)
            }
            val cleanData = data.removeBlacklisted(blacklistedAlbums)
                .mapPinned(pinnedAlbums)
                .mapLocked(lockedAlbums)

            val subfolderMergedData = mergeSubfolderAlbums(
                cleanData,
                mergedSubfolders.mapTo(HashSet()) { it.id }
            )
            val mergedData = if (shouldMerge) mergeAlbumsByLabel(subfolderMergedData) else subfolderMergedData

            val groupMemberAlbumIds = groupMembers.map { it.albumId }.toSet()
            val albumGroups = groups.map { group ->
                val memberAlbumIds = groupMembers
                    .filter { it.groupId == group.id }
                    .map { it.albumId }
                    .toSet()
                AlbumGroupWithAlbums(
                    group = group,
                    albums = mergedData.filter { album ->
                        if (album.isMerged) album.mergedAlbumIds.any { it in memberAlbumIds }
                        else album.id in memberAlbumIds
                    }
                )
            }
            val groupedMergedIds = mergedData
                .filter { album ->
                    if (album.isMerged) album.mergedAlbumIds.any { it in groupMemberAlbumIds }
                    else album.id in groupMemberAlbumIds
                }
                .mapTo(HashSet()) { it.id }

            AlbumState(
                albums = mergedData,
                albumsWithBlacklisted = data,
                albumsUnpinned = mergedData.filter { album ->
                    !album.isPinned && album.id !in groupedMergedIds &&
                        (if (album.isMerged) album.mergedAlbumIds.none { it in collectionAlbumIds }
                         else album.id !in collectionAlbumIds)
                },
                albumsPinned = mergedData.filter { album ->
                    album.isPinned &&
                        (if (album.isMerged) album.mergedAlbumIds.none { it in collectionAlbumIds }
                         else album.id !in collectionAlbumIds)
                }.sortedBy { it.label },
                albumGroups = albumGroups,
                collections = collections,
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
                groupSimilarMedia,
                enabledGroupTypes
            ) { values ->
                val allMediaResult = values[0] as Resource<List<Media.UriMedia>>
                val albumState = values[1] as AlbumState
                val settings = values[2] as TimelineSettings?
                @Suppress("UNCHECKED_CAST")
                val blacklistedAlbums = values[3] as List<IgnoredAlbum>
                @Suppress("UNCHECKED_CAST")
                val dateFormats = values[4] as Triple<String, String, String>
                val albumSort = values[5] as Settings.Album.LastSort
                val shouldGroupSimilar = values[6] as Boolean
                @Suppress("UNCHECKED_CAST")
                val groupTypes = values[7] as Set<MediaGroupType>

                val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
                val allMedia = allMediaResult.data ?: emptyList()
                val albumIds = albumState.albums.mapTo(HashSet()) { it.id }

                val sorter = when (albumSort.kind) {
                    FilterKind.DATE -> MediaOrder.Date(albumSort.orderType)
                    FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(albumSort.orderType)
                    FilterKind.NAME -> MediaOrder.Label(albumSort.orderType)
                }

                val mediaByAlbum = allMedia.groupBy { it.albumID }
                val albumById = albumState.albums.associateBy { it.id }
                val result = HashMap<Long, MediaState<Media.UriMedia>>(albumIds.size)
                for (albumId in albumIds) {
                    val album = albumById[albumId]
                    val albumMedia = if (album != null && album.isMerged) {
                        album.mergedAlbumIds.flatMap { mediaByAlbum[it] ?: emptyList() }
                    } else {
                        mediaByAlbum[albumId] ?: continue
                    }
                    val filtered = albumMedia.toMutableList().apply {
                        removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media, albumId) } }
                    }
                    result[albumId] = mapMediaToItem(
                        data = sorter.sortMedia(filtered),
                        error = "",
                        albumId = albumId,
                        groupByMonth = settings?.groupTimelineByMonth == true,
                        groupSimilarMedia = shouldGroupSimilar,
                        enabledGroupTypes = groupTypes,
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
            lockedAlbumsFlow,
            dateFormatsFlow,
            albumMediaSortFlow,
            groupSimilarMedia,
            enabledGroupTypes
        ) { values ->
            val result = values[0] as Resource<List<Media.UriMedia>>
            val settings = values[1] as TimelineSettings?
            @Suppress("UNCHECKED_CAST")
            val blacklistedAlbums = values[2] as List<IgnoredAlbum>
            @Suppress("UNCHECKED_CAST")
            val lockedAlbums = values[3] as List<LockedAlbum>
            @Suppress("UNCHECKED_CAST")
            val dateFormats = values[4] as Triple<String, String, String>
            val albumSort = values[5] as Settings.Album.LastSort
            val shouldGroupSimilar = values[6] as Boolean
            @Suppress("UNCHECKED_CAST")
            val groupTypes = values[7] as Set<MediaGroupType>
            
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
            val lockedAlbumIds = lockedAlbums.mapTo(HashSet()) { it.id }
            val data = (result.data ?: emptyList()).toMutableList().apply {
                removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media, albumId) } }
                // Hide media from locked albums in the main timeline
                if (albumId == -1L && target == null) {
                    removeAll { media -> media.albumID in lockedAlbumIds }
                }
            }
            mapMediaToItem(
                data = sorter.sortMedia(data),
                error = result.message ?: "",
                albumId = albumId,
                groupByMonth = settings?.groupTimelineByMonth == true,
                groupSimilarMedia = shouldGroupSimilar,
                enabledGroupTypes = groupTypes,
                defaultDateFormat = defaultDateFormat,
                extendedDateFormat = extendedDateFormat,
                weeklyDateFormat = weeklyDateFormat
            )
        }
    }.mapLatest {
        if (triggerDatabaseUpdate) {
            eventHandler.pushEvent(UIEvent.UpdateDatabase)
        }
        triggerRescanForMissingDateTaken(it.media)
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
     * Collections
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun collectionMediaFlow(collectionId: Long): StateFlow<MediaState<Media.UriMedia>> =
        combine(
            repository.getMediaIdsInCollection(collectionId),
            repository.getCompleteMedia(),
            settingsFlow,
            dateFormatsFlow,
            albumMediaSortFlow,
            groupSimilarMedia,
            enabledGroupTypes
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val mediaIds = values[0] as List<Long>
            val allMediaResult = values[1] as Resource<List<Media.UriMedia>>
            val settings = values[2] as TimelineSettings?
            @Suppress("UNCHECKED_CAST")
            val dateFormats = values[3] as Triple<String, String, String>
            val albumSort = values[4] as Settings.Album.LastSort
            val shouldGroupSimilar = values[5] as Boolean
            @Suppress("UNCHECKED_CAST")
            val groupTypes = values[6] as Set<MediaGroupType>

            val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
            val allMedia = allMediaResult.data ?: emptyList()
            val mediaIdSet = mediaIds.toHashSet()
            val collectionMedia = allMedia.filter { it.id in mediaIdSet }

            val sorter = when (albumSort.kind) {
                FilterKind.DATE -> MediaOrder.Date(albumSort.orderType)
                FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(albumSort.orderType)
                FilterKind.NAME -> MediaOrder.Label(albumSort.orderType)
            }

            mapMediaToItem(
                data = sorter.sortMedia(collectionMedia),
                error = allMediaResult.message ?: "",
                albumId = collectionId,
                groupByMonth = settings?.groupTimelineByMonth == true,
                groupSimilarMedia = shouldGroupSimilar,
                enabledGroupTypes = groupTypes,
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

    /**
     * Triggers a MediaStore rescan for media items that have null DATE_TAKEN.
     * When files are transferred between devices, MediaStore may not have
     * processed their EXIF data yet, so DATE_TAKEN is null and the app falls
     * back to DATE_MODIFIED (the transfer time). Rescanning forces MediaStore
     * to read EXIF immediately, populating DATE_TAKEN and triggering a
     * ContentResolver change notification that refreshes the timeline.
     */
    private fun triggerRescanForMissingDateTaken(media: List<Media.UriMedia>) {
        val toScan = media.filter { it.takenTimestamp == null && rescanRequestedIds.add(it.id) }
        if (toScan.isEmpty()) return
        val paths = toScan.mapNotNull { it.path.takeIf { p -> p.isNotBlank() } }.toTypedArray()
        val mimeTypes = toScan.map { it.mimeType }.toTypedArray()
        if (paths.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, paths, mimeTypes, null)
        }
    }

    private fun mergeSubfolderAlbums(
        albums: List<Album>,
        mergedSubfolderIds: Set<Long>
    ): List<Album> {
        if (mergedSubfolderIds.isEmpty()) return albums
        val parentAlbums = albums.filter { it.id in mergedSubfolderIds }
        if (parentAlbums.isEmpty()) return albums

        val absorbedIds = HashSet<Long>()
        val result = mutableListOf<Album>()

        for (parent in parentAlbums) {
            val parentPath = parent.relativePath.removeSuffix("/") + "/"
            val children = albums.filter { album ->
                album.id != parent.id &&
                    album.id !in absorbedIds &&
                    album.relativePath.startsWith(parentPath)
            }
            if (children.isEmpty()) {
                continue
            }
            val allRelated = listOf(parent) + children
            val mergedIds = allRelated.map { it.id }
            children.forEach { absorbedIds.add(it.id) }
            result.add(
                parent.copy(
                    count = allRelated.sumOf { it.count },
                    size = allRelated.sumOf { it.size },
                    timestamp = allRelated.maxOf { it.timestamp },
                    isPinned = allRelated.any { it.isPinned },
                    isLocked = allRelated.any { it.isLocked },
                    mergedAlbumIds = mergedIds
                )
            )
        }

        for (album in albums) {
            if (album.id !in absorbedIds && album.id !in mergedSubfolderIds) {
                result.add(album)
            } else if (album.id in mergedSubfolderIds && result.none { it.id == album.id }) {
                result.add(album)
            }
        }

        return result
    }

    private fun mergeAlbumsByLabel(albums: List<Album>): List<Album> {
        val grouped = albums.groupBy { it.label }
        return grouped.flatMap { (_, sameNameAlbums) ->
            if (sameNameAlbums.size <= 1) {
                sameNameAlbums
            } else {
                val primary = sameNameAlbums.maxBy { it.timestamp }
                val mergedIds = sameNameAlbums.map { it.id }
                listOf(
                    primary.copy(
                        count = sameNameAlbums.sumOf { it.count },
                        size = sameNameAlbums.sumOf { it.size },
                        timestamp = sameNameAlbums.maxOf { it.timestamp },
                        isPinned = sameNameAlbums.any { it.isPinned },
                        isLocked = sameNameAlbums.any { it.isLocked },
                        mergedAlbumIds = mergedIds
                    )
                )
            }
        }
    }

}