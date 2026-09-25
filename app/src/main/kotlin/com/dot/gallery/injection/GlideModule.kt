package com.dot.gallery.injection

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.module.AppGlideModule
import com.dot.gallery.core.decoder.glide.EncryptedFileModelLoader
import com.dot.gallery.core.decoder.glide.EncryptedGenericImageDecoder
import com.dot.gallery.core.decoder.glide.EncryptedGifDecoder
import com.dot.gallery.core.decoder.glide.EncryptedMediaSource
import com.dot.gallery.core.decoder.glide.EncryptedMediaStream
import com.dot.gallery.core.decoder.glide.EncryptedSourceToStreamLoader
import com.dot.gallery.core.decoder.glide.EncryptedStreamingFileLoader
import com.dot.gallery.core.decoder.glide.EncryptedStreamingUriLoader
import com.dot.gallery.core.decoder.glide.EncryptedUriModelLoader
import com.dot.gallery.core.decoder.glide.EncryptedVideoFrameDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.ParcelFileDescriptor
import com.dot.gallery.core.decoder.glide.HeifBitmapDecoder
import com.dot.gallery.core.decoder.glide.HeifBitmapDrawableDecoder
import com.dot.gallery.core.decoder.glide.HeifDrawableDecoder
import com.dot.gallery.core.decoder.glide.HeifEncryptedDecoder
import com.dot.gallery.core.decoder.glide.HeifEncryptedSourceDecoder
import com.dot.gallery.core.decoder.glide.HeifInputStreamModelLoader
import com.dot.gallery.core.decoder.glide.HeifMimeInputStreamDecoder
import com.dot.gallery.core.decoder.glide.HeifPfdBitmapDecoder
import com.dot.gallery.core.decoder.glide.HeifPfdBitmapDrawableDecoder
import com.dot.gallery.core.decoder.glide.HeifPfdDrawableDecoder
import com.dot.gallery.core.decoder.glide.JxlBitmapDecoder
import com.dot.gallery.core.decoder.glide.SandboxedHeifBitmapDecoder
import com.dot.gallery.core.decoder.glide.SandboxedHeifMimeDecoder
import com.dot.gallery.core.decoder.glide.SandboxedJxlBitmapDecoder
import com.dot.gallery.core.decoder.glide.JxlEncryptedDecoder
import com.dot.gallery.core.decoder.glide.JxlEncryptedSourceDecoder
import com.dot.gallery.core.decoder.glide.MediaStoreThumbnailModelLoader
import com.dot.gallery.core.decoder.glide.MimeInputStream
import com.dot.gallery.cloud.image.CloudGlideModelLoader
import com.dot.gallery.core.decoder.glide.MimeInputStreamModelLoader
import com.dot.gallery.core.decoder.glide.PsdBitmapDecoder
import com.dot.gallery.core.decoder.glide.Jp2BitmapDecoder
import com.dot.gallery.core.decoder.glide.SvgBitmapDecoder
import com.dot.gallery.core.decoder.glide.TiffMimeInputStreamDecoder
import com.dot.gallery.core.decoder.glide.RawMimeInputStreamDecoder
import com.dot.gallery.core.decoder.glide.StreamingEncryptedVideoFrameDecoder
import java.io.File
import java.io.InputStream

@GlideModule
class GlideModule: AppGlideModule() {

    /**
     * Phase 5 (#1076): executor + disk-cache tuning to reduce thumbnail starvation.
     *
     * The default Glide source executor is capped at min(cpuCount, 4) threads. On multi-core
     * devices a few slow tasks (cloud fetch, HEIF/RAW decode) could occupy every source worker
     * and block visible local thumbnails, producing the contiguous blank runs in #1076. Sizing
     * the source pool to the core count (bounded to [4,6]) leaves spare workers for fast visible
     * local loads. Kept conservative so it does not steal memory the Sketch full-image viewer
     * needs; memory cache and bitmap pool remain at Glide's memory-class-derived defaults.
     */
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val cores = Runtime.getRuntime().availableProcessors()
        val sourceThreads = cores.coerceIn(4, 6)
        builder.setSourceExecutor(
            GlideExecutor.newSourceBuilder()
                .setThreadCount(sourceThreads)
                .setName("glide-source")
                .build()
        )
        // Bounded on-disk thumbnail cache (default is 250MB; make it explicit and stable).
        builder.setDiskCache(
            InternalCacheDiskCacheFactory(context, "image_manager_disk_cache", 250L * 1024 * 1024)
        )
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val pool: BitmapPool = glide.bitmapPool

        // Cloud media URI loader (cloud://{provider}/{remoteId})
        registry.prepend(
            Uri::class.java,
            InputStream::class.java,
            CloudGlideModelLoader.Factory()
        )

