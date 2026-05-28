/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.storycards

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.StoryCard
import com.dot.gallery.feature_node.domain.model.StoryCardType
import com.dot.gallery.feature_node.domain.model.StoryCardsConfig
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class StoryCardsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val distributor: MediaDistributor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val configFlow = Settings.Misc.getStoryCardsConfig(context)

    private val timelineMedia = distributor.timelineMediaFlow
        .map { it.media }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val albumsState = distributor.albumsFlow

    private val favoritesMedia = distributor.favoritesMediaFlow
        .map { it.media }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val metadataFlow = repository.getMetadata()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val topCategories = repository.getTopCategories(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val storyCards: StateFlow<List<StoryCard>> = combine(
        configFlow,
        timelineMedia,
        albumsState,
        favoritesMedia,
        metadataFlow,
    ) { config, media, albums, favorites, metadata ->
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
            }
        }
        cards
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            StoryCard(
                id = 3_000_000L + cat.id,
                type = StoryCardType.CATEGORIES,
                title = cat.name,
                subtitle = "${cat.mediaCount} items",
                thumbnailMedia = categoryMedia.firstOrNull(),
                mediaList = categoryMedia.take(20),
                categoryId = cat.id
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCards: StateFlow<List<StoryCard>?> = combine(
        configFlow,
        storyCards,
        categoryCards,
        timelineMedia
    ) { config, cards, catCards, media ->
        // null = still loading (timeline hasn't loaded yet)
        if (media.isEmpty()) return@combine null
        if (!config.enabled) return@combine emptyList()
        val merged = mutableListOf<StoryCard>()
        val orderedTypes = config.activeTypes
        for (type in orderedTypes) {
            if (type == StoryCardType.CATEGORIES) {
                merged.addAll(catCards)
            } else {
                merged.addAll(cards.filter { it.type == type })
            }
        }
        merged
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildMemoryCards(media: List<Media.UriMedia>): List<StoryCard> {
        val today = Calendar.getInstance()
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_MONTH)
        val currentYear = today.get(Calendar.YEAR)

        val cal = Calendar.getInstance()

        // Exact day match first
        var memories = media.filter { m ->
            cal.timeInMillis = m.definedTimestamp * 1000L
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)
            year < currentYear && month == todayMonth && day == todayDay
        }

        // Fallback: ±3 day window if fewer than 3 results
        if (memories.size < 3) {
            memories = media.filter { m ->
                cal.timeInMillis = m.definedTimestamp * 1000L
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                val day = cal.get(Calendar.DAY_OF_MONTH)
                if (year >= currentYear) return@filter false

                val mediaCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                val todayCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, todayMonth)
                    set(Calendar.DAY_OF_MONTH, todayDay)
                }
                val diffMs = kotlin.math.abs(mediaCal.timeInMillis - todayCal.timeInMillis)
                val diffDays = diffMs / (1000 * 60 * 60 * 24)
                diffDays <= 3
            }
        }

        if (memories.isEmpty()) return emptyList()

        // Group by year
        val byYear = memories.groupBy { m ->
            cal.timeInMillis = m.definedTimestamp * 1000L
            cal.get(Calendar.YEAR)
        }.toSortedMap(compareByDescending { it })

        return byYear.map { (year, yearMedia) ->
            val yearsAgo = currentYear - year
            StoryCard(
                id = 1_000_000L + year.toLong(),
                type = StoryCardType.MEMORIES,
                title = "$yearsAgo ${if (yearsAgo == 1) "year" else "years"} ago",
                subtitle = "$year",
                thumbnailMedia = yearMedia.firstOrNull(),
                mediaList = yearMedia.sortedByDescending { it.definedTimestamp },
                year = year
            )
        }
    }

    private fun buildAlbumCards(
        media: List<Media.UriMedia>,
        albums: List<com.dot.gallery.feature_node.domain.model.Album>
    ): List<StoryCard> {
        // Pick recent/pinned albums with content, limit to 5
        val highlighted = albums
            .filter { it.count > 0 && !it.isLocked }
            .sortedWith(
                compareByDescending<com.dot.gallery.feature_node.domain.model.Album> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
            .take(5)

        val mediaByAlbum = media.groupBy { it.albumID }

        return highlighted.mapNotNull { album ->
            val albumMedia = mediaByAlbum[album.id] ?: return@mapNotNull null
            val thumbnail = albumMedia.maxByOrNull { it.definedTimestamp }
            StoryCard(
                id = 2_000_000L + album.id,
                type = StoryCardType.ALBUMS,
                title = album.label,
                subtitle = "${album.count} items",
                thumbnailMedia = thumbnail,
                mediaList = albumMedia.sortedByDescending { it.definedTimestamp }.take(20),
                albumId = album.id
            )
        }
    }

    private fun buildFavoritesCard(favorites: List<Media.UriMedia>): StoryCard {
        return StoryCard(
            id = 4_000_000L,
            type = StoryCardType.FAVORITES,
            title = "Favorites",
            subtitle = "${favorites.size} items",
            thumbnailMedia = favorites.firstOrNull(),
            mediaList = favorites.take(20)
        )
    }

    private fun buildLocationCards(
        media: List<Media.UriMedia>,
        metadata: List<MediaMetadata>
    ): List<StoryCard> {
        val mediaById = media.associateBy { it.id }
        // Group metadata entries by "city, country", collecting all matching media
        val locationGroups = LinkedHashMap<String, MutableList<Media.UriMedia>>()
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
                val city = location.substringBefore(",").trim()
                val country = location.substringAfterLast(", ").trim()
                StoryCard(
                    id = 5_000_000L + (location.hashCode().toLong() and 0xFFFFFFL),
                    type = StoryCardType.LOCATIONS,
                    title = location,
                    subtitle = "${locationMedia.size} items",
                    thumbnailMedia = sorted.firstOrNull(),
                    mediaList = sorted.take(20),
                    locationCity = city,
                    locationCountry = country
                )
            }
    }
}
