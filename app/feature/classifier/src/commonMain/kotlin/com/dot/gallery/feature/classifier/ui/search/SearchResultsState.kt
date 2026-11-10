/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.runtime.Stable
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia


/**
 * Snapshot of the search screen's result pipeline. Produced app-side by the
 * search ViewModel and consumed by [SearchContent].
 */
@Stable
data class SearchResultsState(
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val isRelevanceSearch: Boolean = false,
    val progress: Float = 0f,
    val results: MediaState<UriMedia> = MediaState(isLoading = false),
    val dateResults: MediaState<UriMedia>? = null,
    /** Text in photos: matches shown in their own section above [results], never inside it. */
    val textMatches: List<TextPhotoMatch> = emptyList(),
) {
    /** What the viewer pages through when a result is opened: text matches first, then [results]. */
    val viewerResults: MediaState<UriMedia>
        get() = if (textMatches.isEmpty()) results else {
            val textIds = textMatches.mapTo(HashSet()) { it.media.id }
            val textMedia = textMatches.map { it.media }
            // The viewer pages through pagerMedia whenever it is set, so the matches go there too;
            // otherwise the opened id is missing from the pager and the viewer cannot find it.
            results.copy(
                media = textMedia + results.media.filterNot { it.id in textIds },
                pagerMedia = if (results.pagerMedia.isEmpty()) emptyList()
                else textMedia + results.pagerMedia.filterNot { it.id in textIds },
            )
        }
}
