/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

/** The catalog JSON is malformed or breaks one of the catalog rules. */
class OcrCatalogException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
