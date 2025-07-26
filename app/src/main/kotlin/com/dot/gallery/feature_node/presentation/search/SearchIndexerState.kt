package com.dot.gallery.feature_node.presentation.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchIndexerState(
    val isIndexing: Boolean = false,
    val progress: Float = 0f,
)
