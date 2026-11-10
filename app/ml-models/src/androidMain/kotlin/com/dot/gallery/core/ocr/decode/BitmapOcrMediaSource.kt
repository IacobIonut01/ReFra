/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

import android.graphics.Bitmap
import com.dot.gallery.core.ocr.domain.OcrLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class BitmapOcrMediaSource(
    private var ownedBitmap: Bitmap?,
    override val orientedWidth: Int = ownedBitmap?.width ?: 0,
    override val orientedHeight: Int = ownedBitmap?.height ?: 0,
    private val closeOwner: () -> Unit = {},
) : OcrMediaSource {
    override val supportsRegionDecode: Boolean = false
    private var closed = false

    init {
        require(OcrLimits.checkedSourceArea(orientedWidth, orientedHeight) != null)
    }

    override suspend fun overview(maxLongEdge: Int): Bitmap? = withContext(Dispatchers.Default) {
        validateMaxLongEdge(maxLongEdge)
        currentCoroutineContext().ensureActive()
        val source = synchronized(this@BitmapOcrMediaSource) {
            ownedBitmap?.takeUnless(Bitmap::isRecycled)
        } ?: return@withContext null
        val result = source.ownedArgbScaled(maxLongEdge)
        try {
            currentCoroutineContext().ensureActive()
            result
        } catch (cancelled: CancellationException) {
            result?.recycle()
            throw cancelled
        }
    }

    override fun close() {
        val bitmap = synchronized(this) {
            if (closed) return
            closed = true
            ownedBitmap.also { ownedBitmap = null }
        }
        try {
            bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
        } finally {
            closeOwner()
        }
    }
}
