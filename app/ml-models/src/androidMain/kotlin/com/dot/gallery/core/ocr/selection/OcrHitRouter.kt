/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.selection

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.geometry.OcrGeometry

object OcrHitRouter {
    fun route(
        point: NormalizedPoint,
        document: OcrDocument?,
        fallback: ViewerLongPressFallback,
        inflation: Float = 0.012f
    ): ViewerLongPressResult {
        val hit = document?.lines
            ?.asSequence()
            ?.flatMap { it.words.asSequence() }
            ?.sortedBy { it.readingIndex }
            ?.firstOrNull { OcrGeometry.contains(it.quad, point, inflation) }
        return hit?.let { ViewerLongPressResult.SelectText(it.id) }
            ?: ViewerLongPressResult.Delegate(fallback)
    }
}
