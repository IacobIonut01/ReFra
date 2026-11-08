/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image

import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.capabilities.PeopleCapableProvider
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.resolveRemote
import com.dot.gallery.core.decoder.format.ImageFormatSniffer
import com.dot.gallery.core.logging.printWarn
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.fetch.Fetcher
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.source.ByteArrayDataSource
import com.github.panpf.sketch.source.DataFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import com.dot.gallery.cloud.offline.CloudCacheInterceptor
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.dot.gallery.cloud.core.ProviderRegistry
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection


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

            // Wait for the provider to finish connecting (cold start) the way the old Glide
            // loader did — a plain resolve would fail every grid cell during init.
            val provider = awaitInitializedRemoteProvider(registry, providerType, configId)
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
            val offlineKey = CloudMediaCache.keyFor(
                providerType, configId, remoteId, sizeParam, typeParam
            )
            fetchBytes(
                url = url,
                authHeaders = authHeaders,
                offlineKey = offlineKey,
                logTag = "Sketch.fetch[$providerType] $sizeParam '$remoteId'"
            )
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
}

/**
 * okhttp-backed impl of `DomainHooks.httpRangeFetcher` — ranged GET honoring the
 * `RemoteMediaProvider.fetchRange` contract: null when the server ignored Range on a non-zero
 * [offset] (full-body 200 can't serve a mid-file read), capped prefix for 200 at offset 0.
 */
suspend fun fetchHttpRange(
    url: String,
    offset: Long,
    length: Long,
    headers: Map<String, String>,
): ByteArray? = withContext(Dispatchers.IO) {
    val client = CloudFetcherRegistryHolder.okHttpClient ?: return@withContext null
    runCatching {
        val request = Request.Builder().url(url)
            .header("Range", "bytes=$offset-${offset + length - 1}")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            when {
                !response.isSuccessful -> null
                offset > 0L && response.code != HttpURLConnection.HTTP_PARTIAL -> null
                else -> response.body.byteStream().use { stream ->
                    val out = ByteArrayOutputStream(minOf(length, 64L * 1024L).toInt())
                    val buffer = ByteArray(16 * 1024)
                    var remaining = length
                    while (remaining > 0L) {
                        val read = stream.read(
                            buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()
                        )
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                    out.toByteArray().takeIf { it.isNotEmpty() }
                }
            }
        }
    }.getOrNull()
}

/**
 * Signals that a cloud item has no server-side preview (e.g. a video on a server
 * without preview generation). Used to fail the fetch quietly, without an HTTP
 * round-trip that would only return 404.
 */
class NoPreviewAvailableException(remoteId: String) :
    Exception("No server preview available for $remoteId")

private const val CLOUD_PROVIDER_INIT_TIMEOUT_MILLIS = 35_000L

internal fun isClearlyTruncatedJpeg(contentType: String?, bytes: ByteArray): Boolean {
    val isJpeg = contentType?.startsWith("image/jpeg", ignoreCase = true) == true ||
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
    if (!isJpeg) return false
    return (1 until bytes.size).none { index ->
        bytes[index - 1] == 0xFF.toByte() && bytes[index] == 0xD9.toByte()
    }
}

/**
 * The image MIME type of a cloud response, or null for a non-image payload. A declared
 * `image/` type wins; otherwise the type is sniffed from the bytes, because offline-cache hits
 * and some servers report no type or `application/octet-stream` for valid images.
 */
internal fun resolveImageMimeType(contentType: String?, bytes: ByteArray): String? =
    contentType?.takeIf { it.startsWith("image/", ignoreCase = true) }
        ?: ImageFormatSniffer.standardMimeFor(bytes)

/**
 * Executes the authenticated GET for a cloud image and packages the body as a [FetchResult].
 * Extracted so the fetch pipeline is unit-testable without a [RequestContext].
 * Reads the body eagerly — the response is closed before returning.
 */
internal fun fetchBytes(
    url: String,
    authHeaders: Map<String, String>,
    offlineKey: String,
    logTag: String = "Sketch.fetch"
): Result<FetchResult> {
    val client = CloudFetcherRegistryHolder.okHttpClient ?: OkHttpClient()
    val requestBuilder = Request.Builder().url(url).get()
    authHeaders.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
    // Persistent offline cache key (handled by CloudCacheInterceptor).
    requestBuilder.addHeader(CloudMediaCache.HEADER_KEY, offlineKey)

    CloudTrace.d("$logTag -> GET $url")
    val response = CloudTrace.time("$logTag HTTP") {
        client.newCall(requestBuilder.build()).execute()
    }
    response.use {
        if (!it.isSuccessful) {
            CloudTrace.w("$logTag -> HTTP ${it.code}")
            return Result.failure(Exception("HTTP ${it.code}: ${it.message}"))
        }

        val bytes = it.body.bytes()
        if (bytes.isEmpty()) {
            return Result.failure(Exception("Empty response body"))
        }
        if (isClearlyTruncatedJpeg(it.header("Content-Type"), bytes)) {
            return Result.failure(Exception("Truncated JPEG response"))
        }

        val contentType = it.body.contentType()?.toString() ?: it.header("Content-Type")
        // Servers sometimes answer a preview request with a non-image payload (HTML
        // login/redirect page, JSON error, or an SVG mimetype icon when no real preview
        // exists). Feeding those bytes to the decoder produces a long chain of useless
        // decode failures, so reject them up front.
        val mimeType = resolveImageMimeType(contentType, bytes)
        if (mimeType == null) {
            val snippet = String(bytes.copyOf(minOf(bytes.size, 180)))
                .replace('\n', ' ').replace('\r', ' ').trim()
            printWarn(
                "cloud.fetch",
                "Non-image preview response: url=$url contentType=$contentType " +
                    "bytes=${bytes.size} snippet=\"$snippet\""
            )
            return Result.failure(
                Exception("Server returned non-image content (Content-Type=$contentType)")
            )
        }

        CloudTrace.d("$logTag <- ${CloudTrace.bytes(bytes.size.toLong())} ($mimeType)")
        return Result.success(
            FetchResult(
                dataSource = ByteArrayDataSource(bytes, DataFrom.NETWORK),
                mimeType = mimeType
            )
        )
    }
}

internal suspend fun awaitInitializedRemoteProvider(
    registry: ProviderRegistry,
    providerType: ProviderType,
    configId: Long,
    timeoutMillis: Long = CLOUD_PROVIDER_INIT_TIMEOUT_MILLIS
): RemoteMediaProvider? = withTimeoutOrNull(timeoutMillis) {
    if (configId <= 0L) {
        registry.resolveRemote(providerType, configId) ?: registry.connectionStates
            .map { registry.resolveRemote(providerType, configId) }
            .first { it != null }
    } else {
        registry.connectionStates
            .map { states -> states[configId] to registry.resolveRemote(providerType, configId) }
            .first { (state, _) ->
                state == ConnectionState.ERROR || state == ConnectionState.CONNECTED
            }
            .second
    }
}

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
