/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.selection

sealed interface ViewerLongPressResult {
    data class SelectText(val wordId: String) : ViewerLongPressResult
    data class Delegate(val fallback: ViewerLongPressFallback) : ViewerLongPressResult
}
