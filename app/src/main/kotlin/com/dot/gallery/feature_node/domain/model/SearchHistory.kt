package com.dot.gallery.feature_node.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistory(
    val timestamp: Long,
    val query: String,
)