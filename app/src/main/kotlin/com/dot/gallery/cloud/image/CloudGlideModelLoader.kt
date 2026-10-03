/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image

import android.net.Uri
import android.util.Log
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.capabilities.PeopleCapableProvider
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.resolveRemote
import com.dot.gallery.cloud.offline.CloudMediaCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Glide ModelLoader that resolves cloud:// URIs to authenticated HTTP requests
 * using the shared [CloudFetcherRegistryHolder.okHttpClient] (which supports
 * insecure TLS on debug/staging builds).
 */
class CloudGlideModelLoader : ModelLoader<Uri, InputStream> {

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        if (model.scheme != CloudMediaFetcher.SCHEME) return null

        val registry = CloudFetcherRegistryHolder.registry ?: return null

        // Shared slash-tolerant parse (remoteId may contain '/', e.g. WebDAV "Photos/IMG.jpg").
        val parsed = CloudUri.parse(model.toString()) ?: return null

        // Glide only renders grid/album thumbnails for cloud media (the media viewer uses Sketch).
        // When Glide requests a small target (a grid cell), fetch the small THUMBNAIL instead of
        // the larger PREVIEW — much less to download and, on WebDAV-family `core/preview`
        // endpoints, much cheaper for the server to generate. `original` and person thumbnails are
        // left untouched. A non-positive size (Target.SIZE_ORIGINAL) keeps the requested size.
        val effectiveSize = parsed.effectiveSize(maxOf(width, height))
        val acct = if (parsed.configId > 0L) "${parsed.configId}/" else ""
        val cacheKey = if (parsed.typeParam != null) {
            "${parsed.providerType.name}/$acct${parsed.typeParam}/${parsed.remoteId}"
        } else {
            "${parsed.providerType.name}/$acct${parsed.remoteId}/$effectiveSize"
        }
        return ModelLoader.LoadData(
            ObjectKey("v$CLOUD_GLIDE_SOURCE_VERSION/$cacheKey"),
            CloudProviderResolvingFetcher(registry, parsed, effectiveSize)
        )
    }

    override fun handles(model: Uri): Boolean = model.scheme == CloudMediaFetcher.SCHEME

    class Factory : ModelLoaderFactory<Uri, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, InputStream> =
            CloudGlideModelLoader()

        override fun teardown() {}
    }
}

private const val CLOUD_PROVIDER_INIT_TIMEOUT_MILLIS = 35_000L
private const val CLOUD_GLIDE_SOURCE_VERSION = 2

