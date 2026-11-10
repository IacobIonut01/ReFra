/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

/**
 * A model read from a picked document. Stored as `modelId|size|lastModified|uri` in the
 * `ocrExternalModelUris` setting, so a restart can re-check it without hashing when nothing changed.
 */
data class OcrExternalLink(val modelId: String, val size: Long, val lastModified: Long, val uri: String) {
    fun encode(): String = "$modelId|$size|$lastModified|$uri"

    companion object {
        fun decode(value: String): OcrExternalLink? {
            val parts = value.split("|", limit = 4)
            if (parts.size != 4) return null
            return OcrExternalLink(parts[0], parts[1].toLongOrNull() ?: return null, parts[2].toLongOrNull() ?: return null, parts[3])
        }
    }
}
