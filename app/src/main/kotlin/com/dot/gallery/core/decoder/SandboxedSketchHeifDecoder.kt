/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.os.Build
import android.util.Size as AndroidSize
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
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import okio.buffer
import kotlin.math.roundToInt

fun ComponentRegistry.Builder.supportSandboxedHeifDecoder(): ComponentRegistry.Builder = apply {
    add(SandboxedSketchHeifDecoder.Factory())
}

/**
 * Sketch decoder for HEIF/AVIF that delegates to [com.dot.gallery.core.sandbox.IsolatedDecoderService]
 * when sandboxed decoding is enabled. Falls through to the standard [SketchHeifDecoder] when disabled.
 */
class SandboxedSketchHeifDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
    private val mimeType: String
) : Decoder {

    private val sizeGetter = HeifCoder()

    class Factory : Decoder.Factory {

        override val key: String
            get() = "SandboxedHeifDecoder"

        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val context = requestContext.sketch.context
            if (!SandboxedDecoderHolder.isEnabled(context)) return null
            val mimeType = requestContext.request.extras?.get("realMimeType") as String? ?: return null
            return if (SketchHeifDecoder.Factory.HEIF_MIMETYPES.any { mimeType.contains(it) }) {
                SandboxedSketchHeifDecoder(requestContext, fetchResult.dataSource, fetchResult.mimeType!!)
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
        val sourceData = dataSource.openSource().use { src ->
            src.buffer().readByteArray()
        }

        // Animated AVIF: bypass sandbox and use ImageDecoder directly (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isAnimatedAvif(sourceData)) {
            val animated = decodeAnimatedAvif(
                bytes = sourceData,
                requestContext = requestContext,
                dataFrom = dataSource.dataFrom,
                mimeType = mimeType,
                getSize = sizeGetter::getSize
            )
            if (animated != null) return animated
        }

        val decoder = SandboxedDecoderHolder.decoder
            ?: return decodeStaticFromBytes(
                sourceData = sourceData,
                requestContext = requestContext,
                dataFrom = dataSource.dataFrom,
                mimeType = mimeType,
                getSize = sizeGetter::getSize,
                decodeSampled = sizeGetter::decodeSampled
            )

        val originalSize = sizeGetter.getSize(sourceData) ?: AndroidSize(0, 0)
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

        val decodedImage = decoder.decode(sourceData, mimeType, dstW, dstH)
            ?: sizeGetter.decodeSampled(sourceData, dstW, dstH)

        val imageInfo = ImageInfo(
            width = dstW,
            height = dstH,
            mimeType = mimeType,
        )
        val resize = requestContext.computeResize(imageInfo.size)
        return ImageData(
            image = decodedImage.asImage(),
            imageInfo = imageInfo,
            dataFrom = dataSource.dataFrom,
            resize = resize,
            transformeds = transformeds,
            extras = null
        )
    }

    override suspend fun getImageInfo(): ImageInfo {
        return dataSource.getImageInfo(
            requestContext = requestContext,
            mimeType = mimeType,
            getSize = sizeGetter::getSize
        )
    }
}
