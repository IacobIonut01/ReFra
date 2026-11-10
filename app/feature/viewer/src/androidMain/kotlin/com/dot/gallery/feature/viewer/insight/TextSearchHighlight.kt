/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.Normalizer
import java.util.Locale

/**
 * The search query a photo was opened from, so the viewer can highlight the lines that matched.
 * Set by Search just before it opens a "Text in photos" result and cleared when the viewer closes.
 */
object TextSearchHighlight {
    var query by mutableStateOf<String?>(null)

    /** Lowercased, accent-folded words of [query]; empty when nothing is highlighted. */
    fun tokens(query: String?): List<String> =
        query?.let(::fold)?.split(' ')?.filter(String::isNotBlank).orEmpty()

    /** Whether [line] contains any of [tokens]. */
    fun matches(line: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val text = fold(line)
        return tokens.any { it in text }
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ").trim()
}
