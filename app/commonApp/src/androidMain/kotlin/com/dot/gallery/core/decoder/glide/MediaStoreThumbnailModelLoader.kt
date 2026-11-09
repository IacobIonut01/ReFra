/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Option
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.dot.gallery.core.image.thumbnail.HeavyThumbnailClassifier
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printWarn
import java.io.IOException

/**
 * dedicated local MediaStore motion-tier fast path.
 *
 * Baseline device telemetry showed the entire slow (>750ms) thumbnail tail was fresh full-size
 * `LOCAL` decodes, while disk-cached loads were fast. Instead of decoding the full-size original
 * for a small grid cell, this loader asks the platform for its pre-generated thumbnail via
 * [ContentResolver.loadThumbnail], which is dramatically cheaper for large photos/videos and is
 * cancellable through a [CancellationSignal] wired to Glide's [DataFetcher.cancel].
 *
 * Scope guards keep it safe and non-regressing:
 * - Only plain MediaStore content URIs (`content://media/...`) are handled; cloud/vault/SAF/
 *   FileProvider URIs (different authorities/schemes) are declined so their existing loaders win.
 * - Only bounded grid-sized requests are handled (<= [MAX_DIMENSION]); larger requests and
 *   `SIZE_ORIGINAL` decline so full-quality paths are never degraded.
 * - Any failure (e.g. platform can't thumbnail JXL/PSD) declines to a normal fetcher failure, so
 *   Glide falls through to the next registered loader / custom decoder.
 */
