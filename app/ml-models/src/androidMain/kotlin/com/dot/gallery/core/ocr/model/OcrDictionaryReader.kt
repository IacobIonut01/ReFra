/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

/** Reads a PaddleOCR `inference.yml` character dictionary in class order (duplicates kept). */
object OcrDictionaryReader {
    fun read(yml: String): List<String> {
        val lines = yml.lines()
        val start = lines.indexOfFirst { it.trim() == "character_dict:" }
        if (start < 0) return emptyList()
        return lines.drop(start + 1).map { it.trimEnd(13.toChar()) }.takeWhile { it.startsWith("  - ") }.map { line ->
            val value = line.removePrefix("  - ")
            if (value.length >= 2 && value.first() == 39.toChar() && value.last() == 39.toChar()) {
                value.substring(1, value.length - 1).replace("\u0027\u0027", "\u0027")
            } else {
                value
            }
        }
    }
}
