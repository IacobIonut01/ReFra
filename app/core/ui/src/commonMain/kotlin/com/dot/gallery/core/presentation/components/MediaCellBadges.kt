/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import com.dot.gallery.domain.settings.FAV_ICON_BOTTOM_START
import com.dot.gallery.domain.settings.FAV_ICON_DISABLED
import com.dot.gallery.domain.settings.FAV_ICON_TOP_END
import com.dot.gallery.domain.settings.FAV_ICON_TOP_START

enum class CellCorner(val alignment: Alignment) {
    TopStart(Alignment.TopStart),
    TopEnd(Alignment.TopEnd),
    BottomStart(Alignment.BottomStart),
    BottomEnd(Alignment.BottomEnd),
}

/**
 * Where each badge of a grid cell sits. A null corner means the badge isn't shown.
 *
 * @param status The metadata (motion, night, panorama) and cloud indicators, which share a corner.
 */
@Immutable
data class CellBadgeLayout(
    val favorite: CellCorner?,
    val video: CellCorner?,
    val stack: CellCorner?,
    val status: CellCorner?,
)

/** The corner the favourite icon setting asks for, or null when the icon is turned off. */
fun favoriteCorner(position: String): CellCorner? = when (position) {
    FAV_ICON_DISABLED -> null
    FAV_ICON_BOTTOM_START -> CellCorner.BottomStart
    FAV_ICON_TOP_END -> CellCorner.TopEnd
    FAV_ICON_TOP_START -> CellCorner.TopStart
    else -> CellCorner.BottomEnd
}

private val FallbackOrder = listOf(
    CellCorner.BottomEnd, CellCorner.TopEnd, CellCorner.BottomStart, CellCorner.TopStart
)

/**
 * Places a cell's badges so none overlap. The favourite icon goes where the user put it; a badge
 * whose usual corner it took (video duration top end, stack count top start, status bottom start)
 * moves to the first free corner, starting with the favourite's usual bottom end. While selecting,
 * the top start corner belongs to the selection check. With no corner left, a badge keeps its own
 * and overlaps, as cells did before.
 */
fun resolveCellBadgeLayout(
    favoritePosition: String,
    isFavorite: Boolean,
    hasVideo: Boolean,
    hasStack: Boolean,
    hasStatus: Boolean,
    selectionActive: Boolean,
): CellBadgeLayout {
    val taken = HashSet<CellCorner>(4)
    if (selectionActive) taken += CellCorner.TopStart

    fun claim(preferred: CellCorner?): CellCorner? {
        if (preferred == null) return null
        val corner = if (preferred !in taken) preferred
        else FallbackOrder.firstOrNull { it !in taken } ?: preferred
        taken += corner
        return corner
    }

    val favorite = if (isFavorite) claim(favoriteCorner(favoritePosition)) else null
    val stack = claim(CellCorner.TopStart.takeIf { hasStack })
    val video = claim(CellCorner.TopEnd.takeIf { hasVideo })
    val status = claim(CellCorner.BottomStart.takeIf { hasStatus })
    return CellBadgeLayout(favorite = favorite, video = video, stack = stack, status = status)
}
