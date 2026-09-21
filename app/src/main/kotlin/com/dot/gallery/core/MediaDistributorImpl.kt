package com.dot.gallery.core

import android.content.Context
import android.net.Uri
import android.media.MediaScannerConnection
import androidx.compose.runtime.compositionLocalOf
import androidx.work.WorkManager
import com.dot.gallery.core.Settings.Misc.DEFAULT_DATE_FORMAT
import com.dot.gallery.core.Settings.Misc.EXTENDED_DATE_FORMAT
import com.dot.gallery.core.Settings.Misc.WEEKLY_DATE_FORMAT
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumGroup
import com.dot.gallery.feature_node.domain.model.AlbumGroupMember
import com.dot.gallery.feature_node.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.feature_node.domain.model.AlbumMergeResolver
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
import com.dot.gallery.feature_node.domain.model.matchesLocationCoordinates
import com.dot.gallery.feature_node.domain.model.matchesLocationName
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.MediaTypeAlbum
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.MergedSubfolderAlbum
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineDateSource
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.model.ScannedMedia
import com.dot.gallery.feature_node.domain.model.shouldIgnore
import com.dot.gallery.feature_node.data.data_source.ScannedMediaDao
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.feature_node.data.data_source.SmartScanDao
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.cloudAlbumId
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.CloudMediaSnapshotMapper
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.sync.CloudAlbumCopyWorker
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.sync.isActiveBackupWork
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
import com.dot.gallery.feature_node.presentation.util.SystemDateFormatField
import com.dot.gallery.feature_node.presentation.util.applyOptimisticMutations
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.dot.gallery.feature_node.presentation.util.mediaFlow
import com.dot.gallery.feature_node.presentation.util.resolvedDateFormat

import dagger.hilt.android.qualifiers.ApplicationContext
import android.provider.MediaStore
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.startup.StartupWorkGate
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

val LocalMediaDistributor = compositionLocalOf<MediaDistributor> {
    error("No MediaDistributor provided!!! This is likely due to a missing Hilt injection in the Composable hierarchy.")
}

internal fun matchingLocationMediaIds(
    localMediaIds: Set<Long>,
    cloudMedia: List<CloudMediaEntity>,
    city: String,
    country: String,
    latitude: Double? = null,
    longitude: Double? = null,
    additionalMediaIds: Set<Long> = emptySet(),
): Set<Long> = buildSet {
    addAll(localMediaIds)
    addAll(additionalMediaIds)
    cloudMedia.forEach {
        if (matchesLocationName(it.city, it.country, city, country) ||
            matchesLocationCoordinates(it.latitude, it.longitude, latitude, longitude)
        ) {
            add(it.globalMediaId)
        }
    }
}

internal fun usesLiveCloudAlbumMembership(providerType: ProviderType): Boolean =
    providerType == ProviderType.SMB || providerType == ProviderType.NFS

private fun Settings.Album.LastSort.toMediaOrder(): MediaOrder = when (kind) {
    FilterKind.DATE -> MediaOrder.Date(orderType)
    FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(orderType)
    FilterKind.NAME -> MediaOrder.Label(orderType)
}

private val Settings.Album.LastSort.dateSource: TimelineDateSource
    get() = if (kind == FilterKind.DATE_MODIFIED) {
        TimelineDateSource.MODIFIED_TIME
    } else {
        TimelineDateSource.CAPTURE_TIME
    }

internal fun expandLocationMediaIds(
    matchingMediaIds: Set<Long>,
    cloudBackupIdsByLocalId: Map<Long, List<Long>>,
): Set<Long> = buildSet {
    addAll(matchingMediaIds)
    cloudBackupIdsByLocalId.forEach { (localId, cloudIds) ->
        if (cloudIds.any { it in matchingMediaIds }) add(localId)
    }
}

private const val PENDING_REMOVAL_TTL_MS = 30_000L
private const val FAVORITE_OVERRIDE_TTL_MS = 15_000L

