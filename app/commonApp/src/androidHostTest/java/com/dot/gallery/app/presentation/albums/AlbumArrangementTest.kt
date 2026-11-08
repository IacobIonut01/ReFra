/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.albums

import com.dot.gallery.feature.albums.ui.util.AlbumArrangement
import com.dot.gallery.feature.albums.ui.util.move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the generic arrangement core with a plain stand-in for `Album`, whose
 * `android.net.Uri` field cannot be constructed on the host JVM. The stand-in mirrors
 * `Album.sourceAlbumIds`: the merged ids, or the album's own id when it is not merged.
 */
class AlbumArrangementTest {

    private data class TestAlbum(val id: Long, val merged: List<Long> = emptyList()) {
        val sourceIds: List<Long> get() = merged.ifEmpty { listOf(id) }
    }

    private fun album(id: Long, merged: List<Long> = emptyList()) = TestAlbum(id, merged)

    private fun split(sorted: List<TestAlbum>, order: List<Long>) =
        AlbumArrangement.split(sorted, order, TestAlbum::id, TestAlbum::sourceIds)

    private fun mergeOrder(previous: List<Long>, draft: List<TestAlbum>) =
        AlbumArrangement.mergeOrder(previous, draft, TestAlbum::id, TestAlbum::sourceIds)

    private val List<TestAlbum>.ids get() = map { it.id }

    @Test
    fun emptyOrderKeepsSortedListWithoutArrangement() {
        val sorted = listOf(album(3), album(1), album(2))
        val result = split(sorted, emptyList())
        assertFalse(result.hasArrangement)
        assertEquals(sorted, result.others)
    }

    @Test
    fun arrangedAlbumsFollowStoredOrderRegardlessOfSort() {
        val order = listOf(2L, 3L, 1L)
        val byDate = split(listOf(album(3), album(2), album(1)), order)
        val byName = split(listOf(album(1), album(2), album(3)), order)
        assertEquals(order, byDate.arranged.ids)
        assertEquals(order, byName.arranged.ids)
        assertTrue(byDate.others.isEmpty())
    }

    @Test
    fun newAlbumsLandInOthersInSortOrder() {
        val sorted = listOf(album(5), album(1), album(4), album(2))
        val result = split(sorted, listOf(2L, 1L))
        assertEquals(listOf(2L, 1L), result.arranged.ids)
        assertEquals(listOf(5L, 4L), result.others.ids)
    }

    @Test
    fun mergedAlbumMatchesAnySourceIdOnce() {
        // The merged album took the id of its newest source after a library change.
        val merged = album(20, merged = listOf(10L, 20L))
        val result = split(listOf(album(1), merged), listOf(10L, 1L, 20L))
        assertEquals(listOf(20L, 1L), result.arranged.ids)
        assertTrue(result.others.isEmpty())
    }

    @Test
    fun staleIdsAreSkipped() {
        val result = split(listOf(album(1), album(2)), listOf(99L, 2L, 1L))
        assertEquals(listOf(2L, 1L), result.arranged.ids)
    }

    @Test
    fun mergeOrderKeepsHiddenAlbumsInTheirSlot() {
        // Album 2 is pinned (absent from the draft) and sat after album 1.
        val previous = listOf(1L, 2L, 3L)
        val draft = listOf(album(3), album(1), album(4))
        val order = mergeOrder(previous, draft)
        assertEquals(listOf(3L, 1L, 2L, 4L), order)
        // When album 2 returns it is placed right after album 1 again.
        val back = split(listOf(album(1), album(2), album(3), album(4)), order)
        assertEquals(listOf(3L, 1L, 2L, 4L), back.arranged.ids)
    }

    @Test
    fun mergeOrderPutsLeadingHiddenAlbumsFirst() {
        val order = mergeOrder(listOf(9L, 1L, 2L), listOf(album(2), album(1)))
        assertEquals(listOf(9L, 2L, 1L), order)
    }

    @Test
    fun mergeOrderDoesNotDuplicateMergedSourceIds() {
        val merged = album(20, merged = listOf(10L, 20L))
        val order = mergeOrder(listOf(10L, 1L), listOf(album(1), merged))
        assertEquals(listOf(1L, 20L), order)
    }

    @Test
    fun moveShiftsElementsBetween() {
        val list = mutableListOf(1, 2, 3, 4)
        list.move(0, 2)
        assertEquals(listOf(2, 3, 1, 4), list)
        list.move(3, 0)
        assertEquals(listOf(4, 2, 3, 1), list)
        list.move(0, 9)
        assertEquals(listOf(4, 2, 3, 1), list)
    }
}
