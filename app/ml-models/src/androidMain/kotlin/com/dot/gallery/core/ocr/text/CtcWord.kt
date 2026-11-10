/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.text

data class CtcWord(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val tokenStart: Int,
    val tokenEnd: Int,
    val confidence: Float,
)
