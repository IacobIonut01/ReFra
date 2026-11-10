/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

/** Resolves packs to the models they need, dependencies first (the shared detector before a recognizer). */
class OcrDependencyResolver(private val catalog: OcrModelCatalog) {
    private val modelsById = catalog.models.associateBy { it.id }

    fun resolvePack(packId: String): List<OcrCatalogModel> {
        val pack = catalog.pack(packId) ?: throw OcrCatalogException("Unknown OCR pack: $packId")
        return resolveModels(pack.rootModelIds)
    }

    fun resolvePacks(packIds: Collection<String>): List<OcrCatalogModel> =
        packIds.sorted().flatMap(::resolvePack).distinctBy { it.id }

    /** Models no longer needed by any of [remaining] once [removed] is deleted (a detector stays while any pack needs it). */
    fun modelsToDelete(removed: String, remaining: Collection<String>): List<OcrCatalogModel> {
        val kept = resolvePacks(remaining - removed).map { it.id }.toSet()
        return resolvePack(removed).filter { it.id !in kept }
    }

    fun resolveModels(rootModelIds: Collection<String>): List<OcrCatalogModel> {
        val ordered = mutableListOf<OcrCatalogModel>()
        val visiting = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in visiting) throw OcrCatalogException("Model dependency cycle at $id")
            if (!visited.add(id)) return
            val model = modelsById[id] ?: throw OcrCatalogException("Unknown model: $id")
            visiting += id
            model.dependencies.forEach(::visit)
            visiting -= id
            ordered += model
        }
        rootModelIds.forEach(::visit)
        return ordered
    }
}
