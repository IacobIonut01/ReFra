/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.util

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Formats a byte count as a human-readable size with two decimals, e.g.
 * `1.25 MB`. Locale-independent (always uses `.` as decimal separator) so the
 * output is identical on every platform.
 */
fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0))
        .toInt()
        .coerceAtMost(units.lastIndex)

    val scaled = size / 1024.0.pow(digitGroups.toDouble())
    val rounded = (scaled * 100).roundToLong()
    return "${rounded / 100}.${(rounded % 100).toString().padStart(2, '0')} ${units[digitGroups]}"
}

/**
 * Formats a byte count with one decimal, e.g. `1.2 GB`, for album labels where space is short.
 * Bytes stay whole (`512 B`). Locale-independent like [formatSize].
 */
fun formatSizeShort(size: Long): String {
    if (size <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0))
        .toInt()
        .coerceAtMost(units.lastIndex)
    if (digitGroups == 0) return "$size B"

    val rounded = (size / 1024.0.pow(digitGroups.toDouble()) * 10).roundToLong()
    return "${rounded / 10}.${rounded % 10} ${units[digitGroups]}"
}
