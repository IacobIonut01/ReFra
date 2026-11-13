/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.search

import com.dot.gallery.feature.help.domain.HelpSearchItem
import com.dot.gallery.feature.help.domain.HelpSearchKind
import com.frosch2010.fuzzywuzzy_kotlin.FuzzySearch
import com.frosch2010.fuzzywuzzy_kotlin.ToStringFunction
import com.dot.gallery.feature.help.domain.HelpSearchEngine

/**
 * Typo-tolerant [HelpSearchEngine] for the unified help search. Reuses the
 * [FuzzySearch] library already bundled in the app (see SearchViewModel); it is
 * JVM/Android-only, so this engine lives in `androidMain` — other targets fall
 * back to [com.dot.gallery.feature.help.domain.BasicHelpSearchEngine].
 *
 * The same matcher powers both the Help screen search bar and the "Help"
 * section injected into the timeline media search, guaranteeing one ranking
 * behavior across the app.
 */
object HelpFuzzyMatcher : HelpSearchEngine {

    /**
     * Returns [items] that match [query], ranked best-first. An empty query
     * yields an empty list (callers decide what to show for the empty state).
     */
    override fun search(
        items: List<HelpSearchItem>,
        query: String,
        limit: Int,
    ): List<HelpSearchItem> {
        val q = query.trim()
        if (q.isEmpty() || items.isEmpty()) return emptyList()

        val matches = FuzzySearch.extractSorted(
            query = q,
            choices = items,
            toStringFunction = object : ToStringFunction<HelpSearchItem> {
                override fun apply(item: HelpSearchItem): String = item.searchText
            },
            cutoff = 45
        )

        return matches
            .map { it.referent to boostedScore(it.referent, q, it.score) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Adds field-weighting and kind priority on top of the raw fuzzy score so
     * exact/prefix title matches outrank generic body matches.
     */
    private fun boostedScore(item: HelpSearchItem, query: String, base: Int): Int {
        val ql = query.lowercase()
        val title = item.title.lowercase()
        var score = base

        when {
            title == ql -> score += 70
            title.startsWith(ql) -> score += 45
            title.contains(ql) -> score += 28
        }
        if (item.subtitle.lowercase().contains(ql)) score += 12
        if (item.keywords.lowercase().contains(ql)) score += 10

        score += when (item.kind) {
            HelpSearchKind.TIP -> 6
            HelpSearchKind.SETTING -> 5
            HelpSearchKind.QUICK_ACTION -> 4
            HelpSearchKind.CHANGELOG -> 0
        }
        return score
    }
}
