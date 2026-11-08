/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.util

import com.dot.gallery.domain.model.Album

object AlbumArrangement {

    fun split(sortedAlbums: List<Album>, order: List<Long>): ArrangedAlbums<Album> =
        split(sortedAlbums, order, Album::id, Album::sourceAlbumIds)

    fun mergeOrder(previous: List<Long>, draft: List<Album>): List<Long> =
        mergeOrder(previous, draft, Album::id, Album::sourceAlbumIds)

    /**
     * Splits [sortedAlbums] using [order]. An empty [order] means nothing is arranged and every
     * album stays in sort order. Stored ids match an album's id or any of its merged source ids,
     * so a merged album keeps its slot when the source that names it changes.
     */
    fun <T> split(
        sortedAlbums: List<T>,
        order: List<Long>,
        idOf: (T) -> Long,
        sourceIdsOf: (T) -> List<Long>,
    ): ArrangedAlbums<T> {
        if (order.isEmpty() || sortedAlbums.isEmpty()) return ArrangedAlbums(emptyList(), sortedAlbums)
        val byId = HashMap<Long, T>(sortedAlbums.size * 2)
        for (album in sortedAlbums) {
            byId.getOrPut(idOf(album)) { album }
            for (sourceId in sourceIdsOf(album)) byId.getOrPut(sourceId) { album }
        }
        val placed = HashSet<Long>()
        val arranged = ArrayList<T>()
        for (id in order) {
            val album = byId[id] ?: continue
            if (placed.add(idOf(album))) arranged += album
        }
        val others = sortedAlbums.filter { idOf(it) !in placed }
        return ArrangedAlbums(arranged, others)
    }

    /**
     * Builds the order to persist from the edited [draft]. Ids in [previous] that no draft album
     * covers (pinned, hidden or temporarily missing albums) are kept after the id that preceded
     * them, so those albums return to their slot when they reappear in the list.
     */
    fun <T> mergeOrder(
        previous: List<Long>,
        draft: List<T>,
        idOf: (T) -> Long,
        sourceIdsOf: (T) -> List<Long>,
    ): List<Long> {
        val result = draft.mapTo(ArrayList(), idOf)
        val ownerOf = HashMap<Long, Long>()
        draft.forEach { album ->
            val id = idOf(album)
            ownerOf[id] = id
            sourceIdsOf(album).forEach { ownerOf.getOrPut(it) { id } }
        }
        var anchor: Long? = null
        for (id in previous) {
            val owner = ownerOf[id]
            if (owner != null) {
                anchor = owner
                continue
            }
            if (id in result) continue
            val insertAt = anchor?.let { result.indexOf(it) + 1 } ?: 0
            result.add(insertAt, id)
            anchor = id
        }
        return result
    }
}

/** Moves the element at [from] to [to], shifting the elements in between. */
fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}
