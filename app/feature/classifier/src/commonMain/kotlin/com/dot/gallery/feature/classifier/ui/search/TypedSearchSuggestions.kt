/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.runtime.Immutable
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.SearchHistory
import com.dot.gallery.domain.model.locationIdentityKey
import com.dot.gallery.feature.classifier.ui.components.CategoryCarouselItem

/** A library entity offered while the user types; opening one skips the search entirely. */
@Immutable
sealed interface TypedSearchSuggestion {
    val key: String
    val title: String

    data class Category(val item: CategoryCarouselItem) : TypedSearchSuggestion {
        override val key: String get() = "category_${item.id}"
        override val title: String get() = item.name
    }

    data class Place(val location: LocationMedia) : TypedSearchSuggestion {
        override val key: String get() = "place_${location.locationIdentityKey()}"
        override val title: String get() = location.location
    }

    data class Album(val album: com.dot.gallery.domain.model.Album) : TypedSearchSuggestion {
        override val key: String get() = "album_${album.id}"
        override val title: String get() = album.label
    }
}

private val WORD_SEPARATORS = charArrayOf(' ', '-', '_', ',', '.', '/', '(', ')')

/**
 * How well [name] matches [query]: 0 for a prefix of the whole name, 1 for a prefix of a later
 * word, 2 for any other substring, `null` for no match. Lower ranks sort first.
 */
fun suggestionMatchRank(name: String, query: String): Int? {
    val needle = query.trim()
    if (needle.isEmpty() || name.isBlank()) return null
    return when {
        name.startsWith(needle, ignoreCase = true) -> 0
        name.split(*WORD_SEPARATORS).any { it.startsWith(needle, ignoreCase = true) } -> 1
        name.contains(needle, ignoreCase = true) -> 2
        else -> null
    }
}

private fun <T> List<T>.bestMatches(query: String, limit: Int, name: (T) -> String): List<T> =
    mapNotNull { item -> suggestionMatchRank(name(item), query)?.let { rank -> rank to item } }
        .sortedBy { it.first }
        .map { it.second }
        .distinctBy { name(it).lowercase() }
        .take(limit)

/**
 * Categories, places and albums whose names match [query], each kind capped at [limitPerKind]
 * and ranked by [suggestionMatchRank]. Locked albums are never suggested.
 */
fun matchTypedSuggestions(
    query: String,
    categories: List<CategoryCarouselItem>,
    locations: List<LocationMedia>,
    albums: List<Album>,
    limitPerKind: Int = 3,
): List<TypedSearchSuggestion> {
    if (query.isBlank()) return emptyList()
    return categories.bestMatches(query, limitPerKind) { it.name }
        .map(TypedSearchSuggestion::Category) +
        locations.bestMatches(query, limitPerKind) { it.location }
            .map(TypedSearchSuggestion::Place) +
        albums.filterNot { it.isLocked }.bestMatches(query, limitPerKind) { it.label }
            .map(TypedSearchSuggestion::Album)
}

/** Past text searches matching [query], newest first, without an exact repeat of the query. */
fun matchingHistory(query: String, history: List<SearchHistory>, limit: Int = 3): List<SearchHistory> {
    if (query.isBlank()) return emptyList()
    return history
        .filter { it.mediaId == null && !it.query.equals(query.trim(), ignoreCase = true) }
        .sortedByDescending { it.timestamp }
        .bestMatches(query, limit) { it.query }
}
