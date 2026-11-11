/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.definedTimestamp
import kotlinx.serialization.Serializable


/** Most photos a shortcut card shows. */
const val MAX_SHORTCUT_PREVIEW_MEDIA = 4

/**
 * Photos the larger Favorites and Trash tiles show. [nextTrashExpiry] is the earliest trash
 * expiry, in epoch seconds like `Media.expiryTimestamp`.
 */
@Serializable
data class LibraryShortcutPreviews(
    val latestFavorites: List<UriMedia> = emptyList(),
    val oldestFavorites: List<UriMedia> = emptyList(),
    val recentTrash: List<UriMedia> = emptyList(),
    val nextTrashExpiry: Long? = null,
)

/** The newest and oldest favourites by date, and the most recently trashed items. */
fun shortcutPreviews(trash: List<UriMedia>, favorites: List<UriMedia>): LibraryShortcutPreviews {
    val byDate = favorites.sortedByDescending { it.definedTimestamp }
    return LibraryShortcutPreviews(
        latestFavorites = byDate.take(MAX_SHORTCUT_PREVIEW_MEDIA),
        oldestFavorites = byDate.takeLast(MAX_SHORTCUT_PREVIEW_MEDIA).reversed(),
        recentTrash = trash.sortedByDescending { it.expiryTimestamp ?: it.definedTimestamp }
            .take(MAX_SHORTCUT_PREVIEW_MEDIA),
        nextTrashExpiry = trash.mapNotNull { it.expiryTimestamp }.minOrNull(),
    )
}
