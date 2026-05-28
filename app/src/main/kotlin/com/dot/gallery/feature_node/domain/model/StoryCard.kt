/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.model

import android.net.Uri
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/**
 * Represents the type of a Story Card displayed above the timeline.
 */
@Serializable
enum class StoryCardType {
    MEMORIES,
    ALBUMS,
    CATEGORIES,
    LOCATIONS,
    FAVORITES
}

/**
 * Configuration for which Story Card types are enabled and their display order.
 */
@Serializable
data class StoryCardsConfig(
    val enabled: Boolean = true,
    val cardOrder: List<StoryCardType> = StoryCardType.entries.toList(),
    val disabledTypes: Set<StoryCardType> = emptySet()
) {
    val activeTypes: List<StoryCardType>
        get() = if (enabled) cardOrder.filter { it !in disabledTypes } else emptyList()
}

/**
 * Represents a single Story Card to be displayed in the timeline carousel.
 * Each card has a type, title, subtitle, thumbnail, and the backing media list
 * that will be shown in the story viewer.
 */
@Stable
data class StoryCard(
    val id: Long,
    val type: StoryCardType,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUri: Uri? = null,
    val thumbnailMedia: Media.UriMedia? = null,
    val mediaList: List<Media.UriMedia> = emptyList(),
    val albumId: Long? = null,
    val categoryId: Long? = null,
    val locationCity: String? = null,
    val locationCountry: String? = null,
    val year: Int? = null
)
