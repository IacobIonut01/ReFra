/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.runtime.Immutable

/**
 * Selected lines of a [com.dot.gallery.core.insight.TextInsight], as a range in reading order.
 * Handles move [start] and [end]; the range never inverts.
 */
@Immutable
data class TextSelection(val start: Int, val end: Int) {
    val range: IntRange get() = minOf(start, end)..maxOf(start, end)
    val count: Int get() = range.last - range.first + 1

    fun withStart(index: Int) = copy(start = index.coerceAtMost(end))
    fun withEnd(index: Int) = copy(end = index.coerceAtLeast(start))

    fun text(lines: List<String>): String = range.filter { it in lines.indices }.joinToString("\n") { lines[it] }

    companion object {
        fun all(lineCount: Int) = TextSelection(0, (lineCount - 1).coerceAtLeast(0))
        fun single(index: Int) = TextSelection(index, index)
    }
}
