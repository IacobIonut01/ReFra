/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library

import com.dot.gallery.feature.library.ui.ExploreArrangement
import com.dot.gallery.feature.library.ui.LibraryHeaderLayout
import com.dot.gallery.feature.library.ui.LibrarySections
import com.dot.gallery.feature.library.ui.exploreArrangement
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryExploreArrangementTest {

    private val all = LibrarySections(locations = true, people = true, categories = true)
    private val none = LibrarySections(locations = false, people = false, categories = false)

    @Test
    fun `nothing to show renders no block in any layout`() {
        LibraryHeaderLayout.entries.forEach { layout ->
            assertEquals(ExploreArrangement.NONE, exploreArrangement(none, layout))
        }
    }

    @Test
    fun `all three on a phone put the map beside the other two`() {
        assertEquals(
            ExploreArrangement.TALL_MAP_BESIDE_PAIR,
            exploreArrangement(all, LibraryHeaderLayout.COMPACT),
        )
    }

    @Test
    fun `one or two sections on a phone share a row`() {
        listOf(
            all.copy(locations = false),
            all.copy(people = false),
            all.copy(categories = false),
            none.copy(locations = true),
            none.copy(people = true),
            none.copy(categories = true),
        ).forEach { sections ->
            assertEquals(ExploreArrangement.ROW, exploreArrangement(sections, LibraryHeaderLayout.COMPACT))
        }
    }

    @Test
    fun `wide windows always use a row and the side pane always stacks`() {
        assertEquals(ExploreArrangement.ROW, exploreArrangement(all, LibraryHeaderLayout.WIDE))
        assertEquals(ExploreArrangement.STACKED, exploreArrangement(all, LibraryHeaderLayout.SIDE_PANE))
        assertEquals(
            ExploreArrangement.STACKED,
            exploreArrangement(none.copy(people = true), LibraryHeaderLayout.SIDE_PANE),
        )
    }
}
