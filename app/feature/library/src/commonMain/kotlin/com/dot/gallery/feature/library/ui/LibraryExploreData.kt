/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.runtime.Immutable
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.domain.model.UriMedia

/** Most faces the People tile draws. */
private const val MAX_SHORTCUT_FACES = 6

/**
 * What the Locations, People and Categories tiles show. Each flag says whether that tile has
 * anything to open; a tile without it is not offered at all.
 */
@Immutable
data class LibraryExploreData(
    val locations: Boolean = false,
    val locationCount: Int = 0,
    val latestGeo: LibraryGeoPreview? = null,
    val people: Boolean = false,
    val peopleCount: Int = 0,
    val faces: List<String?> = emptyList(),
    val categories: Boolean = false,
    val categoryCount: Int = 0,
    val categoryThumbnails: List<UriMedia> = emptyList(),
)

/**
 * The explore tiles' data from [state]. Categories stays offered while the first categories are
 * still being made, so it does not vanish and come back once they arrive.
 */
fun libraryExploreData(state: LibraryState, noClassification: Boolean): LibraryExploreData {
    val snapshot = state.snapshot
    val cloud = snapshot.cloud
    val awaitingFirstCategory = noCategories(snapshot.categories) && state.modelStatus == ModelStatus.READY
    return LibraryExploreData(
        locations = !snapshot.locations.isNullOrEmpty(),
        locationCount = snapshot.locationCount,
        latestGeo = snapshot.latestGeo,
        people = cloud.hasPeople && (cloud.people.isNotEmpty() || cloud.hiddenPeopleCount > 0),
        peopleCount = snapshot.peopleCount,
        faces = cloud.people.take(MAX_SHORTCUT_FACES).map { it.thumbnailUrl },
        categories = state.aiAvailable && !noClassification &&
            (!snapshot.categories.isNullOrEmpty() || awaitingFirstCategory),
        categoryCount = snapshot.categoryCount,
        categoryThumbnails = snapshot.categories.orEmpty()
            .mapNotNull { it.thumbnailMedia }
            .take(MAX_SHORTCUT_PREVIEW_MEDIA),
    )
}
