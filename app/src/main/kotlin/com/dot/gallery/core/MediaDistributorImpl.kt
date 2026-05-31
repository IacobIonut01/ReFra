package com.dot.gallery.core

import android.content.Context
import android.net.Uri
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
import com.dot.gallery.feature_node.domain.model.AlbumSection
import com.dot.gallery.feature_node.domain.model.AlbumSectionMember
import com.dot.gallery.feature_node.domain.model.AlbumSectionType
import com.dot.gallery.feature_node.domain.model.AlbumSectionWithAlbums
import com.dot.gallery.feature_node.domain.util.AlbumClassifier
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
import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.stableIdHash
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.EventHandler
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.domain.util.MediaGroupType
import com.dot.gallery.feature_node.domain.util.cloudGroupKey
import com.dot.gallery.feature_node.domain.util.groupKey
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    private val cloudRepository: CloudRepository,
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
            // Refresh cloud data if providers are connected
            if (cloudRepository.hasConfiguredProviders) {
                refreshCloudData()
            }
        }
        delay(1500)
        isRefreshing.value = false
    }

    /**
     * Album Media Sort preference flow
     */
    private val albumMediaSortFlow: StateFlow<Settings.Album.LastSort> = 
        Settings.Album.getAlbumMediaSortFlow(context)
            .distinctUntilChanged()
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
    }.distinctUntilChanged()
    .stateIn(
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

    override var groupByYear: Boolean
        get() = settingsFlow.value?.groupTimelineByYear == true
        set(value) {
            appScope.launch {
                settingsFlow.value?.copy(groupTimelineByYear = value)?.let {
                    repository.updateTimelineSettings(it)
                }
            }
        }

    override val groupSimilarMedia: StateFlow<Boolean> =
        repository.getSetting(Settings.Misc.GROUP_SIMILAR_MEDIA, true)
            .distinctUntilChanged()
            .stateIn(appScope, prioritySharingMethod, true)

    override val enabledGroupTypes: StateFlow<Set<MediaGroupType>> = combine(
        repository.getSetting(Settings.Misc.GROUP_RAW_JPG, true),
        repository.getSetting(Settings.Misc.GROUP_EDITED_COPIES, true),
        repository.getSetting(Settings.Misc.GROUP_BURST_SEQUENCES, true),
        repository.getSetting(Settings.Misc.GROUP_CLOUD_LOCAL, true)
    ) { rawJpg, editedCopies, burstSequences, cloudLocal ->
        buildSet {
            if (rawJpg) add(MediaGroupType.RAW_JPG)
            if (editedCopies) add(MediaGroupType.EDITS)
            if (burstSequences) add(MediaGroupType.BURST)
            if (cloudLocal) add(MediaGroupType.CLOUD_LOCAL)
        }
    }.distinctUntilChanged()
    .stateIn(appScope, prioritySharingMethod, MediaGroupType.entries.toSet())

    override val mergeAlbumsByName: StateFlow<Boolean> =
        repository.getSetting(Settings.Album.MERGE_ALBUMS_BY_NAME, true)
            .stateIn(appScope, prioritySharingMethod, true)

    /**
     * Settings
     */
    override val settingsFlow: StateFlow<TimelineSettings?> = repository.getTimelineSettings()
        .distinctUntilChanged()
        .stateIn(
            scope = appScope,
            started = prioritySharingMethod,
            initialValue = TimelineSettings()
        )

    /**
     * Albums
     */
    private val _blacklistedAlbumsInternal = MutableStateFlow<List<IgnoredAlbum>?>(null)

    init {
        // Eagerly load blacklisted albums via a one-shot query that uses a read
        // connection, bypassing Room's InvalidationTracker setup (which serializes
        // all DAO Flow observers through the write connection and adds ~1.7s).
        // After the initial load, the reactive DAO Flow takes over for live updates.
        appScope.launch {
            _blacklistedAlbumsInternal.value = repository.getBlacklistedAlbumsAsync()
            repository.getBlacklistedAlbums().collect {
                _blacklistedAlbumsInternal.value = it
            }
        }
    }

    override val blacklistedAlbumsFlow: StateFlow<List<IgnoredAlbum>> =
        _blacklistedAlbumsInternal
            .map { it ?: emptyList() }
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

    // === Cloud integration at distributor level ===

    private val _cloudAlbumsFlow = MutableStateFlow<List<CloudAlbum>>(emptyList())
    private val _cloudAlbumMemberRemoteIds = MutableStateFlow<Set<String>>(emptySet())

    companion object {
        private const val UNSORTED_ALBUM_SENTINEL = "__unsorted__"
    }

    // Eagerly load cached cloud media so the timeline can merge them.
    // The one-shot Room query runs on IO and typically completes before
    // the slower MediaStore query.
    // distinctUntilChangedBy avoids redundant combine passes when the
    // reactive Room Flow re-emits the same data as the one-shot query.
    private val _cloudCachedMedia: StateFlow<List<Media.UriMedia>> = flow {
        emit(withContext(Dispatchers.IO) {
            cloudRepository.getCachedMediaAsync().map { it.toUriMedia() }
        })
        emitAll(cloudRepository.getCachedMedia().map { entities ->
            entities.map { it.toUriMedia() }
        })
    }.distinctUntilChangedBy { it.size }
     .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    private val _cloudCachedFavorites: StateFlow<List<Media.UriMedia>> = flow {
        emit(withContext(Dispatchers.IO) {
            cloudRepository.getCachedFavoritesAsync().map { it.toUriMedia() }
        })
        emitAll(cloudRepository.getCachedFavorites().map { entities ->
            entities.map { it.toUriMedia() }
        })
    }.distinctUntilChangedBy { it.size }
     .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    private val _cloudCachedTrashed: StateFlow<List<Media.UriMedia>> = flow {
        emit(withContext(Dispatchers.IO) {
            cloudRepository.getCachedTrashedAsync().map { it.toUriMedia() }
        })
        emitAll(cloudRepository.getCachedTrashed().map { entities ->
            entities.map { it.toUriMedia() }
        })
    }.distinctUntilChangedBy { it.size }
     .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    override val cloudSyncStates: StateFlow<Map<Long, SyncState>> =
        cloudRepository.getCachedMedia().map { entities ->
            entities.associate { stableIdHash(it.remoteId) to it.syncState }
        }.stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    init {
        appScope.launch {
            cloudRepository.connectionStates.collect { states ->
                val hasConnected = states.any { it.value == ConnectionState.CONNECTED }
                if (hasConnected) {
                    refreshCloudData()
                } else {
                    _cloudAlbumsFlow.value = emptyList()
                }
            }
        }
    }

    private suspend fun refreshCloudData() {
        try {
            cloudRepository.getAllRemoteAlbums().collect { resource ->
                when (resource) {
                    is Resource.Success -> _cloudAlbumsFlow.value = resource.data ?: emptyList()
                    is Resource.Error -> _cloudAlbumsFlow.value = resource.data ?: emptyList()
                }
            }
        } catch (_: Exception) { }
        // Collect all asset IDs that belong to at least one cloud album
        try {
            val albums = _cloudAlbumsFlow.value
            val memberIds = HashSet<String>()
            for (album in albums) {
                val resource = cloudRepository.getAlbumMedia(album.providerType, album.remoteId).first()
                if (resource is Resource.Success) {
                    resource.data?.forEach { memberIds.add(it.remoteId) }
                }
            }
            _cloudAlbumMemberRemoteIds.value = memberIds
        } catch (_: Exception) { }
        // Fetch trashed items into cache so the trash screen has cloud data
        try {
            cloudRepository.getRemoteTrashed().first()
        } catch (_: Exception) { }
    }

    private fun isCloudAlbumId(albumId: Long): Boolean {
        if (albumId >= 0) return false
        // Check unsorted virtual albums
        if (isUnsortedCloudAlbumId(albumId)) return true
        return _cloudAlbumsFlow.value.any {
            (CloudAlbum.CLOUD_ALBUM_ID_BASE - stableIdHash(it.remoteId)) == albumId
        }
    }

    private fun isUnsortedCloudAlbumId(albumId: Long): Boolean {
        return com.dot.gallery.cloud.core.ProviderType.remoteTypes().any { providerType ->
            (CloudAlbum.CLOUD_ALBUM_ID_BASE - stableIdHash(UNSORTED_ALBUM_SENTINEL + providerType.name)) == albumId
        }
    }

    /**
     * Virtual "unsorted" album per connected cloud provider.
     * Contains all cached (non-trashed) cloud media that don't belong to any cloud album.
     */
    private val _unsortedCloudAlbumsFlow: StateFlow<List<Album>> = combine(
        _cloudCachedMedia,
        _cloudAlbumMemberRemoteIds
    ) { cachedMedia, memberIds ->
        if (cachedMedia.isEmpty()) return@combine emptyList()
        // Group cached media by provider (derive provider from URI authority)
        val byProvider = cachedMedia.groupBy { media ->
            media.getUri().authority ?: ""
        }.filterKeys { it.isNotEmpty() }
        byProvider.mapNotNull { (providerName, providerMedia) ->
            val providerType = try {
                com.dot.gallery.cloud.core.ProviderType.valueOf(providerName)
            } catch (_: Exception) { return@mapNotNull null }
            val unsortedMedia = providerMedia.filter { media ->
                val remoteId = media.getUri().pathSegments.firstOrNull()
                remoteId != null && remoteId !in memberIds
            }
            if (unsortedMedia.isEmpty()) return@mapNotNull null
            val thumbUri = unsortedMedia.maxByOrNull { it.definedTimestamp }
                ?.getUri()?.let { uri ->
                    uri.buildUpon().clearQuery().appendQueryParameter("size", "thumbnail").build()
                } ?: Uri.EMPTY
            Album(
                id = CloudAlbum.CLOUD_ALBUM_ID_BASE - stableIdHash(UNSORTED_ALBUM_SENTINEL + providerType.name),
                label = providerType.displayName,
                uri = thumbUri,
                pathToThumbnail = thumbUri.toString(),
                relativePath = "cloud/${providerType.name}",
                timestamp = unsortedMedia.maxOf { it.definedTimestamp },
                count = unsortedMedia.size.toLong(),
                size = 0L
            )
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // === End cloud integration ===

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _rawAlbumsFlow: StateFlow<Resource<List<Album>>?> =
        hasPermission.flatMapLatest { granted ->
            if (!granted) flowOf(null)
            else repository.getAlbums(mediaOrder = albumOrder)
                .map<Resource<List<Album>>, Resource<List<Album>>?> { it }
        }.stateIn(appScope, prioritySharingMethod, null)

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

    private val albumSectionsDbFlow: StateFlow<List<AlbumSection>> =
        repository.getAllAlbumSections()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptyList()
            )

    private val albumSectionMembersDbFlow: StateFlow<List<AlbumSectionMember>> =
        repository.getAllSectionMembers()
            .stateIn(
                scope = appScope,
                started = prioritySharingMethod,
                initialValue = emptyList()
            )

    private val sectionsEnabled: StateFlow<Boolean> =
        repository.getSetting(Settings.Album.ALBUM_SECTIONS_ENABLED, false)
            .stateIn(appScope, prioritySharingMethod, false)

    override val albumsFlow: StateFlow<AlbumState> = combine(
            _rawAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.getAlbums(${it?.data?.size ?: 0})").also { s -> StartupTracer.end(s) } },
            pinnedAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.pinned(${it.size})").also { s -> StartupTracer.end(s) } },
            _blacklistedAlbumsInternal
                .onEach { StartupTracer.begin("albums.dep.blacklisted(${it?.size ?: -1})").also { s -> StartupTracer.end(s) } },
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
            _cloudAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.cloudAlbums(${it.size})").also { s -> StartupTracer.end(s) } },
            _unsortedCloudAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.unsortedCloud(${it.size})").also { s -> StartupTracer.end(s) } },
            albumSectionsDbFlow
                .onEach { StartupTracer.begin("albums.dep.sections(${it.size})").also { s -> StartupTracer.end(s) } },
            albumSectionMembersDbFlow
                .onEach { StartupTracer.begin("albums.dep.sectionMembers(${it.size})").also { s -> StartupTracer.end(s) } },
            sectionsEnabled
                .onEach { StartupTracer.begin("albums.dep.sectionsEnabled=$it").also { s -> StartupTracer.end(s) } },
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val result = values[0] as Resource<List<Album>>?
            @Suppress("UNCHECKED_CAST")
            val blacklistedAlbums = values[2] as List<IgnoredAlbum>?
            // Keep loading until both albums and blacklisted albums are loaded from their sources
            if (result == null || blacklistedAlbums == null) return@combine AlbumState()
            val combineSpan = StartupTracer.begin("albums.combine_body(${result.data?.size ?: 0} albums)")
            @Suppress("UNCHECKED_CAST")
            val pinnedAlbums = values[1] as List<PinnedAlbum>
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
            @Suppress("UNCHECKED_CAST")
            val cloudAlbums = values[12] as List<CloudAlbum>
            @Suppress("UNCHECKED_CAST")
            val unsortedCloudAlbums = values[13] as List<Album>
            @Suppress("UNCHECKED_CAST")
            val sections = values[14] as List<AlbumSection>
            @Suppress("UNCHECKED_CAST")
            val sectionMembers = values[15] as List<AlbumSectionMember>
            val areSectionsEnabled = values[16] as Boolean
            val newOrder = settings?.albumMediaOrder ?: albumOrder
            val thumbnailMap = thumbnails.associateBy { it.albumId }
            val localAlbums = newOrder.sortAlbums(result.data ?: emptyList()).map { album ->
                val thumbnail = thumbnailMap[album.id] ?: return@map album
                album.copy(uri = thumbnail.thumbnailUri)
            }
            val data = localAlbums + cloudAlbums.map { it.toAlbum() } + unsortedCloudAlbums
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

            val unpinnedUngrouped = mergedData.filter { album ->
                !album.isPinned && album.id !in groupedMergedIds &&
                    (if (album.isMerged) album.mergedAlbumIds.none { it in collectionAlbumIds }
                     else album.id !in collectionAlbumIds)
            }

            val albumSections = if (areSectionsEnabled && sections.isNotEmpty()) {
                val manualOverrides = sectionMembers.associate { it.albumId to it.sectionId }
                val sectionIdByType = sections.associate { it.sectionType to it.id }
                val classified = AlbumClassifier.classifyAlbums(
                    unpinnedUngrouped, manualOverrides, sectionIdByType
                )
                sections
                    .filter { it.isVisible }
                    .map { section ->
                        AlbumSectionWithAlbums(
                            section = section,
                            albums = classified[section.id] ?: emptyList()
                        )
                    }
                    .filter { it.albums.isNotEmpty() }
            } else emptyList()

            AlbumState(
                albums = mergedData,
                albumsWithBlacklisted = data,
                albumsUnpinned = if (areSectionsEnabled && sections.isNotEmpty()) emptyList() else unpinnedUngrouped,
                albumsPinned = mergedData.filter { album ->
                    album.isPinned &&
                        (if (album.isMerged) album.mergedAlbumIds.none { it in collectionAlbumIds }
                         else album.id !in collectionAlbumIds)
                }.sortedBy { it.label },
                albumGroups = albumGroups,
                albumSections = albumSections,
                collections = collections,
                isLoading = false,
                error = if (result is Resource.Error) result.message ?: "An error occurred" else ""
            ).also {
                StartupTracer.end(combineSpan)
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
            if (isUnsortedCloudAlbumId(albumId)) {
                unsortedCloudAlbumTimelineMediaFlow(albumId)
            } else if (isCloudAlbumId(albumId)) {
                cloudAlbumTimelineMediaFlow(albumId)
            } else {
                localAlbumTimelineMediaFlow(albumId)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun cloudAlbumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>> =
        _cloudAlbumsFlow
            .map { albums ->
                albums.find {
                    (CloudAlbum.CLOUD_ALBUM_ID_BASE - stableIdHash(it.remoteId)) == albumId
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { cloudAlbum ->
                if (cloudAlbum == null) {
                    flowOf(MediaState<Media.UriMedia>(error = "Cloud album not found"))
                } else {
                    combine(
                        cloudRepository.getAlbumMedia(cloudAlbum.providerType, cloudAlbum.remoteId),
                        settingsFlow,
                        dateFormatsFlow,
                        albumMediaSortFlow,
                        _cloudCachedMedia
                    ) { values ->
                        @Suppress("UNCHECKED_CAST")
                        val resource = values[0] as Resource<List<CloudMediaEntity>>
                        val settings = values[1] as TimelineSettings?
                        @Suppress("UNCHECKED_CAST")
                        val dateFormats = values[2] as Triple<String, String, String>
                        val albumSort = values[3] as Settings.Album.LastSort
                        @Suppress("UNCHECKED_CAST")
                        val cachedNonTrashed = values[4] as List<Media.UriMedia>
                        val cachedIds = cachedNonTrashed.mapTo(HashSet()) { it.id }
                        val allMedia = when (resource) {
                            is Resource.Success -> resource.data?.map { it.toUriMedia() } ?: emptyList()
                            is Resource.Error -> resource.data?.map { it.toUriMedia() } ?: emptyList()
                        }
                        // Filter out items that are no longer in the non-trashed cache
                        val media = if (cachedIds.isNotEmpty()) allMedia.filter { it.id in cachedIds } else allMedia
                        val error = if (resource is Resource.Error) resource.message ?: "" else ""
                        val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
                        val sorter = when (albumSort.kind) {
                            FilterKind.DATE -> MediaOrder.Date(albumSort.orderType)
                            FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(albumSort.orderType)
                            FilterKind.NAME -> MediaOrder.Label(albumSort.orderType)
                        }
                        mapMediaToItem(
                            data = sorter.sortMedia(media),
                            error = error,
                            albumId = albumId,
                            groupByMonth = settings?.groupTimelineByMonth == true,
                            groupByYear = settings?.groupTimelineByYear == true,
                            defaultDateFormat = defaultDateFormat,
                            extendedDateFormat = extendedDateFormat,
                            weeklyDateFormat = weeklyDateFormat
                        )
                    }
                }
            }.stateIn(appScope, sharingMethod, MediaState())

    private fun unsortedCloudAlbumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>> =
        combine(
            _cloudCachedMedia,
            _cloudAlbumMemberRemoteIds,
            settingsFlow,
            dateFormatsFlow,
            albumMediaSortFlow
        ) { cachedMedia, memberIds, settings, dateFormats, albumSort ->
            val unsortedMedia = cachedMedia.filter { media ->
                val remoteId = media.getUri().pathSegments.firstOrNull()
                remoteId != null && remoteId !in memberIds
            }
            val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
            val sorter = when (albumSort.kind) {
                FilterKind.DATE -> MediaOrder.Date(albumSort.orderType)
                FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(albumSort.orderType)
                FilterKind.NAME -> MediaOrder.Label(albumSort.orderType)
            }
            mapMediaToItem(
                data = sorter.sortMedia(unsortedMedia),
                error = "",
                albumId = albumId,
                groupByMonth = settings?.groupTimelineByMonth == true,
                groupByYear = settings?.groupTimelineByYear == true,
                defaultDateFormat = defaultDateFormat,
                extendedDateFormat = extendedDateFormat,
                weeklyDateFormat = weeklyDateFormat
            )
        }.stateIn(appScope, sharingMethod, MediaState())

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun localAlbumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>> =
        hasPermission.flatMapLatest { granted ->
            if (!granted) flowOf(MediaState())
            else {
                combine(
                    repository.mediaFlow(albumId, null),
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
                        groupByYear = settings?.groupTimelineByYear == true,
                        groupSimilarMedia = shouldGroupSimilar,
                        enabledGroupTypes = groupTypes,
                        defaultDateFormat = defaultDateFormat,
                        extendedDateFormat = extendedDateFormat,
                        weeklyDateFormat = weeklyDateFormat
                    )
                }
            }
        }.stateIn(appScope, prioritySharingMethod, MediaState())


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
            val isMainTimeline = albumId == -1L && target == null
            val isFavorites = target == Constants.Target.TARGET_FAVORITES
            val isTrash = target == Constants.Target.TARGET_TRASH
            val cloudMediaSource: Flow<List<Media.UriMedia>> = when {
                isMainTimeline -> _cloudCachedMedia
                isFavorites -> _cloudCachedFavorites
                isTrash -> _cloudCachedTrashed
                else -> flowOf(emptyList())
            }
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
                .onEach { StartupTracer.begin("$tag.dep.enabledGroupTypes(${it.size})").also { s -> StartupTracer.end(s) } },
            cloudMediaSource
                .onEach { StartupTracer.begin("$tag.dep.cloudMedia(${it.size})").also { s -> StartupTracer.end(s) } }
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
            @Suppress("UNCHECKED_CAST")
            val cloudMedia = values[8] as List<Media.UriMedia>
            
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
                if (isMainTimeline) {
                    removeAll { media -> media.albumID in lockedAlbumIds }
                }
                if (isMainTimeline || isFavorites || isTrash) {
                    addAll(cloudMedia)
                }
            }
            // Pre-compute cloud→local group key overrides so cloud items
            // are grouped with their local counterparts by the standard groupBy.
            val cloudOverrides = if (
                shouldGroupSimilar &&
                MediaGroupType.CLOUD_LOCAL in groupTypes &&
                cloudMedia.isNotEmpty()
            ) {
                val localByBasename = HashMap<String, String>(data.size)
                for (m in data) {
                    if (!m.isCloud) {
                        localByBasename.putIfAbsent(m.cloudGroupKey, m.groupKey)
                    }
                }
                val overrides = HashMap<Long, String>(cloudMedia.size)
                for (c in cloudMedia) {
                    val localKey = localByBasename[c.cloudGroupKey]
                    if (localKey != null) {
                        overrides[c.id] = localKey
                    }
                }
                overrides
            } else {
                emptyMap()
            }
            val mapSpan = StartupTracer.begin("$tag.mapMediaToItem(${data.size} items)")
            val state = mapMediaToItem(
                data = sorter.sortMedia(data),
                error = result.message ?: "",
                albumId = albumId,
                groupByMonth = settings?.groupTimelineByMonth == true,
                groupByYear = settings?.groupTimelineByYear == true,
                groupSimilarMedia = shouldGroupSimilar,
                enabledGroupTypes = groupTypes,
                cloudGroupKeyOverrides = cloudOverrides,
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
            val scannedItems = triggerRescanForMissingDateTaken(it.media)
            StartupTracer.end(rescanSpan)
            // Insert scanned IDs in a separate step so the rescan span stays fast.
            // Defer the heavy DB write well past startup so it doesn't block
            // Room's DAO Flow InvalidationTracker setup on the write connection.
            // With a warm page cache the insert takes ~5ms; with a cold cache
            // it takes 650ms+ and delays settingsFlow/all #2 combines.
            if (scannedItems.isNotEmpty()) {
                delay(3000)
                val dbSpan = StartupTracer.begin("rescan.insertScannedIds(${scannedItems.size})")
                scannedMediaDao.insertAll(scannedItems)
                StartupTracer.end(dbSpan)
            }
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
        timelineMediaFlow
    ) { metadata, timelineState ->
        val matchingMediaIds = metadata
            .filter {
                it.gpsLocationNameCity == gpsLocationNameCity &&
                        it.gpsLocationNameCountry == gpsLocationNameCountry
            }
            .mapTo(HashSet()) { it.mediaId }
        val filteredMedia = timelineState.media.filter {
            it.id in matchingMediaIds
        }
        return@combine mapMediaToItem(
            data = filteredMedia,
            error = timelineState.error,
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
            groupByYear = settings?.groupTimelineByYear == true,
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
                groupByYear = settings?.groupTimelineByYear == true,
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
    private fun triggerRescanForMissingDateTaken(media: List<Media.UriMedia>): List<ScannedMedia> {
        val toScan = media.filter { it.takenTimestamp == null && rescanRequestedIds.add(it.id) }
        if (toScan.isEmpty()) return emptyList()
        StartupTracer.begin("rescan.found(${toScan.size}/${media.size} missing DATE_TAKEN)").also { s -> StartupTracer.end(s) }
        val paths = toScan.mapNotNull { it.path.takeIf { p -> p.isNotBlank() } }.toTypedArray()
        val mimeTypes = toScan.map { it.mimeType }.toTypedArray()
        if (paths.isNotEmpty()) {
            val scanSpan = StartupTracer.begin("rescan.MediaScannerConnection(${paths.size} files)")
            MediaScannerConnection.scanFile(context, paths, mimeTypes, null)
            StartupTracer.end(scanSpan)
        }
        return toScan.map { ScannedMedia(it.id) }
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