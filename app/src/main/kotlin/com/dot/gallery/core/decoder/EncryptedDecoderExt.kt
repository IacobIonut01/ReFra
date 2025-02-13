package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.core.net.toFile
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia
import com.github.panpf.sketch.decode.DecodeConfig
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.ImageInvalidException
import com.github.panpf.sketch.decode.internal.ExifOrientationHelper
import com.github.panpf.sketch.decode.internal.checkImageInfo
import com.github.panpf.sketch.decode.toBitmapOptions
import com.github.panpf.sketch.source.ContentDataSource
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.source.FileDataSource
import com.github.panpf.sketch.util.Rect
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.toAndroidRect
import java.io.File
import java.io.IOException

fun <T : DataSource> T.getFile(): File {
    return when (this) {
        is ContentDataSource -> contentUri.toFile()
        is FileDataSource -> path.toFile()
        else -> throw IllegalArgumentException("Unsupported DataSource type")
    }
}

/**
 * Decode encrypted bitmap using BitmapFactory
 */
@Throws(IOException::class)
fun DataSource.decodeEncryptedBitmap(
    keychainHolder: KeychainHolder,
    config: DecodeConfig? = null,
    exifOrientationHelper: ExifOrientationHelper? = null
): Bitmap {
    val options = config?.toBitmapOptions()
    val encryptedFile = getFile()
    val encryptedMedia = with(keychainHolder) {
        encryptedFile.decryptKotlin<EncryptedMedia>()
    }
    val bitmap = BitmapFactory.decodeByteArray(
        encryptedMedia.bytes,
        0,
        encryptedMedia.bytes.size,
        options.apply { this?.outMimeType = encryptedMedia.mimeType }
    ) ?: throw ImageInvalidException("decode return null at decodeEncryptedBitmap")
    val exifOrientationHelper1 =
        exifOrientationHelper ?: ExifOrientationHelper(readEncryptedExifOrientation(keychainHolder))
    return exifOrientationHelper1.applyToBitmap(bitmap) ?: bitmap
}

/**
 * Use EncryptedBitmapRegionDecoder to decode part of a bitmap region
 */
@Throws(IOException::class)
fun DataSource.decodeEncryptedRegionBitmap(
    keychainHolder: KeychainHolder,
    srcRect: Rect,
    config: DecodeConfig? = null,
    imageSize: Size? = null,
    exifOrientationHelper: ExifOrientationHelper? = null
): Bitmap {
    val encryptedFile = getFile()
    val encryptedMedia = with(keychainHolder) {
        encryptedFile.decryptKotlin<EncryptedMedia>()
    }
    val regionDecoder = if (VERSION.SDK_INT >= VERSION_CODES.S) {
        BitmapRegionDecoder.newInstance(encryptedMedia.bytes, 0, encryptedMedia.bytes.size)
    } else {
        @Suppress("DEPRECATION")
        BitmapRegionDecoder.newInstance(
            encryptedMedia.bytes,
            0,
            encryptedMedia.bytes.size,
            false
        )
    }
    val imageSize1 =
        imageSize ?: readEncryptedImageInfo(keychainHolder, exifOrientationHelper).size
    val exifOrientationHelper1 =
        exifOrientationHelper ?: ExifOrientationHelper(
            readEncryptedExifOrientation(
                keychainHolder
            )
        )
    val originalRegion = exifOrientationHelper1.applyToRect(
        srcRect = srcRect,
        spaceSize = imageSize1,
        reverse = true
    )
    val bitmapOptions = config?.toBitmapOptions()
    val regionBitmap = try {
        regionDecoder.decodeRegion(originalRegion.toAndroidRect(), bitmapOptions)
            ?: throw ImageInvalidException("decode return null at decodeEncryptedRegionBitmap")
    } finally {
        regionDecoder.recycle()
    }

    val correctedRegionImage =
        exifOrientationHelper1.applyToBitmap(regionBitmap) ?: regionBitmap
    return correctedRegionImage

}

@Throws(IOException::class)
fun DataSource.readEncryptedExifOrientation(keychainHolder: KeychainHolder): Int {
    val encryptedFile = getFile()
    val encryptedMedia = with(keychainHolder) {
        encryptedFile.decryptKotlin<EncryptedMedia>()
    }
    return encryptedMedia.bytes.inputStream().use {
        ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
    }
}

@Throws(IOException::class)
fun DataSource.readEncryptedImageInfoWithIgnoreExifOrientation(keychainHolder: KeychainHolder): ImageInfo {
    val boundOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    val encryptedFile = getFile()
    val encryptedMedia = with(keychainHolder) {
        encryptedFile.decryptKotlin<EncryptedMedia>()
    }
    try {
        BitmapFactory.decodeByteArray(
            encryptedMedia.bytes,
            0,
            encryptedMedia.bytes.size,
            boundOptions
        )
    } catch (e: Exception) {
        e.printStackTrace()
        throw ImageInvalidException("decode return null at readEncryptedImageInfoWithIgnoreExifOrientation")
    }

    val imageSize = Size(width = boundOptions.outWidth, height = boundOptions.outHeight)
    return ImageInfo(size = imageSize, mimeType = encryptedMedia.mimeType)
        .apply { checkImageInfo(this) }
}

/**
 * Read image information using BitmapFactory. Parse Exif orientation
 */
fun DataSource.readEncryptedImageInfo(
    keychainHolder: KeychainHolder,
    helper: ExifOrientationHelper?
): ImageInfo {
    val imageInfo = readEncryptedImageInfoWithIgnoreExifOrientation(keychainHolder)
    val exifOrientationHelper = if (helper != null) {
        helper
    } else {
        val exifOrientation =
            readEncryptedExifOrientationWithMimeType(keychainHolder, imageInfo.mimeType)
        ExifOrientationHelper(exifOrientation)
    }
    val correctedImageSize = exifOrientationHelper.applyToSize(imageInfo.size)
    return imageInfo.copy(size = correctedImageSize)
}

/**
 * Read the Exif orientation attribute of the image, if the mimeType is not supported, return [ExifInterface.ORIENTATION_UNDEFINED]
 */
@Throws(IOException::class)
fun DataSource.readEncryptedExifOrientationWithMimeType(
    keychainHolder: KeychainHolder,
    mimeType: String
): Int =
    if (ExifInterface.isSupportedMimeType(mimeType)) {
        readEncryptedExifOrientation(keychainHolder)
    } else {
        ExifInterface.ORIENTATION_UNDEFINED
    }

/**
 * Decode image width, height, MIME type. Parse Exif orientation
 */
fun DataSource.readEncryptedImageInfo(keychainHolder: KeychainHolder): ImageInfo =
    readEncryptedImageInfo(keychainHolder, null)