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
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.IOException

fun <T: Media> WorkManager.copyMedia(vararg sets: Pair<T, String>) {
    if (sets.isEmpty()) return
    sets.toList().chunked(32).forEachIndexed { index, chunk ->
        val uris = chunk.map { it.first.getUri().toString() }.toTypedArray()
        val paths = chunk.map { it.second }.toTypedArray()

        val request = OneTimeWorkRequestBuilder<MediaCopyWorker>()
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

        val copyJobs = uris.zip(paths).mapIndexed { index, (uriStr, relPath) ->
            async {
                val uri = uriStr.toUri()
                copyOne(uri, relPath).also { success ->
                    setProgress(
                        workDataOf("progress" to (index + 1).toFloat() / uris.size)
                    )
                }
            }
        }

        val results = copyJobs.map { it.await() }
        when {
            results.all { it } -> Result.success()
            results.any { !it } -> Result.retry()
            else -> Result.failure()
        }
    }

    private suspend fun copyOne(src: android.net.Uri, relPath: String): Boolean = withContext(Dispatchers.IO) {
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
                    input?.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                }
            }

            val updateValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                }
            }
            return@withContext cr.update(targetUri, updateValues, null, null) > 0
        } catch (e: IOException) {
            if (e.message?.contains("ENOSPC") == true) return@withContext false  // will retry
            return@withContext false
        }
    }
}

