package com.dot.gallery.core.workers

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

fun <T : Media> WorkManager.copyMedia(vararg sets: Pair<T, String>) {
    if (sets.isEmpty()) return
    sets.toList().chunked(32).forEachIndexed { index, chunk ->
        val uris = chunk.map { it.first.getUri().toString() }.toTypedArray()
        val paths = chunk.map { it.second }.toTypedArray()

        val request = OneTimeWorkRequestBuilder<MediaCopyWorker>()
            .addTag("MediaCopyWorker")
            .addTag("MediaCopyWorker_${index + 1}_${chunk.size}")
            .setInputData(
                workDataOf(
                    "uris" to uris,
                    "paths" to paths
                )
            )
            .build()

        enqueue(request)
    }
}


fun WorkManager.copyMedia(
    from: Media.UriMedia,
    path: String,
) = copyMedia(from to path)

@HiltWorker
class MediaCopyWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uris = params.inputData.getStringArray("uris") ?: return@withContext Result.failure()
        val paths = params.inputData.getStringArray("paths") ?: return@withContext Result.failure()
        if (uris.size != paths.size) return@withContext Result.failure()

        val total = uris.size
        val completed = AtomicInteger(0)
        val throttler = ProgressThrottler()
        // Track byte-level progress
        val bytesTotal = AtomicInteger(0)
        val bytesCopied = AtomicInteger(0)

        // First, compute approximate total bytes (best-effort) to enable smoother progress.
        uris.forEach { uriStr ->
            try {
                appContext.contentResolver.openAssetFileDescriptor(uriStr.toUri(), "r")?.use { afd ->
                    val length = afd.length
                    if (length > 0) bytesTotal.addAndGet(length.toInt().coerceAtMost(Int.MAX_VALUE))
                }
            } catch (_: Throwable) { /* ignore, fallback to per-file progress */ }
        }

        val copyJobs = uris.zip(paths).map { (uriStr, relPath) ->
            async {
                if (!currentCoroutineContext().isActive || isStopped) return@async false
                val uri = uriStr.toUri()
                val result = copyOne(uri, relPath) { delta ->
                    if (bytesTotal.get() > 0) {
                        val newTotal = bytesCopied.addAndGet(delta)
                        val pctBytes = ((newTotal.toFloat() / bytesTotal.get().toFloat()) * 100f).toInt().coerceIn(0, 100)
                        throttler.emit(pctBytes) { value -> setProgress(workDataOf("progress" to value)) }
                    }
                }
                val done = completed.incrementAndGet()
                if (bytesTotal.get() == 0) {
                    val pct = ((done.toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
                    throttler.emit(pct) { value -> setProgress(workDataOf("progress" to value)) }
                }
                result
            }
        }

        val results = copyJobs.map { it.await() }
        when {
            results.all { it } -> {
                if (isActive) {
                    setProgress(workDataOf("progress" to 100))
                }
                Result.success()
            }

            results.any { !it } -> Result.retry()
            else -> Result.failure()
        }
    }

    private suspend fun copyOne(src: android.net.Uri, relPath: String, onBytesCopied: suspend (Int) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            val cr: ContentResolver = appContext.contentResolver
            try {
                val mediaType = cr.getType(src) ?: return@withContext false
                val isVideo = mediaType.startsWith("video")
                val targetUri = cr.insert(
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, src.lastPathSegment)
                        put(MediaStore.MediaColumns.MIME_TYPE, mediaType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                ) ?: return@withContext false

                cr.openInputStream(src).use { input ->
                    cr.openOutputStream(targetUri).use { output ->
                        if (input != null && output != null) {
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                onBytesCopied(read)
                            }
                        }
                    }
                }

                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.MediaColumns.DATE_MODIFIED,
                            System.currentTimeMillis() / 1000
                        )
                    }
                }
                return@withContext cr.update(targetUri, updateValues, null, null) > 0
            } catch (e: IOException) {
                if (e.message?.contains("ENOSPC") == true) return@withContext false  // will retry
                return@withContext false
            }
        }
}

