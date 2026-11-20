/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.ui.Alignment
import com.dot.gallery.data.entity.MediaMetadata
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

/** The favourite icon's configured corner, or the disabled marker when the grid hides it. */
fun cellFavoritePosition(position: String, showFavoriteIcon: Boolean): String =
    if (showFavoriteIcon) position else FAV_ICON_DISABLED

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

/** Photo megapixel thresholds offered for resolution labels. */
val ResolutionBadgeThresholdsMp: List<Int> = listOf(12, 24, 48, 100)

/**
 * The resolution label for a grid cell, or null when there is none to show. Photos get their
 * megapixels, rounded ("50MP"), once that rounded figure reaches [minPhotoMegapixels]; videos get
 * "4K" or "8K" from their shorter side, so portrait clips count too. Missing metadata or unknown
 * dimensions give null: the grid never reads files to find out.
 */
fun resolutionBadgeLabel(metadata: MediaMetadata?, isVideo: Boolean, minPhotoMegapixels: Int): String? {
    if (metadata == null) return null
    if (isVideo) {
        val width = metadata.videoWidth ?: metadata.imageWidth
        val height = metadata.videoHeight ?: metadata.imageHeight
        val shorter = minOf(width, height)
        return when {
            shorter >= 4320 -> "8K"
            shorter >= 2160 -> "4K"
            else -> null
        }
    }
    val pixels = metadata.imageWidth.toLong() * metadata.imageHeight
    if (pixels <= 0) return null
    val megapixels = (pixels + 500_000) / 1_000_000
    return if (megapixels >= minPhotoMegapixels) "${megapixels}MP" else null
}
