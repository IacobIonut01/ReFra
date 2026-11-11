/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui


/** Where a tile sits in the shortcut grid, in grid units. */
data class ShortcutCell(val column: Int, val row: Int, val width: Int, val height: Int) {
    val bottom: Int get() = row + height
}
