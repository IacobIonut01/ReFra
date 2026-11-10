/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.geometry

enum class ExifOrientation(val value: Int) {
    NORMAL(1),
    FLIP_HORIZONTAL(2),
    ROTATE_180(3),
    FLIP_VERTICAL(4),
    TRANSPOSE(5),
    ROTATE_90(6),
    TRANSVERSE(7),
    ROTATE_270(8),
    ;

    val swapsDimensions: Boolean get() = value in 5..8

    companion object {
        fun fromValue(value: Int): ExifOrientation = entries.firstOrNull { it.value == value } ?: NORMAL
    }
}
