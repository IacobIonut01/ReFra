/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.util.OrderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFlowSortTest {
    private val timeline = LastSort(OrderType.Descending, FilterKind.DATE)
    private val album = LastSort(OrderType.Ascending, FilterKind.NAME)
    private val favorites = LastSort(OrderType.Ascending, FilterKind.DATE_MODIFIED)

    private fun sortFor(albumId: Long, target: String?) =
        mediaFlowSort(albumId, target, timelineSort = timeline, albumSort = album, favoritesSort = favorites)

    @Test
    fun favoritesUseTheirOwnSort() {
        assertEquals(favorites, sortFor(-1L, Constants.Target.TARGET_FAVORITES))
    }

    @Test
    fun timelineAndAlbumsKeepTheirSorts() {
        assertEquals(timeline, sortFor(-1L, null))
        assertEquals(album, sortFor(42L, null))
    }

    @Test
    fun trashKeepsTheDefaultOrder() {
        assertNull(sortFor(-1L, Constants.Target.TARGET_TRASH))
    }
}
