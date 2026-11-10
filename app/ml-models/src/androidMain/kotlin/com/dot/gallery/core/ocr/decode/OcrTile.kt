/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

import com.dot.gallery.core.ocr.domain.OcrLimits

data class OcrTile(
    val id: String,
    val kind: OcrTileKind,
    val source: OcrSourceRect,
    val decodeWidth: Int,
    val decodeHeight: Int,
) {
    init {
        require(id.isNotBlank())
        require(decodeWidth > 0 && decodeHeight > 0)
        require(OcrLimits.checkedRgbaByteCount(decodeWidth, decodeHeight) != null)
    }
}
