/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSelectorImplTest {
    private val state = MediaState<UriMedia>()

    @Test
    fun startSelectionIsActiveBeforeAnythingIsPicked() {
        val selector = MediaSelectorImpl()

        selector.startSelection()

        assertTrue(selector.isSelectionActive.value)
        assertEquals(emptySet<Long>(), selector.selectedMedia.value)
    }

    @Test
    fun startedSelectionSurvivesDeselectingEveryItem() {
        val selector = MediaSelectorImpl()
        selector.startSelection()

        selector.toggleSelectionById(state, 1L)
        selector.toggleSelectionById(state, 1L)
        selector.addToSelection(listOf(2L))
        selector.removeFromSelection(listOf(2L))
        selector.rawUpdateSelection(emptySet())

        assertTrue(selector.isSelectionActive.value)
    }

    @Test
    fun clearSelectionEndsTheStartedMode() {
        val selector = MediaSelectorImpl()
        selector.startSelection()
        selector.clearSelection()

        selector.toggleSelectionById(state, 1L)
        selector.toggleSelectionById(state, 1L)

        assertFalse(selector.isSelectionActive.value)
    }

    @Test
    fun deselectAllKeepsALongPressSelectionOpen() {
        // Select all then Deselect all must not end the selection (issue #1433).
        val selector = MediaSelectorImpl()
        selector.toggleSelectionById(state, 1L)
        selector.addToSelection(listOf(2L, 3L))

        selector.deselectAll()

        assertTrue(selector.isSelectionActive.value)
        assertEquals(emptySet<Long>(), selector.selectedMedia.value)
    }

    @Test
    fun deselectAllThenPickingAgainStaysOpenUntilClosed() {
        val selector = MediaSelectorImpl()
        selector.toggleSelectionById(state, 1L)
        selector.deselectAll()

        selector.toggleSelectionById(state, 2L)
        selector.toggleSelectionById(state, 2L)
        assertTrue(selector.isSelectionActive.value)

        selector.clearSelection()
        assertFalse(selector.isSelectionActive.value)
    }

    @Test
    fun longPressSelectionStillEndsWithItsLastItem() {
        val selector = MediaSelectorImpl()

        selector.toggleSelectionById(state, 1L)
        assertTrue(selector.isSelectionActive.value)
        selector.toggleSelectionById(state, 1L)

        assertFalse(selector.isSelectionActive.value)
    }
}
