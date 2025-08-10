package com.dot.gallery.core

import androidx.compose.runtime.compositionLocalOf
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.Settings.Misc.DEFAULT_DATE_FORMAT
import com.dot.gallery.core.Settings.Misc.EXTENDED_DATE_FORMAT
import com.dot.gallery.core.Settings.Misc.WEEKLY_DATE_FORMAT
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
    private val repository: MediaRepository,
    private val eventHandler: EventHandler,
    workManager: WorkManager
) : MediaDistributor {
    
    private val sharingMethod = SharingStarted.WhileSubscribed(5_000L)
    private val prioritySharingMethod = SharingStarted.Eagerly

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    override val albumsFlow: StateFlow<AlbumState> = combine(
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
    }.stateIn(appScope, started = prioritySharingMethod, AlbumState())

    /**
     * Media
     */
    override val timelineMediaFlow: SharedFlow<MediaState<Media.UriMedia>> =
        mediaFlow(-1L, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val albumsTimelinesMediaFlow: StateFlow<Map<Long, MediaState<Media.UriMedia>>> =
        albumsFlow.flatMapLatest { albumState ->
            val albums = albumState.albums
            if (albums.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    albums.map { album ->
                        mediaFlow(album.id, null)
                            .map { album.id to it }
                    }
                ) { states -> states.toMap() }
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
    private fun mediaFlow(albumId: Long, target: String?) = combine(
        repository.mediaFlow(albumId, target),
        settingsFlow,
        blacklistedAlbumsFlow,
        dateFormatsFlow,
        hasPermission
    ) { result, settings, blacklistedAlbums, (defaultDateFormat, extendedDateFormat, weeklyDateFormat), hasPermission ->
        if (!hasPermission) return@combine MediaState(
            error = "No permission to access media",
            isLoading = false
        )
        if (result is Resource.Error) return@combine MediaState(
            error = result.message ?: "",
            isLoading = false
        )
        val sorter = MediaOrder.Default
        val data = (result.data ?: emptyList()).toMutableList().apply {
            removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media) } }
        }
        mapMediaToItem(
            data = sorter.sortMedia(data),
            error = result.message ?: "",
            albumId = albumId,
            groupByMonth = settings?.groupTimelineByMonth == true,
            defaultDateFormat = defaultDateFormat,
            extendedDateFormat = extendedDateFormat,
            weeklyDateFormat = weeklyDateFormat
        )
    }.mapLatest {
        eventHandler.pushEvent(UIEvent.UpdateDatabase)
        it
    }.shareIn(
        scope = appScope,
        started = sharingMethod,
        replay = 1
    )

    /**
     * Media Metadata
     */
    override val metadataFlow: StateFlow<MediaMetadataState> = combine(
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
    }.stateIn(appScope, started = sharingMethod, MediaMetadataState())

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