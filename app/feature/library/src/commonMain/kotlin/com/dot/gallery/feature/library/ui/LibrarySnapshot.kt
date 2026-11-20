/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import com.dot.gallery.domain.model.LibraryIndicatorState
import com.dot.gallery.domain.model.LocationMedia
import kotlinx.serialization.Serializable


/**
 * Serializable snapshot of everything the Library screen renders — persisted by
 * the app-side startup cache (StartupMediaCache) so the screen restores
 * instantly on the next cold start.
 */
@Serializable
data class LibrarySnapshot(
    val categories: List<LibraryCategoryPreview>? = null,
    val categoryCount: Int = 0,
    val locations: List<LocationMedia>? = null,
    val locationCount: Int = 0,
    val latestGeo: LibraryGeoPreview? = null,
    val peopleCount: Int = 0,
    val peopleCountsByAccount: Map<Long, Int> = emptyMap(),
    val sharedLinkCountsByAccount: Map<Long, Int> = emptyMap(),
    val cloud: CloudLibraryState = CloudLibraryState(),
    /** False until the people collectors have settled; the People tile reserves its slot meanwhile. */
    val peopleLoaded: Boolean = false,
    /** False until every configured provider resolved; the cloud tiles reserve their slots meanwhile. */
    val cloudLoaded: Boolean = false,
    val indicators: LibraryIndicatorState = LibraryIndicatorState(),
    val shortcutPreviews: LibraryShortcutPreviews = LibraryShortcutPreviews(),
    val viewport: LibraryViewport = LibraryViewport()
)

fun categoriesLoaded(items: List<*>?): Boolean = items != null

fun noCategories(items: List<*>?): Boolean = items?.isEmpty() == true