        // #1244: raw stream for >10-bit HEIF/AVIF media URIs, prepended ahead of Glide's
        // built-in thumbnail/stream fetchers (MediaStoreThumbFetcher re-encodes a corrupt
        // platform thumbnail; UriLoader feeds raw bytes to platform StreamBitmapDecoder).
        // Deep-bit-depth streams continue to HeifBitmapDecoder below; everything else fails
        // fast and falls through to the built-in loaders unchanged.
        registry.prepend(
            Uri::class.java,
            InputStream::class.java,
            HeifInputStreamModelLoader.Factory(context)
        )

        // Phase 4 (#1076): local MediaStore motion-tier fast path. Handles plain
        // content://media image/video URIs at grid sizes via ContentResolver.loadThumbnail
        // (cheap, cancellable) and declines everything else so it falls through to the normal
        // pipeline / custom decoders below.
        registry.prepend(
            Uri::class.java,
            Bitmap::class.java,
            MediaStoreThumbnailModelLoader.Factory(context)
        )

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
            HeifMimeInputStreamDecoder(context, pool)
        )
        // TIFF via content Uri MIME (image/tiff is reliably reported by MediaStore)
        registry.prepend(
            MimeInputStream::class.java,
            Bitmap::class.java,
            TiffMimeInputStreamDecoder(pool)
        )
        // Camera RAW (CR2/NEF/ARW/DNG/ORF/PEF/RW2/SRW/…) via content Uri MIME. Android can't
        // decode these natively, so the grid renders their embedded JPEG preview instead.
        registry.prepend(
            MimeInputStream::class.java,
            Bitmap::class.java,
            RawMimeInputStreamDecoder(pool)
        )
        // Formats Android can't decode natively, detected by magic bytes (MIME is unreliable):
        // PSD, JPEG 2000, and SVG (rasterized). Registered on the InputStream path.
        registry.prepend(
            InputStream::class.java,
            Bitmap::class.java,
            PsdBitmapDecoder(pool)
        )
        registry.prepend(
            InputStream::class.java,
            Bitmap::class.java,
            Jp2BitmapDecoder(pool)
        )
        registry.prepend(
            InputStream::class.java,
            Bitmap::class.java,
            SvgBitmapDecoder(pool)
        )
        // Sandboxed MIME decoder: when enabled, intercepts HEIF before HeifMimeInputStreamDecoder
        registry.prepend(
            MimeInputStream::class.java,
            Bitmap::class.java,
            SandboxedHeifMimeDecoder(context, pool)
        )
        registry.prepend(
            InputStream::class.java,
            Bitmap::class.java,
            JxlBitmapDecoder(pool)
        )
        // #1244: software decode for >10-bit HEIF/AVIF (depth-gated inside handles(), so
        // <=10-bit streams still reach the platform decoders below). decode(Drawable) requests
        // never consult Bitmap decoders — the Drawable/BitmapDrawable and ParcelFileDescriptor
        // resource paths get their own twins so a platform ImageDecoder can't claim the bytes
        // first on any of them.
        registry.prepend(
            InputStream::class.java,
            Bitmap::class.java,
            HeifBitmapDecoder(pool)
        )
        registry.prepend(
            InputStream::class.java,
            Drawable::class.java,
            HeifDrawableDecoder(context)
        )
        registry.prepend(
            InputStream::class.java,
            BitmapDrawable::class.java,
            HeifBitmapDrawableDecoder(context, pool)
        )
        registry.prepend(
            ParcelFileDescriptor::class.java,
            Bitmap::class.java,
            HeifPfdBitmapDecoder(pool)
        )
        registry.prepend(
            ParcelFileDescriptor::class.java,
            Drawable::class.java,
            HeifPfdDrawableDecoder(context)
        )
        registry.prepend(
            ParcelFileDescriptor::class.java,
            BitmapDrawable::class.java,
            HeifPfdBitmapDrawableDecoder(context, pool)
        )
        // Sandboxed decoders: when enabled, intercept before the standard decoders above
        registry.prepend(
            InputStream::class.java,
            Bitmap::class.java,
            SandboxedHeifBitmapDecoder(context, pool)
        )
        registry.prepend(
            InputStream::class.java,
            Bitmap::class.java,
            SandboxedJxlBitmapDecoder(context, pool)
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
        // GIF decoder for encrypted media - must be registered before generic image decoder
        // to properly handle animated GIFs in vault
        registry.prepend(
            EncryptedMediaStream::class.java,
            GifDrawable::class.java,
            EncryptedGifDecoder(context, pool, glide.arrayPool)
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