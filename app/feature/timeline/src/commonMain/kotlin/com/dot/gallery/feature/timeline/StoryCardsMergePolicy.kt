/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.timeline

import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.domain.model.StoryCardsConfig

/** Null means generation is pending; an empty list means there are no highlights to display. */
fun mergeReadyStoryCards(
    config: StoryCardsConfig,
    isTimelineLoading: Boolean,
    hasMedia: Boolean,
    localCards: List<StoryCard>?,
    categoryCards: List<StoryCard>,
    cloudCards: List<StoryCard>,
): List<StoryCard>? {
    if (!config.enabled) return emptyList()
    if (isTimelineLoading) return null
    if (!hasMedia) return emptyList()
    if (localCards == null) return null
    return config.activeTypes.flatMap { type ->
        when (type) {
            StoryCardType.CATEGORIES -> categoryCards
            StoryCardType.CLOUD_MEMORIES -> cloudCards
            else -> localCards.filter { it.type == type }
        }
    }
}
