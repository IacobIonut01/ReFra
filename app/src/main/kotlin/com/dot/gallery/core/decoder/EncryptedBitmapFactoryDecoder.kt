package com.dot.gallery.core.decoder

import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.Image
import com.github.panpf.sketch.asImage
import com.github.panpf.sketch.decode.DecodeConfig
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.internal.DecodeHelper
import com.github.panpf.sketch.decode.internal.ExifOrientationHelper
import com.github.panpf.sketch.decode.internal.HelperDecoder
import com.github.panpf.sketch.decode.internal.supportBitmapRegionDecoder
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.source.FileDataSource
import com.github.panpf.sketch.util.Rect

fun ComponentRegistry.Builder.supportVaultDecoder(): ComponentRegistry.Builder = apply {
    addDecoder(EncryptedBitmapFactoryDecoder.Factory())
    addDecoder(EncryptedVideoFrameDecoder.Factory())
}

open class EncryptedBitmapFactoryDecoder(
    requestContext: RequestContext,
    dataSource: DataSource,
) : HelperDecoder(
    requestContext = requestContext,
    dataSource = dataSource,
    decodeHelperFactory = { EncryptedBitmapFactoryDecodeHelper(requestContext.request, dataSource) }
) {

    class Factory : Decoder.Factory {

        override val key: String = "EncryptedBitmapFactoryDecoder"

        override fun create(
            requestContext: RequestContext,
            fetchResult: FetchResult
        ): Decoder? {
            val mimeType = requestContext.request.extras?.get("realMimeType") as String? ?: return null
            val dataSource = fetchResult.dataSource as? FileDataSource ?: return null
            val path = dataSource.getFile().path
            return if (path.toString().contains(BuildConfig.APPLICATION_ID) && mimeType.startsWith("image"))
                EncryptedBitmapFactoryDecoder(requestContext, dataSource)
            else null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other != null && this::class == other::class
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }

        override fun toString(): String = "EncryptedBitmapFactoryDecoder"
    }
}

private class EncryptedBitmapFactoryDecodeHelper(val request: ImageRequest, private val dataSource: DataSource) :
    DecodeHelper {

    private val keychainHolder = KeychainHolder(request.context)

    override val imageInfo: ImageInfo by lazy {
        dataSource.readEncryptedImageInfo(keychainHolder, exifOrientationHelper)
    }
    override val supportRegion: Boolean by lazy {
        // The result returns null, which means unknown, but future versions may support it, so it is still worth trying.
        supportBitmapRegionDecoder(imageInfo.mimeType) != false
    }

    private val exifOrientation: Int by lazy { dataSource.readEncryptedExifOrientation(keychainHolder) }
    private val exifOrientationHelper by lazy { ExifOrientationHelper(exifOrientation) }

    override fun decode(sampleSize: Int): Image {
        val decodeConfig = DecodeConfig(request, imageInfo.mimeType, isOpaque = false).apply {
            this.sampleSize = sampleSize
        }
        val bitmap = dataSource.decodeEncryptedBitmap(
            keychainHolder = keychainHolder,
            config = decodeConfig,
            exifOrientationHelper = exifOrientationHelper
        )
        return bitmap.asImage()
    }

    override fun decodeRegion(region: Rect, sampleSize: Int): Image {
        val decodeConfig = DecodeConfig(request, imageInfo.mimeType, isOpaque = false).apply {
            this.sampleSize = sampleSize
        }
        val bitmap = dataSource.decodeEncryptedRegionBitmap(
            keychainHolder = keychainHolder,
            srcRect = region,
            config = decodeConfig,
            imageSize = imageInfo.size,
            exifOrientationHelper = exifOrientationHelper
        )
        return bitmap.asImage()
    }

    override fun close() {

    }

    override fun toString(): String {
        return "EncryptedBitmapFactoryDecodeHelper(uri='${request.uri}', dataSource=$dataSource)"
    }
}