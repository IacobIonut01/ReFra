/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.resolveRemote
import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import okhttp3.Request
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.cloud.image.CloudMediaFetcher
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.domain.uri.scheme
import okhttp3.CacheControl


/**
 * ExoPlayer [DataSource] that resolves `cloud://` URIs to authenticated HTTP streams.
 *
 * URI format: `cloud://{ProviderType}/{remoteId}[?size=original|preview|thumbnail]`
 *
 * For video playback the original URL is always used regardless of the `size` parameter,
 * since ExoPlayer needs the full media stream.
 *
 * If the URI is **not** a `cloud://` scheme, the request is delegated to a
 * [DefaultDataSource] so local content:// and file:// URIs still work.
 */
@OptIn(UnstableApi::class)
class CloudDataSource private constructor(
    private val fallbackFactory: DataSource.Factory
) : BaseDataSource(/* isNetwork = */ true) {

    private var activeSource: DataSource? = null
    private var openedCloudStream = false

    // OkHttp call handle for cancellation
    private var activeCall: okhttp3.Call? = null
    private var inputStream: java.io.InputStream? = null
    private var bytesRemaining: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri

        if (uri.scheme != CloudMediaFetcher.SCHEME) {
            // Delegate non-cloud URIs to the default pipeline
            val fallback = fallbackFactory.createDataSource()
            activeSource = fallback
            return fallback.open(dataSpec)
        }

        // Resolve cloud:// → authenticated HTTP via the shared slash-tolerant parser
        // (remoteId may contain slashes, e.g. WebDAV/SMB/NFS paths like "Photos/clip.mp4").
        val parsed = CloudUri.parse(uri.toString())
            ?: throw IllegalArgumentException("Invalid cloud URI: $uri")
        val providerType = parsed.providerType
        val remoteId = parsed.remoteId

        val registry = CloudFetcherRegistryHolder.registry
            ?: throw IllegalStateException("ProviderRegistry not available")
        val provider = registry.resolveRemote(providerType, parsed.configId)
            ?: throw IllegalStateException("No RemoteMediaProvider for $providerType")

        val url = provider.getOriginalUrl(remoteId)
        val authHeaders = provider.getAuthHeaders()

        val client = CloudFetcherRegistryHolder.okHttpClient ?: okhttp3.OkHttpClient()
        val position = dataSpec.position
        val requestedLength = dataSpec.length
        val requestBuilder = Request.Builder().url(url)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
        authHeaders.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        // Support range requests for seeking — also send an explicit length-bounded
        // range when ExoPlayer knows how many bytes it wants.
        if (position > 0 || requestedLength != C.LENGTH_UNSET.toLong()) {
            requestBuilder.addHeader(
                "Range",
                if (requestedLength != C.LENGTH_UNSET.toLong()) {
                    "bytes=$position-${position + requestedLength - 1}"
                } else {
                    "bytes=$position-"
                }
            )
        }

        val call = client.newCall(requestBuilder.build())
        activeCall = call
        CloudTrace.d("Video[$providerType] '$remoteId' -> GET $url (pos=$position)")
        val response = CloudTrace.time("Video[$providerType] '$remoteId' open") {
            call.execute()
        }

        // A 416 past the end of the file means the player is seeking beyond EOF —
        // report end-of-input instead of failing the source.
        if (response.code == 416 && position > 0) {
            CloudTrace.d("Video[$providerType] '$remoteId' -> HTTP 416 at pos=$position; EOF")
            response.close()
            bytesRemaining = 0L
            return 0L
        }

        if (!response.isSuccessful) {
            CloudTrace.w("Video[$providerType] '$remoteId' -> HTTP ${response.code}")
            printWarn(
                "cloud.video",
                "cloud media open failed: HTTP ${response.code}",
                mapOf(
                    "remoteId" to remoteId,
                    "provider" to providerType.name,
                    "code" to response.code.toString()
                )
            )
            response.close()
            throw java.io.IOException("HTTP ${response.code}: ${response.message}")
        }

        return try {
            val body = response.body
            val contentLength = body.contentLength()
            val rangeOpen = cloudRangeOpen(position, requestedLength, response.code, contentLength)
            val source = body.source()
            if (rangeOpen.skipBytes > 0) {
                // The server ignored Range and sent the whole body — skip to the
                // requested offset. skip() throws EOFException on a short body.
                source.skip(rangeOpen.skipBytes)
                CloudTrace.w(
                    "Video[$providerType] '$remoteId' server ignored Range; " +
                        "skipped ${rangeOpen.skipBytes} bytes"
                )
                printWarn(
                    "cloud.video",
                    "server ignored Range; skipped ${rangeOpen.skipBytes} bytes",
                    mapOf("remoteId" to remoteId, "provider" to providerType.name)
                )
            }
            inputStream = source.inputStream()
            CloudTrace.d(
                "Video[$providerType] '$remoteId' streaming " +
                    CloudTrace.bytes(if (rangeOpen.length > 0) rangeOpen.length else contentLength)
            )
            bytesRemaining = if (rangeOpen.length == C.LENGTH_UNSET.toLong()) {
                Long.MAX_VALUE
            } else {
                rangeOpen.length
            }

            transferInitializing(dataSpec)
            transferStarted(dataSpec)
            openedCloudStream = true

            rangeOpen.length
        } catch (t: Throwable) {
            response.close()
            inputStream = null
            activeCall = null
            bytesRemaining = 0L
            throw t
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        activeSource?.let { return it.read(buffer, offset, length) }

        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = inputStream
            ?: return C.RESULT_END_OF_INPUT

        val toRead = if (bytesRemaining == Long.MAX_VALUE) length
        else minOf(length.toLong(), bytesRemaining).toInt()

        val bytesRead = stream.read(buffer, offset, toRead)
        if (bytesRead == -1) {
            bytesRemaining = 0
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != Long.MAX_VALUE) {
            bytesRemaining -= bytesRead
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? {
        return activeSource?.uri
    }

    override fun close() {
        try {
            activeSource?.close()
            activeSource = null
        } finally {
            try {
                inputStream?.close()
                inputStream = null
            } finally {
                activeCall = null
                if (openedCloudStream) {
                    openedCloudStream = false
                    transferEnded()
                }
            }
        }
    }

    /**
     * Factory that creates [CloudDataSource] instances.
     *
     * Usage in ExoPlayer:
     * ```
     * ExoPlayer.Builder(context)
     *     .setMediaSourceFactory(
     *         DefaultMediaSourceFactory(CloudDataSource.Factory(context))
     *     )
     *     .build()
     * ```
     */
    class Factory(
        private val fallbackFactory: DataSource.Factory
    ) : DataSource.Factory {

        constructor(context: android.content.Context) : this(
            DefaultDataSource.Factory(context)
        )

        override fun createDataSource(): DataSource = CloudDataSource(fallbackFactory)
    }
}

/** How an opened cloud response maps onto the requested byte range. */
internal data class CloudRangeOpen(
    /** Bytes to discard from the start of the response body (server ignored Range). */
    val skipBytes: Long,
    /** Bytes the source may still deliver; [C.LENGTH_UNSET] when unknown. */
    val length: Long
)

/**
 * Interprets an open() response for a ranged request.
 *
 * A 206 honors the requested range, so the body starts at [position] and its
 * length is the response's content length (or the requested length / unset when
 * the server doesn't report one). Any other successful code means the server
 * ignored Range and sent the whole entity: the caller must skip [position]
 * bytes itself, and the deliverable length is the remainder of the body,
 * capped by the requested length when one was given.
 */
internal fun cloudRangeOpen(
    position: Long,
    requestedLength: Long,
    responseCode: Int,
    contentLength: Long
): CloudRangeOpen {
    val unset = C.LENGTH_UNSET.toLong()
    val lengthRequested = requestedLength != unset
    if (responseCode == 206) {
        return CloudRangeOpen(
            skipBytes = 0L,
            length = when {
                contentLength > 0 -> contentLength
                lengthRequested -> requestedLength
                else -> unset
            }
        )
    }
    var length = if (contentLength > 0) {
        (contentLength - position).coerceAtLeast(0)
    } else {
        unset
    }
    if (lengthRequested) {
        length = if (length == unset) requestedLength else minOf(length, requestedLength)
    }
    return CloudRangeOpen(skipBytes = position, length = length)
}
