package com.dot.gallery.core.decoder

import android.annotation.SuppressLint
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.BitmapParams
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.decryption.VaultDecryptCache
import com.dot.gallery.core.decryption.VaultDecryptStore
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.github.panpf.sketch.Image
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.asImage
import com.github.panpf.sketch.decode.DecodeConfig
import com.github.panpf.sketch.decode.DecodeException
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.internal.DecodeHelper
import com.github.panpf.sketch.decode.internal.ExifOrientationHelper
import com.github.panpf.sketch.decode.internal.HelperDecoder
import com.github.panpf.sketch.decode.internal.checkImageInfo
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.request.videoFrameMicros
import com.github.panpf.sketch.request.videoFrameOption
import com.github.panpf.sketch.request.videoFramePercent
import com.github.panpf.sketch.source.ContentDataSource
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.source.FileDataSource
import com.github.panpf.sketch.source.getFileOrNull
import com.github.panpf.sketch.util.Rect
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.div
import java.io.File

class EncryptedVideoFrameDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
    private val mimeType: String,
) : HelperDecoder(
    requestContext = requestContext,
    dataSource = dataSource,
    decodeHelperFactory = {
        EncryptedVideoFrameDecodeHelper(
            sketch = requestContext.sketch,
            request = requestContext.request,
            dataSource = dataSource,
            mimeType = mimeType
        )
    }
) {

    class Factory : Decoder.Factory {

        override val key: String = "EncryptedVideoFrameDecoder"

        override val sortWeight: Int = 0

        override fun create(
            requestContext: RequestContext,
            fetchResult: FetchResult
        ): EncryptedVideoFrameDecoder? {
            val mimeType = requestContext.request.extras?.get("realMimeType") as String? ?: return null
            val dataSource = fetchResult.dataSource as? FileDataSource ?: return null
            if (mimeType.startsWith("video")) {
                return EncryptedVideoFrameDecoder(
                    requestContext = requestContext,
                    dataSource = dataSource,
                    mimeType = mimeType
                )
            }
            return null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other != null && this::class == other::class
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }

        override fun toString(): String = "EncryptedVideoFrameDecoder"
    }
}

private class EncryptedVideoFrameDecodeHelper(
    val sketch: Sketch,
    val request: ImageRequest,
    val dataSource: DataSource,
    private val mimeType: String,
) : DecodeHelper {

    private var _cachedImageInfo: ImageInfo? = null

    override suspend fun getImageInfo(): ImageInfo {
        return _cachedImageInfo ?: readInfo().also { _cachedImageInfo = it }
    }

    override suspend fun isSupportRegion(): Boolean = false

    private val keychainHolder = KeychainHolder(request.context)

    // Not lazy-backed properties: close() must not trigger a decrypt on an unused helper (#1282).
    private var decryptedHandle: VaultDecryptCache.Handle? = null
    private var decryptedFileResolved = false
    private val tempFile: File?
        get() {
            if (!decryptedFileResolved) {
                decryptedFileResolved = true
                decryptedHandle = dataSource.getFileOrNull(sketch)?.toFile()
                    ?.let { VaultDecryptStore.acquire(keychainHolder, it) }
            }
            return decryptedHandle?.file
        }
    private var _mediaMetadataRetriever: MediaMetadataRetriever? = null
    private val mediaMetadataRetriever: MediaMetadataRetriever
        get() = _mediaMetadataRetriever ?: MediaMetadataRetriever().apply {
            if (dataSource is ContentDataSource) {
                setDataSource(request.context, dataSource.contentUri)
            } else {
                tempFile?.let {
                    setDataSource(it.path)
                } ?: throw Exception("Unsupported DataSource: ${dataSource::class}")
            }
        }.also { _mediaMetadataRetriever = it }
    private val exifOrientation: Int by lazy { readExifOrientation() }
    private val exifOrientationHelper by lazy { ExifOrientationHelper(exifOrientation) }

    override suspend fun decode(sampleSize: Int): Image {
        val frameMicros = request.videoFrameMicros
            ?: request.videoFramePercent?.let { percentDuration ->
                val duration = mediaMetadataRetriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                (duration * percentDuration * 1000).toLong()
            }
            ?: 0L
        val option = request.videoFrameOption ?: MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val imageSize = getImageInfo().size
        val dstSize = imageSize / sampleSize.toFloat()
        val config = DecodeConfig(request, getImageInfo().mimeType, isOpaque = false)
        val bitmapParams = BitmapParams().apply {
            config.colorType?.also { preferredConfig = it }
        }
        @SuppressLint("NewApi")
        val bitmap = mediaMetadataRetriever.getScaledFrameAtTime(
            /* timeUs = */ frameMicros,
            /* option = */ option,
            /* dstWidth = */ dstSize.width,
            /* dstHeight = */ dstSize.height,
            /* params = */ bitmapParams
        ) ?: throw DecodeException(
            "Failed to getScaledFrameAtTime. " +
                    "frameMicros=$frameMicros, " +
                    "option=${optionToName(option)}, " +
                    "dstSize=$dstSize, " +
                    "imageSize=$imageSize, " +
                    "preferredConfig=${config.colorType}"
        )
        val correctedBitmap = exifOrientationHelper.applyToBitmap(bitmap) ?: bitmap
        return correctedBitmap.asImage()
    }

    override suspend fun decodeRegion(region: Rect, sampleSize: Int): Image {
        throw UnsupportedOperationException("Unsupported region decode")
    }

    private fun readInfo(): ImageInfo {
        val srcWidth = mediaMetadataRetriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val srcHeight = mediaMetadataRetriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val imageSize = Size(width = srcWidth, height = srcHeight)
        val correctedImageSize = exifOrientationHelper.applyToSize(imageSize)
        return ImageInfo(size = correctedImageSize, mimeType = mimeType)
            .apply { checkImageInfo(this) }
    }

    private fun readExifOrientation(): Int {
        val videoRotation = mediaMetadataRetriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val exifOrientation = when (videoRotation) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_UNDEFINED
        }
        return exifOrientation
    }

    private fun optionToName(option: Int): String {
        return when (option) {
            MediaMetadataRetriever.OPTION_CLOSEST -> "CLOSEST"
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC -> "CLOSEST_SYNC"
            MediaMetadataRetriever.OPTION_NEXT_SYNC -> "NEXT_SYNC"
            MediaMetadataRetriever.OPTION_PREVIOUS_SYNC -> "PREVIOUS_SYNC"
            else -> "Unknown($option)"
        }
    }

    override fun toString(): String {
        return "VideoFrameDecodeHelper(uri='${request.uri}', dataSource=$dataSource, mimeType=$mimeType)"
    }

    override fun close() {
        _mediaMetadataRetriever?.let { runCatching { it.close() } }
        _mediaMetadataRetriever = null
        decryptedHandle?.release()
        decryptedHandle = null
    }
}
