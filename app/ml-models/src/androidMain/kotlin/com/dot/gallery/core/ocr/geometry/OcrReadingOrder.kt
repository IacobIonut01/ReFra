/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.geometry

import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.domain.OcrWord

object OcrReadingOrder {
    /**
     * Deterministic basic column ordering. Horizontal text is read column-by-column; vertical text
     * is read top-to-bottom within columns. [rtl] reverses column order without reversing glyphs.
     */
    fun order(
        lines: List<OcrLine>,
        rtl: Boolean = false,
        vertical: Boolean = false,
    ): List<OcrLine> {
        if (lines.size < 2) return lines.toList()
        val columns = groupColumns(lines).let { if (vertical) it else mergeTableColumns(it) }
        val reverseColumns = rtl || vertical
        val orderedColumns = columns.sortedWith { first, second ->
            val primary = if (reverseColumns) {
                second.left.compareTo(first.left)
            } else {
                first.left.compareTo(second.left)
            }
            if (primary != 0) primary else first.stableId.compareTo(second.stableId)
        }
        return orderedColumns.flatMap { column ->
            if (!vertical) return@flatMap rowsInOrder(column.lines, rtl)
            column.lines.sortedWith { first, second ->
                val firstBounds = OcrGeometry.bounds(first.quad)
                val secondBounds = OcrGeometry.bounds(second.quad)
                val primary = firstBounds.top.compareTo(secondBounds.top)
                if (primary != 0) return@sortedWith primary
                val horizontal = if (rtl && !vertical) {
                    secondBounds.left.compareTo(firstBounds.left)
                } else {
                    firstBounds.left.compareTo(secondBounds.left)
                }
                if (horizontal != 0) horizontal else first.id.compareTo(second.id)
            }
        }
    }

    fun assignReadingIndices(
        lines: List<OcrLine>,
        rtl: Boolean = false,
        vertical: Boolean = false,
    ): List<OcrLine> {
        var wordIndex = 0
        return order(lines, rtl, vertical).mapIndexed { index, line ->
            line.copy(
                readingIndex = index,
                words = line.words.sortedWith(compareBy(OcrWord::readingIndex, OcrWord::id))
                    .map { word -> word.copy(readingIndex = wordIndex++) },
            )
        }
    }

    private fun groupColumns(lines: List<OcrLine>): List<Column> {
        val columns = ArrayList<Column>()
        val leftToRight = lines.sortedWith(compareBy<OcrLine>({ OcrGeometry.bounds(it.quad).left }, { it.id }))
        for (line in leftToRight) {
            val bounds = OcrGeometry.bounds(line.quad)
            val target = columns.asSequence()
                .map { column -> column to horizontalAffinity(column, bounds) }
                .filter { it.second >= COLUMN_AFFINITY }
                .maxWithOrNull(compareBy<Pair<Column, Float>>({ it.second }, { -it.first.left }))
                ?.first
            if (target == null) {
                columns += Column(bounds.left, bounds.right, line.id, arrayListOf(line))
            } else {
                target.left = minOf(target.left, bounds.left)
                target.right = maxOf(target.right, bounds.right)
                target.lines += line
                if (line.id < target.stableId) target.stableId = line.id
            }
        }
        return columns
    }

    /**
     * Joins a narrow column into the column beside it when their lines sit on the same rows: the
     * prices next to a receipt's items, values next to labels. Read as separate columns, the copied
     * text listed every item and then every price. Columns of running text have similar widths and
     * stay apart even when their lines happen to align.
     */
    private fun mergeTableColumns(columns: List<Column>): List<Column> {
        val merged = columns.sortedWith(compareBy(Column::left, Column::stableId)).toMutableList()
        var changed = true
        while (changed) {
            changed = false
            for (index in 0 until merged.size - 1) {
                val first = merged[index]
                val second = merged[index + 1]
                val narrow = minOf(first.width, second.width)
                val wide = maxOf(first.width, second.width)
                if (narrow > wide * TABLE_NARROW_RATIO || rowAlignment(first, second) < TABLE_ROW_ALIGNMENT) continue
                first.left = minOf(first.left, second.left)
                first.right = maxOf(first.right, second.right)
                first.lines += second.lines
                if (second.stableId < first.stableId) first.stableId = second.stableId
                merged.removeAt(index + 1)
                changed = true
                break
            }
        }
        return merged
    }

    /** Share of the shorter column's lines that have a line on the same row in the other column. */
    private fun rowAlignment(first: Column, second: Column): Float {
        val (fewer, more) = if (first.lines.size <= second.lines.size) first to second else second to first
        if (fewer.lines.isEmpty()) return 0f
        val aligned = fewer.lines.count { line -> more.lines.any { sameRow(line, it) } }
        return aligned.toFloat() / fewer.lines.size
    }

    private fun sameRow(first: OcrLine, second: OcrLine): Boolean {
        val a = OcrGeometry.bounds(first.quad)
        val b = OcrGeometry.bounds(second.quad)
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val minimumHeight = minOf(a.height, b.height)
        return minimumHeight > 0f && overlap >= minimumHeight * ROW_OVERLAP
    }

    /**
     * Lines of one column in rows: lines that overlap vertically by half the smaller height share a
     * row and are read left to right (right to left for [rtl]); rows are read top to bottom. Sorting by
     * top alone put a price whose box starts a pixel higher before its item.
     */
    private fun rowsInOrder(lines: List<OcrLine>, rtl: Boolean): List<OcrLine> {
        val rows = ArrayList<ArrayList<OcrLine>>()
        val byTop = lines.sortedWith(compareBy<OcrLine>({ OcrGeometry.bounds(it.quad).top }, { it.id }))
        for (line in byTop) {
            val row = rows.lastOrNull()?.takeIf { current -> current.any { sameRow(it, line) } }
            if (row != null) row += line else rows += arrayListOf(line)
        }
        return rows.flatMap { row ->
            row.sortedWith { first, second ->
                val a = OcrGeometry.bounds(first.quad)
                val b = OcrGeometry.bounds(second.quad)
                val horizontal = if (rtl) b.left.compareTo(a.left) else a.left.compareTo(b.left)
                if (horizontal != 0) horizontal else first.id.compareTo(second.id)
            }
        }
    }

    private fun horizontalAffinity(column: Column, bounds: NormalizedBounds): Float {
        val overlap = (minOf(column.right, bounds.right) - maxOf(column.left, bounds.left)).coerceAtLeast(0f)
        val minimumWidth = minOf(column.right - column.left, bounds.width)
        if (minimumWidth <= 0f) return 0f
        return overlap / minimumWidth
    }

    private data class Column(
        var left: Float,
        var right: Float,
        var stableId: String,
        val lines: ArrayList<OcrLine>,
    ) {
        val width: Float get() = right - left
    }

    private const val COLUMN_AFFINITY = 0.25f
    private const val TABLE_NARROW_RATIO = 0.5f
    private const val TABLE_ROW_ALIGNMENT = 0.6f
    private const val ROW_OVERLAP = 0.5f
}
