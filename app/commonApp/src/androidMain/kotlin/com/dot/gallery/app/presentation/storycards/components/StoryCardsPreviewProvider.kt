/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.storycards.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType

internal class StoryCardsPreviewProvider : PreviewParameterProvider<List<StoryCard>?> {
    private val cards = listOf(
        StoryCard(id = 1L, type = StoryCardType.MEMORIES, title = "On this day", subtitle = "A year ago"),
        StoryCard(id = 2L, type = StoryCardType.LOCATIONS, title = "A weekend at the coast", subtitle = "12 items"),
        StoryCard(id = 3L, type = StoryCardType.FAVORITES, title = "Your favorites", subtitle = "8 items"),
        StoryCard(id = 4L, type = StoryCardType.ALBUMS, title = "Camera", subtitle = "24 items"),
    )
    override val values = sequenceOf(null, emptyList(), cards.take(1), cards.take(2), cards)
}
