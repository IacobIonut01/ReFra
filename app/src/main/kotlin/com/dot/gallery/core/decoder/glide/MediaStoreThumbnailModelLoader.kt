/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import java.io.IOException

/**
 * Phase 4 (#1076): dedicated local MediaStore motion-tier fast path.
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
        if (width == Target.SIZE_ORIGINAL || height == Target.SIZE_ORIGINAL) return null
        if (width <= 0 || height <= 0) return null
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) return null
        // Key by uri + exact size so distinct tiers cache independently and stay stable.
        val key = ObjectKey("mediastore-thumb-v2:$model:${width}x$height")
        return ModelLoader.LoadData(
            key,
            MediaStoreThumbnailFetcher(
                contentResolver = contentResolver,
                uri = model,
                width = width,
                height = height,
                shouldBypass = { shouldBypassPlatformThumbnail(model) },
            )
        )
    }

    private fun shouldBypassPlatformThumbnail(uri: Uri): Boolean {
        val key = uri.toString()
        bypassCache.get(key)?.let { return it }
        val shouldBypass = runCatching {
            if (contentResolver.getType(uri) != "image/jpeg") return@runCatching false
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                shouldBypassPlatformThumbnail(
                    hasEmbeddedThumbnail = exif.hasThumbnail(),
                    lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                )
            } ?: false
        }.getOrDefault(false)
        bypassCache.put(key, shouldBypass)
        return shouldBypass
    }

    private class MediaStoreThumbnailFetcher(
        private val contentResolver: ContentResolver,
        private val uri: Uri,
        private val width: Int,
        private val height: Int,
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
                callback.onDataReady(bitmap)
            } catch (e: Exception) {
                // Includes OperationCanceledException on cancel and IOException when the provider
                // cannot generate a thumbnail (unsupported format). Both fall through to the next
                // registered Glide loader.
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

    companion object {
        /**
         * Upper bound (px) for either dimension. Larger requests use the full pipeline.
         * Platform thumbnails are typically generated at ~512 px, so requests above
         * this size would receive an upscaled thumbnail instead of a real decode —
         * the #1119 wide-tile pixelation. Keep the fast path for the MOTION tier
         * (256 px) and small cells only.
         */
        private const val MAX_DIMENSION = 512
    }
}

internal fun shouldBypassPlatformThumbnail(
    hasEmbeddedThumbnail: Boolean,
    lensModel: String?,
): Boolean = hasEmbeddedThumbnail && lensModel?.contains("front camera", ignoreCase = true) == true
