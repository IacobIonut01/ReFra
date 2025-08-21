package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.github.panpf.sketch.decode.internal.ExifOrientationHelper
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Decodes generic images from decrypted InputStream (JPEG/PNG/etc).
 * Applies orientation if EXIF present in encrypted bytes.
 */
class EncryptedBitmapDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, Bitmap> {

    override fun handles(source: InputStream, options: Options): Boolean {
        // Fallback decoder; let specialized (HEIF/JXL) claim first.
        return true
    }

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bytes = source.readBytes()
        // Orientation extraction
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val helper = ExifOrientationHelper(orientation)
        val oriented = helper.applyToBitmap(bmp) ?: bmp
        return BitmapResource.obtain(oriented, bitmapPool)
    }
}