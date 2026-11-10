/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Parses and validates the catalog JSON; anything that does not pass [OcrCatalogValidator] is rejected. */
object OcrCatalogParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun parse(value: String): OcrModelCatalog {
        val catalog = try {
            json.decodeFromString(OcrModelCatalog.serializer(), value)
        } catch (e: SerializationException) {
            throw OcrCatalogException("Invalid OCR model catalog JSON", e)
        } catch (e: IllegalArgumentException) {
            throw OcrCatalogException("Invalid OCR model catalog JSON", e)
        }
        return OcrCatalogValidator.validate(catalog)
    }
}
