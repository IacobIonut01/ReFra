/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import android.util.Size as AndroidSize
import com.awxkee.jxlcoder.JxlCoder
import com.dot.gallery.core.decoder.SketchJxlDecoder.Factory.Companion.JXL_MIMETYPE
import com.dot.gallery.core.sandbox.SandboxedDecoderHolder
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.asImage
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.internal.createScaledTransformed
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ImageData
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.calculateScaleMultiplierWithOneSide
import okio.buffer
import kotlin.math.roundToInt

fun ComponentRegistry.Builder.supportSandboxedJxlDecoder(): ComponentRegistry.Builder = apply {
    add(SandboxedSketchJxlDecoder.Factory())
}

/**
 * Sketch decoder for JXL that delegates to [com.dot.gallery.core.sandbox.IsolatedDecoderService]
 * when sandboxed decoding is enabled. Falls through to the standard [SketchJxlDecoder] when disabled.
 */
class SandboxedSketchJxlDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
) : Decoder {

    class Factory : Decoder.Factory {

        override val key: String
            get() = "SandboxedJxlDecoder"

        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val context = requestContext.sketch.context
            if (!SandboxedDecoderHolder.isEnabled(context)) return null
            val mimeType = requestContext.request.extras?.get("realMimeType") as String? ?: return null
            return if (mimeType.contains(JXL_MIMETYPE)) {
                SandboxedSketchJxlDecoder(requestContext, fetchResult.dataSource)
            } else {
                null
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Factory
        }

        override fun hashCode(): Int = this@Factory::class.hashCode()

        override fun toString(): String = key
    }

    override suspend fun decode(): ImageData {
        val decoder = SandboxedDecoderHolder.decoder
            ?: return dataSource.withCustomDecoder(
                requestContext = requestContext,
                mimeType = JXL_MIMETYPE,
                getSize = JxlCoder::getSize,
                decodeSampled = JxlCoder::decodeSampled
            )

        return dataSource.openSource().use { src ->
            val sourceData = src.buffer().readByteArray()

            val originalSize = JxlCoder.getSize(sourceData) ?: AndroidSize(0, 0)
            val originalSketchSize = Size(originalSize.width, originalSize.height)
            val targetSize = requestContext.size
            val scale = calculateScaleMultiplierWithOneSide(
                sourceSize = originalSketchSize,
                targetSize = targetSize
            )
            var transformeds: List<String>? = null
            if (scale != 1f) {
                transformeds = listOf(createScaledTransformed(scale))
            }

            val dstW: Int
            val dstH: Int
            if (requestContext.size == Size.Origin) {
                dstW = originalSize.width
                dstH = originalSize.height
            } else {
                dstW = (originalSize.width * scale).roundToInt()
                dstH = (originalSize.height * scale).roundToInt()
            }

            val decodedImage = decoder.decode(sourceData, JXL_MIMETYPE, dstW, dstH)
                ?: JxlCoder.decodeSampled(sourceData, dstW, dstH)

            val imageInfo = ImageInfo(
                width = dstW,
                height = dstH,
                mimeType = JXL_MIMETYPE,
            )
            val resize = requestContext.computeResize(imageInfo.size)
            ImageData(
                image = decodedImage.asImage(),
                imageInfo = imageInfo,
                dataFrom = dataSource.dataFrom,
                resize = resize,
                transformeds = transformeds,
                extras = null
            )
        }
    }

    override suspend fun getImageInfo(): ImageInfo {
        return dataSource.getImageInfo(
            requestContext = requestContext,
            mimeType = JXL_MIMETYPE,
            getSize = JxlCoder::getSize
        )
    }
}
