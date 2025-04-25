package com.dot.gallery.feature_node.domain.model

data class MediaMetadataState(
    val metadata: List<MediaMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingProgress: Int = 0,
)
