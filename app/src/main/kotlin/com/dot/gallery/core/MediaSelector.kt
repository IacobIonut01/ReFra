package com.dot.gallery.core

import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import kotlinx.coroutines.flow.MutableStateFlow

interface MediaSelector {
    val selectedMedia: MutableStateFlow<Set<Long>>
    val isSelectionActive: MutableStateFlow<Boolean>

    fun clearSelection()

    fun <T: Media> toggleSelection(
        mediaState: MediaState<T>,
        index: Int
    )

    fun addToSelection(
        list: List<Long>
    )

    fun removeFromSelection(
        list: List<Long>
    )

    fun rawUpdateSelection(
        list: Set<Long>
    )
}