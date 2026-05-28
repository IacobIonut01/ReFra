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
import com.dot.gallery.feature_node.domain.model.ScannedMedia
import com.dot.gallery.feature_node.domain.model.shouldIgnore
import com.dot.gallery.feature_node.data.data_source.ScannedMediaDao
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
import com.dot.gallery.core.metrics.StartupTracer
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
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
    workManager: WorkManager,
    private val scannedMediaDao: ScannedMediaDao
) : MediaDistributor {
    
    private val sharingMethod = SharingStarted.WhileSubscribed(5_000L)
    private val prioritySharingMethod = SharingStarted.Eagerly

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Tracks media IDs that have already been submitted for a MediaStore rescan
     * to avoid redundant scanning of the same files.
     * Persisted to the Room database so entries are cleaned when media is deleted.
     * Loaded asynchronously to avoid blocking the main thread during startup.
     */
    private val rescanRequestedIds = ConcurrentHashMap.newKeySet<Long>()

    init {
        appScope.launch {
            rescanRequestedIds.addAll(scannedMediaDao.getScannedIds())
            scannedMediaDao.removeStaleEntries()
        }
    }

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
        started = prioritySharingMethod,
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
            .stateIn(appScope, prioritySharingMethod, true)

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
    }.stateIn(appScope, prioritySharingMethod, MediaGroupType.entries.toSet())

    override val mergeAlbumsByName: StateFlow<Boolean> =
        repository.getSetting(Settings.Album.MERGE_ALBUMS_BY_NAME, true)
            .stateIn(appScope, prioritySharingMethod, true)

    /**
     * Settings
     */
    override val settingsFlow: StateFlow<TimelineSettings?> = repository.getTimelineSettings()
        .stateIn(
            scope = appScope,
            started = prioritySharingMethod,
            initialValue = TimelineSettings()
        )

    /**
     * Albums
     */
    override val blacklistedAlbumsFlow: StateFlow<List<IgnoredAlbum>> =
        repository.getBlacklistedAlbums()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptyList()
            )

    override val pinnedAlbumsFlow: StateFlow<List<PinnedAlbum>> =
        repository.getPinnedAlbums()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptyList()
            )

    override val lockedAlbumsFlow: StateFlow<List<LockedAlbum>> =
        repository.getLockedAlbums()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptyList()
            )

    override val mergedSubfolderAlbumsFlow: StateFlow<List<MergedSubfolderAlbum>> =
        repository.getMergedSubfolderAlbums()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
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
                started = prioritySharingMethod,
                initialValue = emptyList()
            )

    private val albumGroupMembersFlow: StateFlow<List<AlbumGroupMember>> =
        repository.getAllGroupMembers()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptyList()
            )

    /**
     * Collections
     */
    override val collectionsFlow: StateFlow<List<CollectionWithCount>> =
        repository.getCollectionsWithCount()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptyList()
            )

    override val collectionAlbumIdsFlow: StateFlow<Set<Long>> =
        repository.getAllAlbumIdsInCollections()
            .map { it.toSet() }
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptySet()
            )

    override fun collectionAlbumIdsInCollection(collectionId: Long): Flow<List<Long>> =
        repository.getAlbumIdsInCollection(collectionId)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val albumsFlow: StateFlow<AlbumState> = hasPermission.flatMapLatest { granted ->
        if (!granted) flowOf(AlbumState())
        else {
            StartupTracer.begin("albums.permission_granted→combine_setup").also { s -> StartupTracer.end(s) }
            combine(
            repository.getAlbums(mediaOrder = albumOrder)
                .onEach { StartupTracer.begin("albums.dep.getAlbums(${it.data?.size ?: 0})").also { s -> StartupTracer.end(s) } },
            pinnedAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.pinned(${it.size})").also { s -> StartupTracer.end(s) } },
            blacklistedAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.blacklisted(${it.size})").also { s -> StartupTracer.end(s) } },
            lockedAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.locked(${it.size})").also { s -> StartupTracer.end(s) } },
            settingsFlow
                .onEach { StartupTracer.begin("albums.dep.settings").also { s -> StartupTracer.end(s) } },
            albumThumbnails
                .onEach { StartupTracer.begin("albums.dep.thumbnails(${it.size})").also { s -> StartupTracer.end(s) } },
            albumGroupsFlow
                .onEach { StartupTracer.begin("albums.dep.groups(${it.size})").also { s -> StartupTracer.end(s) } },
            albumGroupMembersFlow
                .onEach { StartupTracer.begin("albums.dep.groupMembers(${it.size})").also { s -> StartupTracer.end(s) } },
            mergeAlbumsByName
                .onEach { StartupTracer.begin("albums.dep.mergeByName=$it").also { s -> StartupTracer.end(s) } },
            mergedSubfolderAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.mergedSubfolders(${it.size})").also { s -> StartupTracer.end(s) } },
            collectionsFlow
                .onEach { StartupTracer.begin("albums.dep.collections(${it.size})").also { s -> StartupTracer.end(s) } },
            collectionAlbumIdsFlow
                .onEach { StartupTracer.begin("albums.dep.collectionAlbumIds(${it.size})").also { s -> StartupTracer.end(s) } },
        ) { values ->
            val combineSpan = StartupTracer.begin("albums.combine_body(${(values[0] as Resource<*>).data?.let { (it as? List<*>)?.size } ?: 0} albums)")
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
            val thumbnailMap = thumbnails.associateBy { it.albumId }
            val data = newOrder.sortAlbums(result.data ?: emptyList()).map { album ->
                val thumbnail = thumbnailMap[album.id] ?: return@map album
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

            val groupMemberAlbumIds = groupMembers.mapTo(HashSet(groupMembers.size)) { it.albumId }
            val membersByGroupId = groupMembers.groupBy { it.groupId }
            val albumGroups = groups.map { group ->
                val memberAlbumIds = membersByGroupId[group.id]
                    ?.mapTo(HashSet()) { it.albumId }
                    ?: emptySet()
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
            ).also { StartupTracer.end(combineSpan) }
        }
        }
    }.stateIn(appScope, started = prioritySharingMethod, AlbumState())

    /**
     * Media
     */
    override val timelineMediaFlow: SharedFlow<MediaState<Media.UriMedia>> =
        mediaFlow(-1L, null, triggerDatabaseUpdate = true)

    private val albumTimelineCache = ConcurrentHashMap<Long, StateFlow<MediaState<Media.UriMedia>>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun albumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>> =
        albumTimelineCache.getOrPut(albumId) {
            hasPermission.flatMapLatest { granted ->
                if (!granted) flowOf(MediaState<Media.UriMedia>())
                else {
                    // Build a reactive media flow for this album.
                    // For merged albums, we need the mergedAlbumIds which come from albumsFlow.
                    // Use flatMapLatest on albumsFlow so that if the album becomes merged/unmerged,
                    // we switch to the appropriate media source.
                    albumsFlow.map { state -> state.albums.find { it.id == albumId } }
                        .distinctUntilChanged()
                        .flatMapLatest { album ->
                            val mediaSource: Flow<Resource<List<Media.UriMedia>>> =
                                if (album != null && album.isMerged && album.mergedAlbumIds.size > 1) {
                                    // Combine media from all sub-albums reactively
                                    val subFlows = album.mergedAlbumIds.map { subId ->
                                        repository.mediaFlow(subId, null)
                                    }
                                    combine(subFlows) { results ->
                                        val merged = results.flatMap { it.data ?: emptyList() }
                                        Resource.Success(merged) as Resource<List<Media.UriMedia>>
                                    }
                                } else {
                                    repository.mediaFlow(albumId, null)
                                }
                            combine(
                                mediaSource,
                                settingsFlow,
                                blacklistedAlbumsFlow,
                                dateFormatsFlow,
                                albumMediaSortFlow,
                                groupSimilarMedia,
                                enabledGroupTypes
                            ) { values ->
                                val mediaResult = values[0] as Resource<List<Media.UriMedia>>
                                val settings = values[1] as TimelineSettings?
                                @Suppress("UNCHECKED_CAST")
                                val blacklistedAlbums = values[2] as List<IgnoredAlbum>
                                @Suppress("UNCHECKED_CAST")
                                val dateFormats = values[3] as Triple<String, String, String>
                                val albumSort = values[4] as Settings.Album.LastSort
                                val shouldGroupSimilar = values[5] as Boolean
                                @Suppress("UNCHECKED_CAST")
                                val groupTypes = values[6] as Set<MediaGroupType>

                                val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats

                                val sorter = when (albumSort.kind) {
                                    FilterKind.DATE -> MediaOrder.Date(albumSort.orderType)
                                    FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(albumSort.orderType)
                                    FilterKind.NAME -> MediaOrder.Label(albumSort.orderType)
                                }

                                val filtered = (mediaResult.data ?: emptyList()).toMutableList().apply {
                                    removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media, albumId) } }
                                }
                                mapMediaToItem(
                                    data = sorter.sortMedia(filtered),
                                    error = if (mediaResult is Resource.Error) mediaResult.message ?: "" else "",
                                    albumId = albumId,
                                    groupByMonth = settings?.groupTimelineByMonth == true,
                                    groupSimilarMedia = shouldGroupSimilar,
                                    enabledGroupTypes = groupTypes,
                                    defaultDateFormat = defaultDateFormat,
                                    extendedDateFormat = extendedDateFormat,
                                    weeklyDateFormat = weeklyDateFormat
                                )
                            }
                        }
                }
            }.stateIn(appScope, sharingMethod, MediaState())
        }


    override val favoritesMediaFlow: SharedFlow<MediaState<Media.UriMedia>> =
        mediaFlow(-1L, Constants.Target.TARGET_FAVORITES)

    override val trashMediaFlow: SharedFlow<MediaState<Media.UriMedia>> =
        mediaFlow(-1L, Constants.Target.TARGET_TRASH)


    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun mediaFlow(albumId: Long, target: String?, triggerDatabaseUpdate: Boolean = false): SharedFlow<MediaState<Media.UriMedia>> {
        val tag = when {
            target == Constants.Target.TARGET_FAVORITES -> "favorites"
            target == Constants.Target.TARGET_TRASH -> "trash"
            albumId > 0 -> "album($albumId)"
            else -> "timeline"
        }
        var combineEmissionCount = 0
        return hasPermission.flatMapLatest { granted ->
        if (!granted) flowOf(MediaState())
        else {
            StartupTracer.begin("$tag.permission_granted→combine_setup")
            combineEmissionCount = 0
            combine(
            repository.mediaFlow(albumId, target)
                .onEach { StartupTracer.begin("$tag.mediaStore_first_emit(${it.data?.size ?: 0} items)").also { s -> StartupTracer.end(s) } },
            settingsFlow
                .onEach { StartupTracer.begin("$tag.dep.settingsFlow").also { s -> StartupTracer.end(s) } },
            blacklistedAlbumsFlow
                .onEach { StartupTracer.begin("$tag.dep.blacklistedAlbums(${it.size})").also { s -> StartupTracer.end(s) } },
            lockedAlbumsFlow
                .onEach { StartupTracer.begin("$tag.dep.lockedAlbums(${it.size})").also { s -> StartupTracer.end(s) } },
            dateFormatsFlow
                .onEach { StartupTracer.begin("$tag.dep.dateFormats").also { s -> StartupTracer.end(s) } },
            albumMediaSortFlow
                .onEach { StartupTracer.begin("$tag.dep.albumMediaSort").also { s -> StartupTracer.end(s) } },
            groupSimilarMedia
                .onEach { StartupTracer.begin("$tag.dep.groupSimilar=$it").also { s -> StartupTracer.end(s) } },
            enabledGroupTypes
                .onEach { StartupTracer.begin("$tag.dep.enabledGroupTypes(${it.size})").also { s -> StartupTracer.end(s) } }
        ) { values ->
            combineEmissionCount++
            val combineSpan = StartupTracer.begin("$tag.combine_body(#$combineEmissionCount)")
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
            
            if (result is Resource.Error) {
                StartupTracer.end(combineSpan)
                return@combine MediaState(
                    error = result.message ?: "",
                    isLoading = false
                )
            }
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
            val mapSpan = StartupTracer.begin("$tag.mapMediaToItem(${data.size} items)")
            val state = mapMediaToItem(
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
            StartupTracer.end(mapSpan)
            StartupTracer.end(combineSpan)
            state
        }
        }
    }.mapLatest {
        if (triggerDatabaseUpdate) {
            eventHandler.pushEvent(UIEvent.UpdateDatabase)
        }
        // Fire-and-forget: don't block data delivery on the DB insert
        appScope.launch {
            val rescanSpan = StartupTracer.begin("$tag.triggerRescan(${it.media.size} items)")
            triggerRescanForMissingDateTaken(it.media)
            StartupTracer.end(rescanSpan)
        }
        if (it.media.isNotEmpty()) {
            StartupTracer.begin("$tag.READY(${it.media.size} items, ${it.mappedMedia.size} mapped)").also { s -> StartupTracer.end(s) }
            StartupTracer.dump()
        }
        it
    }.shareIn(
        scope = appScope,
        started = prioritySharingMethod,
        replay = 1
    )
    }

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

    private val locationsAndGeoMediaFlow: SharedFlow<Pair<List<LocationMedia>, List<GeoMedia>>> = combine(
        repository.getMetadata(),
        timelineMediaFlow
    ) { metadata, timelineState ->
        val mediaById = HashMap<Long, Media.UriMedia>(timelineState.media.size)
        for (m in timelineState.media) { mediaById[m.id] = m }

        val locationGroupMap = LinkedHashMap<String, Media.UriMedia>()
        val geoList = ArrayList<GeoMedia>(metadata.size / 2)

        for (meta in metadata) {
            val media = mediaById[meta.mediaId] ?: continue

            if (meta.gpsLocationNameCity != null && meta.gpsLocationNameCountry != null) {
                val key = "${meta.gpsLocationNameCity}, ${meta.gpsLocationNameCountry}"
                val existing = locationGroupMap[key]
                if (existing == null || media.definedTimestamp > existing.definedTimestamp) {
                    locationGroupMap[key] = media
                }
            }

            if (meta.gpsLatitude != null && meta.gpsLongitude != null) {
                geoList.add(
                    GeoMedia(
                        mediaId = meta.mediaId,
                        latitude = meta.gpsLatitude,
                        longitude = meta.gpsLongitude,
                        locationCity = meta.gpsLocationNameCity,
                        locationCountry = meta.gpsLocationNameCountry,
                        media = media
                    )
                )
            }
        }

        val locations = locationGroupMap.entries
            .map { (location, media) -> LocationMedia(media = media, location = location) }
            .sortedBy { it.location }

        Pair(locations, geoList)
    }.shareIn(appScope, sharingMethod, replay = 1)

    override val locationsMediaFlow: Flow<List<LocationMedia>> =
        locationsAndGeoMediaFlow.map { it.first }

    override val geoMediaFlow: Flow<List<GeoMedia>> =
        locationsAndGeoMediaFlow.map { it.second }

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
    private suspend fun triggerRescanForMissingDateTaken(media: List<Media.UriMedia>) {
        val toScan = media.filter { it.takenTimestamp == null && rescanRequestedIds.add(it.id) }
        if (toScan.isEmpty()) return
        StartupTracer.begin("rescan.found(${toScan.size}/${media.size} missing DATE_TAKEN)").also { s -> StartupTracer.end(s) }
        val paths = toScan.mapNotNull { it.path.takeIf { p -> p.isNotBlank() } }.toTypedArray()
        val mimeTypes = toScan.map { it.mimeType }.toTypedArray()
        if (paths.isNotEmpty()) {
            val scanSpan = StartupTracer.begin("rescan.MediaScannerConnection(${paths.size} files)")
            MediaScannerConnection.scanFile(context, paths, mimeTypes, null)
            StartupTracer.end(scanSpan)
        }
        val dbSpan = StartupTracer.begin("rescan.insertScannedIds(${toScan.size})")
        scannedMediaDao.insertAll(toScan.map { ScannedMedia(it.id) })
        StartupTracer.end(dbSpan)
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