/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

import com.dot.gallery.core.ocr.catalog.OcrArtifact
import com.dot.gallery.core.ocr.catalog.OcrCatalogFixtures
import com.dot.gallery.domain.settings.OCR_MODE_AUTO
import com.dot.gallery.domain.settings.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class OcrInstallPoliciesTest {
    private val catalog = OcrCatalogFixtures.catalog()

    @Test
    fun defaultsAreOffWithNothingSelected() {
        assertFalse(SettingsKeys.ocrEnabled.default)
        assertEquals(emptySet<String>(), SettingsKeys.ocrPacks.default)
        assertEquals(emptySet<String>(), SettingsKeys.ocrExternalModelUris.default)
        assertEquals(OCR_MODE_AUTO, SettingsKeys.ocrMode.default)
    }

    @Test
    fun switchStaysOffWithoutAReadyPack() {
        assertFalse(OcrSwitchPolicy.effective(requested = true, readyPackIds = emptySet()))
        assertTrue(OcrSwitchPolicy.effective(requested = true, readyPackIds = setOf("latin")))
        assertFalse(OcrSwitchPolicy.effective(requested = false, readyPackIds = setOf("latin")))
        assertEquals(listOf("greek", "latin"), OcrSwitchPolicy.activePacks(setOf("latin", "greek", "thai"), setOf("latin", "greek")))
    }

    @Test
    fun offlineBuildsNeverDownload() {
        assertFalse(OcrInstallPolicy.of(offlineBuild = true, hasInternetPermission = false).downloadsAllowed)
        assertFalse(OcrInstallPolicy.of(offlineBuild = true, hasInternetPermission = true).downloadsAllowed)
        assertFalse(OcrInstallPolicy.of(offlineBuild = false, hasInternetPermission = false).downloadsAllowed)
        assertTrue(OcrInstallPolicy.of(offlineBuild = false, hasInternetPermission = true).downloadsAllowed)
    }

    @Test
    fun pickedFilesAreMatchedBySizeThenHash() {
        val bytes = "not a model".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val fake = catalog.copy(models = catalog.models.map { model ->
            if (model.id != "pp-ocrv5-mobile-rec-latin") model
            else model.copy(artifacts = model.artifacts.map { a: OcrArtifact ->
                if (a.fileName.endsWith(".onnx")) a.copy(sizeBytes = bytes.size.toLong(), sha256 = sha) else a
            })
        })
        val matcher = OcrFileMatcher(fake)
        assertEquals(OcrFileMatch.Accepted("pp-ocrv5-mobile-rec-latin"), matcher.match(bytes.size.toLong()) { bytes.inputStream() })
        val altered = bytes.copyOf().also { it[0] = 'N'.code.toByte() }
        assertEquals(OcrFileMatch.Altered("pp-ocrv5-mobile-rec-latin"), matcher.match(altered.size.toLong()) { altered.inputStream() })
        assertEquals(OcrFileMatch.Unknown, matcher.match(3) { "abc".byteInputStream() })
    }

    @Test
    fun externalLinksRoundTripAndRejectGarbage() {
        val link = OcrExternalLink("pp-ocrv5-mobile-det", 4826518, 1700000000000, "content://com.android.externalstorage.documents/document/primary%3AOCR%7Cdet.onnx")
        assertEquals(link, OcrExternalLink.decode(link.encode()))
        assertEquals(null, OcrExternalLink.decode("broken"))
    }

    @Test
    fun deletingAPackKeepsTheSharedDetectorWhileAnotherPackNeedsIt() {
        val resolver = com.dot.gallery.core.ocr.catalog.OcrDependencyResolver(catalog)
        assertEquals(listOf("pp-ocrv5-mobile-rec-greek"), resolver.modelsToDelete("greek", listOf("greek", "latin")).map { it.id })
        assertEquals(setOf("pp-ocrv5-mobile-det", "pp-ocrv5-mobile-rec-greek"), resolver.modelsToDelete("greek", listOf("greek")).map { it.id }.toSet())
    }

    @Test
    fun dictionaryKeepsClassOrderAndQuotes() {
        val yml = "PostProcess:\n  character_dict:\n  - a\n  - ''''\n  - ' '\n  - a\nother: 1\n"
        assertEquals(listOf("a", "'", " ", "a"), OcrDictionaryReader.read(yml))
    }
}
