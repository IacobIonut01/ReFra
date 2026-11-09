/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import androidx.compose.runtime.Immutable

/** A QR code or barcode read from the photo. */
@Immutable
data class CodeInsight(
    val format: CodeFormat,
    val text: String,
    val payload: CodePayload,
    override val quad: ImageQuad,
) : Insight {
    override val kind: InsightKind get() = InsightKind.CODES
}
