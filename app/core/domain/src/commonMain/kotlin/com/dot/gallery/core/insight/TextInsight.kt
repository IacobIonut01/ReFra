/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import androidx.compose.runtime.Immutable

/**
 * The text read from a photo. [lines] are in reading order (columns, right-to-left and vertical CJK
 * handled by the engine); [quad] spans all of them. Vault results only ever live in memory.
 */
@Immutable
data class TextInsight(
    val lines: List<TextLine>,
    override val quad: ImageQuad,
) : Insight {
    override val kind: InsightKind get() = InsightKind.TEXT

    val fullText: String get() = lines.joinToString("\n") { it.text }

    /** Index of the line at a normalised point, or -1. */
    fun lineAt(x: Float, y: Float): Int = lines.indexOfFirst { it.quad.contains(x, y) }

    companion object {
        fun of(lines: List<TextLine>): TextInsight? {
            if (lines.isEmpty()) return null
            val left = lines.minOf { it.quad.left }
            val top = lines.minOf { it.quad.top }
            val right = lines.maxOf { it.quad.right }
            val bottom = lines.maxOf { it.quad.bottom }
            return TextInsight(lines, ImageQuad(left, top, right, top, right, bottom, left, bottom))
        }
    }
}
