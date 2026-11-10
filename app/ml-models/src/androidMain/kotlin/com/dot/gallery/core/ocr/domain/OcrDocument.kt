/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.domain

import java.security.MessageDigest
import kotlinx.serialization.Serializable

/**
 * Versioned, source-oriented OCR payload. Source dimensions are measured after EXIF orientation.
 * Text offsets are UTF-16 indices into [fullText], matching Kotlin and Android string APIs.
 */
@Serializable
data class OcrDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val fullText: String,
    val attemptedPackIds: List<String> = emptyList(),
    val lines: List<OcrLine> = emptyList(),
) {
    init {
        require(schemaVersion > 0)
        require(OcrLimits.checkedSourceArea(sourceWidth, sourceHeight) != null)
        require(fullText.codePointCount(0, fullText.length) <= OcrLimits.MAX_TEXT_CODE_POINTS)
        require(lines.size <= OcrLimits.MAX_LINES)
        require(lines.sumOf { it.words.size } <= OcrLimits.MAX_TOKENS)
        require(attemptedPackIds.none(String::isBlank))
        require(attemptedPackIds.distinct().size == attemptedPackIds.size)
        require(lines.map(OcrLine::id).distinct().size == lines.size)
        require(lines.map(OcrLine::readingIndex).distinct().size == lines.size)
        require(lines.all { line ->
            line.endOffset <= fullText.length &&
                fullText.regionMatches(line.startOffset, line.text, 0, line.text.length)
        })
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Stable across process runs and independent of JVM hash randomization. */
fun stableOcrId(vararg components: Any?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    components.forEach { component ->
        val bytes = component.toString().toByteArray(Charsets.UTF_8)
        digest.update((bytes.size ushr 24).toByte())
        digest.update((bytes.size ushr 16).toByte())
        digest.update((bytes.size ushr 8).toByte())
        digest.update(bytes.size.toByte())
        digest.update(bytes)
    }
    return digest.digest().take(12).joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
