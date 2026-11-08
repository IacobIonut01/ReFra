/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.util

/**
 * Albums split by the user's manual arrangement. [arranged] follows the stored order and ignores
 * the active sort; [others] holds albums that were never arranged, in the incoming (sorted) order.
 */
data class ArrangedAlbums<T>(
    val arranged: List<T>,
    val others: List<T>,
) {
    val hasArrangement: Boolean get() = arranged.isNotEmpty()
}
