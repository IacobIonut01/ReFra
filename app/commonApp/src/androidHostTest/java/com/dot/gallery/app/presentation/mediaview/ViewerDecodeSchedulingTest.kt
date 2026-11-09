/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerDecodeSchedulingTest {

    @Test
    fun neighboursAreOrderedNearestFirstForwardBeforeBackward() {
        assertEquals(listOf(6, 4, 7, 3), neighbourPrefetchOrder(page = 5, count = 10, radius = 2))
    }

    @Test
    fun neighboursAreClippedAtTheEnds() {
        assertEquals(listOf(1, 2), neighbourPrefetchOrder(page = 0, count = 10, radius = 2))
        assertEquals(listOf(8, 7), neighbourPrefetchOrder(page = 9, count = 10, radius = 2))
        assertEquals(emptyList<Int>(), neighbourPrefetchOrder(page = 0, count = 1, radius = 2))
    }

    @Test
    fun outOfRangePageHasNoNeighbours() {
        assertEquals(emptyList<Int>(), neighbourPrefetchOrder(page = 3, count = 0))
        assertEquals(emptyList<Int>(), neighbourPrefetchOrder(page = -1, count = 5))
    }
}
