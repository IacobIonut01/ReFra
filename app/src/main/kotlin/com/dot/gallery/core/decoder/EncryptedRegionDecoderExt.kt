package com.dot.gallery.core.decoder

import android.graphics.BitmapFactory
import androidx.core.net.toFile
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia
import com.github.panpf.sketch.decode.ImageInvalidException
import com.github.panpf.sketch.decode.internal.ExifOrientationHelper
import com.github.panpf.sketch.util.Size
import com.github.panpf.zoomimage.subsampling.ContentImageSource
import com.github.panpf.zoomimage.subsampling.ImageInfo
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.util.IntSizeCompat
import com.github.panpf.zoomimage.util.isEmpty
import java.io.IOException

@Throws(IOException::class)
fun ImageSource.readEncryptedExifOrientation(keychainHolder: KeychainHolder): Int {
    return with(this as ContentImageSource) {
        val encryptedFile = uri.toFile()
        val encryptedMedia = with(keychainHolder) {
            encryptedFile.decryptKotlin<EncryptedMedia>()
        }
        encryptedMedia.bytes.inputStream().use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        }
    }
}

@Throws(IOException::class)
fun ImageSource.readEncryptedImageInfoWithIgnoreExifOrientation(keychainHolder: KeychainHolder): ImageInfo {
    with(this as ContentImageSource) {
        val boundOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val encryptedFile = uri.toFile()
        val encryptedMedia = with(keychainHolder) {
            encryptedFile.decryptKotlin<EncryptedMedia>()
        }
        try {
            BitmapFactory.decodeByteArray(
                encryptedMedia.bytes,
                0,
                encryptedMedia.bytes.size,
                boundOptions.apply { this.outMimeType = encryptedMedia.mimeType }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw ImageInvalidException("decode return null at readEncryptedImageInfoWithIgnoreExifOrientation")
        }

        val mimeType = encryptedMedia.mimeType
        val imageSize = IntSizeCompat(width = boundOptions.outWidth, height = boundOptions.outHeight)
        return ImageInfo(size = imageSize, mimeType = mimeType)
            .apply { checkImageInfo(this) }
    }
}

/**
 * Check if the image is valid
 */
fun checkImageSize(imageSize: IntSizeCompat) {
    if (imageSize.isEmpty()) {
        throw ImageInvalidException("Invalid image size. size=$imageSize")
    }
}

/**
 * Check if the image is valid
 */
fun checkImageInfo(imageInfo: ImageInfo) {
    checkImageSize(imageInfo.size)
}

    /**
 * Read image information using BitmapFactory. Parse Exif orientation
 */
fun ImageSource.readEncryptedImageInfo(keychainHolder: KeychainHolder, helper: ExifOrientationHelper?): ImageInfo {
    val imageInfo = readEncryptedImageInfoWithIgnoreExifOrientation(keychainHolder)
    val exifOrientationHelper = if (helper != null) {
        helper
    } else {
        val exifOrientation = readEncryptedExifOrientationWithMimeType(keychainHolder, imageInfo.mimeType)
        ExifOrientationHelper(exifOrientation)
    }
    val correctedImageSize = exifOrientationHelper.applyToSize(Size(width = imageInfo.size.width, height = imageInfo.size.height))
    return imageInfo.copy(size = correctedImageSize.toIntSizedCompat())
}
    fun Size.toIntSizedCompat() = IntSizeCompat(width = width, height = height)

/**
 * Read the Exif orientation attribute of the image, if the mimeType is not supported, return [ExifInterface.ORIENTATION_UNDEFINED]
 */
@Throws(IOException::class)
fun ImageSource.readEncryptedExifOrientationWithMimeType(keychainHolder: KeychainHolder, mimeType: String): Int =
    if (ExifInterface.isSupportedMimeType(mimeType)) {
        readEncryptedExifOrientation(keychainHolder)
    } else {
        ExifInterface.ORIENTATION_UNDEFINED
    }

/**
 * Decode image width, height, MIME type. Parse Exif orientation
 */
fun ImageSource.readEncryptedImageInfo(keychainHolder: KeychainHolder): ImageInfo = readEncryptedImageInfo(keychainHolder,null)
