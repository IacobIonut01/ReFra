package com.dot.gallery.core.decoder

import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.decode.DecodeResult
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.source.DataSource
import com.radzivon.bartoshyk.avif.coder.HeifCoder

fun ComponentRegistry.Builder.supportHeifDecoder(): ComponentRegistry.Builder = apply {
    addDecoder(SketchHeifDecoder.Factory())
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

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            return if (HEIF_MIMETYPES.any { fetchResult.mimeType?.contains(it) == true }) {
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

    override fun decode(): DecodeResult {
        return dataSource.withCustomDecoder(
            requestContext = requestContext,
            mimeType = mimeType,
            getSize = coder::getSize,
            decodeSampled = coder::decodeSampled
        )
    }

    override val imageInfo: ImageInfo by lazy {
        dataSource.getImageInfo(
            requestContext = requestContext,
            mimeType = mimeType,
            getSize = coder::getSize
        )
    }

}