/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.os.Build
import com.dot.gallery.core.decoder.format.ImageFormatSniffer
import com.dot.gallery.core.decoder.format.Jp2ImageDecoder
import com.dot.gallery.core.decoder.format.PsdImageDecoder
import com.dot.gallery.core.decoder.format.TiffImageDecoder
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.asImage
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ImageData
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.source.ContentDataSource
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.source.getFileOrNull
import com.github.panpf.sketch.util.Size
import okio.buffer
import com.dot.gallery.core.util.and
import com.dot.gallery.domain.util.isJp2
import com.dot.gallery.domain.util.isPsd

class SketchTiffDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
) : Decoder {

    class Factory : Decoder.Factory {
        override val key: String get() = "TiffDecoder"
        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            val realMime = requestContext.request.extras?.get("realMimeType") as String?
            val mime = realMime ?: fetchResult.mimeType
            if (mime == null || !(mime.contains("image/tiff") || mime.contains("image/tif"))) return null
            // a file mislabeled image/tiff that is actually a standard JPEG/PNG/WebP/GIF/BMP
            // must not be full-decoded as TIFF (fails / yields only a thumbnail). Peek the header and
            // defer to Sketch's native decoder when the bytes are a natively-decodable image.
            val (head, read) = fetchResult.dataSource.peekHeader(32)
            if (read > 0 && ImageFormatSniffer.isNativelyDecodable(head, read)) return null
            return SketchTiffDecoder(requestContext, fetchResult.dataSource)
        }

        override fun equals(other: Any?): Boolean = this === other || other is Factory
        override fun hashCode(): Int = this@Factory::class.hashCode()
        override fun toString(): String = key
    }

    override suspend fun decode(): ImageData {
        val (w, h) = requestContext.targetDims()
        // Never read the whole file onto the heap: a large 16-bit TIFF (the file IS the raster) can
        // be 150+ MB and readByteArray() OOMs. Prefer a File / content URI so TiffImageDecoder can
        // memory-map + downsample oversized files; only tiny non-file sources fall back to bytes.
        val bitmap = decodeViaFileOrUri(w, h)
            ?: throw IllegalStateException("Unable to decode TIFF image")
        return dataSource.toImageData(bitmap, TIFF_MIMETYPE, requestContext)
    }

    private fun decodeViaFileOrUri(w: Int, h: Int): Bitmap? {
        val context = requestContext.request.context
        (dataSource as? ContentDataSource)?.let {
            return TiffImageDecoder.decode(context, it.contentUri, w, h)
        }
        dataSource.getFileOrNull(requestContext.sketch)?.let {
            return TiffImageDecoder.decode(it.toFile(), w, h)
        }
        val bytes = dataSource.openSource().buffer().use { it.readByteArray() }
        return TiffImageDecoder.decode(bytes, w, h)
    }

    override suspend fun getImageInfo(): ImageInfo {
        val context = requestContext.request.context
        val size = when {
            dataSource is ContentDataSource ->
                TiffImageDecoder.getSize(context, (dataSource as ContentDataSource).contentUri)
            else -> dataSource.getFileOrNull(requestContext.sketch)?.let {
                TiffImageDecoder.getSize(it.toFile())
            } ?: dataSource.openSource().buffer().use { TiffImageDecoder.getSize(it.readByteArray()) }
        }
        return ImageInfo(size?.width ?: 0, size?.height ?: 0, TIFF_MIMETYPE)
    }
}