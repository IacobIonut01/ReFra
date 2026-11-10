/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

import com.dot.gallery.core.ocr.catalog.OcrModelCatalog
import java.io.InputStream
import java.security.MessageDigest

/**
 * Identifies a file by size, then SHA-256, against every downloadable model of the catalog. File names
 * are ignored: every PaddleOCR repository calls its model `inference.onnx`.
 */
class OcrFileMatcher(private val catalog: OcrModelCatalog) {
    fun match(size: Long, open: () -> InputStream): OcrFileMatch {
        val candidates = catalog.models.filter { it.onnx.sizeBytes == size }
        if (candidates.isEmpty()) return OcrFileMatch.Unknown
        val digest = open().use { sha256(it) }
        return candidates.firstOrNull { it.onnx.sha256 == digest }?.let { OcrFileMatch.Accepted(it.id) }
            ?: OcrFileMatch.Altered(candidates.first().id)
    }

    companion object {
        fun sha256(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
