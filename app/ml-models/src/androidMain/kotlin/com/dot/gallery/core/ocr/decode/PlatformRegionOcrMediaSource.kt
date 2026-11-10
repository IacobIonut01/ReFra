/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.decoder.RawOrientation
import com.dot.gallery.core.ocr.domain.OcrLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class PlatformRegionOcrMediaSource private constructor(
    private var descriptor: ParcelFileDescriptor?,
    private var decoder: BitmapRegionDecoder?,
    private val rawWidth: Int,
    private val rawHeight: Int,
    private val orientation: Int,
    override val orientedWidth: Int,
    override val orientedHeight: Int,
    private val closeOwner: () -> Unit,
) : OcrMediaSource {
    override val supportsRegionDecode: Boolean = true
    private val lock = Any()
    private var closed = false

    override suspend fun overview(maxLongEdge: Int): Bitmap? =
        region(OcrSourceRect(0, 0, orientedWidth, orientedHeight), maxLongEdge)

    override suspend fun region(source: OcrSourceRect, maxLongEdge: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            validateMaxLongEdge(maxLongEdge)
            validateRegion(source, orientedWidth, orientedHeight)
            currentCoroutineContext().ensureActive()
            val result = synchronized(lock) {
                val active = decoder?.takeUnless(BitmapRegionDecoder::isRecycled) ?: return@synchronized null
                val rawRegion = orientedToRaw(source, rawWidth, rawHeight, orientation)
                val target = fitWithin(source.width, source.height, maxLongEdge)
                val sample = powerOfTwoSample(rawRegion.width(), rawRegion.height(), target.first, target.second)
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = sample
                }
                val decoded = active.decodeRegion(rawRegion, options) ?: return@synchronized null
                val oriented = applyExifToTile(decoded, orientation)
                if (oriented !== decoded) decoded.recycle()
                oriented.resizeOwned(target.first, target.second)
            }
            try {
                currentCoroutineContext().ensureActive()
                result
            } catch (cancelled: CancellationException) {
                result?.recycle()
                throw cancelled
            }
        }

    override fun close() {
        val owned = synchronized(lock) {
            if (closed) return
            closed = true
            val result = decoder to descriptor
            decoder = null
            descriptor = null
            result
        }
        try {
            runCatching { owned.first?.recycle() }
            runCatching { owned.second?.close() }
        } finally {
            closeOwner()
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun open(
            resolver: ContentResolver,
            uri: Uri,
            expectedOrientedSize: Pair<Int, Int>? = null,
            closeOwner: () -> Unit = {},
        ): PlatformRegionOcrMediaSource? {
            var descriptor: ParcelFileDescriptor? = null
            var decoder: BitmapRegionDecoder? = null
            try {
                descriptor = resolver.openFileDescriptor(uri, "r") ?: return null
                val orientation = runCatching {
                    ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                decoder = BitmapRegionDecoder.newInstance(descriptor.fileDescriptor, false) ?: return null
                val rawWidth = decoder.width
                val rawHeight = decoder.height
                if (rawWidth <= 0 || rawHeight <= 0) return null
                val orientedSize = if (RawOrientation.swapsDimensions(orientation)) {
                    rawHeight to rawWidth
                } else {
                    rawWidth to rawHeight
                }
                if (expectedOrientedSize != null && expectedOrientedSize != orientedSize) return null
                if (OcrLimits.checkedSourceArea(orientedSize.first, orientedSize.second) == null) return null
                val probeSize = minOf(16, rawWidth, rawHeight).coerceAtLeast(1)
                val probe = decoder.decodeRegion(
                    Rect(0, 0, probeSize, probeSize),
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                ) ?: return null
                probe.recycle()
                return PlatformRegionOcrMediaSource(
                    descriptor = descriptor,
                    decoder = decoder,
                    rawWidth = rawWidth,
                    rawHeight = rawHeight,
                    orientation = orientation,
                    orientedWidth = orientedSize.first,
                    orientedHeight = orientedSize.second,
                    closeOwner = closeOwner,
                ).also {
                    descriptor = null
                    decoder = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                return null
            } finally {
                runCatching { decoder?.recycle() }
                runCatching { descriptor?.close() }
            }
        }
    }
}
