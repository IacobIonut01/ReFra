package com.dot.gallery.injection

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.module.AppGlideModule
import com.dot.gallery.core.decoder.glide.EncryptedFileModelLoader
import com.dot.gallery.core.decoder.glide.EncryptedGenericImageDecoder
import com.dot.gallery.core.decoder.glide.EncryptedMediaSource
import com.dot.gallery.core.decoder.glide.EncryptedMediaStream
import com.dot.gallery.core.decoder.glide.EncryptedSourceToStreamLoader
import com.dot.gallery.core.decoder.glide.EncryptedStreamingFileLoader
import com.dot.gallery.core.decoder.glide.EncryptedStreamingUriLoader
import com.dot.gallery.core.decoder.glide.EncryptedUriModelLoader
import com.dot.gallery.core.decoder.glide.EncryptedVideoFrameDecoder
import com.dot.gallery.core.decoder.glide.HeifEncryptedDecoder
import com.dot.gallery.core.decoder.glide.HeifEncryptedSourceDecoder
import com.dot.gallery.core.decoder.glide.HeifMimeInputStreamDecoder
import com.dot.gallery.core.decoder.glide.JxlBitmapDecoder
import com.dot.gallery.core.decoder.glide.JxlEncryptedDecoder
import com.dot.gallery.core.decoder.glide.JxlEncryptedSourceDecoder
import com.dot.gallery.core.decoder.glide.MimeInputStream
import com.dot.gallery.core.decoder.glide.MimeInputStreamModelLoader
import com.dot.gallery.core.decoder.glide.StreamingEncryptedVideoFrameDecoder
import java.io.File
import java.io.InputStream

@GlideModule
class GlideModule: AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val pool: BitmapPool = glide.bitmapPool

        // New streaming model loaders (File/Uri -> EncryptedMediaSource -> InputStream) placed first.
        registry.prepend(
            File::class.java,
            EncryptedMediaSource::class.java,
            EncryptedStreamingFileLoader.Factory(context)
        )
        registry.prepend(
            Uri::class.java,
            EncryptedMediaSource::class.java,
            EncryptedStreamingUriLoader.Factory(context)
        )
        registry.prepend(
            EncryptedMediaSource::class.java,
            java.io.InputStream::class.java,
            EncryptedSourceToStreamLoader.Factory()
        )

        // Legacy byte-array path (will still catch cases needing format-specific decoders).
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
            Uri::class.java,
            MimeInputStream::class.java,
            MimeInputStreamModelLoader.Factory(context)
        )
        registry.prepend(
            MimeInputStream::class.java,
            Bitmap::class.java,
            HeifMimeInputStreamDecoder(pool)
        )
        registry.prepend(
            InputStream::class.java,
            Bitmap::class.java,
            JxlBitmapDecoder(pool)
        )

        // Decoders for our custom model type
        registry.prepend(
            EncryptedMediaStream::class.java,
            Bitmap::class.java,
            HeifEncryptedDecoder(pool)
        )

        // Bridging decoders: EncryptedMediaSource -> Bitmap (HEIF/JXL) without forcing legacy byte array for all images.
        registry.prepend(
            EncryptedMediaSource::class.java,
            Bitmap::class.java,
            HeifEncryptedSourceDecoder(pool)
        )
        registry.prepend(
            EncryptedMediaSource::class.java,
            Bitmap::class.java,
            JxlEncryptedSourceDecoder(pool)
        )
        // Streaming video frame decoder (EncryptedMediaSource -> Bitmap) preferred over legacy byte-array path
        registry.prepend(
            EncryptedMediaSource::class.java,
            Bitmap::class.java,
            StreamingEncryptedVideoFrameDecoder(pool, context.applicationContext) { context.cacheDir }
        )
        registry.prepend(
            EncryptedMediaStream::class.java,
            Bitmap::class.java,
            JxlEncryptedDecoder(pool)
        )
        registry.prepend(
            EncryptedMediaStream::class.java,
            Bitmap::class.java,
            EncryptedVideoFrameDecoder(pool) { context.cacheDir }
        )
        registry.prepend(
            EncryptedMediaStream::class.java,
            Bitmap::class.java,
            EncryptedGenericImageDecoder(pool)
        )
    }

    // Disable manifest parsing for speed
    override fun isManifestParsingEnabled(): Boolean = false

}