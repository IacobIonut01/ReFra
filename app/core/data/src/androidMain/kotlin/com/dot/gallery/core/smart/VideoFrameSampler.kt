/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.dot.gallery.core.logging.printWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton

/**
 * Extracts uniformly-spaced frames from a video for EmbeddingGemma 2 video embeds.
 * Mirrors the transformers.js video sampling contract (`fps = 1`, `max_frames`,
 * `overflow_strategy = "uniform"`): for clips shorter than [MAX_FRAMES] seconds one frame
 * per second is taken, longer clips are subsampled uniformly across the duration.
 */
@Singleton
class VideoFrameSampler @Inject constructor(
    private val context: Context
) {
    private val decodeMutex = Mutex()

    /**
     * Sample up to [MAX_FRAMES] frames from [uri]'s video track, scaled to fit
     * [MAX_FRAME_DIMENSION]. Returns an empty list when the video can't be decoded
     * (e.g. an unreachable cloud stream) — callers fall back to a thumbnail embed.
     */
    suspend fun sampleFrames(uri: Uri, maxFrames: Int = MAX_FRAMES): List<Bitmap> =
        decodeMutex.withLock {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val durationMs = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: return@withContext emptyList()
                    if (durationMs <= 0) return@withContext emptyList()
                    val rotation = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull() ?: 0
                    val wanted = minOf(maxFrames, maxOf(1, (durationMs / 1000L).toInt()))
                    val stepUs = durationMs * 1000L / wanted
                    (0 until wanted).mapNotNull { i ->
                        val timeUs = i * stepUs + stepUs / 2
                        runCatching {
                            retriever.getFrameAtTime(
                                timeUs,
                                MediaMetadataRetriever.OPTION_CLOSEST
                            )?.let { frame ->
                                val rotated = if (rotation != 0) rotate(frame, rotation) else frame
                                scaleDown(rotated)
                            }
                        }.onFailure {
                            printWarn(
                                "ml.smart",
                                "video frame extract failed",
                                mapOf("timeUs" to timeUs.toString(), "error" to it.message.orEmpty())
                            )
                        }.getOrNull()
                    }
                } catch (e: Exception) {
                    printWarn(
                        "ml.smart",
                        "video frame sampler failed",
                        mapOf("uri" to uri.toString(), "error" to e.message.orEmpty())
                    )
                    emptyList()
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_FRAME_DIMENSION) return bitmap
        val scale = MAX_FRAME_DIMENSION.toFloat() / maxSide
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private companion object {
        /** 8 uniform frames keeps an on-device video embed inside a few seconds. */
        const val MAX_FRAMES = 8
        const val MAX_FRAME_DIMENSION = 1024
    }
}
