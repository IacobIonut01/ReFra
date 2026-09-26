/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.CancellationSignal
import android.util.Size
import com.dot.gallery.core.decoder.glide.HeifUriProbe
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.presentation.util.printError
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.decode.BitmapColorSpace
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val decodeMutex = Mutex()

    suspend fun load(media: Media.UriMedia, size: Int): Bitmap? = decodeMutex.withLock {
        withContext(Dispatchers.IO) {
            val cloud = media.isCloud
            // The platform thumbnailer corrupts >10-bit HEIF/AVIF instead of failing, so those
            // files go through the Sketch decode path (HeifDecodeEngine → software) instead.
            val viaPlatformThumbnailer = !cloud && !runCatching {
                HeifUriProbe.needsSoftwareDecode(context.contentResolver, media.uri)
            }.getOrDefault(false)
            val source = if (viaPlatformThumbnailer) {
                suspendCancellableCoroutine { continuation ->
                    val cancellationSignal = CancellationSignal()
                    continuation.invokeOnCancellation { cancellationSignal.cancel() }
                    val bitmap = runCatching {
                        context.contentResolver.loadThumbnail(
                            media.uri,
                            Size(size, size),
                            cancellationSignal
                        )
                    }.onFailure {
                        printError("ml.smart", "smart thumbnail decode failed", it)
                    }.getOrNull()
                    if (continuation.isActive) {
                        continuation.resume(bitmap) { _, cancelledBitmap, _ -> cancelledBitmap?.recycle() }
                    } else {
                        bitmap?.recycle()
                    }
                }
            } else {
                runCatching {
                    val request = ImageRequest(context, media.uri.toString()) {
                        colorSpace(BitmapColorSpace(ColorSpace.Named.SRGB))
                        size(size, size)
                    }
                    context.sketch.execute(request).image?.asBitmapOrNull()
                }.onFailure {
                    printError("ml.smart", "smart thumbnail decode failed", it)
                }.getOrNull()
            } ?: return@withContext null
            if (source.width !in 1..MAX_DIMENSION || source.height !in 1..MAX_DIMENSION) {
                if (viaPlatformThumbnailer) source.recycle()
                return@withContext null
            }
            val owned = source.copy(Bitmap.Config.ARGB_8888, false)
            if (viaPlatformThumbnailer) source.recycle()
            owned
        }
    }

    private companion object {
        const val MAX_DIMENSION = 1024
    }
}
