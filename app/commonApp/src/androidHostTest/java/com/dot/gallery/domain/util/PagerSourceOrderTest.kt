/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PagerSourceOrderTest {

    @Test
    fun dateHeadersKeepTheGroupedWalk() {
        assertEquals(
            listOf(1L, 2L, 3L),
            pagerIdsForOpenGrid(
                sourceIds = listOf(3L, 1L, 2L),
                groupedIds = listOf(1L, 2L, 3L),
                dateHeadersEnabled = true,
            ),
        )
    }

    @Test
    fun flatGridKeepsTheSortedSourceOrder() {
        assertEquals(
            listOf(3L, 1L, 2L),
            pagerIdsForOpenGrid(
                sourceIds = listOf(3L, 1L, 2L),
                groupedIds = listOf(1L, 2L, 3L),
                dateHeadersEnabled = false,
            ),
        )
    }

    @Test
    fun flatGridKeepsTheFirstCopyOfADuplicateId() {
        assertEquals(
            listOf(3L, 1L),
            pagerIdsForOpenGrid(
                sourceIds = listOf(3L, 1L, 3L),
                groupedIds = listOf(1L, 3L),
                dateHeadersEnabled = false,
            ),
        )
    }
}
