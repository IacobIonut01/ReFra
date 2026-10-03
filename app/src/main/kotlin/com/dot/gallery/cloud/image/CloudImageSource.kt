/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image

import android.content.Context
import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.resolveRemote
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.github.panpf.zoomimage.subsampling.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException

internal enum class CloudSubsamplingMode {
    NONE,
    PLATFORM,
    JXL,
    CUSTOM,
    HEIF
}

internal fun resolveCloudSubsamplingMode(
    isJxl: Boolean,
    hasCustomRegionDecoder: Boolean,
    isHeif: Boolean,
    isSpecialFormat: Boolean,
    isAnimated: Boolean,
    isAnimatedRaster: Boolean
): CloudSubsamplingMode = when {
    isJxl -> CloudSubsamplingMode.JXL
    hasCustomRegionDecoder -> CloudSubsamplingMode.CUSTOM
    isHeif && !isAnimated -> CloudSubsamplingMode.HEIF
    isSpecialFormat -> CloudSubsamplingMode.NONE
    !isAnimated && !isAnimatedRaster -> CloudSubsamplingMode.PLATFORM
    else -> CloudSubsamplingMode.NONE
}

internal fun shouldLoadCloudOriginal(
    isSelected: Boolean,
    subsamplingMode: CloudSubsamplingMode
): Boolean = isSelected && subsamplingMode != CloudSubsamplingMode.NONE

private val cloudOriginalLocks = Array(64) { Mutex() }

internal suspend fun storeCloudOriginal(target: File, write: suspend (File) -> Unit) {
    val lockIndex = (target.absolutePath.hashCode() and Int.MAX_VALUE) % cloudOriginalLocks.size
    cloudOriginalLocks[lockIndex].withLock {
        if (target.isFile && target.length() > 0L) return
        val temporaryFile = File.createTempFile("${target.name}.", ".tmp", target.parentFile)
        try {
            write(temporaryFile)
            check(temporaryFile.length() > 0L) { "Downloaded original is empty" }
            if (!temporaryFile.renameTo(target)) {
                try {
                    temporaryFile.copyTo(target, overwrite = true)
                } catch (e: Exception) {
                    target.delete()
                    throw e
                }
            }
        } finally {
            temporaryFile.delete()
        }
    }
}

/**
 * [ImageSource] for cloud media that serves the **original** image to ZoomImage's subsampling
 * pipeline from a locally-cached file.
 *
 * Why file-backed instead of streaming from the network: ZoomImage (1.6.0-alpha01) sniffs the
 * image header by calling `RegionDecoder.Factory.accept()` -> `openSource()` on the MAIN thread
 * (only `getImageInfo()`/`prepare()`/region decoding run on the IO dispatcher). A network read
 * there throws `NetworkOnMainThreadException`, and doing the download synchronously would block
 * the UI. So the original is downloaded once, off the main thread, via [create]; every
 * [openSource] call is then a cheap local file read that is safe on any thread and gives
 * `BitmapRegionDecoder` the seekable source it needs for tiling.
 */
class CloudImageSource private constructor(
    private val providerType: ProviderType,
    private val remoteId: String,
    private val configId: Long,
    private val localFile: File,
) : ImageSource {

    override val key: String = keyOf(providerType, remoteId, configId)

    override fun openSource(): Source = localFile.source()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CloudImageSource
        return providerType == other.providerType && remoteId == other.remoteId &&
            configId == other.configId
    }

    override fun hashCode(): Int {
        var result = providerType.hashCode()
        result = 31 * result + remoteId.hashCode()
        result = 31 * result + configId.hashCode()
        return result
    }

    override fun toString(): String = "CloudImageSource(cloud://${providerType.name}/$remoteId)"

    companion object {
        private fun keyOf(providerType: ProviderType, remoteId: String, configId: Long): String =
            "cloud://${providerType.name}/$remoteId?size=original" +
                (if (configId > 0L) "&cfg=$configId" else "")

        /**
         * Ensure the original is cached locally (downloading it off the main thread if needed) and
         * return a file-backed source. Call from a coroutine before handing the source to ZoomImage.
         */
        suspend fun create(
            context: Context,
            providerType: ProviderType,
            remoteId: String,
            configId: Long = -1L,
        ): CloudImageSource = withContext(Dispatchers.IO) {
            val file = cacheFileFor(context, providerType, remoteId, configId)
            downloadOriginal(providerType, remoteId, configId, file)
            CloudImageSource(providerType, remoteId, configId, file)
        }

        private fun cacheFileFor(
            context: Context,
            providerType: ProviderType,
            remoteId: String,
            configId: Long,
        ): File {
            val dir = File(context.cacheDir, "cloud_zoom_originals").apply { mkdirs() }
            val ext = remoteId.substringAfterLast('/').substringAfterLast('.', "")
                .takeIf { it.length in 1..5 && it.all { char -> char.isLetterOrDigit() } } ?: "img"
            val key = CloudMediaCache.keyFor(providerType, configId, remoteId, "original")
            return File(dir, "$key.$ext")
        }

        private suspend fun downloadOriginal(
            providerType: ProviderType,
            remoteId: String,
            configId: Long,
            target: File,
        ) {
            val registry = CloudFetcherRegistryHolder.registry
                ?: throw IllegalStateException("ProviderRegistry not available")
            val provider = registry.resolveRemote(providerType, configId)
                ?: throw IllegalStateException("No remote provider for $providerType")

            val url = provider.getOriginalUrl(remoteId)
            if (url.isBlank()) throw IllegalStateException("No original URL for $remoteId")

            val requestBuilder = Request.Builder().url(url).get()
            provider.getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            requestBuilder.addHeader(
                CloudMediaCache.HEADER_KEY,
                CloudMediaCache.keyFor(providerType, configId, remoteId, "original")
            )

            val client = CloudFetcherRegistryHolder.okHttpClient
                ?: throw IllegalStateException("Cloud OkHttpClient not initialized")
            // Stream to a temp file then rename, so a partial/failed download never leaves a
            // truncated file that a later open would treat as complete.
            storeCloudOriginal(target) { temporaryFile ->
                CloudTrace.d("ZoomSource[$providerType] original '$remoteId' -> GET $url")
                val start = System.nanoTime()
                try {
                    downloadToFile(client.newCall(requestBuilder.build()), temporaryFile)
                    CloudTrace.d(
                        "ZoomSource[$providerType] original '$remoteId' download took " +
                            "${(System.nanoTime() - start) / 1_000_000}ms"
                    )
                } catch (e: Exception) {
                    CloudTrace.w(
                        "ZoomSource[$providerType] original '$remoteId' download failed after " +
                            "${(System.nanoTime() - start) / 1_000_000}ms",
                        e
                    )
                    throw e
                }
            }
            // no shared imaga reader path; since this is could be the first time
            // an image is downloaded, we need to record the capture time here too.
            CloudFetcherRegistryHolder.repository?.recordCaptureTime(
                providerType, configId, remoteId, url, target
            )
        }

        private suspend fun downloadToFile(call: Call, target: File) {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        target.delete()
                        if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            try {
                                if (!it.isSuccessful) {
                                    throw IOException("HTTP ${it.code}: ${it.message}")
                                }
                                it.body.source().use { source ->
                                    target.sink().buffer().use { sink -> sink.writeAll(source) }
                                }
                                if (continuation.isActive) {
                                    continuation.resumeWith(Result.success(Unit))
                                } else {
                                    target.delete()
                                }
                            } catch (e: Exception) {
                                target.delete()
                                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                            }
                        }
                    }
                })
            }
        }
    }
}
