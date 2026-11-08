/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.runtime.Stable
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.classifier.ui.components.CategoryCarouselItem


/**
 * Aggregated input for [SearchContent] — collected app-side from the search
 * ViewModel and forwarded as a single stable value.
 */
@Stable
data class SearchState(
    val query: String = "",
    val searchResults: SearchResultsState = SearchResultsState(),
    val selectedImageMedia: UriMedia? = null,
    val isModelAvailable: Boolean = false,
    val searchableMediaIds: Set<Long> = emptySet(),
    val searchIndexerState: SearchIndexerState = SearchIndexerState(),
    val topCategories: List<CategoryCarouselItem> = emptyList(),
    val topLocations: List<LocationMedia> = emptyList(),
    val topMimeTypes: List<SearchMediaItem> = emptyList(),
    val topLensModels: List<SearchMediaItem> = emptyList(),
    val topMediaModes: List<SearchMediaItem> = emptyList(),
    val topGroupTypes: List<SearchMediaItem> = emptyList(),
    val typedSuggestions: List<TypedSearchSuggestion> = emptyList(),
)


fun shouldClearSearchOnBack(query: String, hasSelectedImage: Boolean): Boolean =
    query.isNotEmpty() || hasSelectedImage
