/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.selection

data class OcrSelectionRange(val startIndex: Int, val endIndex: Int) {
    init {
        require(startIndex >= 0 && endIndex >= startIndex)
    }
}
