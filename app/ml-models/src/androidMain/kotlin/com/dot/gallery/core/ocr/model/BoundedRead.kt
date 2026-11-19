/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

import java.io.InputStream

/**
 * Reads up to [limit] bytes, stopping early at the end of the stream. Same contract as
 * `InputStream.readNBytes(int)`, which only exists from API 33 while the app supports API 29.
 */
fun InputStream.readAtMost(limit: Int): ByteArray {
    require(limit >= 0) { "limit must not be negative: $limit" }
    val bytes = ByteArray(limit)
    var count = 0
    while (count < limit) {
        val read = read(bytes, count, limit - count)
        if (read < 0) break
        count += read
    }
    return if (count == limit) bytes else bytes.copyOf(count)
}
