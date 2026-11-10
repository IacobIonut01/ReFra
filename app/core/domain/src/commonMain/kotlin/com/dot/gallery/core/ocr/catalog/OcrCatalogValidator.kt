/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

/**
 * Catalog rules. Every downloadable file is pinned to an immutable Hugging Face commit URL with a size
 * and SHA-256, every pack resolves to exactly one detector and at least one recognizer, and the
 * recognizer dictionaries ship with the app.
 */
object OcrCatalogValidator {
    const val SCHEMA_VERSION = 2

    private val idPattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val commitPattern = Regex("[0-9a-f]{40}")
    private val shaPattern = Regex("[0-9a-f]{64}")
    private val scriptPattern = Regex("[A-Z][a-z]{3}")
    private val localePattern = Regex("[a-z]{2,3}(?:-[A-Z][a-z]{3})?")

    fun validate(catalog: OcrModelCatalog): OcrModelCatalog {
        val errors = mutableListOf<String>()
        if (catalog.schemaVersion != SCHEMA_VERSION) errors += "schemaVersion must be $SCHEMA_VERSION"
        if (catalog.models.isEmpty() || catalog.packs.isEmpty()) errors += "models and packs must not be empty"
        val ids = catalog.models.map { it.id } + catalog.packs.map { it.id }
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { errors += "duplicate id $it" }
        val byId = catalog.models.associateBy { it.id }
        catalog.models.forEach { validateModel(it, byId, errors) }
        catalog.packs.forEach { validatePack(it, catalog, errors) }
        catalog.unsupportedLocales.forEach { tag ->
            if (!localePattern.matches(tag)) errors += "invalid unsupported locale $tag"
            if (catalog.packs.any { tag in it.locales }) errors += "$tag is listed as unsupported but a pack covers it"
        }
        if (errors.isNotEmpty()) throw OcrCatalogException(errors.joinToString("; ", prefix = "Invalid OCR catalog: "))
        return catalog
    }

    private fun validateModel(model: OcrCatalogModel, byId: Map<String, OcrCatalogModel>, errors: MutableList<String>) {
        if (!idPattern.matches(model.id)) errors += "invalid model id ${model.id}"
        if (!commitPattern.matches(model.commit)) errors += "invalid commit for ${model.id}"
        if (model.license != "Apache-2.0") errors += "${model.id} must be Apache-2.0"
        model.dependencies.forEach { if (it !in byId) errors += "unknown dependency $it of ${model.id}" }
        if (model.artifacts.count { it.fileName.endsWith(".onnx") } != 1) errors += "${model.id} needs one .onnx"
        model.artifacts.forEach { artifact ->
            if (artifact.sizeBytes <= 0) errors += "invalid size of ${model.id}/${artifact.fileName}"
            if (!shaPattern.matches(artifact.sha256)) errors += "invalid SHA-256 of ${model.id}/${artifact.fileName}"
            val pinned = "https://huggingface.co/${model.repository}/resolve/${model.commit}/${artifact.fileName}"
            if (artifact.url != pinned) errors += "${model.id}/${artifact.fileName} is not pinned to its commit"
            if (artifact.delivery == OcrArtifactDelivery.BUNDLED && artifact.resource.isNullOrBlank()) {
                errors += "bundled ${model.id}/${artifact.fileName} has no resource"
            }
            if (artifact.fileName.endsWith(".onnx") && artifact.delivery != OcrArtifactDelivery.DOWNLOAD) {
                errors += "${model.id} model weights must never be bundled"
            }
        }
        val input = model.tensorContract.inputs.singleOrNull()
        val output = model.tensorContract.outputs.singleOrNull()
        if (input?.name != "x" || output?.name != "fetch_name_0") errors += "unexpected tensor names for ${model.id}"
        if (model.preprocessing.colorOrder != "BGR" || model.preprocessing.mean.size != 3 ||
            model.preprocessing.std.size != 3
        ) errors += "invalid preprocessing for ${model.id}"
        when (model.role) {
            OcrModelRole.DETECTOR -> if (model.detectorContract == null || model.recognizerContract != null ||
                input?.shape != listOf("dynamic", "3", "dynamic", "dynamic")
            ) errors += "invalid detector contract for ${model.id}"
            OcrModelRole.RECOGNIZER -> {
                val contract = model.recognizerContract
                if (contract == null || model.detectorContract != null ||
                    input?.shape != listOf("dynamic", "3", "48", "dynamic") || contract.ctcBlankIndex != 0 ||
                    model.artifacts.none { it.fileName == contract.dictionaryArtifact && it.delivery == OcrArtifactDelivery.BUNDLED }
                ) errors += "invalid recognizer contract for ${model.id}"
            }
        }
    }

    private fun validatePack(pack: OcrCatalogPack, catalog: OcrModelCatalog, errors: MutableList<String>) {
        if (!idPattern.matches(pack.id)) errors += "invalid pack id ${pack.id}"
        val closure = try {
            OcrDependencyResolver(catalog).resolveModels(pack.rootModelIds)
        } catch (e: OcrCatalogException) {
            errors += "${pack.id}: ${e.message}"
            return
        }
        if (closure.count { it.role == OcrModelRole.DETECTOR } != 1) errors += "${pack.id} must resolve one detector"
        if (closure.none { it.role == OcrModelRole.RECOGNIZER }) errors += "${pack.id} must resolve a recognizer"
        if (pack.scripts.isEmpty() || pack.scripts.any { !scriptPattern.matches(it) }) errors += "invalid scripts of ${pack.id}"
        (pack.trainedLocales + pack.locales).forEach { if (!localePattern.matches(it)) errors += "invalid locale $it in ${pack.id}" }
        pack.locales.forEach { if (it !in pack.trainedLocales) errors += "${pack.id} covers $it but was not trained on it" }
    }
}
