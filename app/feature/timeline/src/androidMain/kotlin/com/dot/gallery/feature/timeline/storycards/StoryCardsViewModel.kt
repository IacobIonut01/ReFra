/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.timeline.storycards

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.MemoryInfo
import com.dot.gallery.cloud.core.capabilities.MemoriesCapableProvider
import com.dot.gallery.cloud.core.stableIdHash
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Resource
import com.dot.gallery.core.Settings
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.feature.timeline.mergeReadyStoryCards
import com.dot.gallery.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.min
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Album
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.core.startup.StartupWorkGate
import com.dot.gallery.core.logging.printError
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.feature.timeline.generated.resources.Res
import com.dot.gallery.feature.timeline.generated.resources.favorites
import com.dot.gallery.feature.timeline.generated.resources.story_highlight_count
import com.dot.gallery.feature.timeline.generated.resources.story_item_count
import com.dot.gallery.feature.timeline.generated.resources.story_this_year
import com.dot.gallery.feature.timeline.generated.resources.story_years_ago
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

fun createStoryViewerSnapshot(
    cards: List<StoryCard>?,
    initialCardId: Long,
): StoryViewerSnapshot? = cards
    ?.takeIf { list -> list.any { it.id == initialCardId } }
    ?.let { StoryViewerSnapshot(cards = it.toList(), initialCardId = initialCardId) }

private const val MAX_STORY_ITEMS = 20
private const val STORY_DATE_REFERENCE_YEAR = 2000
private const val STORY_DATE_REFERENCE_DAYS = 366L

fun storyCardStableId(type: StoryCardType, sourceKey: String): Long =
    stableIdHash("story-card/${type.name}/$sourceKey")

fun annualDayDistance(first: MonthDay, second: MonthDay): Long {
    val firstDate = first.atYear(STORY_DATE_REFERENCE_YEAR)
    val secondDate = second.atYear(STORY_DATE_REFERENCE_YEAR)
    val distance = ChronoUnit.DAYS.between(firstDate, secondDate).absoluteValue
    return min(distance, STORY_DATE_REFERENCE_DAYS - distance)
}

class StoryCardsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val distributor: MediaDistributor,
    private val providerRegistry: ProviderRegistry,
    private val startupGate: StartupWorkGate,
    private val context: Context
) : ViewModel() {

    private val configFlow = Settings.Misc.getStoryCardsConfig(context)

    private fun <T> Flow<T>.afterFirstContent(): Flow<T> = flow {
        startupGate.awaitFirstContent()
        emitAll(this@afterFirstContent)
    }

    private val timelineMediaState = distributor.timelineMediaFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MediaState<UriMedia>(),
        )

    private val timelineMedia = timelineMediaState
        .map { it.media }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val albumsState = distributor.albumsFlow.afterFirstContent()

    private val favoritesMedia = distributor.favoritesMediaFlow
        .afterFirstContent()
        .map { it.media }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val metadataFlow = repository.getMetadata()
        .afterFirstContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val topCategories = repository.getTopCategories(5)
        .afterFirstContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val storyCards: StateFlow<List<StoryCard>?> = combine(
        configFlow,
        timelineMediaState,
        albumsState,
        favoritesMedia,
        metadataFlow,
    ) { config, mediaState, albums, favorites, metadata ->
        if (mediaState.isLoading) return@combine null
        if (albums.isLoading && StoryCardType.ALBUMS in config.activeTypes) return@combine null
        val media = mediaState.media
        if (!config.enabled || media.isEmpty()) return@combine emptyList()

        val cards = mutableListOf<StoryCard>()
        for (type in config.activeTypes) {
            when (type) {
                StoryCardType.MEMORIES -> {
                    cards.addAll(buildMemoryCards(media))
                }
                StoryCardType.ALBUMS -> {
                    cards.addAll(buildAlbumCards(media, albums.albums))
                }
                StoryCardType.FAVORITES -> {
                    if (favorites.isNotEmpty()) {
                        cards.add(buildFavoritesCard(favorites))
                    }
                }
                StoryCardType.LOCATIONS -> {
                    cards.addAll(buildLocationCards(media, metadata))
                }
                StoryCardType.CATEGORIES -> {
                    // Categories are handled in the separate combine below
                }
                StoryCardType.CLOUD_MEMORIES -> {
                    // Cloud memories are handled in the separate _cloudMemoryCards flow
                }
            }
        }
        cards
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _cloudMemoryCards = MutableStateFlow<List<StoryCard>>(emptyList())

    init {
        loadCloudMemories()
    }

    private fun loadCloudMemories() {
        viewModelScope.launch {
            combine(configFlow, providerRegistry.connectionStates) { config, states ->
                config to states
            }.distinctUntilChanged().collectLatest { (config, _) ->
                if (!config.enabled || StoryCardType.CLOUD_MEMORIES in config.disabledTypes) {
                    _cloudMemoryCards.value = emptyList()
                    return@collectLatest
                }
                startupGate.awaitFirstContent()
                val providers = providerRegistry.getByCapability<MemoriesCapableProvider>()
                _cloudMemoryCards.value = coroutineScope {
                    providers.map { provider ->
                        async {
                            runCatching { provider.getMemories().first() }
                                .fold(
                                    onSuccess = { resource ->
                                        if (resource is Resource.Error) {
                                            printError(
                                                "Story memories failed for ${provider.providerType}: " +
                                                    resource.message.orEmpty()
                                            )
                                        }
                                        buildCloudMemoryCards(resource.data.orEmpty())
                                    },
                                    onFailure = { error ->
                                        printError(
                                            "Story memories failed for ${provider.providerType}: " +
                                                error.message.orEmpty()
                                        )
                                        emptyList()
                                    },
                                )
                        }
                    }.awaitAll().flatten().distinctBy { it.id }
                }
            }
        }
    }

    private suspend fun buildCloudMemoryCards(memories: List<MemoryInfo>): List<StoryCard> {
        val currentYear = LocalDate.now().year
        return memories.filter { it.media.isNotEmpty() }.map { memory ->
            val yearsAgo = currentYear - memory.year
            val storyMedia = memory.media.take(MAX_STORY_ITEMS)
            StoryCard(
                id = storyCardStableId(StoryCardType.CLOUD_MEMORIES, memory.accountKey),
                type = StoryCardType.CLOUD_MEMORIES,
                title = if (yearsAgo > 0) {
                    getPluralString(Res.plurals.story_years_ago, yearsAgo, yearsAgo)
                } else {
                    getString(Res.string.story_this_year)
                },
                subtitle = if (memory.year > 0) memory.year.toString() else null,
                thumbnailMedia = storyMedia.firstOrNull(),
                mediaList = storyMedia,
                year = memory.year
            )
        }
    }

    val categoryCards: StateFlow<List<StoryCard>> = combine(
        configFlow,
        topCategories,
        timelineMedia
    ) { config, categories, media ->
        if (!config.enabled || StoryCardType.CATEGORIES in config.disabledTypes) {
            return@combine emptyList()
        }
        val mediaMap = media.associateBy { it.id }
        categories.mapNotNull { cat ->
            val mediaIds = repository.getMediaIdsInCategoryAsync(cat.id)
            val categoryMedia = mediaIds.mapNotNull { mediaMap[it] }
                .sortedByDescending { it.definedTimestamp }
            if (categoryMedia.isEmpty()) return@mapNotNull null
            val storyMedia = categoryMedia.take(MAX_STORY_ITEMS)
            StoryCard(
                id = storyCardStableId(StoryCardType.CATEGORIES, cat.id.toString()),
                type = StoryCardType.CATEGORIES,
                title = cat.name,
                subtitle = storyCountSubtitle(categoryMedia.size, storyMedia.size),
                thumbnailMedia = storyMedia.firstOrNull(),
                mediaList = storyMedia,
                categoryId = cat.id
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCards: StateFlow<List<StoryCard>?> = combine(
        configFlow,
        storyCards,
        categoryCards,
        _cloudMemoryCards,
        timelineMediaState
    ) { config, cards, catCards, cloudCards, mediaState ->
        mergeReadyStoryCards(
            config = config,
            isTimelineLoading = mediaState.isLoading,
            hasMedia = mediaState.media.isNotEmpty(),
            localCards = cards,
            categoryCards = catCards,
            cloudCards = cloudCards,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _viewerSnapshot = MutableStateFlow<StoryViewerSnapshot?>(null)
    val viewerSnapshot = _viewerSnapshot.asStateFlow()

    fun prepareViewer(initialCardId: Long): Boolean {
        val snapshot = createStoryViewerSnapshot(allCards.value, initialCardId) ?: return false
        _viewerSnapshot.value = snapshot
        return true
    }

    fun clearViewerSnapshot() {
        _viewerSnapshot.value = null
    }

    private var lastMetadataFetchId: Long? = null

    fun ensureMetadataAvailable(media: Media?) {
        if (media == null) return
        if (media.id == lastMetadataFetchId) return
        val existing = metadataFlow.value.firstOrNull { it.mediaId == media.id }
        if (existing != null && (existing.imageWidth > 0 || existing.manufacturerName != null)) {
            return
        }
        lastMetadataFetchId = media.id
        viewModelScope.launch(Dispatchers.IO) {
            repository.collectMetadataFor(media)
        }
    }

    private suspend fun storyCountSubtitle(total: Int, shown: Int): String =
        if (shown < total) {
            getString(Res.string.story_highlight_count, shown, total)
        } else {
            getPluralString(Res.plurals.story_item_count, total, total)
        }

    private suspend fun buildMemoryCards(media: List<UriMedia>): List<StoryCard> {
        val today = LocalDate.now()
        val todayMonthDay = MonthDay.from(today)
        val zoneId = ZoneId.systemDefault()
        val datedMedia = media.map { item ->
            item to Instant.ofEpochSecond(item.definedTimestamp).atZone(zoneId).toLocalDate()
        }.filter { (_, date) -> date.year < today.year }

        // Exact day match first
        var memories = datedMedia.filter { (_, date) -> MonthDay.from(date) == todayMonthDay }

        // Fallback: ±3 day window if fewer than 3 results
        if (memories.size < 3) {
            memories = datedMedia.filter { (_, date) ->
                annualDayDistance(MonthDay.from(date), todayMonthDay) <= 3
            }
        }

        if (memories.isEmpty()) return emptyList()

        // Group by year
        val byYear = memories.groupBy { (_, date) -> date.year }
            .toSortedMap(compareByDescending { it })

        return byYear.map { (year, yearMedia) ->
            val yearsAgo = today.year - year
            val storyMedia = yearMedia.map { it.first }
                .sortedByDescending { it.definedTimestamp }
                .take(MAX_STORY_ITEMS)
            StoryCard(
                id = storyCardStableId(StoryCardType.MEMORIES, year.toString()),
                type = StoryCardType.MEMORIES,
                title = getPluralString(Res.plurals.story_years_ago, yearsAgo, yearsAgo),
                subtitle = year.toString(),
                thumbnailMedia = storyMedia.firstOrNull(),
                mediaList = storyMedia,
                year = year
            )
        }
    }

    private suspend fun buildAlbumCards(
        media: List<UriMedia>,
        albums: List<com.dot.gallery.domain.model.Album>
    ): List<StoryCard> {
        // Pick recent/pinned albums with content, limit to 5
        val highlighted = albums
            .filter { it.count > 0 && !it.isLocked }
            .sortedWith(
                compareByDescending<com.dot.gallery.domain.model.Album> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
            .take(5)

        val mediaByAlbum = media.groupBy { it.albumID }

        return highlighted.mapNotNull { album ->
            val albumMedia = mediaByAlbum[album.id] ?: return@mapNotNull null
            val sorted = albumMedia.sortedByDescending { it.definedTimestamp }
            val storyMedia = sorted.take(MAX_STORY_ITEMS)
            StoryCard(
                id = storyCardStableId(StoryCardType.ALBUMS, album.id.toString()),
                type = StoryCardType.ALBUMS,
                title = album.label,
                subtitle = storyCountSubtitle(sorted.size, storyMedia.size),
                thumbnailMedia = storyMedia.firstOrNull(),
                mediaList = storyMedia,
                albumId = album.id
            )
        }
    }

    private suspend fun buildFavoritesCard(favorites: List<UriMedia>): StoryCard {
        val storyMedia = favorites.take(MAX_STORY_ITEMS)
        return StoryCard(
            id = storyCardStableId(StoryCardType.FAVORITES, "favorites"),
            type = StoryCardType.FAVORITES,
            title = getString(Res.string.favorites),
            subtitle = storyCountSubtitle(favorites.size, storyMedia.size),
            thumbnailMedia = storyMedia.firstOrNull(),
            mediaList = storyMedia
        )
    }

    private suspend fun buildLocationCards(
        media: List<UriMedia>,
        metadata: List<MediaMetadata>
    ): List<StoryCard> {
        val mediaById = media.associateBy { it.id }
        // Group metadata entries by "city, country", collecting all matching media
        val locationGroups = LinkedHashMap<String, MutableList<UriMedia>>()
        for (meta in metadata) {
            if (meta.gpsLocationNameCity == null || meta.gpsLocationNameCountry == null) continue
            val m = mediaById[meta.mediaId] ?: continue
            val key = "${meta.gpsLocationNameCity}, ${meta.gpsLocationNameCountry}"
            locationGroups.getOrPut(key) { mutableListOf() }.add(m)
        }
        // Sort groups by count descending, take top 5
        return locationGroups.entries
            .sortedByDescending { it.value.size }
            .take(5)
            .mapNotNull { (location, locationMedia) ->
                val sorted = locationMedia.sortedByDescending { it.definedTimestamp }
                val storyMedia = sorted.take(MAX_STORY_ITEMS)
                val city = location.substringBefore(",").trim()
                val country = location.substringAfterLast(", ").trim()
                StoryCard(
                    id = storyCardStableId(StoryCardType.LOCATIONS, location),
                    type = StoryCardType.LOCATIONS,
                    title = location,
                    subtitle = storyCountSubtitle(sorted.size, storyMedia.size),
                    thumbnailMedia = storyMedia.firstOrNull(),
                    mediaList = storyMedia,
                    locationCity = city,
                    locationCountry = country
                )
            }
    }
}
