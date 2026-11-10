/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import com.dot.gallery.core.ocr.policy.OcrCoverageEvaluator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest

/**
 * The catalog's `locales` must be exactly the trained locales whose CLDR letters are all in the pack
 * dictionary, the bundled dictionaries must match their pinned hashes, and PACKS.md must match the
 * catalog. Run with `-DupdateOcrCatalog=true` (or UPDATE_OCR_CATALOG=1) to rewrite both files.
 */
class OcrCatalogCoverageTest {
    private val update = System.getProperty("updateOcrCatalog") == "true" || System.getenv("UPDATE_OCR_CATALOG") == "1"

    @Test
    fun bundledDictionariesMatchTheirPins() {
        val catalog = OcrCatalogFixtures.catalog()
        catalog.models.flatMap { it.artifacts }.filter { it.delivery == OcrArtifactDelivery.BUNDLED }.forEach { artifact ->
            val file = OcrCatalogFixtures.resource(artifact.resource!!)
            assertTrue("missing ${file.path}", file.isFile)
            val bytes = file.readBytes()
            assertEquals("size of ${artifact.resource}", artifact.sizeBytes, bytes.size.toLong())
            assertEquals("SHA-256 of ${artifact.resource}", artifact.sha256, sha256(bytes))
        }
    }

    @Test
    fun localesAreDerivedFromDictionaries() {
        val catalog = OcrCatalogFixtures.catalog()
        val derived = deriveLocales(catalog)
        if (update) {
            val updated = catalog.copy(packs = catalog.packs.map { it.copy(locales = derived.getValue(it.id)) })
            OcrCatalogFixtures.catalogFile.writeText(prettyJson.encodeToString(OcrModelCatalog.serializer(), updated) + "\n")
            return
        }
        val diffs = catalog.packs.mapNotNull { pack ->
            val expected = derived.getValue(pack.id)
            if (pack.locales == expected) null else "${pack.id}: catalog ${pack.locales} but dictionaries cover $expected"
        }
        if (diffs.isNotEmpty()) fail(diffs.joinToString("\n", postfix = "\nRe-run with UPDATE_OCR_CATALOG=1 to write them."))
    }

    @Test
    fun knownGapsStayDocumented() {
        val catalog = OcrCatalogFixtures.catalog()
        // Vietnamese is in PaddleOCR's Latin list, but the dictionary has no tone-marked vowels.
        assertTrue("vi" in catalog.pack("latin")!!.trainedLocales)
        assertTrue("vi" !in catalog.pack("latin")!!.locales)
        assertEquals(listOf("bn", "he", "or", "pa", "vi"), catalog.unsupportedLocales)
        // Romanian comma-below letters, Turkish dotted/dotless i and Lithuanian ogoneks are covered by Latin.
        val latin = OcrCatalogFixtures.catalog().pack("latin")!!.locales
        listOf("ro", "tr", "lt", "cs", "pl", "hu").forEach { assertTrue(it, it in latin) }
        // Moldovan is never shown under Cyrillic.
        assertTrue(catalog.packs.none { "mo" in it.trainedLocales })
    }

    @Test
    fun packListMatchesCatalog() {
        val expected = OcrPackListMarkdown.render(OcrCatalogFixtures.catalog(), OcrCatalogFixtures.englishNames())
        if (update) {
            OcrCatalogFixtures.packListFile.writeText(expected)
            return
        }
        assertEquals(
            "PACKS.md is out of date; re-run with UPDATE_OCR_CATALOG=1",
            expected,
            OcrCatalogFixtures.packListFile.readText().replace("\r\n", "\n"),
        )
    }

    private fun deriveLocales(catalog: OcrModelCatalog): Map<String, List<String>> {
        val exemplars = OcrCatalogFixtures.exemplars().mapValues { OcrCoverageEvaluator.parseExemplars(it.value) }
        val resolver = OcrDependencyResolver(catalog)
        return catalog.packs.associate { pack ->
            val dictionary = resolver.resolvePack(pack.id).mapNotNull { it.dictionary }.flatMapTo(HashSet()) {
                OcrCoverageEvaluator.parseDictionary(OcrCatalogFixtures.resource(it.resource!!).readText())
            }
            pack.id to pack.trainedLocales.filter { tag ->
                val letters = exemplars[tag] ?: return@filter false
                OcrCoverageEvaluator.missing(letters, dictionary, pack.coverageExceptions[tag].orEmpty()).isEmpty()
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }
}
