/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.search

import com.dot.gallery.domain.model.EncryptedMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.SearchHistory
import com.dot.gallery.feature.classifier.ui.components.CategoryCarouselItem
import com.dot.gallery.feature.classifier.ui.search.SearchPhase
import com.dot.gallery.feature.classifier.ui.search.SearchResultsState
import com.dot.gallery.feature.classifier.ui.search.TypedSearchSuggestion
import com.dot.gallery.feature.classifier.ui.search.matchTypedSuggestions
import com.dot.gallery.feature.classifier.ui.search.matchingHistory
import com.dot.gallery.feature.classifier.ui.search.searchPhase
import com.dot.gallery.feature.classifier.ui.search.suggestionMatchRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionsTest {

    @Test
    fun wholeNamePrefixRanksAheadOfWordPrefixAndSubstring() {
        assertEquals(0, suggestionMatchRank("Beaches", "bea"))
        assertEquals(1, suggestionMatchRank("Bear Lake, Colorado", "colo"))
        assertEquals(2, suggestionMatchRank("Abeam", "bea"))
        assertNull(suggestionMatchRank("Forest", "bea"))
        assertNull(suggestionMatchRank("Beaches", "  "))
    }

    @Test
    fun typedSuggestionsGroupKindsInOrderAndRankWithinEach() {
        val suggestions = matchTypedSuggestions(
            query = "bea",
            categories = listOf(category(1, "Abeam"), category(2, "Beaches"), category(3, "Forests")),
            locations = listOf(location(10, "Bear Lake, Colorado")),
            albums = emptyList(),
        )

        assertEquals(
            listOf("Beaches", "Abeam", "Bear Lake, Colorado"),
            suggestions.map { it.title }
        )
        assertTrue(suggestions[0] is TypedSearchSuggestion.Category)
        assertTrue(suggestions[2] is TypedSearchSuggestion.Place)
    }

    @Test
    fun eachKindIsCappedAndDuplicateNamesCollapse() {
        val suggestions = matchTypedSuggestions(
            query = "lake",
            categories = emptyList(),
            locations = listOf(
                location(1, "Lake Louise"),
                location(2, "Lake Louise"),
                location(3, "Lake Tahoe"),
                location(4, "Lake Como"),
                location(5, "Lake Bled"),
            ),
            albums = emptyList(),
            limitPerKind = 3,
        )

        assertEquals(listOf("Lake Louise", "Lake Tahoe", "Lake Como"), suggestions.map { it.title })
    }

    @Test
    fun blankQueryHasNoTypedSuggestions() {
        assertTrue(
            matchTypedSuggestions("", listOf(category(1, "Beaches")), emptyList(), emptyList()).isEmpty()
        )
    }

    @Test
    fun historyMatchesSkipImageEntriesAndTheExactQuery() {
        val history = listOf(
            SearchHistory(timestamp = 1, query = "beach"),
            SearchHistory(timestamp = 3, query = "beach at sunset"),
            SearchHistory(timestamp = 2, query = "beach photo", mediaId = 7L),
            SearchHistory(timestamp = 4, query = "dog"),
        )

        assertEquals(listOf("beach at sunset"), matchingHistory("beach", history).map { it.query })
    }

    @Test
    fun phaseFollowsTheResultPipeline() {
        val idle = SearchResultsState()
        assertEquals(SearchPhase.Home, searchPhase(idle, ""))
        assertEquals(SearchPhase.Typing, searchPhase(idle, "bea"))
        assertEquals(SearchPhase.Searching, searchPhase(idle.copy(hasSearched = true, isSearching = true), "bea"))
        assertEquals(
            SearchPhase.Error,
            searchPhase(idle.copy(hasSearched = true, results = MediaState(error = "failed", isLoading = false)), "bea")
        )
        assertEquals(SearchPhase.NoResults, searchPhase(idle.copy(hasSearched = true), "unicorn"))
    }

    private fun category(id: Long, name: String) =
        CategoryCarouselItem(id = id, name = name, mediaCount = 1, thumbnailMedia = null)

    private fun location(id: Long, label: String) = LocationMedia(
        media = EncryptedMedia(
            id = id,
            label = label,
            bytes = byteArrayOf(),
            path = "",
            relativePath = "",
            albumID = 0L,
            albumLabel = "",
            timestamp = 0L,
            fullDate = "",
            mimeType = "image/jpeg",
            favorite = 0,
            trashed = 0,
            size = 0L,
        ),
        location = label,
    )
}
