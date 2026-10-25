/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size as AndroidSize
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.image.thumbnail.HeavyThumbFormat
import com.dot.gallery.core.image.thumbnail.HeavyThumbnailClassifier
import com.dot.gallery.core.image.thumbnail.HeavyThumbnailDecoder
import com.dot.gallery.core.image.thumbnail.LocalThumbnailStore
import com.dot.gallery.core.image.thumbnail.ThumbnailSingleFlight
import com.dot.gallery.core.image.thumbnail.ThumbnailTelemetry
import com.dot.gallery.core.logging.printError
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ImageData
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.util.Size
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import okio.buffer
import java.io.IOException

/**
 * Sketch ports of the Glide thumbnail fast paths (`MediaStoreThumbnailModelLoader`,
 * `PersistentThumbnailModelLoader`).
 *
 * Both are [Decoder.Factory] implementations that claim bounded `content://media` requests and
 * bypass the normal fetch+decode pipeline: the platform thumbnailer or the persistent thumbnail
 * store produces the bitmap directly. Out-of-scope requests return null from [Factory.create] so
 * the ordinary decoder chain handles them unchanged.
 *
 * Ordering: the persistent (heavy-format) decoder claims files the platform thumbnailer cannot
 * serve, while the MediaStore decoder self-excludes them via [shouldBypassPlatformThumbnail] —
 * the two are mutually exclusive, so registration order between them is irrelevant.
 */
fun ComponentRegistry.Builder.supportLocalThumbnailDecoders(): ComponentRegistry.Builder = apply {
    add(PersistentThumbnailDecoder.Factory())
    add(MediaStoreThumbnailDecoder.Factory())
}

/**
 * Upper bound (px) for either dimension — matches the Glide loaders' grid-cell scope so
 * full-resolution requests always take the real decode path.
 */
private const val MAX_THUMBNAIL_DIMENSION = 512

/**
 * Platform-thumbnail fast path (#1076 Phase 4), ported from `MediaStoreThumbnailModelLoader`.
 * `ContentResolver.loadThumbnail` is dramatically cheaper than a full decode for large media and
 * covers video poster frames too. Failures fall back to a sampled [BitmapFactory] decode of the
 * fetched source — the same safety net Glide's loader fallthrough provided.
 */
private class MediaStoreThumbnailDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
    private val resolver: ContentResolver,
    private val uri: android.net.Uri,
) : Decoder {

    override suspend fun decode(): ImageData {
        val target = requestContext.size
        val signal = CancellationSignal()
        currentCoroutineContext().job.invokeOnCompletion { signal.cancel() }
        try {
            val bitmap = resolver.loadThumbnail(uri, AndroidSize(target.width, target.height), signal)
            return imageDataFromBitmap(bitmap, requestContext, dataSource.dataFrom, "image/jpeg")
        } catch (e: OperationCanceledException) {
            throw e
        } catch (e: Exception) {
            printError("decode.thumb", "mediastore thumbnail load failed", e)
        }
        // Platform couldn't thumbnail this file — decode the fetched source directly.
        return dataSource.openSource().use { src ->
            val bitmap = src.buffer().inputStream().let { BitmapFactory.decodeStream(it) }
                ?: throw IOException("unable to decode $uri")
            imageDataFromBitmap(bitmap, requestContext, dataSource.dataFrom, "image/jpeg")
        }
    }

    override suspend fun getImageInfo(): ImageInfo = dataSource.openSource().use { src ->
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(src.buffer().inputStream(), null, opts)
        ImageInfo(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0), "image/jpeg")
    }

    class Factory : Decoder.Factory {

        override val key: String get() = "MediaStoreThumbnailDecoder"
        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val uri = requestContext.request.uri
            if (uri.scheme != ContentResolver.SCHEME_CONTENT ||
                uri.authority != MediaStore.AUTHORITY
            ) return null
            val target = requestContext.size
            if (target == Size.Origin) return null
            if (target.width <= 0 || target.height <= 0) return null
            if (target.width > MAX_THUMBNAIL_DIMENSION || target.height > MAX_THUMBNAIL_DIMENSION) {
                return null
            }
            val resolver = requestContext.sketch.context.contentResolver
            val androidUri = android.net.Uri.parse(uri.toString())
            if (shouldBypassPlatformThumbnail(resolver, androidUri, fetchResult.mimeType)) {
                return null
            }
            return MediaStoreThumbnailDecoder(requestContext, fetchResult.dataSource, resolver, androidUri)
        }

        override fun equals(other: Any?): Boolean = other is Factory
        override fun hashCode(): Int = Factory::class.hashCode()
        override fun toString(): String = key
    }

    companion object {
        /** Memoized per-Uri bypass decisions — same bound as the Glide loader's cache. */
        private val bypassCache = LruCache<String, Boolean>(512)

        private fun shouldBypassPlatformThumbnail(
            resolver: ContentResolver,
            uri: android.net.Uri,
            fetchedMime: String?
        ): Boolean {
            val key = uri.toString()
            bypassCache.get(key)?.let { return it }
            val shouldBypass = runCatching {
                val mime = resolver.getType(uri) ?: fetchedMime
                when {
                    // The platform thumbnailer has no decoder for software-only formats
                    // (JXL/PSD/JP2/TIFF/RAW, >10-bit HEIF) — claimed by the persistent decoder.
                    HeavyThumbnailClassifier.classify(resolver, uri) != null -> true
                    mime == "image/jpeg" -> {
                        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                            val exif = ExifInterface(descriptor.fileDescriptor)
                            shouldBypassPlatformThumbnail(
                                hasEmbeddedThumbnail = exif.hasThumbnail(),
                                lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                            )
                        } ?: false
                    }
                    // >10-bit HEIF/AVIF is already classified HEIF_SW above; no second probe.
                    else -> false
                }
            }.getOrDefault(false)
            bypassCache.put(key, shouldBypass)
            return shouldBypass
        }
    }
}

