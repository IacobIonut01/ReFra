package com.dot.gallery.core.decoder

import android.os.Build
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.request.ImageData
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.source.DataSource
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import okio.buffer

fun ComponentRegistry.Builder.supportHeifDecoder(): ComponentRegistry.Builder = apply {
    add(SketchHeifDecoder.Factory())
}

@Suppress("SpellCheckingInspection")
class SketchHeifDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
    private val mimeType: String
) : Decoder {

    private val coder = HeifCoder()

    class Factory : Decoder.Factory {

        override val key: String
            get() = "HeifDecoder"

        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val mimeType = requestContext.request.extras?.get("realMimeType") as String? ?: return null
            return if (HEIF_MIMETYPES.any { mimeType.contains(it) }) {
                SketchHeifDecoder(requestContext, fetchResult.dataSource, fetchResult.mimeType!!)
            } else {
                null
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Factory
        }

        override fun hashCode(): Int {
            return this@Factory::class.hashCode()
        }

        override fun toString(): String = key

        companion object {
            val HEIF_MIMETYPES = listOf(
                "image/heif",
                "image/heic",
                "image/heif-sequence",
                "image/heic-sequence",
                "image/avif",
                "image/avis"
            )
        }
    }

    override suspend fun decode(): ImageData {
        val sourceData = dataSource.openSource().use { src ->
            src.buffer().readByteArray()
        }

        // Animated AVIF: requires API 31+ for ImageDecoder AVIF support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isAnimatedAvif(sourceData)) {
            val animated = decodeAnimatedAvif(
                bytes = sourceData,
                requestContext = requestContext,
                dataFrom = dataSource.dataFrom,
                mimeType = mimeType,
                getSize = coder::getSize
            )
            if (animated != null) return animated
        }

        // Static: use HeifCoder (works on all API levels)
        return decodeStaticFromBytes(
            sourceData = sourceData,
            requestContext = requestContext,
            dataFrom = dataSource.dataFrom,
            mimeType = mimeType,
            getSize = coder::getSize,
            decodeSampled = coder::decodeSampled
        )
    }

    override suspend fun getImageInfo(): ImageInfo {
        return dataSource.getImageInfo(
            requestContext = requestContext,
            mimeType = mimeType,
            getSize = coder::getSize
        )
    }

}