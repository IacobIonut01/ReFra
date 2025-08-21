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

/**
 * Fallback decoder for decrypted images that are not handled by Heif/Jxl decoders.
 */
class EncryptedGenericImageDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<EncryptedMediaStream, Bitmap> {

    override fun handles(source: EncryptedMediaStream, options: Options): Boolean {
        if (source.isVideo) return false
        val mime = source.mimeType.lowercase()
        return mime.startsWith("image/")  // Let more specific decoders run first
    }

    override fun decode(
        source: EncryptedMediaStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bytes = source.bytes
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val oriented = ExifOrientationHelper(orientation).applyToBitmap(bitmap) ?: bitmap
        return BitmapResource.obtain(oriented, bitmapPool)
    }
}