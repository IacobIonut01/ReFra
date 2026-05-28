/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.model

import androidx.compose.runtime.Stable

/**
 * Represents the set of active filters applied to the timeline.
 * Each field that is null/empty means "no filter for this category".
 */
@Stable
data class TimelineFilter(
    val mediaType: MediaTypeFilter = MediaTypeFilter.ALL,
    val favoritesOnly: Boolean = false,
    val selectedYears: Set<Int> = emptySet(),
    val selectedAlbumIds: Set<Long> = emptySet(),
) {
    val isActive: Boolean
        get() = mediaType != MediaTypeFilter.ALL ||
                favoritesOnly ||
                selectedYears.isNotEmpty() ||
                selectedAlbumIds.isNotEmpty()
}

enum class MediaTypeFilter {
    ALL, PHOTOS, VIDEOS
}
