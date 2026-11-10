/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.decode.OcrTileKind
import com.dot.gallery.core.ocr.decode.OcrTilePlanner
import com.dot.gallery.core.ocr.domain.OcrLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTilePlannerTest {
    @Test
    fun overviewIsAlwaysPresentAndFakeTilesAreNeverMade() {
        val noRegions = OcrTilePlanner.plan(8_000, 4_000, supportsRegionDecoding = false)!!
        val noDetail = OcrTilePlanner.plan(8_000, 4_000, supportsRegionDecoding = true, requestFineDetail = false)!!

        assertEquals(listOf(OcrTileKind.OVERVIEW), noRegions.tiles.map { it.kind })
        assertEquals(1, noDetail.tiles.size)
        assertEquals(2_048, noRegions.overview.decodeWidth)
        assertEquals(1_024, noRegions.overview.decodeHeight)
    }

    @Test
    fun fineRegionsOverlapAndStayWithinBudget() {
        val plan = OcrTilePlanner.plan(8_000, 4_000, supportsRegionDecoding = true)!!
        assertTrue(plan.fineTiles.isNotEmpty())
        assertTrue(plan.tiles.size <= OcrLimits.MAX_TILES)
        val firstRow = plan.fineTiles.filter { it.source.top == plan.fineTiles.first().source.top }
            .sortedBy { it.source.left }
        assertTrue(firstRow.size > 1)
        assertTrue(firstRow.zipWithNext().all { (left, right) -> left.source.right > right.source.left })
    }

    @Test
    fun rejectsOverflowAndConfiguredSourceLimits() {
        assertNull(OcrTilePlanner.plan(Int.MAX_VALUE, Int.MAX_VALUE, true))
        assertNull(OcrTilePlanner.plan(100_000, 100_000, true))
        assertNull(OcrTilePlanner.plan(0, 100, true))
    }

    @Test
    fun veryWidePanoramaUsesAdaptiveBudgetAndCoversBothEnds() {
        val plan = OcrTilePlanner.plan(100_000, 100, supportsRegionDecoding = true)!!
        assertTrue(plan.tiles.size <= OcrLimits.MAX_TILES)
        assertTrue(plan.fineTiles.first().source.left == 0)
        assertTrue(plan.fineTiles.last().source.right == 100_000)
        assertTrue(plan.fineTiles.all { it.source.top == 0 && it.source.bottom == 100 })
    }

    @Test
    fun nearAreaLimitAdaptsRatherThanExceedingTileBudget() {
        val plan = OcrTilePlanner.plan(31_000, 31_000, supportsRegionDecoding = true)
        assertNotNull(plan)
        assertTrue(plan!!.tiles.size <= OcrLimits.MAX_TILES)
        assertTrue(plan.fineTiles.isNotEmpty())
    }
}
