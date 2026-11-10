/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.domain

/** Hard limits shared by the platform-independent OCR pipeline. */
object OcrLimits {
    const val OVERVIEW_MAX_DIMENSION = 2_048
    const val TILE_OVERLAP_FRACTION = 0.20f
    const val MAX_DECODE_BYTES = 32 * 1_024 * 1_024
    const val MAX_SOURCE_DIMENSION = 100_000
    const val MAX_SOURCE_AREA = 1_000_000_000L
    const val MAX_TILES = 64
    const val MAX_LINES = 512
    const val MAX_TOKENS = 4_096
    const val MAX_TEXT_CODE_POINTS = 262_144
    const val MAX_JSON_BYTES = 4 * 1_024 * 1_024
    const val MAX_FTS_CODE_POINTS = 524_288
    const val MAX_QUERY_CODE_POINTS = 256
    const val MAX_DAO_BATCH_SIZE = 5_000

    // Descriptive aliases used by storage and decode call sites.
    const val MAX_OVERVIEW_LONG_EDGE = OVERVIEW_MAX_DIMENSION
    const val FINE_TILE_OVERLAP = TILE_OVERLAP_FRACTION
    const val MAX_DECODED_BYTES = MAX_DECODE_BYTES
    const val MAX_ORIENTED_DIMENSION = MAX_SOURCE_DIMENSION
    const val MAX_ORIENTED_AREA = MAX_SOURCE_AREA
    const val MAX_TILE_COUNT = MAX_TILES
    const val MAX_LINE_COUNT = MAX_LINES
    const val MAX_TOKEN_COUNT = MAX_TOKENS
    const val MAX_FULL_TEXT_CODE_POINTS = MAX_TEXT_CODE_POINTS
    const val MAX_DOCUMENT_JSON_BYTES = MAX_JSON_BYTES
    const val MAX_FTS_TEXT_CODE_POINTS = MAX_FTS_CODE_POINTS
    const val MAX_QUERY_LENGTH_CODE_POINTS = MAX_QUERY_CODE_POINTS
    const val MAX_DAO_BATCH_IDS = MAX_DAO_BATCH_SIZE

    fun checkedSourceArea(width: Int, height: Int): Long? {
        if (width <= 0 || height <= 0) return null
        if (width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION) return null
        val area = width.toLong() * height.toLong()
        return area.takeIf { it <= MAX_SOURCE_AREA }
    }

    fun checkedRgbaByteCount(width: Int, height: Int): Long? {
        if (width <= 0 || height <= 0) return null
        val pixels = width.toLong() * height.toLong()
        val bytes = pixels * 4L
        return bytes.takeIf { pixels <= Long.MAX_VALUE / 4L && it <= MAX_DECODE_BYTES }
    }
}
