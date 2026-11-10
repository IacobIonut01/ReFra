/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

/**
 * Renders the published pack list (`app/ml-models/catalog/PACKS.md`) from the catalog, so people on
 * offline builds can fetch pack files elsewhere and add them with "Add model files". The committed file
 * must equal this output; OcrCatalogCoverageTest checks it.
 */
object OcrPackListMarkdown {
    fun render(catalog: OcrModelCatalog, englishNames: Map<String, String>): String = buildString {
        val resolver = OcrDependencyResolver(catalog)
        val detector = catalog.models.first { it.role == OcrModelRole.DETECTOR }
        fun name(tag: String) = englishNames[tag] ?: tag
        appendLine("# ReFra text recognition packs")
        appendLine()
        appendLine("<!-- Generated from model-catalog-v1.json by OcrCatalogCoverageTest. Do not edit by hand. -->")
        appendLine()
        appendLine("ReFra reads text in photos on the device with PaddlePaddle PP-OCRv5 mobile models (Apache-2.0).")
        appendLine("Versions without internet access cannot download packs. Download the files below on another device,")
        appendLine("copy them to the phone, then open **Settings › Smart features › Text recognition › Language packs ›")
        appendLine("Add model files** and pick them. ReFra accepts a file only when its size and SHA-256 match this list;")
        appendLine("file names do not matter.")
        appendLine()
        appendLine("Every pack needs the shared **text finder** once. Each pack adds one recognizer file.")
        appendLine()
        appendLine("## Text finder (needed once)")
        appendLine()
        appendModel(detector)
        appendLine("## Language packs")
        appendLine()
        for (pack in catalog.packs) {
            appendLine("### ${pack.title}")
            appendLine()
            val languages = pack.locales.filter { it != "en" || pack.id == "english" }.map(::name).sorted()
            appendLine("Reads: ${(languages.ifEmpty { listOf(name("en")) }).joinToString(", ")}${if (pack.id != "english" && "en" in pack.locales) ", and English" else ""}.")
            appendLine()
            resolver.resolvePack(pack.id).filter { it.role == OcrModelRole.RECOGNIZER }.forEach { appendModel(it) }
        }
        if (catalog.unsupportedLocales.isNotEmpty()) {
            appendLine("## Not available yet")
            appendLine()
            appendLine("No on-device model reads ${catalog.unsupportedLocales.map(::name).sorted().joinToString(", ")} yet.")
            appendLine("ReFra does not substitute another pack for them.")
        }
    }

    private fun StringBuilder.appendModel(model: OcrCatalogModel) {
        val onnx = model.onnx
        appendLine("| Model | File | Size | SHA-256 | Download |")
        appendLine("| --- | --- | --- | --- | --- |")
        appendLine("| `${model.id}` | `${onnx.fileName}` | ${onnx.sizeBytes} bytes | `${onnx.sha256}` | [${model.repository}@${model.commit.take(12)}](${onnx.url}) |")
        appendLine()
    }
}
