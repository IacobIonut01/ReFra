/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSearchMediaPoolTest {
    @Test
    fun refreshedResultsKeepOrderReplaceChangedItemsAndDropDeletedItems() {
        val oldFirst = TestMedia(id = 1L, timestamp = 1L)
        val oldSecond = TestMedia(id = 2L, timestamp = 2L)
        val updatedSecond = oldSecond.copy(timestamp = 20L)

        assertEquals(
            listOf(updatedSecond),
            reconcileSearchResults(
                previousResults = listOf(oldSecond, oldFirst),
                currentMedia = listOf(updatedSecond, TestMedia(id = 3L)),
                identity = TestMedia::id,
            )
        )
    }

    @Test
    fun dateProjectionSortsNewestFirstWithoutChangingRelevanceOrder() {
        val relevanceOrder = listOf(
            TestMedia(id = 1L, timestamp = 10L),
            TestMedia(id = 2L, timestamp = 30L),
            TestMedia(id = 3L, timestamp = 20L)
        )

        val dateOrder = dateOrderedSearchResults(relevanceOrder, TestMedia::timestamp)

        assertEquals(listOf(2L, 3L, 1L), dateOrder.map(TestMedia::id))
        assertEquals(listOf(1L, 2L, 3L), relevanceOrder.map(TestMedia::id))
    }

    @Test
    fun disabledSettingKeepsTimelinePoolUnchanged() {
        val timeline = listOf(TestMedia(id = 1L))

        assertEquals(
            timeline,
            smartSearchMediaPool(
                timelineMedia = timeline,
                completeLocalMedia = timeline + TestMedia(id = 2L, ignored = true),
                includeIgnoredAlbums = false,
                identity = TestMedia::id,
                isSearchableIgnoredMedia = { it.ignored && !it.locked },
                sort = ::sortMedia
            )
        )
    }

    @Test
    fun enabledSettingAddsOnlyTimelineHiddenUnlockedMedia() {
        val visible = TestMedia(id = 1L, timestamp = 1L)
        val ignored = TestMedia(id = 2L, ignored = true, timestamp = 4L)
        val ordinary = TestMedia(id = 3L, timestamp = 3L)
        val lockedIgnored = TestMedia(id = 4L, ignored = true, locked = true, timestamp = 2L)

        val result = smartSearchMediaPool(
            timelineMedia = listOf(visible),
            completeLocalMedia = listOf(visible, ignored, ordinary, lockedIgnored),
            includeIgnoredAlbums = true,
            identity = TestMedia::id,
            isSearchableIgnoredMedia = { it.ignored && !it.locked },
            sort = ::sortMedia
        )

        assertEquals(listOf(ignored, visible), result)
    }

    @Test
    fun visibleMediaIsNotDuplicatedWhenItAlsoMatchesAnIgnoredRule() {
        val visibleIgnored = TestMedia(id = 1L, ignored = true)

        val result = smartSearchMediaPool(
            timelineMedia = listOf(visibleIgnored),
            completeLocalMedia = listOf(visibleIgnored),
            includeIgnoredAlbums = true,
            identity = TestMedia::id,
            isSearchableIgnoredMedia = { it.ignored && !it.locked },
            sort = ::sortMedia
        )

        assertEquals(listOf(visibleIgnored), result)
    }

    private fun sortMedia(media: List<TestMedia>) = media.sortedByDescending(TestMedia::timestamp)

    private data class TestMedia(
        val id: Long,
        val ignored: Boolean = false,
        val locked: Boolean = false,
        val timestamp: Long = id
    )
}
