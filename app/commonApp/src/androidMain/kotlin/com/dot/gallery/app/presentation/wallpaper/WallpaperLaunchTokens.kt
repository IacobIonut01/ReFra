/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.wallpaper

import java.util.UUID

/**
 * One-time, URI-bound proof that an ATTACH_DATA launch originated inside ReFra.
 *
 * The generic "Use as" chooser forwards every extra to whichever handler the user picks. That is
 * safe here because a leaked token only re-proves the exact URI the handler already received; it
 * cannot be rebound to another private file and is consumed on first use.
 */
internal object WallpaperLaunchTokens {
    const val EXTRA_TOKEN = "com.dot.gallery.extra.WALLPAPER_LAUNCH_TOKEN"

    private const val MAX_PENDING_TOKENS = 16
    private val lock = Any()
    private val pending = object : LinkedHashMap<String, String>(MAX_PENDING_TOKENS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_PENDING_TOKENS
    }

    fun issue(uri: String): String {
        val token = UUID.randomUUID().toString()
        synchronized(lock) {
            pending[token] = uri
        }
        return token
    }

    fun consume(uri: String, token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        return synchronized(lock) {
            if (pending[token] != uri) {
                false
            } else {
                pending.remove(token)
                true
            }
        }
    }
}
