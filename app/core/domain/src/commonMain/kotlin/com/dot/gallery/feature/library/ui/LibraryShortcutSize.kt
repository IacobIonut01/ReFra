/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui


/**
 * The sizes a Library shortcut can take, in grid units: a phone row is [COLUMNS] units wide and
 * every row is one tile tall. Taller sizes are cards that show more than an icon and a label.
 */
enum class LibraryShortcutSize(val width: Int, val height: Int) {
    ICON(1, 1),
    HALF(2, 1),
    FULL(4, 1),
    CARD(2, 2),
    TALL_CARD(2, 3),
    WIDE_CARD(4, 2);

    val isCard: Boolean get() = height > 1

    val label: String get() = "$width×$height"

    companion object {
        /** Grid units in a phone-width row; wider layouts use a multiple of it. */
        const val COLUMNS = 4

        val widths: List<Int> = entries.map { it.width }.distinct().sorted()

        fun heightsFor(width: Int): List<Int> =
            entries.filter { it.width == width }.map { it.height }.sorted()

        fun fromSpan(span: Int): LibraryShortcutSize =
            if (span >= LibraryShortcutSpan.FULL) FULL else HALF

        /** The allowed size closest to [width] × [height]; unknown stored values never fail. */
        fun of(width: Int, height: Int): LibraryShortcutSize {
            val w = widths.minBy { kotlin.math.abs(it - width) }
            val h = heightsFor(w).minBy { kotlin.math.abs(it - height) }
            return entries.first { it.width == w && it.height == h }
        }
    }
}
