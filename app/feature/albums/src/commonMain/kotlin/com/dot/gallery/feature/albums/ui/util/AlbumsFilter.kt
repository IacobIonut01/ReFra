/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.util

import androidx.compose.runtime.saveable.Saver

enum class AlbumsFilterKind { ALL, DEVICE, SECTION, COLLECTIONS, CLOUD, MEDIA_TYPES }

/** One chip above the album grid; [sectionId] is set only for [AlbumsFilterKind.SECTION]. */
data class AlbumsFilter(val kind: AlbumsFilterKind, val sectionId: Long? = null) {
    companion object {
        val All = AlbumsFilter(AlbumsFilterKind.ALL)

        val Saver: Saver<AlbumsFilter, String> = Saver(
            save = { "${it.kind.name}:${it.sectionId ?: ""}" },
            restore = { saved ->
                val kind = AlbumsFilterKind.entries.firstOrNull { it.name == saved.substringBefore(':') }
                if (kind == null) All else AlbumsFilter(kind, saved.substringAfter(':').toLongOrNull())
            },
        )
    }
}

/**
 * Filters worth offering for what the user has. Album sections replace the single "On device"
 * chip, one chip each. A single kind of album has nothing to tell apart, so then no chips show.
 */
fun availableAlbumFilters(
    hasDevice: Boolean,
    sectionIds: List<Long> = emptyList(),
    hasCollections: Boolean,
    hasCloud: Boolean,
    hasMediaTypes: Boolean,
): List<AlbumsFilter> {
    val kinds = buildList {
        if (sectionIds.isNotEmpty()) {
            sectionIds.forEach { add(AlbumsFilter(AlbumsFilterKind.SECTION, it)) }
        } else if (hasDevice) {
            add(AlbumsFilter(AlbumsFilterKind.DEVICE))
        }
        if (hasCollections) add(AlbumsFilter(AlbumsFilterKind.COLLECTIONS))
        if (hasCloud) add(AlbumsFilter(AlbumsFilterKind.CLOUD))
        if (hasMediaTypes) add(AlbumsFilter(AlbumsFilterKind.MEDIA_TYPES))
    }
    return if (kinds.size < 2) emptyList() else listOf(AlbumsFilter.All) + kinds
}

/** A filter whose albums went away (or whose section was hidden) falls back to [AlbumsFilter.All]. */
fun resolveAlbumFilter(selected: AlbumsFilter, available: List<AlbumsFilter>): AlbumsFilter =
    if (selected in available) selected else AlbumsFilter.All

internal const val KEY_LIBRARY_HEADER = "libraryHeader"
internal const val KEY_ALBUMS_HEADING = "albumsHeading"
internal const val KEY_PINNED_ALBUMS = "pinnedAlbums"
internal const val KEY_FILTER_BAR = "filterButton"
private const val KEY_PINNED_ITEM_PREFIX = "pinned_"

/**
 * Whether the filter bar has scrolled under the top chrome and must be pinned there.
 *
 * [barOffset] is measured from the content padding edge, so it turns negative exactly as the bar
 * slides under the top chrome. It is null while the bar is not composed; in that
 * case [firstVisibleKey] tells the two sides apart: above it the bar is still below the viewport,
 * which happens on short landscape windows, and past it the bar has scrolled away.
 */
fun albumsFilterBarPinned(barOffset: Int?, firstVisibleKey: Any?): Boolean {
    if (barOffset != null) return barOffset < 0
    val key = firstVisibleKey as? String ?: return firstVisibleKey != null
    return key != KEY_LIBRARY_HEADER && key != KEY_ALBUMS_HEADING && key != KEY_PINNED_ALBUMS &&
        !key.startsWith(KEY_PINNED_ITEM_PREFIX)
}
