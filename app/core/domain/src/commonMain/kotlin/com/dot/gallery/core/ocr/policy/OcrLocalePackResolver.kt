/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.policy

import com.dot.gallery.core.ocr.catalog.OcrModelCatalog

/**
 * Suggests packs for the device and app languages.
 *
 * Each language gets the covering pack with the lowest `suggestPriority`, so Russian, Ukrainian and
 * Belarusian get the East Slavic pack (as PaddleOCR routes them) while Bulgarian, Serbian and
 * Macedonian get the broad Cyrillic one. The English-only pack is suggested only when every language
 * is English. When a non-Latin pack is suggested, the Latin pack is added too, because signs, receipts
 * and screenshots often mix scripts.
 */
object OcrLocalePackResolver {
    const val LATIN = "latin"
    const val ENGLISH = "english"

    fun bestPack(tag: String, catalog: OcrModelCatalog): String? {
        val key = OcrLocaleKey.of(tag)
        return catalog.packs
            .filter { key in it.locales && it.id != ENGLISH }
            .minByOrNull { it.suggestPriority }
            ?.id
    }

    fun suggest(languageTags: List<String>, catalog: OcrModelCatalog): OcrPackSuggestion {
        val keys = languageTags.map(OcrLocaleKey::of).filter { it.isNotEmpty() }.distinct()
        if (keys.isNotEmpty() && keys.all { it == "en" } && catalog.pack(ENGLISH) != null) {
            return OcrPackSuggestion(setOf(ENGLISH), emptySet())
        }
        val packs = LinkedHashSet<String>()
        val unsupported = LinkedHashSet<String>()
        for (key in keys) {
            val pack = bestPack(key, catalog)
            if (pack == null) unsupported += key else packs += pack
        }
        if (packs.any { it != LATIN } && catalog.pack(LATIN) != null) packs += LATIN
        return OcrPackSuggestion(packs, unsupported)
    }
}