/** Embedded front-camera thumbnails may not match the full image — skip the fast path for those. */
internal fun shouldBypassPlatformThumbnail(
    hasEmbeddedThumbnail: Boolean,
    lensModel: String?,
): Boolean = hasEmbeddedThumbnail && lensModel?.contains("front camera", ignoreCase = true) == true

/**
 * Persistent-thumbnail fast path for software-decoded formats (#1276), ported from
 * `PersistentThumbnailModelLoader`. Serves a canonical [HeavyThumbnailDecoder.CANONICAL_PX] WebP
 * thumbnail from [LocalThumbnailStore], filling it with a single software decode on miss — one
 * decode per file per install, not one per grid pass.
 */
private class PersistentThumbnailDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
    private val resolver: ContentResolver,
    private val uri: android.net.Uri,
    private val kind: HeavyThumbFormat,
) : Decoder {

    override suspend fun decode(): ImageData {
        val context = requestContext.sketch.context
        val identity = mediaIdentity(resolver, uri)
            ?: throw IOException("no media identity for $uri")
        val store = LocalThumbnailStore.shared(context.applicationContext)
        serveStored(store, identity)?.let {
            return imageDataFromBitmap(it, requestContext, dataSource.dataFrom, mimeTypeFor(kind))
        }
        // A permanently undecodable file must not re-pay a multi-MB decode per grid pass.
        if (recentFailures.get(identity) != null) {
            throw IOException("decode previously failed ($kind)")
        }
        // Single-flight on the cache key: concurrent tier requests for the same file run one
        // decode + one write; the loser re-reads the just-written entry.
        return flights.run(identity) {
            serveStored(store, identity)?.let {
                return@run imageDataFromBitmap(it, requestContext, dataSource.dataFrom, mimeTypeFor(kind))
            }
            val bmp = HeavyThumbnailDecoder.decode(context.applicationContext, uri, kind)
                ?: throw IOException("heavy thumbnail decode failed ($kind)").also {
                    recentFailures.put(identity, true)
                }
            encodeCanonical(bmp)?.let { store.put(identity, it) }
            ThumbnailTelemetry.recordStoreFill(kind.name)
            imageDataFromBitmap(bmp, requestContext, dataSource.dataFrom, mimeTypeFor(kind))
        }
    }

    /** Stored thumbnail for [identity]; a corrupt entry is evicted so the next miss self-heals. */
    private fun serveStored(store: LocalThumbnailStore, identity: String) =
        store.get(identity)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: null.also { store.remove(identity) }
        }

    /**
     * `fstat` yields mtime + size in one binder round-trip; both feed the cache key so an
     * in-place edit lands a fresh entry while the stale one ages out via LRU. Providers that
     * don't support fstat (FUSE/remote) fall back to declared length only.
     */
    private fun mediaIdentity(resolver: ContentResolver, uri: android.net.Uri): String? {
        val stat = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { android.system.Os.fstat(it.fileDescriptor) }
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

    private fun encodeCanonical(bmp: android.graphics.Bitmap): ByteArray? {
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Bitmap.CompressFormat.WEBP
        }
        return runCatching {
            java.io.ByteArrayOutputStream().use { out ->
                bmp.compress(format, WEBP_QUALITY, out)
                out.toByteArray()
            }
        }.getOrNull()
    }

    private fun mimeTypeFor(kind: HeavyThumbFormat): String = when (kind) {
        HeavyThumbFormat.JXL -> "image/jxl"
        HeavyThumbFormat.PSD -> "image/vnd.adobe.photoshop"
        HeavyThumbFormat.JP2 -> "image/jp2"
        HeavyThumbFormat.TIFF -> "image/tiff"
        HeavyThumbFormat.RAW -> "image/x-adobe-dng"
        HeavyThumbFormat.HEIF_SW -> "image/heif"
    }

    override suspend fun getImageInfo(): ImageInfo {
        val target = requestContext.size
        return ImageInfo(
            width = target.width.coerceAtLeast(1),
            height = target.height.coerceAtLeast(1),
            mimeType = mimeTypeFor(kind)
        )
    }

    class Factory : Decoder.Factory {

        override val key: String get() = "PersistentThumbnailDecoder"
        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val uri = requestContext.request.uri
            if (uri.scheme != ContentResolver.SCHEME_CONTENT ||
                uri.authority != MediaStore.AUTHORITY
            ) return null
            val target = requestContext.size
            if (target == Size.Origin) return null
            if (target.width <= 0 || target.height <= 0) return null
            if (target.width > HeavyThumbnailDecoder.CANONICAL_PX ||
                target.height > HeavyThumbnailDecoder.CANONICAL_PX
            ) return null
            val resolver = requestContext.sketch.context.contentResolver
            val androidUri = android.net.Uri.parse(uri.toString())
            val kind = HeavyThumbnailClassifier.classify(resolver, androidUri) ?: return null
            return PersistentThumbnailDecoder(
                requestContext, fetchResult.dataSource, resolver, androidUri, kind
            )
        }

        override fun equals(other: Any?): Boolean = other is Factory
        override fun hashCode(): Int = Factory::class.hashCode()
        override fun toString(): String = key
    }

    companion object {
        private const val WEBP_QUALITY = 88
        private val flights = ThumbnailSingleFlight()

        /** Bounded memo of identities whose decode already failed this session (corrupt files). */
        private val recentFailures = LruCache<String, Boolean>(256)
    }
}