@Singleton
class MediaDistributorImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val cloudRepository: CloudRepository,
    private val eventHandler: EventHandler,
    workManager: WorkManager,
    private val scannedMediaDao: ScannedMediaDao,
    private val smartScanDao: SmartScanDao,
    private val startupGate: StartupWorkGate
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
    private val rescanHistoryReady = CompletableDeferred<Unit>()

    init {
        appScope.launch {
            startupGate.awaitFirstContent()
            try {
                rescanRequestedIds.addAll(scannedMediaDao.getScannedIds())
                scannedMediaDao.removeStaleEntries()
            } finally {
                rescanHistoryReady.complete(Unit)
            }
        }
    }

    /**
     * Pull-to-refresh
     */
    override val isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // === Optimistic mutations ===
    // Media ids confirmed for trash/delete/restore are hidden from the media flows until the
    // underlying source catches up, and favorite toggles are applied over the source the same
    // way. Entries carry a timestamp so a silently-failed mutation can't hide an item or pin
    // a stale favorite indefinitely.
    private data class PendingRemovalMark(val scope: PendingRemovalScope, val atMs: Long)
    private data class FavoriteOverrideMark(val favorite: Boolean, val atMs: Long)

    private val _pendingRemoval = MutableStateFlow<Map<Long, PendingRemovalMark>>(emptyMap())
    private val _favoriteOverrideMarks =
        MutableStateFlow<Map<Long, FavoriteOverrideMark>>(emptyMap())

    override val pendingRemovalIds: StateFlow<Set<Long>> =
        _pendingRemoval.map { it.keys }
            .stateIn(appScope, sharingMethod, emptySet())

    override val favoriteOverrides: StateFlow<Map<Long, Boolean>> =
        _favoriteOverrideMarks.map { marks -> marks.mapValues { it.value.favorite } }
            .stateIn(appScope, sharingMethod, emptyMap())

    override fun markPendingRemoval(ids: Collection<Long>, scope: PendingRemovalScope) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        _pendingRemoval.update { current ->
            current + ids.associateWith { PendingRemovalMark(scope, now) }
        }
    }

    override fun unmarkPendingRemoval(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        _pendingRemoval.update { it - ids.toSet() }
    }

    override fun setFavoriteOverride(mediaId: Long, favorite: Boolean) {
        _favoriteOverrideMarks.update {
            it + (mediaId to FavoriteOverrideMark(favorite, System.currentTimeMillis()))
        }
    }

    override fun clearFavoriteOverride(mediaId: Long) {
        _favoriteOverrideMarks.update { it - mediaId }
    }

    /**
     * Appends optimistic mutations (pending removals + favorite overrides) to a media flow.
     * Reconcile rules run against each emitted raw list: a pending id that reappears after
     * being observed absent (trash restore, external re-add) is un-marked, and overrides the
     * source already reflects are dropped. Expired entries are pruned on the next emission.
     */
    private fun Flow<MediaState<Media.UriMedia>>.withOptimisticMutations(
        inTrashView: Boolean = false,
        dropUnfavorited: Boolean = false,
    ): Flow<MediaState<Media.UriMedia>> {
        val seenAbsent = HashSet<Long>()
        return combine(this, _pendingRemoval, _favoriteOverrideMarks) { state, pending, favs ->
            val now = System.currentTimeMillis()
            val activePending = pending.filterValues {
                now - it.atMs < PENDING_REMOVAL_TTL_MS &&
                        (it.scope == PendingRemovalScope.EVERYWHERE ||
                                (inTrashView == (it.scope == PendingRemovalScope.TRASH_ONLY)))
            }
            val activeFavs = favs.filterValues { now - it.atMs < FAVORITE_OVERRIDE_TTL_MS }
            if (pending.size != activePending.size) {
                _pendingRemoval.update { it.filterValues { m -> now - m.atMs < PENDING_REMOVAL_TTL_MS } }
            }
            if (favs.size != activeFavs.size) {
                _favoriteOverrideMarks.update { it.filterValues { m -> now - m.atMs < FAVORITE_OVERRIDE_TTL_MS } }
            }
            if (state.media.isNotEmpty()) {
                if (activePending.isNotEmpty()) {
                    val rawIds = HashSet<Long>(state.media.size)
                    state.media.forEach { rawIds.add(it.id) }
                    activePending.keys.forEach { id -> if (id !in rawIds) seenAbsent.add(id) }
                    seenAbsent.retainAll(activePending.keys)
                    val returned = activePending.keys.filter { it in rawIds && it in seenAbsent }
                    if (returned.isNotEmpty()) {
                        _pendingRemoval.update { it - returned.toSet() }
                    }
                }
                if (activeFavs.isNotEmpty()) {
                    val landed = ArrayList<Long>()
                    for (m in state.media) {
                        val mark = activeFavs[m.id] ?: continue
                        if ((m.favorite == 1) == mark.favorite) landed.add(m.id)
                    }
                    if (landed.isNotEmpty()) {
                        _favoriteOverrideMarks.update { it - landed.toSet() }
                    }
                }
            }
            state.applyOptimisticMutations(
                pendingRemovalIds = activePending.keys,
                favoriteOverrides = activeFavs.mapValues { it.value.favorite },
                dropUnfavorited = dropUnfavorited
            )
        }
    }

    override suspend fun invalidate() {
        isRefreshing.value = true
        try {
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
        } finally {
            // Always clear the refreshing flag, even if the caller's (composition-scoped)
            // coroutine is cancelled mid-refresh by navigating away during the delay.
            // isRefreshing lives in this app-scoped singleton, so a skipped reset would
            // leave the pull-to-refresh spinner stuck forever, including on return (#958).
            isRefreshing.value = false
        }
    }

    /**
     * Album Media Sort preference flow
     */
    private val albumMediaSortFlow: StateFlow<Settings.Album.LastSort> = 
        Settings.Album.getAlbumMediaSortFlow(context)
            .distinctUntilChanged()
            .stateIn(appScope, SharingStarted.Eagerly, Settings.Album.LastSort(OrderType.Descending, FilterKind.DATE))

    private val timelineSortFlow: StateFlow<Settings.Album.LastSort> =
        Settings.Misc.getTimelineSortFlow(context)
            .distinctUntilChanged()
            .stateIn(appScope, SharingStarted.Eagerly, Settings.Album.LastSort(OrderType.Descending, FilterKind.DATE))

    /**
     * Common
     */
    override val hasPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val dateFormatsFlow: StateFlow<Triple<String, String, String>> = combine(
        repository.getSetting(DEFAULT_DATE_FORMAT, "")
            .map { resolvedDateFormat(context, it, SystemDateFormatField.DEFAULT) },
        repository.getSetting(EXTENDED_DATE_FORMAT, "")
            .map { resolvedDateFormat(context, it, SystemDateFormatField.EXTENDED) },
        repository.getSetting(WEEKLY_DATE_FORMAT, "")
            .map { resolvedDateFormat(context, it, SystemDateFormatField.WEEKLY) }
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
        .map { it ?: TimelineSettings() }
        .distinctUntilChanged()
        .stateIn(
            scope = appScope,
            started = prioritySharingMethod,
            initialValue = null
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

    private val _lockedAlbumsInternal = MutableStateFlow<List<LockedAlbum>?>(null)

    init {
        appScope.launch {
            _lockedAlbumsInternal.value = repository.getLockedAlbums().first()
            repository.getLockedAlbums().collect {
                _lockedAlbumsInternal.value = it
            }
        }
    }

    override val lockedAlbumsFlow: StateFlow<List<LockedAlbum>> =
        _lockedAlbumsInternal
            .map { it ?: emptyList() }
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
    /**
     * Per-cloud-album member sets, keyed by album identity. Rebuilt by [refreshCloudData];
     * consumed by the unified timeline to filter media of hidden cloud albums and to derive
     * the flat "in at least one album" set below.
     */
    private val _cloudAlbumMembers =
        MutableStateFlow<Map<CloudAlbumMemberId, Set<CloudAlbumMemberId>>>(emptyMap())
    private val _cloudAlbumMemberIds: StateFlow<Set<CloudAlbumMemberId>> =
        _cloudAlbumMembers.map { it.values.flatten().toSet() }
            .stateIn(appScope, sharingMethod, emptySet())
    private val cloudRefreshMutex = Mutex()

    // Eagerly load cached cloud media so the timeline can merge them.
    // The one-shot Room query runs on IO and typically completes before
    // the slower MediaStore query.
    // distinctUntilChanged() (structural) instead of size-only: an in-place mutation that keeps
    // the count constant (e.g. a favorite/archive toggle, or an asset swapped for another) changes
    // content but not size, so a size-only guard would drop the update and leave the timeline stale.
    private val _cloudCachedMedia: StateFlow<List<Media.UriMedia>> = flow {
        val mapper = CloudMediaSnapshotMapper()
        emit(withContext(Dispatchers.IO) {
            mapper.map(cloudRepository.getCachedMediaAsync())
        })
        emitAll(cloudRepository.getCachedMedia().map { entities ->
            mapper.map(entities)
        })
    }.distinctUntilChanged()
     .stateIn(appScope, sharingMethod, emptyList())

    private val _cloudCachedFavorites: StateFlow<List<Media.UriMedia>> = flow {
        val mapper = CloudMediaSnapshotMapper()
        emit(withContext(Dispatchers.IO) {
            mapper.map(cloudRepository.getCachedFavoritesAsync())
        })
        emitAll(cloudRepository.getCachedFavorites().map { entities ->
            mapper.map(entities)
        })
    }.distinctUntilChanged()
     .stateIn(appScope, sharingMethod, emptyList())

    private val _cloudCachedTrashed: StateFlow<List<Media.UriMedia>> = flow {
        val mapper = CloudMediaSnapshotMapper()
        emit(withContext(Dispatchers.IO) {
            mapper.map(cloudRepository.getCachedTrashedAsync())
        })
        emitAll(cloudRepository.getCachedTrashed().map { entities ->
            mapper.map(entities)
        })
    }.distinctUntilChanged()
     .stateIn(appScope, sharingMethod, emptyList())

    override val cloudSyncStates: StateFlow<Map<Long, SyncState>> =
        cloudRepository.getCachedMedia().map { entities ->
            entities.associate { it.globalMediaId to it.syncState }
        }.stateIn(appScope, sharingMethod, emptyMap())

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
        // Re-fetch cloud albums + membership when a backup run finishes, so newly uploaded
        // media surfaces as a fresh remote album/thumbnail without needing an app restart.
        // (Uploaded rows already reach the timeline reactively via _cloudCachedMedia; the
        // remote album list is a network fetch that only happens in refreshCloudData.)
        appScope.launch {
            var wasRunning = false
            workManager.getWorkInfosByTagFlow(CloudUploadWorker.TAG_BACKUP).collect { infos ->
                val running = infos.any { isActiveBackupWork(it.state, it.tags) }
                if (!running && wasRunning && cloudRepository.hasConfiguredProviders) {
                    refreshCloudData()
                }
                wasRunning = running
            }
        }
        appScope.launch {
            var wasRunning = false
            workManager.getWorkInfosByTagFlow(CloudAlbumCopyWorker.TAG).collect { infos ->
                val running = infos.any { !it.state.isFinished }
                if (!running && wasRunning && cloudRepository.hasConfiguredProviders) {
                    refreshCloudData()
                }
                wasRunning = running
            }
        }
    }

    private suspend fun refreshCloudData() = cloudRefreshMutex.withLock {
        // First pull every account's remote delta into cloud_media (adds, edits AND
        // deletions — reconcileIndex forces a complete-index pass). The Room-backed
        // cloud flow then republishes the unified timeline/album caches instantly.
        try {
            cloudRepository.syncAllRemoteChanges()
        } catch (_: Exception) { }
        try {
            cloudRepository.getAllRemoteAlbums().collect { resource ->
                when (resource) {
                    is Resource.Success -> _cloudAlbumsFlow.value = resource.data ?: emptyList()
                    is Resource.Error -> _cloudAlbumsFlow.value = resource.data ?: emptyList()
                }
            }
        } catch (_: Exception) { }
        // Collect all asset IDs that belong to at least one cloud album, and — for
        // providers that don't populate album metadata from a cheap directory listing
        // (e.g. SMB/NFS/WebDAV report thumbnailAssetId == null and assetCount == 0) —
        // enrich each album from its member list so it gets a working thumbnail and a
        // real item count.
        try {
            val albums = _cloudAlbumsFlow.value
            val membersByAlbum = HashMap<CloudAlbumMemberId, Set<CloudAlbumMemberId>>()
            val enriched = ArrayList<CloudAlbum>(albums.size)
            var didEnrich = false
            for (album in albums) {
                val resource = cloudRepository.getAlbumMedia(
                    album.providerType,
                    album.serverConfigId,
                    album.remoteId
                ).first()
                val media = if (resource is Resource.Success) resource.data ?: emptyList() else emptyList()
                membersByAlbum[CloudAlbumMemberId(
                    album.providerType,
                    album.serverConfigId,
                    album.remoteId
                )] = media.mapTo(HashSet()) {
                    CloudAlbumMemberId(it.providerType, it.serverConfigId, it.remoteId)
                }
                var updated = album
                if (updated.thumbnailAssetId == null) {
                    // Cover with the album's NEWEST asset (max timestamp), not whatever the
                    // directory scan happens to return first — otherwise the thumbnail is an
                    // arbitrary/old item and never tracks freshly uploaded media.
                    media.maxByOrNull { it.timestamp }?.remoteId
                        ?.let { updated = updated.copy(thumbnailAssetId = it) }
                }
                if (updated.assetCount == 0 && media.isNotEmpty()) {
                    updated = updated.copy(assetCount = media.size)
                }
                // Drop empty albums — folders with no media (e.g. ownCloud's default
                // "Documents"/"Learn more about ownCloud" folders) shouldn't clutter the
                // album list. Providers that report a cheap assetCount (Immich) keep their
                // albums even if the media fetch failed transiently (assetCount > 0).
                if (updated.assetCount == 0 && media.isEmpty()) {
                    didEnrich = true // list shrank; publish the filtered result
                    continue
                }
                if (updated !== album) didEnrich = true
                enriched.add(updated)
            }
            _cloudAlbumMembers.value = membersByAlbum
            if (didEnrich) _cloudAlbumsFlow.value = enriched
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
            cloudAlbumId(it.providerType, it.serverConfigId, it.remoteId) == albumId
        }
    }

    private fun isUnsortedCloudAlbumId(albumId: Long): Boolean {
        return unsortedAlbumProviderType(albumId) != null
    }

    /**
     * Virtual "unsorted" album per connected cloud provider.
     * Contains all cached (non-trashed) cloud media that don't belong to any cloud album.
     */
    private val _unsortedCloudAlbumsFlow: StateFlow<List<Album>> = combine(
        _cloudCachedMedia,
        _cloudAlbumMemberIds
    ) { cachedMedia, memberIds ->
        if (cachedMedia.isEmpty()) return@combine emptyList()
        // Group cached media by provider (derive provider from URI authority)
        val byProvider = cachedMedia.groupBy { media ->
            media.getUri().authority ?: ""
        }.filterKeys { it.isNotEmpty() }
        byProvider.mapNotNull { (providerName, providerMedia) ->
            val providerType = try {
                ProviderType.valueOf(providerName)
            } catch (_: Exception) { return@mapNotNull null }
            val unsortedMedia = providerMedia.filter { media ->
                // cloudMemberKey keeps the remoteId whole — path-based providers carry slashes
                // in it, and truncating to a path segment would never match memberIds.
                cloudMemberKey(media.uri.toString())?.let { it !in memberIds } == true
            }
            if (unsortedMedia.isEmpty()) return@mapNotNull null
            val thumbUri = unsortedMedia.maxByOrNull { it.definedTimestamp }
                ?.getUri()?.let { uri ->
                    uri.buildUpon().clearQuery().appendQueryParameter("size", "thumbnail").build()
                } ?: Uri.EMPTY
            Album(
                id = unsortedCloudAlbumId(providerType),
                label = providerType.displayName,
                uri = thumbUri,
                pathToThumbnail = thumbUri.toString(),
                relativePath = "cloud/${providerType.name}",
                timestamp = unsortedMedia.maxOf { it.definedTimestamp },
                count = unsortedMedia.size.toLong(),
                size = 0L
            )
        }
    }.stateIn(appScope, sharingMethod, emptyList())

    // === End cloud integration ===

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _rawAlbumsFlow: StateFlow<Resource<List<Album>>?> =
        hasPermission.flatMapLatest { granted ->
            if (!granted) flowOf(null)
            else repository.getAlbums(mediaOrder = albumOrder)
                .map<Resource<List<Album>>, Resource<List<Album>>?> { it }
        }.stateIn(appScope, sharingMethod, null)

    private val albumThumbnails = repository.getAlbumThumbnails()
        .stateIn(
            scope = appScope,
            started = sharingMethod,
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

    private val albumSectionsDbFlow: StateFlow<List<AlbumSection>> =
        repository.getAllAlbumSections()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptyList()
            )

    private val albumSectionMembersDbFlow: StateFlow<List<AlbumSectionMember>> =
        repository.getAllSectionMembers()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
                initialValue = emptyList()
            )

    private val sectionsEnabled: StateFlow<Boolean> =
        repository.getSetting(Settings.Album.ALBUM_SECTIONS_ENABLED, false)
            .stateIn(appScope, sharingMethod, false)

    override val albumsFlow: StateFlow<AlbumState> = combine(
            _rawAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.getAlbums(${it?.data?.size ?: 0})").also { s -> StartupTracer.end(s) } },
            pinnedAlbumsFlow
                .onEach { StartupTracer.begin("albums.dep.pinned(${it.size})").also { s -> StartupTracer.end(s) } },
            _blacklistedAlbumsInternal
                .onEach { StartupTracer.begin("albums.dep.blacklisted(${it?.size ?: -1})").also { s -> StartupTracer.end(s) } },
            _lockedAlbumsInternal
                .onEach { StartupTracer.begin("albums.dep.locked(${it?.size ?: -1})").also { s -> StartupTracer.end(s) } },
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
            @Suppress("UNCHECKED_CAST")
            val lockedAlbums = values[3] as List<LockedAlbum>?
            val settings = values[4] as TimelineSettings?
            // Keep loading until both albums and blacklisted albums are loaded from their sources
            if (result == null || blacklistedAlbums == null || lockedAlbums == null || settings == null) {
                return@combine AlbumState()
            }
            val combineSpan = StartupTracer.begin("albums.combine_body(${result.data?.size ?: 0} albums)")
            @Suppress("UNCHECKED_CAST")
            val pinnedAlbums = values[1] as List<PinnedAlbum>
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
            val newOrder = settings.albumMediaOrder
            val thumbnailMap = thumbnails.associateBy { it.albumId }
            val localAlbums = newOrder.sortAlbums(result.data ?: emptyList()).map { album ->
                val thumbnail = thumbnailMap[album.id] ?: return@map album
                album.copy(uri = thumbnail.thumbnailUri)
            }
            val rawLocalAlbums = localAlbums.removeBlacklisted(blacklistedAlbums)
                .mapPinned(pinnedAlbums)
                .mapLocked(lockedAlbums)
            val subfolderMergedData = AlbumMergeResolver.mergeSubfolders(
                rawLocalAlbums,
                mergedSubfolders
            )
            val mergedLocalAlbums = if (shouldMerge) {
                AlbumMergeResolver.mergeByName(subfolderMergedData)
            } else subfolderMergedData
            // Hidden cloud albums leave the grid (mergedData) but stay in
            // albumsWithBlacklisted (data) so the ignored screen can still resolve them.
            val allRemoteAlbums = cloudAlbums.map { it.toAlbum() } + unsortedCloudAlbums
            val remoteAlbums = allRemoteAlbums.removeBlacklisted(blacklistedAlbums)
            val mergedData = mergedLocalAlbums + remoteAlbums
            val data = localAlbums + allRemoteAlbums

            val groupMemberAlbumIds = groupMembers.mapTo(HashSet(groupMembers.size)) { it.albumId }
            val membersByGroupId = groupMembers.groupBy { it.groupId }
            val albumGroups = groups.map { group ->
                val memberAlbumIds = membersByGroupId[group.id]
                    ?.mapTo(HashSet()) { it.albumId }
                    ?: emptySet()
                AlbumGroupWithAlbums(
                    group = group,
                    albums = mergedData.filter { album ->
                        album.id in memberAlbumIds || album.sourceAlbumIds.any { it in memberAlbumIds }
                    }
                )
            }
            val groupedMergedIds = mergedData
                .filter { album ->
                    album.id in groupMemberAlbumIds || album.sourceAlbumIds.any { it in groupMemberAlbumIds }
                }
                .mapTo(HashSet()) { it.id }

            val unpinnedUngroupedAll = mergedData.filter { album ->
                !album.isPinned && album.id !in groupedMergedIds &&
                    album.id !in collectionAlbumIds &&
                    album.sourceAlbumIds.none { it in collectionAlbumIds }
            }
            // Cloud albums live in their own dedicated section — keep them out of the
            // local unpinned list and out of section classification.
            val cloudAlbumList = unpinnedUngroupedAll
                .filter { it.relativePath.startsWith("cloud/") }
                .sortedBy { it.label }
            val unpinnedUngrouped = unpinnedUngroupedAll
                .filterNot { it.relativePath.startsWith("cloud/") }

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
                albumsCloud = cloudAlbumList,
                albumsPinned = mergedData.filter { album ->
                    album.isPinned && album.id !in collectionAlbumIds &&
                        album.sourceAlbumIds.none { it in collectionAlbumIds }
                }.sortedBy { it.label },
                albumGroups = albumGroups,
                albumSections = albumSections,
                collections = collections,
                isLoading = false,
                error = if (result is Resource.Error) result.message ?: "An error occurred" else ""
            ).also {
                StartupTracer.end(combineSpan)
            }
        }.stateIn(appScope, started = sharingMethod, AlbumState())

    /**
     * Media
     */
    override val timelineMediaFlow: SharedFlow<MediaState<Media.UriMedia>> =
        mediaFlow(-1L, null, triggerDatabaseUpdate = true)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun albumTimelineMediaFlow(
        albumId: Long,
        loadMode: AlbumMediaLoadMode
    ): Flow<MediaState<Media.UriMedia>> = when {
        MediaTypeAlbum.isMediaTypeAlbumId(albumId) -> mediaTypeAlbumTimelineMediaFlow(albumId)
        isUnsortedCloudAlbumId(albumId) -> unsortedCloudAlbumTimelineMediaFlow(albumId)
        isCloudAlbumId(albumId) -> cloudAlbumTimelineMediaFlow(albumId)
        else -> localAlbumTimelineMediaFlow(albumId, loadMode)
    }

    /**
     * Virtual media-type "album" (Videos/Photos/GIFs/Raw). Derived from the unified timeline
     * media so the card count in the Albums tab and the opened album stay consistent, filtered
     * by the type predicate. Grouping is disabled so every matching item is shown (e.g. RAW
     * files are not folded into a RAW/JPG group).
     */
    private fun mediaTypeAlbumTimelineMediaFlow(albumId: Long): Flow<MediaState<Media.UriMedia>> {
        val type = MediaTypeAlbum.fromAlbumId(albumId)
        return timelineMediaFlow
            .map { timelineState ->
                if (type == null) return@map MediaState<Media.UriMedia>()
                val filtered = timelineState.media.filter { type.matches(it) }
                mapMediaToItem(
                    data = filtered,
                    error = timelineState.error,
                    albumId = albumId,
                    groupSimilarMedia = false,
                    enabledGroupTypes = emptySet(),
                    defaultDateFormat = dateFormatsFlow.value.first,
                    extendedDateFormat = dateFormatsFlow.value.second,
                    weeklyDateFormat = dateFormatsFlow.value.third,
                    dateSource = timelineState.dateSource
                )
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun cloudAlbumTimelineMediaFlow(albumId: Long): Flow<MediaState<Media.UriMedia>> =
        _cloudAlbumsFlow
            .map { albums ->
                albums.find {
                    cloudAlbumId(it.providerType, it.serverConfigId, it.remoteId) == albumId
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { cloudAlbum ->
                if (cloudAlbum == null) {
                    flowOf(MediaState<Media.UriMedia>(error = "Cloud album not found"))
                } else {
                    combine(
                        cloudRepository.getAlbumMedia(
                            cloudAlbum.providerType,
                            cloudAlbum.serverConfigId,
                            cloudAlbum.remoteId
                        ),
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
                        val allMedia = when (resource) {
                            is Resource.Success -> resource.data?.map { it.toUriMedia() } ?: emptyList()
                            is Resource.Error -> resource.data?.map { it.toUriMedia() } ?: emptyList()
                        }
                        // NAS membership comes from one complete live index; other providers stay cache-filtered.
                        val media = if (usesLiveCloudAlbumMembership(cloudAlbum.providerType)) {
                            allMedia
                        } else {
                            val cachedIds = cachedNonTrashed.mapTo(HashSet()) { it.id }
                            if (cachedIds.isNotEmpty()) allMedia.filter { it.id in cachedIds } else allMedia
                        }
                        val error = if (resource is Resource.Error) resource.message ?: "" else ""
                        val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
                        val sorter = albumSort.toMediaOrder()
                        mapMediaToItem(
                            data = sorter.sortMedia(media),
                            error = error,
                            albumId = albumId,
                            groupByMonth = settings?.groupTimelineByMonth == true,
                            groupByYear = settings?.groupTimelineByYear == true,
                            defaultDateFormat = defaultDateFormat,
                            extendedDateFormat = extendedDateFormat,
                            weeklyDateFormat = weeklyDateFormat,
                            dateSource = albumSort.dateSource
                        )
                    }
                }
            }.withOptimisticMutations()

    private fun unsortedCloudAlbumTimelineMediaFlow(albumId: Long): Flow<MediaState<Media.UriMedia>> =
        combine(
            _cloudCachedMedia,
            _cloudAlbumMemberIds,
            settingsFlow,
            dateFormatsFlow,
            albumMediaSortFlow
        ) { cachedMedia, memberIds, settings, dateFormats, albumSort ->
            // Scope to the provider this unsorted album belongs to — otherwise opening one
            // provider's "unsorted" album would show the merged cloud media of every provider.
            val providerType = unsortedAlbumProviderType(albumId)
            val unsortedMedia = cachedMedia.filter { media ->
                val key = cloudMemberKey(media.uri.toString()) ?: return@filter false
                key.providerType == providerType && key !in memberIds
            }
            val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
            val sorter = albumSort.toMediaOrder()
            mapMediaToItem(
                data = sorter.sortMedia(unsortedMedia),
                error = "",
                albumId = albumId,
                groupByMonth = settings?.groupTimelineByMonth == true,
                groupByYear = settings?.groupTimelineByYear == true,
                defaultDateFormat = defaultDateFormat,
                extendedDateFormat = extendedDateFormat,
                weeklyDateFormat = weeklyDateFormat,
                dateSource = albumSort.dateSource
            )
        }.withOptimisticMutations()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun localAlbumTimelineMediaFlow(
        albumId: Long,
        loadMode: AlbumMediaLoadMode
    ): Flow<MediaState<Media.UriMedia>> =
        hasPermission.flatMapLatest { granted ->
            if (!granted) flowOf(MediaState())
            else {
                val mediaSource = combine(albumsFlow, mergedSubfolderAlbumsFlow) { state, configs ->
                    val subGallery = configs.any {
                        it.id == albumId &&
                            it.displayMode == MergedSubfolderAlbum.DISPLAY_MODE_SUB_GALLERY
                    }
                    if (subGallery) {
                        val config = configs.first { it.id == albumId }
                        state.albumsWithBlacklisted.firstOrNull {
                            it.volume == config.volume &&
                                it.relativePath.trim('/') == config.relativePath.trim('/')
                        }?.let { setOf(it.id) }
                            ?: albumId.takeUnless(AlbumMergeResolver::isVirtualAlbumId)?.let(::setOf)
                            ?: emptySet()
                    } else AlbumMergeResolver.resolveSourceAlbumIds(albumId, state.albums)
                        .filterNot(AlbumMergeResolver::isVirtualAlbumId)
                        .toSet()
                }.distinctUntilChanged()
                    .flatMapLatest {
                        repository.getMediaByAlbumIds(it, skipBatching = loadMode.skipBatching)
                    }
                combine(
                    mediaSource,
                    settingsFlow,
                    _blacklistedAlbumsInternal,
                    dateFormatsFlow,
                    albumMediaSortFlow,
                    groupSimilarMedia,
                    enabledGroupTypes
                ) { values ->
                    val mediaResult = values[0] as Resource<List<Media.UriMedia>>
                    val settings = values[1] as TimelineSettings?
                    @Suppress("UNCHECKED_CAST")
                    val blacklistedAlbums = values[2] as List<IgnoredAlbum>?
                    if (blacklistedAlbums == null || settings == null) {
                        return@combine MediaState()
                    }
                    @Suppress("UNCHECKED_CAST")
                    val dateFormats = values[3] as Triple<String, String, String>
                    val albumSort = values[4] as Settings.Album.LastSort
                    val shouldGroupSimilar = values[5] as Boolean
                    @Suppress("UNCHECKED_CAST")
                    val groupTypes = values[6] as Set<MediaGroupType>

                    val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats

                    val sorter = albumSort.toMediaOrder()

                    val filtered = (mediaResult.data ?: emptyList()).toMutableList().apply {
                        removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media, albumId) } }
                    }
                    mapMediaToItem(
                        data = sorter.sortMedia(filtered),
                        error = if (mediaResult is Resource.Error) mediaResult.message ?: "" else "",
                        albumId = albumId,
                        groupByMonth = settings.groupTimelineByMonth == true,
                        groupByYear = settings.groupTimelineByYear == true,
                        groupSimilarMedia = shouldGroupSimilar,
                        enabledGroupTypes = groupTypes,
                        defaultDateFormat = defaultDateFormat,
                        extendedDateFormat = extendedDateFormat,
                        weeklyDateFormat = weeklyDateFormat,
                        dateSource = albumSort.dateSource
                    )
                }
            }
        }.withOptimisticMutations()


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
            _blacklistedAlbumsInternal
                .onEach { StartupTracer.begin("$tag.dep.blacklistedAlbums(${it?.size ?: -1})").also { s -> StartupTracer.end(s) } },
            _lockedAlbumsInternal
                .onEach { StartupTracer.begin("$tag.dep.lockedAlbums(${it?.size ?: -1})").also { s -> StartupTracer.end(s) } },
            dateFormatsFlow
                .onEach { StartupTracer.begin("$tag.dep.dateFormats").also { s -> StartupTracer.end(s) } },
            albumMediaSortFlow
                .onEach { StartupTracer.begin("$tag.dep.albumMediaSort").also { s -> StartupTracer.end(s) } },
            timelineSortFlow
                .onEach { StartupTracer.begin("$tag.dep.timelineSort").also { s -> StartupTracer.end(s) } },
            groupSimilarMedia
                .onEach { StartupTracer.begin("$tag.dep.groupSimilar=$it").also { s -> StartupTracer.end(s) } },
            enabledGroupTypes
                .onEach { StartupTracer.begin("$tag.dep.enabledGroupTypes(${it.size})").also { s -> StartupTracer.end(s) } },
            cloudMediaSource
                .onEach { StartupTracer.begin("$tag.dep.cloudMedia(${it.size})").also { s -> StartupTracer.end(s) } },
            _cloudAlbumsFlow,
            _cloudAlbumMembers
        ) { values ->
            combineEmissionCount++
            val combineSpan = StartupTracer.begin("$tag.combine_body(#$combineEmissionCount)")
            val result = values[0] as Resource<List<Media.UriMedia>>
            val settings = values[1] as TimelineSettings?
            @Suppress("UNCHECKED_CAST")
            val blacklistedAlbums = values[2] as List<IgnoredAlbum>?
            @Suppress("UNCHECKED_CAST")
            val lockedAlbums = values[3] as List<LockedAlbum>?
            if (blacklistedAlbums == null || lockedAlbums == null || settings == null) {
                StartupTracer.end(combineSpan)
                return@combine MediaState()
            }
            @Suppress("UNCHECKED_CAST")
            val dateFormats = values[4] as Triple<String, String, String>
            val albumSort = values[5] as Settings.Album.LastSort
            val timelineSort = values[6] as Settings.Album.LastSort
            val shouldGroupSimilar = values[7] as Boolean
            @Suppress("UNCHECKED_CAST")
            val groupTypes = values[8] as Set<MediaGroupType>
            @Suppress("UNCHECKED_CAST")
            val cloudMedia = values[9] as List<Media.UriMedia>
            @Suppress("UNCHECKED_CAST")
            val cloudAlbums = values[10] as List<CloudAlbum>
            @Suppress("UNCHECKED_CAST")
            val cloudAlbumMembers = values[11] as Map<CloudAlbumMemberId, Set<CloudAlbumMemberId>>
            
            val (defaultDateFormat, extendedDateFormat, weeklyDateFormat) = dateFormats
            
            if (result is Resource.Error) {
                StartupTracer.end(combineSpan)
                return@combine MediaState(
                    error = result.message ?: "",
                    isLoading = false
                )
            }
            // Use custom sort for timeline/album media, default sort for favorites/trash
            val activeSort = when {
                isMainTimeline -> timelineSort
                target == null && albumId > 0 -> albumSort
                else -> null
            }
            val sorter = activeSort?.toMediaOrder() ?: MediaOrder.Default
            val dateSource = activeSort?.dateSource ?: TimelineDateSource.CAPTURE_TIME
            val lockedAlbumIds = lockedAlbums.mapTo(HashSet()) { it.id }
            val data = (result.data ?: emptyList()).toMutableList().apply {
                removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media, albumId) } }
                if (isMainTimeline) {
                    removeAll { media -> media.albumID in lockedAlbumIds }
                }
            }
            // Merge cloud media into the unified timeline. When CLOUD_LOCAL is enabled a cloud
            // copy that matches a local item (by base filename) is NOT added as its own tile;
            // instead it is recorded as a backup of the local item, surfaced via a small grid
            // indicator and the viewer's backup sheet. Cloud-only items still get their tile.
            // When the setting is off, cloud and local items are shown side by side (no skip).
            val cloudBackups: Map<Long, List<Media.UriMedia>>
            if ((isMainTimeline || isFavorites || isTrash) && cloudMedia.isNotEmpty()) {
                // Hidden cloud albums: IgnoredAlbum rows store the computed cloudAlbumId while
                // cloud media carries the constant albumID -500, so IgnoredAlbum.matchesMedia
                // can never match — membership is resolved through the per-album member sets
                // collected by refreshCloudData. Hidden items get no tile and no backup badge.
                val hidePredicate = hiddenCloudMediaPredicate(
                    blacklistedAlbums, cloudAlbums, cloudAlbumMembers
                )
                val visibleCloudMedia = hidePredicate?.let { hidden ->
                    cloudMedia.filterNot { m -> cloudMemberKey(m.uri.toString())?.let(hidden) == true }
                } ?: cloudMedia
                if (MediaGroupType.CLOUD_LOCAL in groupTypes) {
                    val localByBasename = HashMap<String, Long>(data.size)
                    for (m in data) {
                        if (!m.isCloud) localByBasename.putIfAbsent(m.cloudGroupKey, m.id)
                    }
                    val backups = HashMap<Long, MutableList<Media.UriMedia>>()
                    for (c in visibleCloudMedia) {
                        val localId = localByBasename[c.cloudGroupKey]
                        if (localId != null) {
                            backups.getOrPut(localId) { ArrayList(1) }.add(c)
                        } else {
                            data.add(c)
                        }
                    }
                    cloudBackups = backups
                } else {
                    data.addAll(visibleCloudMedia)
                    cloudBackups = emptyMap()
                }
            } else {
                cloudBackups = emptyMap()
            }
            val mapSpan = StartupTracer.begin("$tag.mapMediaToItem(${data.size} items)")
            val state = mapMediaToItem(
                data = sorter.sortMedia(data),
                error = result.message ?: "",
                albumId = albumId,
                groupByMonth = settings.groupTimelineByMonth == true,
                groupByYear = settings.groupTimelineByYear == true,
                groupSimilarMedia = shouldGroupSimilar,
                enabledGroupTypes = groupTypes,
                cloudBackups = cloudBackups,
                defaultDateFormat = defaultDateFormat,
                extendedDateFormat = extendedDateFormat,
                weeklyDateFormat = weeklyDateFormat,
                dateSource = dateSource
            ).copy(isPartial = (result as? Resource.Success)?.isPartial == true)
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
            startupGate.awaitFirstContent()
            rescanHistoryReady.await()
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
    }.withOptimisticMutations(
        inTrashView = target == Constants.Target.TARGET_TRASH,
        dropUnfavorited = target == Constants.Target.TARGET_FAVORITES
    ).shareIn(
        scope = appScope,
        started = sharingMethod,
        replay = 1
    )
    }

    /**
     * Media Metadata
     */
    override val metadataFlow: Flow<MediaMetadataState> = combine(
        repository.getMetadata(),
        smartScanDao.observeActiveRun()
    ) { metadata, run ->
        val isMetadataRun = run != null &&
            SmartScanPlan.runRequestsFeature(run, SmartScanFeature.METADATA) &&
            SmartScanPlan.shouldShowRun(run.userVisible, run.totalMedia)
        val progress = if (run == null || run.totalMedia <= 0) 0
        else (run.processedMedia * 100 / run.totalMedia).coerceIn(0, 100)
        MediaMetadataState(
            metadata = metadata,
            isLoading = isMetadataRun,
            isLoadingProgress = progress
        )
    }

    override fun locationBasedMedia(
        gpsLocationNameCity: String,
        gpsLocationNameCountry: String,
        latitude: Double?,
        longitude: Double?,
        additionalMediaIds: Flow<Set<Long>>,
    ): Flow<MediaState<Media.UriMedia>> {
        val matchingMediaIds = combine(
            repository.getMetadata(),
            cloudRepository.getCachedMedia(),
            additionalMediaIds,
        ) { metadata, cloudMedia, extraIds ->
            val localMediaIds = metadata
                .filter {
                    matchesLocationName(
                        candidateCity = it.gpsLocationNameCity,
                        candidateCountry = it.gpsLocationNameCountry,
                        city = gpsLocationNameCity,
                        country = gpsLocationNameCountry,
                    ) || matchesLocationCoordinates(
                        candidateLatitude = it.gpsLatitude,
                        candidateLongitude = it.gpsLongitude,
                        latitude = latitude,
                        longitude = longitude,
                    )
                }
                .mapTo(HashSet()) { it.mediaId }
            matchingLocationMediaIds(
                localMediaIds = localMediaIds,
                cloudMedia = cloudMedia,
                city = gpsLocationNameCity,
                country = gpsLocationNameCountry,
                latitude = latitude,
                longitude = longitude,
                additionalMediaIds = extraIds,
            )
        }
        return combine(
            matchingMediaIds,
            timelineMediaFlow,
            groupSimilarMedia,
            enabledGroupTypes,
        ) { mediaIds, timelineState, shouldGroupSimilar, groupTypes ->
            val expandedMediaIds = expandLocationMediaIds(
                matchingMediaIds = mediaIds,
                cloudBackupIdsByLocalId = timelineState.cloudBackups.mapValues { (_, copies) ->
                    copies.map { it.id }
                },
            )
            val filteredMedia = timelineState.media.filter { it.id in expandedMediaIds }
            val filteredIds = filteredMedia.mapTo(HashSet(filteredMedia.size)) { it.id }
            mapMediaToItem(
                data = filteredMedia,
                error = timelineState.error,
                albumId = -1L,
                groupSimilarMedia = shouldGroupSimilar,
                enabledGroupTypes = groupTypes,
                cloudBackups = timelineState.cloudBackups.filterKeys { it in filteredIds },
                defaultDateFormat = dateFormatsFlow.value.first,
                extendedDateFormat = dateFormatsFlow.value.second,
                weeklyDateFormat = dateFormatsFlow.value.third
            ).copy(isLoading = timelineState.isLoading)
        }
    }

    private fun <T> Flow<T>.afterFirstContent(): Flow<T> = flow {
        startupGate.awaitFirstContent()
        emitAll(this@afterFirstContent)
    }

    private val locationsAndGeoMediaFlow: SharedFlow<Pair<List<LocationMedia>, List<GeoMedia>>> = combine(
        repository.getMetadata(),
        timelineMediaFlow.filter { !it.isLoading && !it.isPartial && it.error.isEmpty() }
    ) { metadata, timelineState ->
        val mediaById = HashMap<Long, Media.UriMedia>(timelineState.media.size)
        for (m in timelineState.media) { mediaById[m.id] = m }
        for (copies in timelineState.cloudBackups.values) {
            for (m in copies) { mediaById.putIfAbsent(m.id, m) }
        }
        val metadataById = metadata.associateBy { it.mediaId }

        val locationGroupMap = LinkedHashMap<String, Media.UriMedia>()
        val geoList = ArrayList<GeoMedia>(metadata.size / 2)

        for (meta in metadata) {
            val media = mediaById[meta.mediaId] ?: continue

            if (!meta.gpsLocationNameCity.isNullOrBlank() || !meta.gpsLocationNameCountry.isNullOrBlank()) {
                val key = listOfNotNull(
                    meta.gpsLocationNameCity?.takeIf(String::isNotBlank),
                    meta.gpsLocationNameCountry?.takeIf(String::isNotBlank),
                ).joinToString(", ")
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
            .map { (location, media) ->
                val mediaMetadata = metadataById[media.id]
                LocationMedia(
                    media = media,
                    location = location,
                    city = mediaMetadata?.gpsLocationNameCity,
                    country = mediaMetadata?.gpsLocationNameCountry,
                    latitude = mediaMetadata?.gpsLatitude,
                    longitude = mediaMetadata?.gpsLongitude,
                )
            }
            .sortedBy { it.location }

        Pair(locations, geoList.sortedByDescending { it.media.definedTimestamp })
    }.afterFirstContent().shareIn(appScope, sharingMethod, replay = 1)

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

            val sorter = albumSort.toMediaOrder()

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
                weeklyDateFormat = weeklyDateFormat,
                dateSource = albumSort.dateSource
            )
        }.withOptimisticMutations()
            .stateIn(appScope, sharingMethod, MediaState())

    /**
     * Search
     */
    override val imageEmbeddingsFlow: StateFlow<List<ImageEmbedding>> =
        repository.getImageEmbeddings()
            .stateIn(
                scope = appScope,
                started = sharingMethod,
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

    /**
     * Remove cloud duplicates when a local counterpart with the same
     * [cloudGroupKey] exists. Keeps local items and cloud-only items.
     */
    private fun List<Media.UriMedia>.deduplicateCloudLocal(): List<Media.UriMedia> {
        if (none { it.isCloud }) return this
        val localKeys = HashSet<String>()
        for (m in this) {
            if (!m.isCloud) localKeys.add(m.cloudGroupKey)
        }
        if (localKeys.isEmpty()) return this
        return filter { !it.isCloud || it.cloudGroupKey !in localKeys }
    }

}