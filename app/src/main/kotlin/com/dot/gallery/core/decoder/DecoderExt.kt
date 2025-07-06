package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import com.github.panpf.sketch.asImage
import com.github.panpf.sketch.decode.DecodeResult
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.internal.createScaledTransformed
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.calculateScaleMultiplierWithOneSide
import okio.buffer
import kotlin.math.roundToInt

inline fun DataSource.getImageInfo(
    requestContext: RequestContext,
    mimeType: String,
    getSize: (ByteArray) -> android.util.Size?
): ImageInfo {
    openSource().use { src ->
        val sourceData = src.buffer().readByteArray()
        val originalSizeDecoded = getSize(sourceData) ?: android.util.Size(0, 0)
        val size = if (requestContext.size == Size.Origin) {
            Size(originalSizeDecoded.width, originalSizeDecoded.height)
        } else {
            val scale = calculateScaleMultiplierWithOneSide(
                sourceSize = Size(originalSizeDecoded.width, originalSizeDecoded.height),
                targetSize = requestContext.size
            )
            Size(
                width = (originalSizeDecoded.width * scale).roundToInt(),
                height = (originalSizeDecoded.height * scale).roundToInt()
            )
        }
        return ImageInfo(
            width = size.width,
            height = size.height,
            mimeType = mimeType,
        )
    }
}

inline fun DataSource.withCustomDecoder(
    requestContext: RequestContext,
    mimeType: String,
    getSize: (ByteArray) -> android.util.Size?,
    decodeSampled: (ByteArray, Int, Int) -> Bitmap
): DecodeResult = openSource().use { src ->
    val sourceData = src.buffer().readByteArray()

    var transformeds: List<String>? = null
    val originalSizeDecoded = getSize(sourceData) ?: android.util.Size(0, 0)
    val originalSize = Size(originalSizeDecoded.width, originalSizeDecoded.height)
    val targetSize = requestContext.size
    val scale = calculateScaleMultiplierWithOneSide(
        sourceSize = originalSize,
        targetSize = targetSize
    )
    if (scale != 1f) {
        transformeds = listOf(createScaledTransformed(scale))
    }

    val imageInfo = getImageInfo(
        requestContext = requestContext,
        mimeType = mimeType,
        getSize = getSize
    )

    val decodedImage = if (requestContext.size == Size.Origin) {
        decodeSampled(
            sourceData,
            originalSize.width,
            originalSize.height
        )
    } else {
        val dstSize = Size(
            width = (originalSize.width * scale).roundToInt(),
            height = (originalSize.height * scale).roundToInt()
        )
        decodeSampled(
            sourceData,
            dstSize.width,
            dstSize.height
        )
    }

    val resize = requestContext.computeResize(imageInfo.size)
    DecodeResult(
        image = decodedImage.asImage(),
        imageInfo = imageInfo,
        dataFrom = dataFrom,
        resize = resize,
        transformeds = transformeds,
        extras = null
    )
}