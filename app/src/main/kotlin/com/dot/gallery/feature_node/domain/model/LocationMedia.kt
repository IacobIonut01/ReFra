package com.dot.gallery.feature_node.domain.model

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class LocationMedia(
    val media: Media,
    val location: String
)