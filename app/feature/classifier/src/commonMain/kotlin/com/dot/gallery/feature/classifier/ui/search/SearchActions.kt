/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.runtime.Immutable
import com.dot.gallery.domain.model.UriMedia


/**
 * Callbacks [SearchContent] reports to the app shell. Query/search mutations map
 * to the app-side search ViewModel; parameterized destinations get dedicated
 * lambdas so route strings stay app-side.
 */
@Immutable
data class SearchActions(
    /** System/toolbar back when nothing can be cleared. */
    val onBack: () -> Unit,
    /** Clear the active query and selected image (maps to `clearQuery`). */
    val onClearQuery: () -> Unit,
    /** Update the query field; [apply] submits immediately (maps to `setQuery`). */
    val onQueryChange: (query: String, apply: Boolean) -> Unit,
    /** Submit the query and record it in history (`setQuery(apply=true)` + `addHistory`). */
    val onSubmitSearch: (query: String) -> Unit,
    /** Restore a past image-search entry by media id. */
    val onRestoreImageSearch: (mediaId: Long) -> Unit,
    /** Remove a text history entry. */
    val onRemoveHistory: (query: String) -> Unit,
    /** Remove an image-search history entry. */
    val onRemoveImageHistory: (mediaId: Long) -> Unit,
    /** Media picked in the image-search picker sheet (`setSelectedMedia`). */
    val onImageSelected: (media: UriMedia) -> Unit,
    /** Clear the image-search selection (`clearSelectedMedia`). */
    val onClearSelectedMedia: () -> Unit,
    /** Carousel filter chips → ViewModel query setters (`setMimeTypeQuery(key, hideExplicitQuery)`). */
    val onMimeTypeFilter: (key: String, hideExplicitQuery: Boolean) -> Unit,
    val onLensModelFilter: (key: String) -> Unit,
    val onMediaModeFilter: (key: String) -> Unit,
    val onGroupTypeFilter: (key: String) -> Unit,
    /** Retry the current search after an error (`retrySearch`). */
    val onRetrySearch: () -> Unit,
    /** Open a category page. */
    val onOpenCategory: (categoryId: Long) -> Unit,
    /** Open the location timeline for a place (called only when resolvable). */
    val onOpenLocation: (city: String, country: String, latitude: Double?, longitude: Double?) -> Unit,
    /** Fallback media open for unresolvable locations (`idAndAlbum(id, -1)`). */
    val onOpenMedia: (mediaId: Long) -> Unit,
    /** Open a search result in the viewer (`idAndQuery(id)`). */
    val onOpenSearchResult: (mediaId: Long) -> Unit,
    /** Remove every search history entry. */
    val onClearHistory: () -> Unit = {},
    /** Open an album suggested while typing. */
    val onOpenAlbum: (albumId: Long, label: String) -> Unit = { _, _ -> },
    /** Show favourites as results, with [label] in the field (`setFavoritesQuery`). */
    val onFavoritesFilter: (label: String) -> Unit = {},
    /** Open the Smart search setup when the model is missing. */
    val onSetUpSmartSearch: () -> Unit = {},
    /** "See all" on the Categories section. */
    val onSeeAllCategories: () -> Unit = {},
    /** "See all" on the Places section. */
    val onSeeAllPlaces: () -> Unit = {},
    /** Open a "Text in photos" match; the viewer highlights the query's words in it. */
    val onOpenTextMatch: (mediaId: Long) -> Unit = onOpenSearchResult,
)
