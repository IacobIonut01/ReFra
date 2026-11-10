/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

import com.dot.gallery.core.ocr.domain.OcrLimits
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Checked, deterministic source-region planner. It never simulates crops from an overview bitmap. */
object OcrTilePlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        supportsRegionDecoding: Boolean,
        requestFineDetail: Boolean = true,
        detailThreshold: Int = OcrLimits.OVERVIEW_MAX_DIMENSION,
    ): OcrTilePlan? {
        if (OcrLimits.checkedSourceArea(sourceWidth, sourceHeight) == null) return null
        if (detailThreshold <= 0 || detailThreshold > OcrLimits.MAX_SOURCE_DIMENSION) return null

        val fullSource = OcrSourceRect(0, 0, sourceWidth, sourceHeight)
        val overviewSize = fitWithin(sourceWidth, sourceHeight, OcrLimits.OVERVIEW_MAX_DIMENSION)
        if (OcrLimits.checkedRgbaByteCount(overviewSize.first, overviewSize.second) == null) return null
        val overview = OcrTile(
            id = "overview",
            kind = OcrTileKind.OVERVIEW,
            source = fullSource,
            decodeWidth = overviewSize.first,
            decodeHeight = overviewSize.second,
        )

        if (!supportsRegionDecoding || !requestFineDetail ||
            maxOf(sourceWidth, sourceHeight) <= detailThreshold
        ) {
            return OcrTilePlan(sourceWidth, sourceHeight, listOf(overview))
        }

        val maximumFineTiles = OcrLimits.MAX_TILES - 1
        val initialEdge = minOf(
            maxOf(detailThreshold, OcrLimits.OVERVIEW_MAX_DIMENSION),
            maxOf(sourceWidth, sourceHeight),
        )
        val tileEdge = adaptiveTileEdge(sourceWidth, sourceHeight, initialEdge, maximumFineTiles)
            ?: return OcrTilePlan(sourceWidth, sourceHeight, listOf(overview))
        val columns = tileCount(sourceWidth, tileEdge)
        val rows = tileCount(sourceHeight, tileEdge)
        val count = columns.toLong() * rows.toLong()
        if (count <= 0L || count > maximumFineTiles) return null

        val xPositions = positions(sourceWidth, tileEdge, columns)
        val yPositions = positions(sourceHeight, tileEdge, rows)
        val fine = ArrayList<OcrTile>(count.toInt())
        yPositions.forEachIndexed { row, top ->
            xPositions.forEachIndexed { column, left ->
                val right = minOf(sourceWidth, left + tileEdge)
                val bottom = minOf(sourceHeight, top + tileEdge)
                val source = OcrSourceRect(left, top, right, bottom)
                // A one-region plan identical to the overview cannot reveal additional detail.
                if (source == fullSource) return@forEachIndexed
                val decodeSize = fitWithin(source.width, source.height, OcrLimits.OVERVIEW_MAX_DIMENSION)
                if (OcrLimits.checkedRgbaByteCount(decodeSize.first, decodeSize.second) == null) return null
                fine += OcrTile(
                    id = "fine-$row-$column",
                    kind = OcrTileKind.FINE,
                    source = source,
                    decodeWidth = decodeSize.first,
                    decodeHeight = decodeSize.second,
                )
            }
        }
        return OcrTilePlan(sourceWidth, sourceHeight, listOf(overview) + fine)
    }

    private fun adaptiveTileEdge(
        width: Int,
        height: Int,
        initialEdge: Int,
        budget: Int,
    ): Int? {
        if (budget <= 0) return null
        var low = initialEdge
        var high = maxOf(width, height)
        if (checkedCount(width, height, high) > budget) return null
        while (low < high) {
            val middle = low + (high - low) / 2
            if (checkedCount(width, height, middle) <= budget) high = middle else low = middle + 1
        }
        return low
    }

    private fun checkedCount(width: Int, height: Int, edge: Int): Long =
        tileCount(width, edge).toLong() * tileCount(height, edge).toLong()

    private fun tileCount(length: Int, edge: Int): Int {
        if (length <= edge) return 1
        val stride = edge * (1.0 - OcrLimits.TILE_OVERLAP_FRACTION.toDouble())
        return ceil((length - edge) / stride).toInt() + 1
    }

    private fun positions(length: Int, edge: Int, count: Int): List<Int> {
        if (count <= 1) return listOf(0)
        val finalStart = length - minOf(length, edge)
        return List(count) { index ->
            (index.toDouble() * finalStart / (count - 1)).roundToInt().coerceIn(0, finalStart)
        }.distinct()
    }

    private fun fitWithin(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (width <= maxDimension && height <= maxDimension) return width to height
        return if (width >= height) {
            maxDimension to maxOf(1, (height.toLong() * maxDimension / width).toInt())
        } else {
            maxOf(1, (width.toLong() * maxDimension / height).toInt()) to maxDimension
        }
    }
}
