package com.dot.gallery.injection

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.module.AppGlideModule
import com.dot.gallery.core.decoder.glide.EncryptedFileModelLoader
import com.dot.gallery.core.decoder.glide.EncryptedGenericImageDecoder
import com.dot.gallery.core.decoder.glide.EncryptedMediaStream
import com.dot.gallery.core.decoder.glide.EncryptedUriModelLoader
import com.dot.gallery.core.decoder.glide.EncryptedVideoFrameDecoder
import com.dot.gallery.core.decoder.glide.HeifBitmapDecoder
import com.dot.gallery.core.decoder.glide.HeifEncryptedDecoder
import com.dot.gallery.core.decoder.glide.JxlBitmapDecoder
import com.dot.gallery.core.decoder.glide.JxlEncryptedDecoder
import java.io.File
import java.io.InputStream

@GlideModule
class GlideModule: AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val pool: BitmapPool = glide.bitmapPool

        // ModelLoaders: intercept both File and Uri BEFORE defaults.
        registry.prepend(
            File::class.java,
            EncryptedMediaStream::class.java,
            EncryptedFileModelLoader.Factory(context)
        )
        registry.prepend(
            Uri::class.java,
            EncryptedMediaStream::class.java,
            EncryptedUriModelLoader.Factory(context)
        )

        registry.prepend(
            InputStream::class.java,
            android.graphics.Bitmap::class.java,
            HeifBitmapDecoder(pool)
        )
        registry.prepend(
            InputStream::class.java,
            android.graphics.Bitmap::class.java,
            JxlBitmapDecoder(pool)
        )

        // Decoders for our custom model type
        registry.prepend(
            EncryptedMediaStream::class.java,
            android.graphics.Bitmap::class.java,
            HeifEncryptedDecoder(pool)
        )
        registry.prepend(
            EncryptedMediaStream::class.java,
            android.graphics.Bitmap::class.java,
            JxlEncryptedDecoder(pool)
        )
        registry.prepend(
            EncryptedMediaStream::class.java,
            android.graphics.Bitmap::class.java,
            EncryptedVideoFrameDecoder(pool) { context.cacheDir }
        )
        registry.prepend(
            EncryptedMediaStream::class.java,
            android.graphics.Bitmap::class.java,
            EncryptedGenericImageDecoder(pool)
        )
    }

    // Disable manifest parsing for speed
    override fun isManifestParsingEnabled(): Boolean = false

}