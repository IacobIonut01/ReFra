/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.storycards

import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.domain.model.StoryCardsConfig
import com.dot.gallery.feature.timeline.mergeReadyStoryCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryCardsMergePolicyTest {
    @Test
    fun loadedTimelineWaitsForTheFirstStoryGeneration() {
        assertNull(mergeReadyStoryCards(StoryCardsConfig(), false, true, null, emptyList(), emptyList()))
    }

    @Test
    fun loadingTimelineDoesNotPublishAnEmptyHeader() {
        assertNull(mergeReadyStoryCards(StoryCardsConfig(), true, true, emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun disabledStoriesDoNotReserveLoadingSpace() {
        assertEquals(
            emptyList<StoryCard>(),
            mergeReadyStoryCards(StoryCardsConfig(enabled = false), true, true, null, emptyList(), emptyList()),
        )
    }

    @Test
    fun emptyTimelineDoesNotReserveLoadingSpace() {
        assertEquals(
            emptyList<StoryCard>(),
            mergeReadyStoryCards(StoryCardsConfig(), false, false, null, emptyList(), emptyList()),
        )
    }

    @Test
    fun completedEmptyGenerationRemovesThePlaceholder() {
        assertEquals(
            emptyList<StoryCard>(),
            mergeReadyStoryCards(StoryCardsConfig(), false, true, emptyList(), emptyList(), emptyList()),
        )
    }

    @Test
    fun readyStoriesRetainTheConfiguredOrderAcrossSources() {
        val album = StoryCard(id = 1L, type = StoryCardType.ALBUMS, title = "Nature")
        val favorite = StoryCard(id = 2L, type = StoryCardType.FAVORITES, title = "Favorites")
        val category = StoryCard(id = 3L, type = StoryCardType.CATEGORIES, title = "Flowers")
        val cloud = StoryCard(id = 4L, type = StoryCardType.CLOUD_MEMORIES, title = "On this day")
        assertEquals(
            listOf(favorite, album, category, cloud),
            mergeReadyStoryCards(
                config = StoryCardsConfig(cardOrder = listOf(StoryCardType.FAVORITES, StoryCardType.ALBUMS)),
                isTimelineLoading = false,
                hasMedia = true,
                localCards = listOf(album, favorite),
                categoryCards = listOf(category),
                cloudCards = listOf(cloud),
            ),
        )
    }
}
