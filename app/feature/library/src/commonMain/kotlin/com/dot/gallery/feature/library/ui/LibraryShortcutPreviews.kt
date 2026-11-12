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

/** Most favourites a shuffled Favorites cover picks from. */
const val MAX_SHORTCUT_FAVORITE_POOL = 24

/**
 * Photos the larger Favorites and Trash tiles show. [favoritePool] is spread across every
 * favourite, for the shuffled cover. [nextTrashExpiry] is the earliest trash expiry, in epoch
 * seconds like `Media.expiryTimestamp`.
 */
@Serializable
data class LibraryShortcutPreviews(
    val latestFavorites: List<UriMedia> = emptyList(),
    val oldestFavorites: List<UriMedia> = emptyList(),
    val favoritePool: List<UriMedia> = emptyList(),
    val recentTrash: List<UriMedia> = emptyList(),
    val nextTrashExpiry: Long? = null,
)

/**
 * The newest and oldest favourites by date, an evenly spaced pick of all of them, and the most
 * recently trashed items. The pick is the same for the same favourites, so refreshing the
 * Library does not change a shuffled cover while it is on screen.
 */
fun shortcutPreviews(trash: List<UriMedia>, favorites: List<UriMedia>): LibraryShortcutPreviews {
    val byDate = favorites.sortedByDescending { it.definedTimestamp }
    return LibraryShortcutPreviews(
        latestFavorites = byDate.take(MAX_SHORTCUT_PREVIEW_MEDIA),
        oldestFavorites = byDate.takeLast(MAX_SHORTCUT_PREVIEW_MEDIA).reversed(),
        favoritePool = evenlySpaced(byDate, MAX_SHORTCUT_FAVORITE_POOL),
        recentTrash = trash.sortedByDescending { it.expiryTimestamp ?: it.definedTimestamp }
            .take(MAX_SHORTCUT_PREVIEW_MEDIA),
        nextTrashExpiry = trash.mapNotNull { it.expiryTimestamp }.minOrNull(),
    )
}

private fun <T> evenlySpaced(items: List<T>, count: Int): List<T> =
    if (items.size <= count) items
    else List(count) { index -> items[(index.toLong() * items.size / count).toInt()] }
