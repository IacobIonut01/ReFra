/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.system.Os
import android.util.LruCache
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.DataFetcher.DataCallback
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.dot.gallery.core.image.thumbnail.HeavyThumbFormat
import com.dot.gallery.core.image.thumbnail.HeavyThumbnailClassifier
import com.dot.gallery.core.image.thumbnail.HeavyThumbnailDecoder
import com.dot.gallery.core.image.thumbnail.LocalThumbnailStore
import com.dot.gallery.core.image.thumbnail.ThumbnailSingleFlight
import com.dot.gallery.core.image.thumbnail.ThumbnailTelemetry
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * #1276: persistent-thumbnail fast path for software-decoded formats.
 *
 * The platform thumbnailer cannot serve JXL/PSD/JP2/TIFF/RAW or >10-bit HEIF, so every Glide
 * disk-cache miss for those files previously meant a full multi-MB software decode — and the
 * bounded cache evicted entries fast enough that "every miss" was effectively "every open" on
 * libraries of heavy formats. This loader instead serves a canonical
 * [HeavyThumbnailDecoder.CANONICAL_PX] WebP thumbnail from [LocalThumbnailStore]
 * (`filesDir/thumb_cache`, its own LRU, survives `cacheDir` purges), filling it with a single
 * software decode on miss — one decode per file per install, not one per open.
 *
 * Ordering: registered *after* [MediaStoreThumbnailModelLoader]'s prepend so it is checked
 * first; heavyweight URIs never reach the platform thumbnailer and everything else falls
 * through unchanged.
 *
 * Scope guards mirror the platform-thumbnail loader: only plain `content://media` Uris and
 * bounded requests ≤ [HeavyThumbnailDecoder.CANONICAL_PX]; larger/`SIZE_ORIGINAL` requests and
 * non-heavy formats decline so the full pipeline is never degraded. Fetch failures are ordinary
 * fetcher failures — Glide falls through to the per-mime decoders as before.
 */
class PersistentThumbnailModelLoader(
    private val context: Context
) : ModelLoader<Uri, Bitmap> {

    override fun handles(model: Uri): Boolean =
        model.scheme == ContentResolver.SCHEME_CONTENT && model.authority == MediaStore.AUTHORITY

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<Bitmap>? {
        if (width == Target.SIZE_ORIGINAL || height == Target.SIZE_ORIGINAL) return null
        if (width <= 0 || height <= 0) return null
        if (width > HeavyThumbnailDecoder.CANONICAL_PX || height > HeavyThumbnailDecoder.CANONICAL_PX) {
            return null
        }
        // Classification is memoized per Uri — a getType call (+ head sniff only for unusable
        // MIMEs) happens at most once per media item per process.
        val kind = HeavyThumbnailClassifier.classify(context.contentResolver, model)
            ?: return null
        return LoadData(
            ObjectKey("persistent-thumb-v1:$model"),
            PersistentThumbnailFetcher(context, model, kind)
        )
    }

    private class PersistentThumbnailFetcher(
        context: Context,
        private val uri: Uri,
        private val kind: HeavyThumbFormat,
    ) : DataFetcher<Bitmap> {

        private val appContext = context.applicationContext
        @Volatile private var cancelled = false

        override fun loadData(priority: Priority, callback: DataCallback<in Bitmap>) {
            if (cancelled) {
                callback.onLoadFailed(IOException("cancelled"))
                return
            }
            val identity = mediaIdentity(appContext.contentResolver, uri)
            if (identity == null) {
                callback.onLoadFailed(IOException("no media identity for $uri"))
                return
            }
            val store = LocalThumbnailStore.shared(appContext)
            serveStored(store, identity)?.let {
                callback.onDataReady(it)
                return
            }
            // Glide re-invokes fetchers on every request — a permanently undecodable file must
            // not re-pay a multi-MB decode per scroll pass. Bounded memo, this session only.
            if (recentFailures.get(identity) != null) {
                callback.onLoadFailed(IOException("decode previously failed ($kind)"))
                return
            }
            // Single-flight on the cache key: a concurrent MOTION+REFINED pair for the same file
            // runs one decode + one write; the loser re-reads the just-written entry.
            flights.run(identity) {
                serveStored(store, identity)?.let {
                    callback.onDataReady(it)
                    return@run
                }
                if (cancelled) {
                    callback.onLoadFailed(IOException("cancelled"))
                    return@run
                }
                val bmp = HeavyThumbnailDecoder.decode(appContext, uri, kind)
                if (bmp == null) {
                    recentFailures.put(identity, true)
                    callback.onLoadFailed(IOException("heavy thumbnail decode failed ($kind)"))
                    return@run
                }
                encodeCanonical(bmp)?.let { store.put(identity, it) }
                ThumbnailTelemetry.recordStoreFill(kind.name)
                callback.onDataReady(bmp)
            }
        }

        /** Stored thumbnail for [identity]; a corrupt entry is evicted so the next miss self-heals. */
        private fun serveStored(store: LocalThumbnailStore, identity: String): Bitmap? =
            store.get(identity)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: null.also { store.remove(identity) }
            }

        /**
         * `fstat` yields mtime + size in one binder round-trip; both feed the cache key so an
         * in-place edit lands a fresh entry while the stale one ages out via LRU. Providers that
         * don't support fstat (FUSE/remote) fall back to declared length only.
         */
        private fun mediaIdentity(resolver: ContentResolver, uri: Uri): String? {
            val stat = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { Os.fstat(it.fileDescriptor) }
            }.getOrNull()
            if (stat != null && stat.st_size > 0) {
                return LocalThumbnailStore.identity(uri.toString(), stat.st_mtime, stat.st_size)
            }
            val length = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.declaredLength }
            }.getOrNull()
            return length?.takeIf { it > 0 }
                ?.let { LocalThumbnailStore.identity(uri.toString(), -1L, it) }
        }

        private fun encodeCanonical(bmp: Bitmap): ByteArray? {
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            return runCatching {
                ByteArrayOutputStream().use { out ->
                    bmp.compress(format, WEBP_QUALITY, out)
                    out.toByteArray()
                }
            }.getOrNull()
        }

        override fun cleanup() {
            // The bitmap is owned by Glide's engine; the store file outlives it.
        }

        override fun cancel() {
            cancelled = true
        }

        override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

        override fun getDataSource(): DataSource = DataSource.LOCAL

        private companion object {
            /** Lossy WebP quality for canonical entries — thumbnails, not archival. */
            private const val WEBP_QUALITY = 88
        }
    }

    class Factory(private val context: Context) : ModelLoaderFactory<Uri, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, Bitmap> =
            PersistentThumbnailModelLoader(context.applicationContext)

        override fun teardown() {}
    }

    private companion object {
        private val flights = ThumbnailSingleFlight()

        /** Bounded memo of identities whose decode already failed this session (corrupt files). */
        private val recentFailures = LruCache<String, Boolean>(256)
    }
}
