/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

/**
 * A language pack: one recognizer (plus the shared detector through its dependencies).
 *
 * [trainedLocales] lists what PaddleOCR trained the model on; [locales] is the subset whose CLDR letters
 * are all in the dictionary, derived by the coverage test and never edited by hand. A pack is suggested
 * for a language only through [locales]; when several packs cover a language the lowest
 * [suggestPriority] wins.
 */
@Serializable
data class OcrCatalogPack(
    val id: String,
    /** English title, for the published pack list; the app shows localized titles from its strings. */
    val title: String,
    val rootModelIds: List<String>,
    val scripts: List<String>,
    val suggestPriority: Int,
    val trainedLocales: List<String>,
    val locales: List<String>,
    /** Per locale, exemplar letters a pack may lack (obsolete kana, a combining accent on Dutch ij). */
    val coverageExceptions: Map<String, String> = emptyMap(),
)