internal fun isClearlyTruncatedJpeg(contentType: String?, bytes: ByteArray): Boolean {
    val isJpeg = contentType?.startsWith("image/jpeg", ignoreCase = true) == true ||
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
    if (!isJpeg) return false
    return (1 until bytes.size).none { index ->
        bytes[index - 1] == 0xFF.toByte() && bytes[index] == 0xD9.toByte()
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

private fun createCloudDataFetcher(
    provider: RemoteMediaProvider,
    parsed: CloudUri,
    effectiveSize: String,
    scope: CoroutineScope
): DataFetcher<InputStream>? {
    val url = when (parsed.typeParam) {
        "person" -> (provider as? PeopleCapableProvider)?.getPersonThumbnailUrl(parsed.remoteId)
            ?: return null
        else -> when (effectiveSize) {
            "thumbnail" -> provider.getThumbnailUrl(parsed.remoteId, ThumbnailSize.THUMBNAIL, parsed.fileId)
            "preview" -> provider.getThumbnailUrl(parsed.remoteId, ThumbnailSize.PREVIEW, parsed.fileId)
            "original" -> provider.getOriginalUrl(parsed.remoteId)
            else -> provider.getThumbnailUrl(parsed.remoteId, ThumbnailSize.PREVIEW, parsed.fileId)
        }
    }
    if (url.isBlank()) {
        if (parsed.typeParam == null && effectiveSize != "original") {
            val size = if (effectiveSize == "thumbnail") ThumbnailSize.THUMBNAIL else ThumbnailSize.PREVIEW
            return CloudVideoFrameFetcher(provider, parsed.remoteId, size)
        }
        return null
    }
    return CloudOkHttpFetcher(
        url,
        provider.getAuthHeaders(),
        CloudMediaCache.keyFor(
            parsed.providerType,
            parsed.configId,
            parsed.remoteId,
            effectiveSize,
            parsed.typeParam
        ),
        parsed = parsed,
        scope = scope
    )
}

private class CloudProviderResolvingFetcher(
    private val registry: ProviderRegistry,
    private val parsed: CloudUri,
    private val effectiveSize: String
) : DataFetcher<InputStream> {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var delegate: DataFetcher<InputStream>? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        scope.launch {
            val provider = awaitInitializedRemoteProvider(
                registry,
                parsed.providerType,
                parsed.configId
            )
            if (provider == null) {
                if (isActive) callback.onLoadFailed(
                    IllegalStateException("Cloud provider did not initialize for ${parsed.providerType}/${parsed.configId}")
                )
                return@launch
            }
            val fetcher = createCloudDataFetcher(provider, parsed, effectiveSize, scope)
            if (fetcher == null) {
                if (isActive) callback.onLoadFailed(NoPreviewAvailableException(parsed.remoteId))
                return@launch
            }
            delegate = fetcher
            if (isActive) fetcher.loadData(priority, callback) else fetcher.cancel()
        }
    }

    override fun cleanup() {
        scope.cancel()
        delegate?.cleanup()
    }

    override fun cancel() {
        scope.cancel()
        delegate?.cancel()
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java
    override fun getDataSource(): DataSource = DataSource.REMOTE
}

/**
 * Glide DataFetcher that uses the shared OkHttpClient from [CloudFetcherRegistryHolder],
 * inheriting its TLS configuration (insecure on debug/staging).
 */
internal class CloudOkHttpFetcher(
    private val url: String,
    private val authHeaders: Map<String, String>,
    private val offlineKey: String,
    private val parsed: CloudUri,
    private val scope: CoroutineScope,
    private val logDebug: (String) -> Unit = CloudTrace::d,
    private val logWarning: (String) -> Unit = { CloudTrace.w(it) }
) : DataFetcher<InputStream> {

    private var call: Call? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        val client = CloudFetcherRegistryHolder.okHttpClient ?: OkHttpClient()
        val requestBuilder = Request.Builder().url(url)
        authHeaders.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        requestBuilder.addHeader(CloudMediaCache.HEADER_KEY, offlineKey)

        call = client.newCall(requestBuilder.build())
        try {
            logDebug("Glide.fetch -> GET $url")
            val start = System.nanoTime()
            call!!.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback.onLoadFailed(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!response.isSuccessful) {
                                logWarning("Glide.fetch -> HTTP ${response.code} for $url")
                                callback.onLoadFailed(Exception("HTTP ${response.code}: ${response.message}"))
                                return
                            }
                            val contentType = response.body.contentType()?.toString()
                                ?: response.header("Content-Type")
                            val bytes = response.body.bytes()
                            if (bytes.isEmpty()) {
                                callback.onLoadFailed(Exception("Empty response body (Content-Type=$contentType)"))
                                return
                            }
                            if (isClearlyTruncatedJpeg(contentType, bytes)) {
                                callback.onLoadFailed(Exception("Truncated JPEG response"))
                                return
                            }
                            logDebug("Glide.fetch <- ${CloudTrace.bytes(bytes.size.toLong())} ($contentType) in ${(System.nanoTime() - start) / 1_000_000}ms for $url")
                            // Servers sometimes answer a preview request with a non-image payload
                            // (HTML login/redirect page, JSON error, or an SVG mimetype icon when
                            // no real preview exists). Feeding those bytes to Glide produces a long
                            // chain of useless decode failures, so reject them up front with a
                            // descriptive error and log what was actually returned.
                            val isImage = contentType?.startsWith("image/", ignoreCase = true) == true
                            if (!isImage) {
                                val snippet = String(bytes.copyOf(minOf(bytes.size, 180)))
                                    .replace('\n', ' ').replace('\r', ' ').trim()
                                Log.w(
                                    "CloudFetcher",
                                    "Non-image preview response: url=$url contentType=$contentType " +
                                        "bytes=${bytes.size} snippet=\"$snippet\""
                                )
                                callback.onLoadFailed(
                                    Exception("Server returned non-image content (Content-Type=$contentType)")
                                )
                                return
                            }
                            // Read capture metadata without blocking the HTTP callback thread.
                            scope.launch {
                                try {
                                    CloudFetcherRegistryHolder.repository?.recordCaptureTime(
                                        parsed.providerType, parsed.configId, parsed.remoteId, url, bytes
                                    )
                                    if (isActive) callback.onDataReady(ByteArrayInputStream(bytes))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    if (isActive) callback.onLoadFailed(e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        callback.onLoadFailed(e)
                    }
                }
            })
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() { scope.cancel() }
    override fun cancel() {
        scope.cancel()
        call?.cancel()
    }
    override fun getDataClass(): Class<InputStream> = InputStream::class.java
    override fun getDataSource(): DataSource = DataSource.REMOTE
}

/**
 * Glide DataFetcher that produces a locally-decoded video poster frame for items that have
 * no server-side preview (videos on path-based stores like SMB/NFS/WebDAV). Delegates to
 * [RemoteMediaProvider.getVideoThumbnailBytes]. Runs on Glide's background thread, so the
 * blocking bridge to the provider's suspend API is safe here.
 */
private class CloudVideoFrameFetcher(
    private val provider: RemoteMediaProvider,
    private val remoteId: String,
    private val size: ThumbnailSize
) : DataFetcher<InputStream> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        try {
            val bytes = runBlocking { provider.getVideoThumbnailBytes(remoteId, size) }
            if (bytes != null && bytes.isNotEmpty()) {
                callback.onDataReady(ByteArrayInputStream(bytes))
            } else {
                callback.onLoadFailed(NoPreviewAvailableException(remoteId))
            }
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {}
    override fun cancel() {}
    override fun getDataClass(): Class<InputStream> = InputStream::class.java
    override fun getDataSource(): DataSource = DataSource.LOCAL
}
