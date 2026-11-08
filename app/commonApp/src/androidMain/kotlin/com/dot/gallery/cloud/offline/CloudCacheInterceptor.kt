/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.offline

import com.dot.gallery.cloud.core.CloudTrace
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.File


/**
 * OkHttp interceptor that backs the cloud image pipeline with [CloudMediaCache].
 *
 * Only requests carrying [CloudMediaCache.HEADER_KEY] are handled (image thumbnails/previews/
 * originals). Everything else — including ranged video requests — passes straight through.
 *
 * Behaviour per keyed request:
 * - **cache hit**: served from disk immediately (entries are content-addressed and immutable),
 *   saving bandwidth even while online.
 * - **offline + miss**: returns `504` so the decoder fails fast instead of hanging on the network.
 * - **online + miss**: fetched from network and (when policy allows) teed to the auto cache,
 *   then trimmed to the user's size budget.
 */
class CloudCacheInterceptor(
    private val cache: CloudMediaCache,
    private val manager: OfflineModeManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = original.header(CloudMediaCache.HEADER_KEY)
            ?: return chain.proceed(original) // not a cloud media request

        // Never cache/serve ranged requests (e.g. video seeks) — strip the marker and pass through.
        if (original.header("Range") != null) {
            return chain.proceed(original.newBuilder().removeHeader(CloudMediaCache.HEADER_KEY).build())
        }

        val request = original.newBuilder().removeHeader(CloudMediaCache.HEADER_KEY).build()

        // Serve from disk on a hit (immutable content → safe online too).
        cache.get(key)?.let { file ->
            CloudTrace.d("CloudCache HIT ${CloudTrace.bytes(file.length())} key=$key")
            return fileResponse(request, file, cache.contentTypeFor(key))
        }

        if (manager.effectiveOfflineNow) {
            CloudTrace.d("CloudCache OFFLINE MISS key=$key")
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(504)
                .message("Offline: not cached")
                .body(ByteArray(0).toResponseBody(null))
                .build()
        }

        val response = chain.proceed(request)
        if (!response.isSuccessful || !manager.shouldWriteThrough()) return response
        val body = response.body ?: return response

        // Write-through: tee the streamed body into a temp file, promoted to the auto cache on EOF.
        val tmp = cache.newAutoTemp(key)
        val contentType = body.contentType()?.toString()
        val tee = TeeSource(body.source(), tmp, key, contentType)
        val newBody = tee.buffer().let { buffered ->
            object : ResponseBody() {
                override fun contentType(): MediaType? = body.contentType()
                override fun contentLength(): Long = body.contentLength()
                override fun source(): BufferedSource = buffered
            }
        }
        return response.newBuilder().body(newBody).build()
    }

    private fun fileResponse(request: okhttp3.Request, file: File, contentType: String?): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK (cache)")
            .header("Content-Length", file.length().toString())
            .apply { if (contentType != null) header("Content-Type", contentType) }
            .body(FileResponseBody(file, contentType))
            .build()

    /** Copies every byte read by the consumer into [tmp]; promotes to cache on clean EOF. */
    private inner class TeeSource(
        source: Source,
        private val tmp: File,
        private val key: String,
        private val contentType: String?
    ) : ForwardingSource(source) {
        private val sink = runCatching { tmp.sink().buffer() }.getOrNull()
        private var done = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            val n = super.read(sink, byteCount)
            val out = this.sink
            if (out != null) {
                if (n > 0) {
                    runCatching {
                        // Copy just-read bytes without consuming them from the consumer's buffer.
                        sink.copyTo(out.buffer, sink.size - n, n)
                    }.onFailure { abort() }
                } else if (n < 0) {
                    finish()
                }
            }
            return n
        }

        private fun finish() {
            if (done) return
            done = true
            runCatching {
                sink?.flush()
                sink?.close()
                cache.promoteAuto(tmp, key)
                cache.writeAutoType(key, contentType)
                cache.trimAuto(manager.budgetBytesNow)
            }.onFailure { abort() }
        }

        private fun abort() {
            done = true
            runCatching { sink?.close() }
            tmp.delete()
        }

        override fun close() {
            if (!done) abort()
            super.close()
        }
    }

    /** Streams a cache file back as a response body without loading it fully into memory. */
    private class FileResponseBody(
        private val file: File,
        private val contentType: String?
    ) : ResponseBody() {
        override fun contentType(): MediaType? =
            (contentType ?: "application/octet-stream").toMediaTypeOrNull()
        override fun contentLength(): Long = file.length()
        override fun source(): BufferedSource = file.source().buffer()
    }
}
