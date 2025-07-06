package com.dot.gallery.core

import androidx.compose.runtime.compositionLocalOf
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import kotlinx.coroutines.flow.MutableStateFlow

val LocalMediaSelector = compositionLocalOf<MediaSelector> {
    error("No MediaSelector provided!!! This is likely due to a missing Hilt injection in the Composable hierarchy.")
}

class MediaSelectorImpl : MediaSelector {

    override val selectedMedia = MutableStateFlow<Set<Long>>(emptySet())
    override val isSelectionActive = MutableStateFlow(false)

    override fun <T: Media> toggleSelection(
        mediaState: MediaState<T>,
        index: Int
    ) {
        val item = mediaState.media[index]
        val selectedPhoto = selectedMedia.value.find { it == item.id }
        val newSelection = if (selectedPhoto != null) {
            selectedMedia.value.toMutableSet().apply { remove(selectedPhoto) }
        } else {
            selectedMedia.value.toMutableSet().apply { add(item.id) }
        }
        selectedMedia.tryEmit(newSelection)
        isSelectionActive.value = newSelection.isNotEmpty()
    }

    override fun addToSelection(list: List<Long>) {
        val newSelection = selectedMedia.value.toMutableSet().apply { addAll(list) }
        selectedMedia.tryEmit(newSelection)
        isSelectionActive.value = newSelection.isNotEmpty()
    }

    override fun removeFromSelection(list: List<Long>) {
        val newSelection = selectedMedia.value.toMutableSet().apply { removeAll(list) }
        selectedMedia.tryEmit(newSelection)
        isSelectionActive.value = newSelection.isNotEmpty()
    }

    override fun rawUpdateSelection(list: Set<Long>) {
        selectedMedia.tryEmit(list)
        isSelectionActive.tryEmit(list.isNotEmpty())
    }

    override fun clearSelection() {
        selectedMedia.tryEmit(emptySet())
        isSelectionActive.value = false
    }
}