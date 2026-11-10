/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.text

/** An emitted CTC token. [timestepEnd] and [endOffset] are exclusive. */
data class CtcToken(
    val classIndex: Int,
    val text: String,
    val timestepStart: Int,
    val timestepEnd: Int,
    val confidence: Float,
    val startOffset: Int,
    val endOffset: Int,
)
