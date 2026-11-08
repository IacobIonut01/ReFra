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

/**
 * Decodes camera-RAW files (CR2/NEF/ARW/DNG/ORF/PEF/RW2/SRW/…) by extracting their largest
 * embedded JPEG preview via [TiffImageDecoder.decodePreview]. Android has no native decoder for
 * these, so without this Sketch reports "Invalid image size -1x-1" for every RAW asset (timeline
 * grid, viewer, and the ML search indexer). Gated on the RAW MIME (`realMimeType` extra when set,
 * otherwise the fetcher's resolved MIME) so it never hijacks formats handled elsewhere.
 */
class SketchRawDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
) : Decoder {

    class Factory : Decoder.Factory {
        override val key: String get() = "RawDecoder"
        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val realMime = requestContext.request.extras?.get("realMimeType") as String?
            val mime = realMime ?: fetchResult.mimeType ?: return null
            if (!isCameraRawMime(mime)) return null
            // MediaStore sometimes reports a RAW MIME (image/x-* / image/vnd.*) for a file
            // that is really a standard JPEG/PNG/WebP/GIF/BMP. Routing it here extracts only the
            // small embedded/EXIF thumbnail. Peek the header and defer to Sketch's native decoder
            // when the bytes are a natively-decodable image.
            val (head, read) = fetchResult.dataSource.peekHeader(32)
            if (read > 0 && ImageFormatSniffer.isNativelyDecodable(head, read)) return null
            return SketchRawDecoder(requestContext, fetchResult.dataSource)
        }

        override fun equals(other: Any?): Boolean = this === other || other is Factory
        override fun hashCode(): Int = this@Factory::class.hashCode()
        override fun toString(): String = key
    }

    override suspend fun decode(): ImageData {
        val bytes = dataSource.openSource().buffer().use { it.readByteArray() }
        val (w, h) = requestContext.targetDims()
        // Prefer the embedded JPEG preview (fastest); fall back to LibRaw's decoded thumbnail for
        // non-TIFF RAW (RAF/CRW/X3F) that has no extractable embedded JPEG.
        val bitmap = TiffImageDecoder.decodePreview(bytes, w, h)
            ?: NativeRawDecoder.getThumbnail(bytes)
            ?: throw IllegalStateException("Unable to decode RAW preview")
        return dataSource.toImageData(bitmap, RAW_MIMETYPE, requestContext)
    }

    override suspend fun getImageInfo(): ImageInfo {
        val bytes = dataSource.openSource().buffer().use { it.readByteArray() }
        val size = TiffImageDecoder.getSize(bytes)
            ?: NativeRawDecoder.getSize(bytes)?.let { android.util.Size(it.width, it.height) }
        return ImageInfo(size?.width ?: 0, size?.height ?: 0, RAW_MIMETYPE)
    }
}
