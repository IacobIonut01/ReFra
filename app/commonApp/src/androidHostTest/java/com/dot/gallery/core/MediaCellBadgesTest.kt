/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.core.presentation.components.CellBadgeLayout
import com.dot.gallery.core.presentation.components.CellCorner
import com.dot.gallery.core.presentation.components.resolveCellBadgeLayout
import com.dot.gallery.domain.settings.FAV_ICON_BOTTOM_END
import com.dot.gallery.domain.settings.FAV_ICON_BOTTOM_START
import com.dot.gallery.domain.settings.FAV_ICON_DISABLED
import com.dot.gallery.domain.settings.FAV_ICON_TOP_END
import com.dot.gallery.domain.settings.FAV_ICON_TOP_START
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCellBadgesTest {

    private fun layout(
        position: String,
        favorite: Boolean = true,
        video: Boolean = true,
        stack: Boolean = true,
        status: Boolean = true,
        selecting: Boolean = false,
    ) = resolveCellBadgeLayout(position, favorite, video, stack, status, selecting)

    @Test
    fun defaultCornersWhenTheFavouriteSitsBottomEnd() {
        assertEquals(
            CellBadgeLayout(
                favorite = CellCorner.BottomEnd,
                video = CellCorner.TopEnd,
                stack = CellCorner.TopStart,
                status = CellCorner.BottomStart,
            ),
            layout(FAV_ICON_BOTTOM_END),
        )
    }

    @Test
    fun videoDurationMovesWhenTheFavouriteTakesTopEnd() {
        val result = layout(FAV_ICON_TOP_END)
        assertEquals(CellCorner.TopEnd, result.favorite)
        assertEquals(CellCorner.BottomEnd, result.video)
    }

    @Test
    fun stackBadgeMovesWhenTheFavouriteTakesTopStart() {
        val result = layout(FAV_ICON_TOP_START)
        assertEquals(CellCorner.TopStart, result.favorite)
        assertEquals(CellCorner.BottomEnd, result.stack)
        assertEquals(CellCorner.TopEnd, result.video)
    }

    @Test
    fun statusIconsMoveWhenTheFavouriteTakesBottomStart() {
        val result = layout(FAV_ICON_BOTTOM_START)
        assertEquals(CellCorner.BottomStart, result.favorite)
        assertEquals(CellCorner.BottomEnd, result.status)
    }

    @Test
    fun nothingMovesForANonFavourite() {
        val result = layout(FAV_ICON_TOP_END, favorite = false)
        assertEquals(null, result.favorite)
        assertEquals(CellCorner.TopEnd, result.video)
    }

    @Test
    fun hiddenFavouriteIconFreesItsCorner() {
        val result = layout(FAV_ICON_DISABLED)
        assertEquals(null, result.favorite)
        assertEquals(CellCorner.TopEnd, result.video)
        assertEquals(CellCorner.TopStart, result.stack)
    }

    @Test
    fun selectionCheckKeepsTopStart() {
        val result = layout(FAV_ICON_TOP_START, video = false, status = false, selecting = true)
        assertEquals(CellCorner.BottomEnd, result.favorite)
        assertEquals(CellCorner.TopEnd, result.stack)
    }

    @Test
    fun badgesOverlapRatherThanDisappearWhenEveryCornerIsTaken() {
        val result = layout(FAV_ICON_BOTTOM_END, selecting = true)
        assertEquals(CellCorner.BottomStart, result.status)
    }
}
