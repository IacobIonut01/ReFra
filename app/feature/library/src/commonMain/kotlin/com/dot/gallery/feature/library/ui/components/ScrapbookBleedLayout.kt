/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui.components

import com.dot.gallery.feature.library.ui.LibraryShortcutSize
import kotlin.math.round
import kotlin.math.sqrt


/** One frameless scrapbook print: centre and size as fractions of the card, rotation in degrees. */
data class BleedPrint(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
)

/** Prints are wider than their cell so neighbours overlap and no card colour shows through. */
private const val BLEED_OVERSIZE = 1.35f

private val BLEED_JITTER = listOf(
    0.02f to -0.03f,
    -0.03f to 0.02f,
    0.03f to 0.03f,
    -0.02f to -0.03f,
    0.03f to -0.01f,
    -0.03f to 0.02f,
)

private val BLEED_ROTATIONS = listOf(-5f, 6f, -4f, 5f, -6f, 3f)

/** Photos a frameless scrapbook card shows: square cards seat one fewer print. */
fun bleedPrintLimit(size: LibraryShortcutSize): Int =
    if (size == LibraryShortcutSize.CARD) 5 else 6

/**
 * Lays [count] prints edge to edge over a card whose width is [aspect]
 * times its height, in fractions: rows split the height evenly and each row
 * splits its width, with the spare prints in the lower rows. Prints are
 * oversized and slightly jittered and rotated so the overlap reads as a
 * scrapbook spread rather than a grid.
 */
fun bleedPrints(count: Int, aspect: Float): List<BleedPrint> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(BleedPrint(0.5f, 0.5f, 1f, 1f, 0f))
    val rows = round(sqrt(count / aspect)).toInt().coerceIn(1, count)
    val base = count / rows
    val extra = count % rows
    val prints = ArrayList<BleedPrint>(count)
    var k = 0
    for (row in 0 until rows) {
        val cols = base + if (row >= rows - extra) 1 else 0
        val cellWidth = 1f / cols
        val cellHeight = 1f / rows
        for (col in 0 until cols) {
            val slot = k % BLEED_JITTER.size
            val (sx, sy) = BLEED_JITTER[slot]
            prints += BleedPrint(
                centerX = (col + 0.5f) * cellWidth + sx * cellWidth,
                centerY = (row + 0.5f) * cellHeight + sy * cellHeight,
                width = cellWidth * BLEED_OVERSIZE,
                height = cellHeight * BLEED_OVERSIZE,
                rotation = BLEED_ROTATIONS[slot],
            )
            k++
        }
    }
    return prints
}
