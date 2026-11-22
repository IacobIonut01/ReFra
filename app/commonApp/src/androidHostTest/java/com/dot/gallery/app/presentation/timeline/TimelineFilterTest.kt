/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline

import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.util.OrderType
import com.dot.gallery.feature.timeline.DefaultTimelineSort
import com.dot.gallery.feature.timeline.decodeTimelineFilter
import com.dot.gallery.feature.timeline.encodeTimelineFilter
import com.dot.gallery.feature.timeline.resolveTimelineAlbumSourceIds
import com.dot.gallery.feature.timeline.filterTimelineItems
import com.dot.gallery.feature.timeline.filterWithAvailableAlbums
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.timeline.sortToCommit
import com.dot.gallery.feature.timeline.timelineControlsActive
import com.dot.gallery.domain.model.MediaTypeFilter
import com.dot.gallery.domain.model.TimelineFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.domain.util.isFavorite
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isVideo


class TimelineFilterTest {
    @Test
    fun mergedAlbumSelectionExpandsToSourceAlbumIds() {
        assertEquals(
            setOf(10L, 11L),
            resolveTimelineAlbumSourceIds(
                selectedAlbumIds = setOf(99L),
                sourceIdsByAlbumId = mapOf(99L to setOf(10L, 11L)),
            )
        )
    }

    @Test
    fun unsupportedAlbumSelectionDoesNotHideAllMedia() {
        val items = listOf(TestMedia(1L, albumId = 10L), TestMedia(2L, albumId = 11L))
        val filter = TimelineFilter(selectedAlbumIds = setOf(-500L))

        assertEquals(
            items,
            filter(items, filter, selectedAlbumSourceIds = emptySet()),
        )
    }

    @Test
    fun activeFiltersApplyTogether() {
        val matching = TestMedia(
            id = 1L,
            albumId = 10L,
            image = true,
            favorite = true,
            timestampSeconds = 1_704_067_200L,
        )
        val items = listOf(
            matching,
            matching.copy(id = 2L, favorite = false),
            matching.copy(id = 3L, image = false, video = true),
            matching.copy(id = 4L, albumId = 12L),
        )
        val filter = TimelineFilter(
            mediaType = MediaTypeFilter.PHOTOS,
            favoritesOnly = true,
            selectedYears = setOf(2024),
            selectedAlbumIds = setOf(99L),
        )

        assertEquals(listOf(matching), filter(items, filter, setOf(10L, 11L)))
    }

    @Test
    fun sortIsCommittedOnCloseOnlyWhenItChanged() {
        val saved = LastSort(OrderType.Descending, FilterKind.DATE)
        assertNull(sortToCommit(saved, saved))
        val oldest = saved.copy(orderType = OrderType.Ascending)
        assertEquals(oldest, sortToCommit(saved, oldest))
    }

    @Test
    fun controlsAreActiveForAFilterOrANonDefaultSort() {
        assertFalse(timelineControlsActive(TimelineFilter(), DefaultTimelineSort))
        assertTrue(timelineControlsActive(TimelineFilter(mediaType = MediaTypeFilter.VIDEOS), DefaultTimelineSort))
        assertTrue(
            timelineControlsActive(TimelineFilter(), DefaultTimelineSort.copy(kind = FilterKind.DATE_MODIFIED))
        )
    }

    @Test
    fun pageFilterSurvivesSaveAndRestore() {
        val filter = TimelineFilter(
            mediaType = MediaTypeFilter.PHOTOS,
            favoritesOnly = true,
            selectedYears = setOf(2024, 2025),
            selectedAlbumIds = setOf(10L, -4L),
        )
        assertEquals(filter, decodeTimelineFilter(encodeTimelineFilter(filter)))
        assertEquals(TimelineFilter(), decodeTimelineFilter(emptyList()))
    }

    @Test
    fun rememberedFilterSurvivesThePreference() {
        val key = SettingsKeys.savedTimelineFilter
        val filter = TimelineFilter(
            mediaType = MediaTypeFilter.VIDEOS,
            favoritesOnly = true,
            selectedYears = setOf(2023),
            selectedAlbumIds = setOf(42L, -7L),
        )
        assertEquals(filter, key.decode(key.encode(filter)))
        assertEquals(TimelineFilter(), key.decode(key.encode(TimelineFilter())))
    }

    @Test
    fun rememberedFilterIgnoresTheDerivedActiveFlag() {
        val encoded = SettingsKeys.savedTimelineFilter.encode(TimelineFilter(favoritesOnly = true))
        assertFalse(encoded.contains("isActive"))
    }

    @Test
    fun rememberedFilterFillsMissingFieldsWithDefaults() {
        assertEquals(
            TimelineFilter(selectedYears = setOf(2020)),
            SettingsKeys.savedTimelineFilter.decode("""{"selectedYears":[2020]}"""),
        )
    }

    @Test
    fun unreadableRememberedFilterFallsBackToNoFilter() {
        val key = SettingsKeys.savedTimelineFilter
        assertEquals(TimelineFilter(), key.decode("not json"))
        assertEquals(TimelineFilter(), key.decode("""{"mediaType":"DOCUMENTS"}"""))
        assertFalse(key.decode("").isActive)
    }

    @Test
    fun rememberedFilterDropsAlbumsThatNoLongerExist() {
        val filter = TimelineFilter(favoritesOnly = true, selectedAlbumIds = setOf(1L, 2L))
        assertEquals(
            filter.copy(selectedAlbumIds = setOf(2L)),
            filterWithAvailableAlbums(filter, availableAlbumIds = setOf(2L, 3L)),
        )
        assertTrue(filterWithAvailableAlbums(filter, setOf(1L, 2L)) === filter)
    }

    @Test
    fun indicatorClearsOnceOnlyMissingAlbumsWereRemembered() {
        val remembered = TimelineFilter(selectedAlbumIds = setOf(99L))
        assertTrue(timelineControlsActive(remembered, DefaultTimelineSort))
        val pruned = filterWithAvailableAlbums(remembered, availableAlbumIds = setOf(1L))
        assertFalse(timelineControlsActive(pruned, DefaultTimelineSort))
    }

    private fun filter(
        items: List<TestMedia>,
        filter: TimelineFilter,
        selectedAlbumSourceIds: Set<Long>,
    ) = filterTimelineItems(
        items = items,
        filter = filter,
        selectedAlbumSourceIds = selectedAlbumSourceIds,
        isImage = TestMedia::image,
        isVideo = TestMedia::video,
        isFavorite = TestMedia::favorite,
        timestampSeconds = TestMedia::timestampSeconds,
        albumId = TestMedia::albumId,
    )

    private data class TestMedia(
        val id: Long,
        val albumId: Long,
        val image: Boolean = true,
        val video: Boolean = false,
        val favorite: Boolean = false,
        val timestampSeconds: Long = 0L,
    )
}
