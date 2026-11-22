/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.model

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/**
 * Represents the set of active filters applied to the timeline.
 * Each field that is null/empty means "no filter for this category".
 * Serializable so the timeline can keep it in a preference when "Remember filters" is on.
 */
@Serializable
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
