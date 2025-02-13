package com.dot.gallery.core.decoder

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.core.net.toFile
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia
import com.github.panpf.zoomimage.subsampling.BitmapTileImage
import com.github.panpf.zoomimage.subsampling.ContentImageSource
import com.github.panpf.zoomimage.subsampling.ImageInfo
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.RegionDecoder
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.subsampling.TileImage
import com.github.panpf.zoomimage.subsampling.internal.ExifOrientationHelper
import com.github.panpf.zoomimage.util.IntRectCompat

class EncryptedRegionDecoder(
    override val subsamplingImage: SubsamplingImage,
    val imageSource: ImageSource,
    private val keychainHolder: KeychainHolder,
) : RegionDecoder {

    private var bitmapRegionDecoder: BitmapRegionDecoder? = null

    private val exifOrientationHelper: ExifOrientationHelper by lazy {
        val exifOrientation = imageSource.readEncryptedExifOrientation(keychainHolder)
        ExifOrientationHelper(exifOrientation)
    }

    override val imageInfo: ImageInfo by lazy { imageSource.readEncryptedImageInfo(keychainHolder) }

    override fun close() {
        bitmapRegionDecoder?.recycle()
    }

    override fun copy(): RegionDecoder {
        return EncryptedRegionDecoder(
            subsamplingImage = subsamplingImage,
            imageSource = imageSource,
            keychainHolder = keychainHolder
        )
    }

    override fun decodeRegion(key: String, region: IntRectCompat, sampleSize: Int): TileImage {
        prepare()
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val originalRegion = exifOrientationHelper
            .applyToRect(region, imageInfo.size, reverse = true)
        val bitmap = bitmapRegionDecoder!!.decodeRegion(originalRegion.toAndroidRect(), options)
        val tileImage = BitmapTileImage(bitmap, key, fromCache = false)
        val correctedImage = exifOrientationHelper.applyToTileImage(tileImage)
        return correctedImage
    }

    override fun prepare() {
        if (bitmapRegionDecoder != null) return

        val encryptedMedia = with(keychainHolder) {
            (imageSource as ContentImageSource).uri.toFile().decryptKotlin<EncryptedMedia>()
        }

        bitmapRegionDecoder = kotlin.runCatching {
            if (VERSION.SDK_INT >= VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(encryptedMedia.bytes, 0, encryptedMedia.bytes.size)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(encryptedMedia.bytes, 0, encryptedMedia.bytes.size, false)
            }
        }.apply {
            if (isFailure) {
                throw exceptionOrNull()!!
            }
        }.getOrThrow()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as EncryptedRegionDecoder
        if (subsamplingImage != other.subsamplingImage) return false
        if (imageSource != other.imageSource) return false
        return true
    }

    override fun hashCode(): Int {
        var result = subsamplingImage.hashCode()
        result = 31 * result + imageSource.hashCode()
        return result
    }

    override fun toString(): String {
        return "EncryptedRegionDecoder(subsamplingImage=$subsamplingImage, imageSource=$imageSource)"
    }

    private fun IntRectCompat.toAndroidRect(): Rect {
        return Rect(left, top, right, bottom)
    }

    class Factory(private val keychainHolder: KeychainHolder) : RegionDecoder.Factory {

        override suspend fun accept(subsamplingImage: SubsamplingImage): Boolean = true

        override fun checkSupport(mimeType: String): Boolean? = when (mimeType) {
            "image/jpeg", "image/png", "image/webp" -> true
            "image/gif", "image/bmp", "image/svg+xml" -> false
            "image/heic", "image/heif" -> true
            "image/avif" -> if (VERSION.SDK_INT <= 34) false else null
            else -> null
        }

        override fun create(
            subsamplingImage: SubsamplingImage,
            imageSource: ImageSource,
        ): EncryptedRegionDecoder = EncryptedRegionDecoder(
            subsamplingImage = subsamplingImage,
            imageSource = imageSource,
            keychainHolder = keychainHolder
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other != null && this::class == other::class
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }

        override fun toString(): String {
            return "EncryptedRegionDecoder"
        }
    }

}