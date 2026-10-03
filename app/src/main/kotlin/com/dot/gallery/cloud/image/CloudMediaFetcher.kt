/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image

import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.capabilities.PeopleCapableProvider
import com.dot.gallery.cloud.core.resolveRemote
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.fetch.Fetcher
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.source.ByteArrayDataSource
import com.github.panpf.sketch.source.DataFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Custom Sketch fetcher that resolves cloud:// URIs to authenticated HTTP requests.
 *
 * URI scheme: cloud://{providerType}/{remoteId}[?size=thumbnail|preview|original]
 *
 * Examples:
 *   cloud://IMMICH/abc-123?size=preview
 *   cloud://OWNCLOUD/12345?size=thumbnail
 */
class CloudMediaFetcher private constructor(
    private val providerType: ProviderType,
    private val remoteId: String,
    private val sizeParam: String,
    private val typeParam: String? = null,
    private val fileId: String? = null,
    private val configId: Long = -1L
) : Fetcher {

    override suspend fun fetch(): Result<FetchResult> = withContext(Dispatchers.IO) {
        try {
            val registry = CloudFetcherRegistryHolder.registry
                ?: return@withContext Result.failure(IllegalStateException("ProviderRegistry not available"))

            val provider = registry.resolveRemote(providerType, configId)
                ?: return@withContext Result.failure(IllegalStateException("No remote provider for $providerType"))

            val url = when (typeParam) {
                "person" -> {
                    (provider as? PeopleCapableProvider)?.getPersonThumbnailUrl(remoteId)
                        ?: return@withContext Result.failure(IllegalStateException("Provider does not support people"))
                }
                else -> when (sizeParam) {
                    "thumbnail" -> provider.getThumbnailUrl(remoteId, ThumbnailSize.THUMBNAIL, fileId)
                    "preview" -> provider.getThumbnailUrl(remoteId, ThumbnailSize.PREVIEW, fileId)
                    "original" -> provider.getOriginalUrl(remoteId)
                    else -> provider.getThumbnailUrl(remoteId, ThumbnailSize.PREVIEW, fileId)
                }
            }

            // No server preview URL. For videos on path-based stores (SMB/NFS/WebDAV) we
            // can still decode a poster frame locally from the original stream. Fall back
            // to that; if it's unavailable, fail quietly without issuing an HTTP request so
            // we don't spam 404s for items that can never produce a thumbnail.
            if (url.isBlank()) {
                if (typeParam != "person" && sizeParam != "original") {
                    val size = if (sizeParam == "thumbnail") ThumbnailSize.THUMBNAIL else ThumbnailSize.PREVIEW
                    val frame = provider.getVideoThumbnailBytes(remoteId, size)
                    if (frame != null && frame.isNotEmpty()) {
                        return@withContext Result.success(
                            FetchResult(ByteArrayDataSource(frame, DataFrom.LOCAL), "image/jpeg")
                        )
                    }
                }
                return@withContext Result.failure(NoPreviewAvailableException(remoteId))
            }

            val authHeaders = provider.getAuthHeaders()

            val requestBuilder = Request.Builder().url(url).get()
            authHeaders.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            // Persistent offline cache key (handled by CloudCacheInterceptor).
            requestBuilder.addHeader(
                CloudMediaCache.HEADER_KEY,
                CloudMediaCache.keyFor(providerType, configId, remoteId, sizeParam, typeParam)
            )

            val client = CloudFetcherRegistryHolder.okHttpClient
                ?: throw IllegalStateException("Cloud OkHttpClient not initialized — ProviderRegistry not ready")

            CloudTrace.d("Sketch.fetch[$providerType] $sizeParam '$remoteId' -> GET $url")
            val response = CloudTrace.time("Sketch.fetch[$providerType] $sizeParam '$remoteId' HTTP") {
                client.newCall(requestBuilder.build()).execute()
            }
            response.use {
                if (!it.isSuccessful) {
                    CloudTrace.w("Sketch.fetch[$providerType] $sizeParam '$remoteId' -> HTTP ${it.code}")
                    return@withContext Result.failure(Exception("HTTP ${it.code}: ${it.message}"))
                }

                val bytes = it.body.bytes()
                if (bytes.isEmpty()) {
                    return@withContext Result.failure(Exception("Empty response body"))
                }

                if (typeParam == null) {
                    CloudFetcherRegistryHolder.repository?.recordCaptureTime(
                        providerType, configId, remoteId, url, bytes
                    )
                }

                val mimeType = it.header("Content-Type")
                CloudTrace.d("Sketch.fetch[$providerType] $sizeParam '$remoteId' <- ${CloudTrace.bytes(bytes.size.toLong())} ($mimeType)")

                Result.success(
                    FetchResult(
                        dataSource = ByteArrayDataSource(bytes, DataFrom.NETWORK),
                        mimeType = mimeType
                    )
                )
            }
        } catch (e: Exception) {
            CloudTrace.w("Sketch.fetch[$providerType] $sizeParam '$remoteId' failed: ${e.message}")
            Result.failure(e)
        }
    }

    class Factory : Fetcher.Factory {

        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext): Fetcher? {
            val parsed = CloudUri.parse(requestContext.request.uri.toString()) ?: return null

            // Grid cells request a small target. For those, fetch the cheap THUMBNAIL instead of the
            // larger PREVIEW — this is critical for network filesystems (SMB/NFS) where PREVIEW is
            // generated by downloading the ENTIRE original on-device for every cell. Mirrors the
            // same downgrade in CloudGlideModelLoader so Sketch- and Glide-backed grids behave alike.
            val target = requestContext.size
            val requestedMax = if (target == Size.Origin) 0 else maxOf(target.width, target.height)
            val effectiveSize = parsed.effectiveSize(requestedMax)

            return CloudMediaFetcher(
                parsed.providerType, parsed.remoteId, effectiveSize,
                parsed.typeParam, parsed.fileId, parsed.configId
            )
        }

        override fun equals(other: Any?): Boolean = other is Factory
        override fun hashCode(): Int = Factory::class.hashCode()
        override fun toString(): String = "CloudMediaFetcher.Factory"
    }

    companion object {
        const val SCHEME = CloudUri.SCHEME

        fun buildUri(
            providerType: ProviderType,
            remoteId: String,
            size: ThumbnailSize = ThumbnailSize.PREVIEW,
            fileId: String? = null,
            configId: Long = -1L
        ): String {
            val sizeStr = when (size) {
                ThumbnailSize.THUMBNAIL -> "thumbnail"
                ThumbnailSize.PREVIEW -> "preview"
            }
            val sb = StringBuilder("$SCHEME://${providerType.name}/$remoteId?size=$sizeStr")
            if (!fileId.isNullOrBlank()) sb.append("&fileId=$fileId")
            if (configId > 0L) sb.append("&cfg=$configId")
            return sb.toString()
        }

        fun buildOriginalUri(providerType: ProviderType, remoteId: String, configId: Long = -1L): String {
            val base = "$SCHEME://${providerType.name}/$remoteId?size=original"
            return if (configId > 0L) "$base&cfg=$configId" else base
        }

        fun buildPersonUri(providerType: ProviderType, personId: String, configId: Long = -1L): String {
            val base = "$SCHEME://${providerType.name}/$personId?type=person"
            return if (configId > 0L) "$base&cfg=$configId" else base
        }
    }
}

/**
 * Singleton holder for the registry reference, set during app initialization.
 */
object CloudFetcherRegistryHolder {
    @Volatile
    var registry: ProviderRegistry? = null

    @Volatile
    var okHttpClient: OkHttpClient? = null

    @Volatile
    var repository: CloudRepository? = null
}

/**
 * Signals that a cloud item has no server-side preview (e.g. a video on a server
 * without preview generation). Used to fail the fetch quietly, without an HTTP
 * round-trip that would only return 404.
 */
class NoPreviewAvailableException(remoteId: String) :
    Exception("No server preview available for $remoteId")

internal fun <T> firstAvailableVideoFrame(
    durationMillis: Long?,
    frameAtTime: (Long) -> T?
): T? {
    val times = buildList {
        add(-1L)
        add(0L)
        add(1_000_000L)
        durationMillis?.takeIf { it > 0L }?.let {
            add(it.coerceAtMost(Long.MAX_VALUE / 500L) * 500L)
        }
    }.distinct()
    times.forEach { timeUs -> frameAtTime(timeUs)?.let { return it } }
    return null
}

fun ComponentRegistry.Builder.supportCloudMedia() {
    add(CloudMediaFetcher.Factory())
}
