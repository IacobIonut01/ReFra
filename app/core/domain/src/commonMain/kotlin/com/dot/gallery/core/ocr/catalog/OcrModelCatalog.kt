/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

/** The OCR model catalog: every model ReFra may download or load, and the language packs built on them. */
@Serializable
data class OcrModelCatalog(
    val schemaVersion: Int,
    val catalogId: String,
    val notes: String = "",
    val unsupportedLocales: List<String> = emptyList(),
    val models: List<OcrCatalogModel>,
    val packs: List<OcrCatalogPack>,
) {
    fun model(id: String): OcrCatalogModel? = models.firstOrNull { it.id == id }
    fun pack(id: String): OcrCatalogPack? = packs.firstOrNull { it.id == id }
}
