/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.domain

/**
 * Decides which help tips are worth showing in the photo search. A photo search is almost never
 * a question about the app, so the fuzzy ranking used by the Help screen is far too loose there:
 * "dog" scores against "debug" and a tip shows up under every query. Here a tip only counts when
 * each word of the query starts a word of the tip's title or keywords.
 */
object HelpTipMatch {

    /** Shorter queries ("ok", "a") would match the start of almost any word. */
    const val MIN_QUERY_LENGTH = 3

    /** Keeps the [candidates] that strongly match [query], in their original order. */
    fun strongMatches(candidates: List<HelpSearchItem>, query: String): List<HelpSearchItem> {
        val queryWords = words(query)
        if (queryWords.isEmpty() || query.trim().length < MIN_QUERY_LENGTH) return emptyList()
        return candidates.filter { isStrongMatch(it, queryWords) }
    }

    fun isStrongMatch(item: HelpSearchItem, query: String): Boolean {
        val queryWords = words(query)
        if (queryWords.isEmpty() || query.trim().length < MIN_QUERY_LENGTH) return false
        return isStrongMatch(item, queryWords)
    }

    private fun isStrongMatch(item: HelpSearchItem, queryWords: List<String>): Boolean {
        val itemWords = words(item.title) + words(item.keywords)
        return queryWords.all { q -> itemWords.any { it.startsWith(q) } }
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(NON_WORD).filter { it.isNotEmpty() }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
}
