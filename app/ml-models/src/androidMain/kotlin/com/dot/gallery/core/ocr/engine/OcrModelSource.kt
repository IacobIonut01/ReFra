/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.engine

import com.dot.gallery.core.ocr.catalog.OcrCatalogModel
import com.dot.gallery.core.ocr.catalog.OcrCatalogPack
import com.dot.gallery.core.ocr.catalog.OcrModelCatalog

/** What the engine needs from the pack store: ready packs, their models and verified model bytes. */
interface OcrModelSource {
    val catalog: OcrModelCatalog

    fun areReady(packIds: Collection<String>): Boolean

    /** Enabled packs in recognition order, the primary pack first. */
    fun packsFor(packIds: Collection<String>): List<OcrCatalogPack>

    fun detectorFor(packIds: Collection<String>): OcrCatalogModel

    fun recognizerFor(packId: String): OcrCatalogModel

    /** Opens a verified model, or throws if it is missing or no longer verifies. */
    fun open(model: OcrCatalogModel): OcrModelHandle

    /** The bundled character dictionary of a recognizer. */
    fun dictionary(model: OcrCatalogModel): List<String>

    /** Identifies the packs and model bytes behind a result, for re-indexing when they change. */
    fun revision(packIds: Collection<String>): String
}
