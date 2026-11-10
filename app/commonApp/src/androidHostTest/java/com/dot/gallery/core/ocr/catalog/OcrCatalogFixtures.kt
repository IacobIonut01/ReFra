/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Paths and loaders for the committed OCR catalog, dictionaries and CLDR snapshot. */
object OcrCatalogFixtures {
    val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile && File(it, "app/ml-models").isDirectory }
    }
    val catalogDir: File get() = File(repoRoot, "app/ml-models/catalog")
    val catalogFile: File get() = File(catalogDir, "runtime/ocr/model-catalog-v1.json")
    val packListFile: File get() = File(catalogDir, "PACKS.md")

    fun catalog(): OcrModelCatalog = OcrCatalogParser.parse(catalogFile.readText())

    fun resource(path: String): File = File(catalogDir, "runtime/$path")

    private val cldr by lazy {
        Json.parseToJsonElement(File(catalogDir, "coverage/cldr-exemplars.json").readText()).jsonObject
    }

    fun exemplars(): Map<String, String> =
        cldr.getValue("exemplars").jsonObject.mapValues { it.value.jsonPrimitive.content }

    fun englishNames(): Map<String, String> =
        cldr.getValue("englishNames").jsonObject.mapValues { it.value.jsonPrimitive.content }
}
