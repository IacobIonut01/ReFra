/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import androidx.compose.runtime.Immutable

/** One recognized line, in reading order: its text, where it is, how sure the model was, and which pack read it. */
@Immutable
data class TextLine(
    val text: String,
    val quad: ImageQuad,
    val confidence: Float,
    val packId: String,
)
