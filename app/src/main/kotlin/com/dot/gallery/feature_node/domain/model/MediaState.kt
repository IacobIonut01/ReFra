package com.dot.gallery.feature_node.domain.model

import androidx.compose.runtime.Stable

@Stable
data class MediaState<Type: Media>(
    val media: List<Type> = emptyList(),
    val pagerMedia: List<Type> = emptyList(),
    val mediaGroups: Map<Long, List<Type>> = emptyMap(),
    val mappedMedia: List<MediaItem<Type>> = emptyList(),
    val mappedMediaWithMonthly: List<MediaItem<Type>> = emptyList(),
    val headers: List<MediaItem.Header<Type>> = emptyList(),
    val dateHeader: String = "",
    val error: String = "",
    val isLoading: Boolean = true
)