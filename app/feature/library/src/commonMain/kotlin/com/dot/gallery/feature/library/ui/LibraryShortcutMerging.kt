/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui


/**
 * Merge the user's stored [prefs] with the [runtime] shortcuts.
 *
 * - Items present in [prefs] keep their stored order, span and visibility.
 * - Newly introduced shortcuts (not yet in [prefs]) are appended in their
 *   natural enum order, with the layout [defaults] gives them.
 * - Stored entries whose shortcut id is unknown are dropped.
 *
 * The result is ordered exactly as it should be rendered/edited.
 */
fun mergeShortcutPrefs(
    prefs: List<LibraryShortcutPref>,
    runtime: Map<LibraryShortcut, RuntimeShortcut>,
    defaults: (LibraryShortcut) -> LibraryShortcutPref = { LibraryShortcutPref(id = it.id) },
): List<LibraryShortcutItem> {
    val byId = prefs.associateBy { it.id }
    val seen = LinkedHashSet<LibraryShortcut>()
    val result = ArrayList<LibraryShortcutItem>()

    // 1. Stored entries first, preserving their order.
    for (pref in prefs) {
        val shortcut = LibraryShortcut.fromId(pref.id) ?: continue
        if (!seen.add(shortcut)) continue
        val data = runtime[shortcut] ?: continue
        result += data.toItem(pref)
    }
    // 2. Append any runtime shortcut not yet persisted, in enum order.
    for (shortcut in LibraryShortcut.entries) {
        if (shortcut in seen) continue
        val data = runtime[shortcut] ?: continue
        val pref = byId[shortcut.id] ?: defaults(shortcut)
        result += data.toItem(pref)
    }
    return result
}

/**
 * The layout a shortcut starts with before the user stores one. Locations, People and Categories
 * keep the visibility their old switches gave them ([showLocations], the Library's
 * [hiddenSections]), and Locations starts as a 2×2 card so its map stays visible.
 */
fun defaultShortcutPref(
    shortcut: LibraryShortcut,
    showLocations: Boolean,
    hiddenSections: Set<String>,
): LibraryShortcutPref = when (shortcut) {
    LibraryShortcut.LOCATIONS -> LibraryShortcutPref(id = shortcut.id, visible = showLocations)
        .withSize(LibraryShortcutSize.CARD)
    LibraryShortcut.PEOPLE, LibraryShortcut.CATEGORIES ->
        LibraryShortcutPref(id = shortcut.id, visible = shortcut.id !in hiddenSections)
    else -> LibraryShortcutPref(id = shortcut.id)
}

/**
 * Place [sizes] in a grid [columns] units wide, in order. Each tile takes the first free spot at
 * or after the previous tile's, reading row by row, so tiles beside a taller card fill the space
 * next to it while the stored order still decides the reading order. Tiles wider than the grid
 * are narrowed to fit.
 */
fun packShortcutGrid(sizes: List<LibraryShortcutSize>, columns: Int): List<ShortcutCell> {
    val cols = columns.coerceAtLeast(1)
    val taken = ArrayList<BooleanArray>()
    fun isFree(row: Int, column: Int, width: Int, height: Int) = (row until row + height).all { r ->
        r >= taken.size || (column until column + width).none { taken[r][it] }
    }
    var row = 0
    var column = 0
    return sizes.map { size ->
        val width = size.width.coerceAtMost(cols)
        val height = size.height
        while (column + width > cols || !isFree(row, column, width, height)) {
            if (column + width > cols) {
                column = 0
                row++
            } else column++
        }
        while (taken.size < row + height) taken += BooleanArray(cols)
        for (r in row until row + height) for (c in column until column + width) taken[r][c] = true
        ShortcutCell(column, row, width, height).also { column += width }
    }
}

private fun LibraryShortcutSize.withWidth(width: Int): LibraryShortcutSize {
    val heights = LibraryShortcutSize.heightsFor(width)
    return LibraryShortcutSize.of(width, heights.lastOrNull { it <= height } ?: heights.first())
}

/**
 * The size a tile snaps to while its side edge is dragged to [targetUnits] grid units wide: the
 * nearest width that fits [columns], so it changes once the edge passes halfway between two
 * widths. A height the new width does not allow drops to the tallest one it does.
 */
fun resizeShortcutWidth(current: LibraryShortcutSize, targetUnits: Float, columns: Int): LibraryShortcutSize {
    val widths = LibraryShortcutSize.widths.filter { it <= columns }.ifEmpty { listOf(1) }
    return current.withWidth(widths.minBy { kotlin.math.abs(it - targetUnits) })
}

/** As [resizeShortcutWidth], for the bottom edge dragged to [targetRows] rows tall. */
fun resizeShortcutHeight(current: LibraryShortcutSize, targetRows: Float): LibraryShortcutSize {
    val heights = LibraryShortcutSize.heightsFor(current.width)
    return LibraryShortcutSize.of(current.width, heights.minBy { kotlin.math.abs(it - targetRows) })
}

/** The next width on a tap of the side handle, wrapping from the widest that fits to 1. */
fun nextShortcutWidth(current: LibraryShortcutSize, columns: Int): LibraryShortcutSize {
    val widths = LibraryShortcutSize.widths.filter { it <= columns }.ifEmpty { listOf(1) }
    return current.withWidth(widths.firstOrNull { it > current.width } ?: widths.first())
}

/** The next height on a tap of the bottom handle, wrapping to one row. */
fun nextShortcutHeight(current: LibraryShortcutSize): LibraryShortcutSize {
    val heights = LibraryShortcutSize.heightsFor(current.width)
    return LibraryShortcutSize.of(current.width, heights.firstOrNull { it > current.height } ?: heights.first())
}

/** Rows a folded shortcut grid may use, unless its first tile is taller. */
const val COLLAPSED_SHORTCUT_ROWS = 2

/**
 * Rows a folded grid may use while Locations, People or Categories is shown: about the height
 * the shortcuts and the fixed cards below them took together before those became tiles.
 */
const val COLLAPSED_EXPLORE_SHORTCUT_ROWS = 5

/** The folded height for [working]: taller while it shows a Locations, People or Categories tile. */
fun collapsedShortcutRows(working: List<LibraryShortcutPref>): Int =
    if (working.any { it.visible && LibraryShortcut.fromId(it.id)?.isExplore == true }) {
        COLLAPSED_EXPLORE_SHORTCUT_ROWS
    } else COLLAPSED_SHORTCUT_ROWS

/**
 * How a long grid folds; null when every tile fits in the folded height, so no toggle is needed.
 * The folded height is [rows], or the tallest tile's if that is taller, so a card the user made
 * large is not hidden. The toggle shrinks to an icon when that keeps one more tile visible.
 */
fun foldShortcutGrid(
    sizes: List<LibraryShortcutSize>,
    columns: Int,
    rows: Int = COLLAPSED_SHORTCUT_ROWS,
): ShortcutFold? {
    if (sizes.isEmpty()) return null
    val maxRows = maxOf(rows, sizes.maxOf { it.height })
    fun rows(cells: List<ShortcutCell>) = cells.maxOfOrNull { it.bottom } ?: 0
    if (rows(packShortcutGrid(sizes, columns)) <= maxRows) return null
    for (count in sizes.size - 1 downTo 0) {
        for (toggle in listOf(LibraryShortcutSize.HALF, LibraryShortcutSize.ICON)) {
            if (rows(packShortcutGrid(sizes.take(count) + toggle, columns)) <= maxRows) {
                return ShortcutFold(count, toggle)
            }
        }
    }
    return ShortcutFold(0, LibraryShortcutSize.ICON)
}
