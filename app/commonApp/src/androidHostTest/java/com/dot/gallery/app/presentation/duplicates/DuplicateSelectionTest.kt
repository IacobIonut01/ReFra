/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.duplicates

import com.dot.gallery.core.duplicates.DuplicateSet
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateSelectionTest {

    private val id: (Long) -> Long = { it }
    private val set = DuplicateSet(sha1 = "s", items = listOf(1L, 2L, 3L), keeper = 1L, itemSize = 10)
    private val other = DuplicateSet(sha1 = "o", items = listOf(4L, 5L), keeper = 5L, itemSize = 10)

    @Test
    fun everyCopyButTheKeeperStartsSelected() {
        assertEquals(listOf(2L, 3L, 4L), selectedCopies(listOf(set, other), DuplicateChoices(), id))
    }

    @Test
    fun togglingLeavesACopyAloneAndTheKeeperCannotBeSelected() {
        val choices = DuplicateChoices().toggle(set, 2L, id).toggle(set, 1L, id)
        assertEquals(listOf(3L), set.selected(choices, id))
        assertEquals(listOf(2L, 3L), set.selected(choices.toggle(set, 2L, id), id))
    }

    @Test
    fun keepingAnotherCopyMovesTheBadgeAndSelectsTheOldKeeper() {
        val choices = DuplicateChoices().keep(set, 3L, id)
        assertEquals(3L, set.keeperId(choices, id))
        assertEquals(listOf(1L, 2L), set.selected(choices, id))
    }

    @Test
    fun keepingACopyTheUserHadLeftAloneClearsThatChoice() {
        val choices = DuplicateChoices().toggle(set, 2L, id).keep(set, 2L, id)
        assertEquals(2L, set.keeperId(choices, id))
        assertEquals(listOf(1L, 3L), set.selected(choices, id))
    }

    @Test
    fun aKeeperPickThatLeftTheSetFallsBackToTheSuggestion() {
        val choices = DuplicateChoices(keeperOverrides = mapOf("s" to 9L))
        assertEquals(1L, set.keeperId(choices, id))
    }

    @Test
    fun folderLabelsUseAlbumNamesAndPathsWhereNamesClash() {
        data class Copy(val id: Long, val album: String, val path: String)
        val copies = listOf(
            Copy(1, "Trip", "Pictures/Trip/"),
            Copy(2, "Trip", "Download/Trip/"),
            Copy(3, "Camera", "DCIM/Camera/"),
            Copy(4, "", "/"),
        )
        val labels = folderLabels(copies, { it.id }, { it.album }, { it.path })
        assertEquals("Pictures/Trip", labels[1L])
        assertEquals("Download/Trip", labels[2L])
        assertEquals("Camera", labels[3L])
        assertEquals("", labels[4L])
    }
}
