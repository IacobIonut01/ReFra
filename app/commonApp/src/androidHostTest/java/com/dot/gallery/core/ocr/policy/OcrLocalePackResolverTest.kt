/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.policy

import com.dot.gallery.core.ocr.catalog.OcrCatalogFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class OcrLocalePackResolverTest {
    private val catalog = OcrCatalogFixtures.catalog()

    private val expected = mapOf(
        "af" to "latin", "ar" to "arabic", "be" to "eslav", "bg" to "cyrillic", "bn" to null, "ca" to "latin",
        "cs" to "latin", "da" to "latin", "de" to "latin", "el" to "greek", "en" to "latin", "es" to "latin",
        "fi" to "latin", "fr" to "latin", "ga" to "latin", "gl" to "latin", "hi" to "devanagari", "hr" to "latin",
        "hu" to "latin", "in" to "latin", "it" to "latin", "iw" to null, "ja" to "cjk", "ko" to "korean",
        "lt" to "latin", "ne" to "devanagari", "nl" to "latin", "nn" to "latin", "no" to "latin", "or" to null,
        "pa" to null, "pl" to "latin", "pt" to "latin", "ro" to "latin", "ru" to "eslav", "sr" to "cyrillic",
        "sv" to "latin", "tr" to "latin", "uk" to "eslav", "vi" to null, "zh" to "cjk",
    )

    @Test
    fun everyAppLocaleResolvesToItsPackOrIsUnsupported() {
        val res = File(OcrCatalogFixtures.repoRoot, "app/commonApp/src/commonMain/composeResources")
        val tags = res.listFiles()!!.map { it.name }.filter { it.startsWith("values-") }
            .map { it.removePrefix("values-").replace("-r", "-") }
        tags.forEach { tag ->
            val language = tag.substringBefore('-')
            assertEquals(tag, expected.getValue(language), OcrLocalePackResolver.bestPack(tag, catalog))
        }
    }

    @Test
    fun regionsNeverChangeThePack() {
        assertEquals("eslav", OcrLocalePackResolver.bestPack("ru-BY", catalog))
        assertEquals("eslav", OcrLocalePackResolver.bestPack("ru-RU", catalog))
        assertEquals("zh-Hant", OcrLocaleKey.of("zh-TW"))
        assertEquals("zh", OcrLocaleKey.of("zh-CN"))
        assertEquals("zh-Hant", OcrLocaleKey.of("zh-Hant-SG"))
        assertEquals("sr-Latn", OcrLocaleKey.of("sr-Latn-RS"))
        assertEquals("sr", OcrLocaleKey.of("sr-SP"))
    }

    @Test
    fun bulgarianGetsTheBroadCyrillicPack() {
        assertEquals("cyrillic", OcrLocalePackResolver.bestPack("bg-BG", catalog))
        assertEquals("cyrillic", OcrLocalePackResolver.bestPack("mk", catalog))
    }

    @Test
    fun unsupportedLanguagesAreNotSubstituted() {
        listOf("vi-VN", "iw-IL", "he", "bn-BD", "or-IN", "pa-IN").forEach {
            assertNull(it, OcrLocalePackResolver.bestPack(it, catalog))
        }
        val suggestion = OcrLocalePackResolver.suggest(listOf("vi-VN"), catalog)
        assertEquals(emptySet<String>(), suggestion.packIds)
        assertEquals(setOf("vi"), suggestion.unsupported)
    }

    @Test
    fun suggestionsAreHintsForTheLanguagesOnly() {
        assertEquals(setOf("english"), OcrLocalePackResolver.suggest(listOf("en-US", "en-GB"), catalog).packIds)
        assertEquals(setOf("latin"), OcrLocalePackResolver.suggest(listOf("en-US", "ro-RO"), catalog).packIds)
        assertEquals(setOf("greek", "latin"), OcrLocalePackResolver.suggest(listOf("el-GR"), catalog).packIds)
        assertEquals(setOf("cjk", "latin"), OcrLocalePackResolver.suggest(listOf("ja-JP", "zh-TW"), catalog).packIds)
        assertEquals(setOf("eslav", "latin"), OcrLocalePackResolver.suggest(listOf("uk-UA"), catalog).packIds)
        assertEquals(emptySet<String>(), OcrLocalePackResolver.suggest(emptyList(), catalog).packIds)
    }
}
