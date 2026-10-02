/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

/**
 * Exact-key single-flight: concurrent callers for the same [key] serialize on one monitor, so
 * only the first runs [block]'s expensive work while the rest re-check the result afterwards.
 * Entries are removed as soon as their callers finish — ephemeral, never an unbounded map.
 * Same pattern as `NetFsThumbnailSingleFlight` (which lives in the flavor-gated `netfs` source
 * set and can't be shared from here).
 */
internal class ThumbnailSingleFlight {
    private class Entry(val monitor: Any = Any(), var users: Int = 0)

    private val entries = mutableMapOf<String, Entry>()

    fun <T> run(key: String, block: () -> T): T {
        val entry = synchronized(entries) {
            entries.getOrPut(key) { Entry() }.also { it.users++ }
        }
        return try {
            synchronized(entry.monitor) { block() }
        } finally {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0 && entries[key] === entry) entries.remove(key)
            }
        }
    }
}
