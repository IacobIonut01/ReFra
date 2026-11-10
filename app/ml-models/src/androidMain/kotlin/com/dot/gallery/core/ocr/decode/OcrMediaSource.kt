/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

import android.graphics.Bitmap
import com.dot.gallery.core.ocr.domain.OcrLimits
import java.io.Closeable

/** A decoded bitmap returned by this interface is owned by the caller and must be recycled. */
interface OcrMediaSource : Closeable {
    val orientedWidth: Int
    val orientedHeight: Int
    val supportsRegionDecode: Boolean

    suspend fun overview(maxLongEdge: Int = OcrLimits.OVERVIEW_MAX_DIMENSION): Bitmap?

    /** Returns a real source-region decode, or null when this source only has a bounded flat decode. */
    suspend fun region(source: OcrSourceRect, maxLongEdge: Int = OcrLimits.OVERVIEW_MAX_DIMENSION): Bitmap? = null
}
