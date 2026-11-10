/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.policy

/**
 * Maps a BCP 47 or Android locale tag to the catalog's language key: language plus a script only where
 * the script changes the pack. Regions never matter (`ru-BY` and `ru-RU` are both `ru`); Chinese picks
 * its script from the region when none is given (`zh-TW` is Traditional); Serbian defaults to Cyrillic.
 */
object OcrLocaleKey {
    private val legacy = mapOf("iw" to "he", "in" to "id", "ji" to "yi", "no" to "nb", "tl" to "fil", "mo" to "ro")
    private val traditionalRegions = setOf("TW", "HK", "MO")

    fun of(tag: String): String {
        val parts = tag.replace('_', '-').split('-').filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""
        val language = parts[0].lowercase().let { legacy[it] ?: it }
        val script = parts.drop(1).firstOrNull { it.length == 4 && it.all(Char::isLetter) }
            ?.lowercase()?.replaceFirstChar { it.uppercase() }
        val region = parts.drop(1).firstOrNull { (it.length == 2 && it.all(Char::isLetter)) || (it.length == 3 && it.all(Char::isDigit)) }
            ?.uppercase()
        return when (language) {
            "zh" -> if (script == "Hant" || (script == null && region in traditionalRegions)) "zh-Hant" else "zh"
            "sr" -> if (script == "Latn") "sr-Latn" else "sr"
            else -> language
        }
    }
}
