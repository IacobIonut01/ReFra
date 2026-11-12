/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import kotlinx.coroutines.flow.MutableStateFlow

interface MediaSelector {
    val selectedMedia: MutableStateFlow<Set<Long>>
    val isSelectionActive: MutableStateFlow<Boolean>

    fun clearSelection()

    /**
     * Enters selection mode before anything is picked, so taps on media select them. The mode
     * stays active with an empty selection until [clearSelection].
     */
    fun startSelection()

    /**
     * Empties the selection but keeps selection mode active, as [startSelection] does, so only
     * an explicit close ([clearSelection]) ends it.
     */
    fun deselectAll()

    fun <T: Media> toggleSelection(
        mediaState: MediaState<T>,
        index: Int
    )

    fun <T: Media> toggleSelectionById(
        mediaState: MediaState<T>,
        mediaId: Long
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