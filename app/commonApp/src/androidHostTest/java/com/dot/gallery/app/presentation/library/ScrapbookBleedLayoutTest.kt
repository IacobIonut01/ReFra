/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library

import com.dot.gallery.feature.library.ui.LibraryShortcutSize
import com.dot.gallery.feature.library.ui.components.BleedPrint
import com.dot.gallery.feature.library.ui.components.bleedPrintLimit
import com.dot.gallery.feature.library.ui.components.bleedPrints
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class ScrapbookBleedLayoutTest {

    private val aspects = listOf(0.6f, 1.0f, 1.5f, 2.2f, 3.4f, 4.0f)

    /** Point (x, y) in pixel space (x in [0, aspect], y in [0, 1]) inside the rotated print? */
    private fun covers(print: BleedPrint, aspect: Float, x: Float, y: Float): Boolean {
        val dx = x - print.centerX * aspect
        val dy = y - print.centerY
        val rad = Math.toRadians(print.rotation.toDouble())
        val rx = dx * cos(rad) + dy * sin(rad)
        val ry = -dx * sin(rad) + dy * cos(rad)
        return abs(rx) <= print.width * aspect / 2 && abs(ry) <= print.height / 2
    }

    @Test
    fun printsMatchThePhotoCountForEveryAspect() {
        for (aspect in aspects) {
            for (count in 1..6) {
                assertEquals("aspect=$aspect count=$count", count, bleedPrints(count, aspect).size)
            }
        }
        assertTrue(bleedPrints(0, 1f).isEmpty())
        assertTrue(bleedPrints(-3, 1f).isEmpty())
    }

    @Test
    fun printsCoverTheWholeCard() {
        for (aspect in aspects) {
            for (count in 1..6) {
                val prints = bleedPrints(count, aspect)
                for (gy in 0 until 60) {
                    for (gx in 0 until 60) {
                        val x = (gx + 0.5f) / 60f * aspect
                        val y = (gy + 0.5f) / 60f
                        assertTrue(
                            "aspect=$aspect count=$count uncovered ($x, $y)",
                            prints.any { covers(it, aspect, x, y) },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun printLimitDependsOnTheCardShape() {
        assertEquals(5, bleedPrintLimit(LibraryShortcutSize.CARD))
        assertEquals(6, bleedPrintLimit(LibraryShortcutSize.TALL_CARD))
        assertEquals(6, bleedPrintLimit(LibraryShortcutSize.WIDE_CARD))
    }
}
