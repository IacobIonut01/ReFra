/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Composable
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ocr_languages_more
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_arabic
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_cjk
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_cyrillic
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_devanagari
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_english
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_english_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_greek
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_korean
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_latin
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_tamil
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_telugu
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_thai
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_title_tuned
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_tuned_summary
import org.jetbrains.compose.resources.stringResource

/** Pack whose recognizer is tuned for a few languages; its title names them. */
private const val TUNED_PACK = "eslav"
private const val ENGLISH_PACK = "english"

/** Languages shown before a pack's list is shortened to "… and N more". */
private const val SHOWN_LANGUAGES = 6

/** The localized title of a pack, named by writing system. */
@Composable
fun ocrPackTitle(id: String, languages: List<String> = emptyList()): String {
    val script = when (id) {
        "latin" -> stringResource(Res.string.ocr_pack_latin)
        ENGLISH_PACK -> stringResource(Res.string.ocr_pack_english)
        "cjk" -> stringResource(Res.string.ocr_pack_cjk)
        "korean" -> stringResource(Res.string.ocr_pack_korean)
        "cyrillic", TUNED_PACK -> stringResource(Res.string.ocr_pack_cyrillic)
        "arabic" -> stringResource(Res.string.ocr_pack_arabic)
        "devanagari" -> stringResource(Res.string.ocr_pack_devanagari)
        "greek" -> stringResource(Res.string.ocr_pack_greek)
        "thai" -> stringResource(Res.string.ocr_pack_thai)
        "tamil" -> stringResource(Res.string.ocr_pack_tamil)
        "telugu" -> stringResource(Res.string.ocr_pack_telugu)
        else -> id
    }
    return if (id == TUNED_PACK && languages.isNotEmpty()) {
        stringResource(Res.string.ocr_pack_title_tuned, script, languages.joinToString(", "))
    } else script
}

/**
 * The language line of a pack: its CLDR names, shortened after a few to "… and N more". The
 * person's own languages always stay in the shortened list, still in alphabetical order.
 */
@Composable
fun ocrPackLanguages(row: OcrPackRow): String = when (row.id) {
    ENGLISH_PACK -> stringResource(Res.string.ocr_pack_english_summary)
    TUNED_PACK -> stringResource(Res.string.ocr_pack_tuned_summary)
    else -> {
        val shown = row.languages.filterIndexed { index, name -> index < SHOWN_LANGUAGES || name in row.ownLanguages }
        val hidden = row.languages.size - shown.size
        if (hidden <= 0) shown.joinToString(", ")
        else stringResource(Res.string.ocr_languages_more, shown.joinToString(", "), hidden)
    }
}

/** A download or storage size in decimal megabytes, as the pack list publishes them. */
fun ocrSizeLabel(bytes: Long): String {
    val tenths = (bytes + 50_000) / 100_000
    return if (tenths >= 10) "${tenths / 10}.${tenths % 10} MB" else "${(bytes + 500) / 1_000} KB"
}
