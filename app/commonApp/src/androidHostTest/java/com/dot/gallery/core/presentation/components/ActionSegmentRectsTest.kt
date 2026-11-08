/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionSegmentRectsTest {

    @Test
    fun oneRectPerVisibleSegmentSeparatedByTheGap() {
        assertEquals(
            listOf(
                Rect(0f, 0f, 137f, 137f),
                Rect(139f, 0f, 276f, 137f),
                Rect(278f, 0f, 415f, 137f),
            ),
            actionSegmentRects(listOf(137, 137, 137), height = 137, gap = 2),
        )
    }

    @Test
    fun zeroWidthChildrenTakeNoSegment() {
        assertEquals(
            listOf(Rect(0f, 0f, 117f, 117f), Rect(119f, 0f, 236f, 117f)),
            actionSegmentRects(listOf(0, 117, 0, 117), height = 117, gap = 2),
        )
    }

    @Test
    fun repeatedPassesNeverDuplicateSegments() {
        // Placement can run more than once per measure. A duplicated segment cancels itself
        // out in the even-odd cut-out and erased the whole group (viewer quick-action pill).
        val widths = listOf(137, 137, 137, 137, 137, 137)
        val first = actionSegmentRects(widths, height = 137, gap = 2)
        val second = actionSegmentRects(widths, height = 137, gap = 2)
        assertEquals(6, second.size)
        assertEquals(first, second)
    }
}
