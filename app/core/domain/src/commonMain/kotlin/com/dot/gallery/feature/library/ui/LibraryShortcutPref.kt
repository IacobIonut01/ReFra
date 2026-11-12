/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import kotlinx.serialization.Serializable


/**
 * Persisted, user-editable layout entry for a single [LibraryShortcut].
 * The position in the stored list defines display order.
 *
 * [width] and [height] are in grid units. Layouts saved before tiles had a height store only
 * [span], so a [width] of 0 means "derive it from [span]". [span] is still written next to the
 * new size so an older build keeps a close half/full layout.
 */
@Serializable
data class LibraryShortcutPref(
    val id: String,
    val span: Int = LibraryShortcutSpan.HALF,
    val visible: Boolean = true,
    val width: Int = 0,
    val height: Int = 1,
    /** Tiles that show photos (Favorites) show the oldest instead of the latest. */
    val oldestFirst: Boolean = false,
    /** Favorites shows random favourites, picked again each time Library opens. Wins over [oldestFirst]. */
    val shuffle: Boolean = false,
    /** Favorites cards lay their photos out as overlapping prints instead of a grid. */
    val scrapbook: Boolean = false,
) {
    val size: LibraryShortcutSize
        get() = LibraryShortcutSize.of(
            width = if (width > 0) width else LibraryShortcutSize.fromSpan(span).width,
            height = height,
        )

    fun withSize(size: LibraryShortcutSize): LibraryShortcutPref = copy(
        width = size.width,
        height = size.height,
        span = if (size.width >= LibraryShortcutSize.FULL.width) LibraryShortcutSpan.FULL else LibraryShortcutSpan.HALF,
    )
}