class MediaStoreThumbnailModelLoader(
    private val contentResolver: ContentResolver
) : ModelLoader<Uri, Bitmap> {

    private val bypassCache = LruCache<String, Boolean>(512)

    override fun handles(model: Uri): Boolean {
        return model.scheme == ContentResolver.SCHEME_CONTENT &&
                model.authority == MediaStore.AUTHORITY
    }

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<Bitmap>? {
        // Decline unbounded/original-size requests and anything larger than a grid cell so the
        // full-resolution pipeline is never replaced by a capped platform thumbnail.
        if (!coversSize(width, height)) return null
        val allowUpscale = options.get(ThumbnailOptions.ALLOW_UPSCALE) == true
        // Key by uri + exact size so distinct tiers cache independently and stay stable. v3: the
        // fetcher now rejects thumbnails that would be stretched, so v2 entries may be soft.
        val key = ObjectKey("mediastore-thumb-v3:$model:${width}x$height")
        return ModelLoader.LoadData(
            key,
            MediaStoreThumbnailFetcher(
                contentResolver = contentResolver,
                uri = model,
                width = width,
                height = height,
                allowUpscale = allowUpscale,
                shouldBypass = { shouldBypassPlatformThumbnail(model) },
            )
        )
    }

    private fun shouldBypassPlatformThumbnail(uri: Uri): Boolean {
        val key = uri.toString()
        bypassCache.get(key)?.let { return it }
        val shouldBypass = runCatching {
            val mime = contentResolver.getType(uri)
            when {
                // the platform thumbnailer has no decoder for software-only formats
                // (JXL/PSD/JP2/TIFF/RAW, >10-bit HEIF) — attempting it is a guaranteed
                // IOException plus a per-cell error log, once per miss forever. classify() is
                // memoized per Uri, so URIs already claimed by the persistent thumbnail loader
                // cost a map hit; unusable-MIME formats get sniffed once per process.
                HeavyThumbnailClassifier.classify(contentResolver, uri) != null -> true
                mime == "image/jpeg" -> {
                    contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                        val exif = ExifInterface(descriptor.fileDescriptor)
                        shouldBypassPlatformThumbnail(
                            hasEmbeddedThumbnail = exif.hasThumbnail(),
                            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                        )
                    } ?: false
                }
                // >10-bit HEIF/AVIF (which the platform thumbnailer corrupts) is already
                // classified HEIF_SW above — re-probing here doubled the header read per file.
                else -> false
            }
        }.getOrDefault(false)
        bypassCache.put(key, shouldBypass)
        return shouldBypass
    }

    private class MediaStoreThumbnailFetcher(
        private val contentResolver: ContentResolver,
        private val uri: Uri,
        private val width: Int,
        private val height: Int,
        private val allowUpscale: Boolean,
        private val shouldBypass: () -> Boolean,
    ) : DataFetcher<Bitmap> {

        private val cancellationSignal = CancellationSignal()
        @Volatile private var cancelled = false

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
            if (cancelled) {
                callback.onLoadFailed(IOException("cancelled"))
                return
            }
            if (shouldBypass()) {
                callback.onLoadFailed(
                    IOException("Embedded front-camera thumbnail may not match the full image")
                )
                return
            }
            try {
                val bitmap = contentResolver.loadThumbnail(
                    uri,
                    Size(width, height),
                    cancellationSignal
                )
                // The platform caches one thumbnail per image and never upscales it, so it can be
                // smaller than the cell. Stretching it is visibly soft; fall through to a real
                // decode instead, unless the caller asked for speed over sharpness.
                if (!allowUpscale && !platformThumbnailFills(bitmap.width, bitmap.height, width, height)) {
                    bitmap.recycle()
                    callback.onLoadFailed(
                        IOException(
                            "platform thumbnail ${bitmap.width}x${bitmap.height} " +
                                "too small for ${width}x$height"
                        )
                    )
                    return
                }
                callback.onDataReady(bitmap)
            } catch (e: Exception) {
                // Includes OperationCanceledException on cancel and IOException when the provider
                // cannot generate a thumbnail (unsupported format). Both fall through to the next
                // registered Glide loader.
                if (e !is OperationCanceledException) {
                    printError("decode.thumb", "mediastore thumbnail load failed", e)
                }
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() {
            // The returned bitmap is owned by Glide's engine; nothing to release here.
        }

        override fun cancel() {
            cancelled = true
            try {
                cancellationSignal.cancel()
            } catch (_: Exception) {
                // Ignore: signal may already be consumed/finished.
            }
        }

        override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

        override fun getDataSource(): DataSource = DataSource.LOCAL
    }

    class Factory(private val context: Context) : ModelLoaderFactory<Uri, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, Bitmap> =
            MediaStoreThumbnailModelLoader(context.applicationContext.contentResolver)

        override fun teardown() {}
    }

    /**
     * Glide options for this loader. A separate holder rather than the companion: constructing a
     * Glide [Option] calls `TextUtils`, and the companion's pure size checks must stay loadable
     * in JVM unit tests.
     */
    object ThumbnailOptions {
        /**
         * Accept a platform thumbnail even when it must be stretched to fill the request. Set on
         * the scroll (MOTION) tier, where decode speed matters more than sharpness and the
         * refined request replaces it once the grid settles.
         */
        val ALLOW_UPSCALE: Option<Boolean> =
            Option.memory("com.dot.gallery.mediastore.allowUpscale", false)
    }

    companion object {
        /** Lower bound for [maxDimension], used when the display size is unknown. */
        private const val MIN_MAX_DIMENSION = 512

        /** Largest stretch accepted before a thumbnail counts as too small (10%). */
        private const val UPSCALE_TOLERANCE = 1.1f

        /**
         * Edge of the box MediaProvider caches thumbnails in: half the display's short edge
         * (`MediaProvider.mThumbSize`), floored at [MIN_MAX_DIMENSION].
         */
        fun platformThumbnailCap(displayWidthPx: Int, displayHeightPx: Int): Int =
            maxOf(MIN_MAX_DIMENSION, minOf(displayWidthPx, displayHeightPx) / 2)

        /**
         * Upper bound (px) for either request dimension. A larger request can never be filled by
         * the cached thumbnail, so it skips the platform call and decodes directly.
         */
        val maxDimension: Int by lazy {
            runCatching {
                val metrics = Resources.getSystem().displayMetrics
                platformThumbnailCap(metrics.widthPixels, metrics.heightPixels)
            }.getOrElse {
                printWarn(
                    "decode.thumb",
                    "display size unavailable; using default platform thumbnail cap",
                    ctx = mapOf("error" to it.toString()),
                )
                MIN_MAX_DIMENSION
            }
        }

        /**
         * True when a request of this size is served by this loader. Bounded and positive, so
         * `Target.SIZE_ORIGINAL` (a negative sentinel) is never covered.
         */
        fun coversSize(width: Int, height: Int, maxDimension: Int = this.maxDimension): Boolean =
            width in 1..maxDimension && height in 1..maxDimension

        /**
         * True when a [thumbWidth]x[thumbHeight] thumbnail can fill a [reqWidth]x[reqHeight]
         * request without being stretched beyond [UPSCALE_TOLERANCE]. Uses the centre-crop
         * scale (the larger axis ratio), which the grid uses and which is never looser than fit.
         */
        fun platformThumbnailFills(thumbWidth: Int, thumbHeight: Int, reqWidth: Int, reqHeight: Int): Boolean {
            if (thumbWidth <= 0 || thumbHeight <= 0) return false
            val scale = maxOf(reqWidth.toFloat() / thumbWidth, reqHeight.toFloat() / thumbHeight)
            return scale <= UPSCALE_TOLERANCE
        }
    }
}

internal fun shouldBypassPlatformThumbnail(
    hasEmbeddedThumbnail: Boolean,
    lensModel: String?,
): Boolean = hasEmbeddedThumbnail && lensModel?.contains("front camera", ignoreCase = true) == true
