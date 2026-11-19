/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail


class RefinedThumbnailLoadTracker(private val capacity: Int) {
    // Insertion-ordered map; a hit re-inserts at the tail so keys.first() is always the LRU entry.
    private val loadedKeys = LinkedHashMap<RefinedThumbnailKey, Unit>()

    init {
        require(capacity > 0)
    }

    fun hasLoaded(key: RefinedThumbnailKey): Boolean {
        if (key.model == null && key.signature.isEmpty()) return false
        return loadedKeys.remove(key)?.let { loadedKeys[key] = Unit; true } ?: false
    }

    fun markLoaded(key: RefinedThumbnailKey) {
        if (key.model == null && key.signature.isEmpty()) return
        loadedKeys.remove(key)
        loadedKeys[key] = Unit
        while (loadedKeys.size > capacity) {
            loadedKeys.remove(loadedKeys.keys.first())
        }
    }
}
