/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import kotlinx.coroutines.flow.MutableStateFlow

class MediaSelectorImpl : MediaSelector {

    override val selectedMedia = MutableStateFlow<Set<Long>>(emptySet())
    override val isSelectionActive = MutableStateFlow(false)

    // Set by startSelection: the mode then survives deselecting every item.
    private var explicitMode = false

    private fun publish(selection: Set<Long>) {
        selectedMedia.tryEmit(selection)
        isSelectionActive.value = explicitMode || selection.isNotEmpty()
    }

    override fun <T: Media> toggleSelection(
        mediaState: MediaState<T>,
        index: Int
    ) {
        val item = mediaState.media[index]
        toggleSelectionById(mediaState, item.id)
    }

    override fun <T: Media> toggleSelectionById(
        mediaState: MediaState<T>,
        mediaId: Long
    ) {
        val groupIds = mediaState.mediaGroups[mediaId]?.map { it.id }
        val idsToToggle = groupIds ?: listOf(mediaId)
        val isCurrentlySelected = selectedMedia.value.contains(mediaId)
        val newSelection = if (isCurrentlySelected) {
            selectedMedia.value.toMutableSet().apply { removeAll(idsToToggle.toSet()) }
        } else {
            selectedMedia.value.toMutableSet().apply { addAll(idsToToggle) }
        }
        publish(newSelection)
    }

    override fun addToSelection(list: List<Long>) {
        publish(selectedMedia.value.toMutableSet().apply { addAll(list) })
    }

    override fun removeFromSelection(list: List<Long>) {
        publish(selectedMedia.value.toMutableSet().apply { removeAll(list) })
    }

    override fun rawUpdateSelection(list: Set<Long>) {
        publish(list)
    }

    override fun startSelection() {
        explicitMode = true
        isSelectionActive.value = true
    }

    override fun clearSelection() {
        explicitMode = false
        selectedMedia.tryEmit(emptySet())
        isSelectionActive.value = false
    }
}
