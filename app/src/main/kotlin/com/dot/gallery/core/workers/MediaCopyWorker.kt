package com.dot.gallery.core.workers

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import com.dot.gallery.core.Settings
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.core.util.ext.selectedModifiedTimestamp
import com.dot.gallery.core.util.ext.copyToCancellable
import com.dot.gallery.core.util.ext.isVerifiedMediaCopy
import com.dot.gallery.core.util.ext.mediaDateModified
import com.dot.gallery.core.util.ext.mediaSize
import com.dot.gallery.core.util.ext.restoreMediaTimestamp
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.resolveMediaStoreVolume
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

fun <T : Media> WorkManager.copyMedia(vararg sets: Pair<T, String>) {
    if (sets.isEmpty()) return
    sets.toList().chunked(32).forEachIndexed { index, chunk ->
        val uris = chunk.map { it.first.getUri().toString() }.toTypedArray()
        val paths = chunk.map { it.second }.toTypedArray()
        val mimeTypes = chunk.map { it.first.mimeType }.toTypedArray()
        val labels = chunk.map { it.first.label }.toTypedArray()

        val request = OneTimeWorkRequestBuilder<MediaCopyWorker>()
            .addTag("MediaCopyWorker")
            .addTag("MediaCopyWorker_${index + 1}_${chunk.size}")
            .setInputData(
                workDataOf(
                    "uris" to uris,
                    "paths" to paths,
                    "mimeTypes" to mimeTypes,
                    "labels" to labels
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

    companion object {
        private const val MAX_CONCURRENT_COPIES = 4
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uris = params.inputData.getStringArray("uris") ?: return@withContext Result.failure()
        val paths = params.inputData.getStringArray("paths") ?: return@withContext Result.failure()
        val mimeTypes = params.inputData.getStringArray("mimeTypes")
        val labels = params.inputData.getStringArray("labels")
        if (uris.size != paths.size) return@withContext Result.failure()

        val total = uris.size
        val updateModifiedDate = Settings.Album.updateModifiedDate(appContext).firstOrNull() ?: false
        val completed = AtomicInteger(0)
        val throttler = ProgressThrottler()
        // Track byte-level progress
        val bytesTotal = AtomicLong(0)
        val bytesCopied = AtomicLong(0)

        // First, compute approximate total bytes (best-effort) to enable smoother progress.
        uris.forEach { uriStr ->
            val uri = uriStr.toUri()
            if (uri.scheme == "cloud") return@forEach // can't pre-measure cloud URIs
            try {
                appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val length = afd.length
                    if (length > 0) bytesTotal.addAndGet(length)
                }
            } catch (_: Throwable) { /* ignore, fallback to per-file progress */ }
        }

        val semaphore = Semaphore(MAX_CONCURRENT_COPIES)
        val copyJobs = uris.zip(paths).mapIndexed { idx, (uriStr, relPath) ->
            async {
                semaphore.withPermit {
                    if (!currentCoroutineContext().isActive || isStopped) return@withPermit false
                    val uri = uriStr.toUri()
                    val mime = mimeTypes?.getOrNull(idx)
                    val label = labels?.getOrNull(idx)
                    val result = copyOne(uri, relPath, mime, label, updateModifiedDate) { delta ->
                        if (bytesTotal.get() > 0L) {
                            val newTotal = bytesCopied.addAndGet(delta.toLong())
                            val pctBytes = ((newTotal.toFloat() / bytesTotal.get().toFloat()) * 100f).toInt().coerceIn(0, 100)
                            throttler.emit(pctBytes) { value -> setProgress(workDataOf("progress" to value)) }
                        }
                    }
                    val done = completed.incrementAndGet()
                    if (bytesTotal.get() == 0L) {
                        val pct = ((done.toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
                        throttler.emit(pct) { value -> setProgress(workDataOf("progress" to value)) }
                    }
                    result
                }
            }
        }

        val results = copyJobs.map { it.await() }
        // Notify the Files URI so ContentObservers re-query and copied
        // items appear, including copies to external volumes (SD cards).
        appContext.contentResolver.notifyChange(
            MediaStore.Files.getContentUri("external"), null
        )
        when {
            results.all { it } -> {
                if (isActive) {
                    setProgress(workDataOf("progress" to 100))
                }
                Result.success()
            }

            results.any { !it } -> Result.failure()
            else -> Result.failure()
        }
    }

    private suspend fun copyOne(
        src: Uri,
        destPath: String,
        mimeTypeHint: String? = null,
        labelHint: String? = null,
        updateModifiedDate: Boolean,
        onBytesCopied: suspend (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
            val cr: ContentResolver = appContext.contentResolver
            var targetUri: Uri? = null
            var committed = false
            try {
                val isCloudUri = src.scheme == "cloud"
                val (volumeName, relPath) = resolveMediaStoreVolume(destPath)
                val mediaType = if (isCloudUri) {
                    mimeTypeHint ?: "image/jpeg"
                } else {
                    cr.getType(src) ?: return@withContext false
                }
                val displayName = if (isCloudUri) {
                    labelHint ?: src.pathSegments.firstOrNull()?.take(12) ?: "cloud_media"
                } else {
                    src.lastPathSegment ?: "media"
                }
                val isVideo = mediaType.startsWith("video")
                val sourceDateModified = if (isCloudUri) 0L else cr.mediaDateModified(src)
                val sourceSize = if (isCloudUri) -1L else cr.mediaSize(src)
                val insertedUri = cr.insert(
                    if (isVideo) MediaStore.Video.Media.getContentUri(volumeName)
                    else MediaStore.Images.Media.getContentUri(volumeName),
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mediaType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                ) ?: return@withContext false
                targetUri = insertedUri

                val inputStream: InputStream = if (isCloudUri) {
                    openCloudInputStream(src)
                } else {
                    cr.openInputStream(src)
                } ?: throw IOException("Unable to open source media")
                val copiedBytes = inputStream.use { input ->
                    val output = cr.openOutputStream(insertedUri)
                        ?: throw IOException("Unable to open destination media")
                    output.use { input.copyToCancellable(it, onBytesCopied) }
                }
                val targetSize = cr.mediaSize(insertedUri)
                if (!isVerifiedMediaCopy(sourceSize, copiedBytes, targetSize)) {
                    throw IOException("Copied media size verification failed")
                }
                currentCoroutineContext().ensureActive()
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                if (cr.update(insertedUri, updateValues, null, null) <= 0) {
                    throw IOException("Unable to publish copied media")
                }
                selectedModifiedTimestamp(
                    updateModifiedDate = updateModifiedDate,
                    sourceDateModified = sourceDateModified,
                    currentTimeSeconds = System.currentTimeMillis() / 1000L
                )?.let { timestamp ->
                    // Apply the selected modified-date position after MediaStore publishes the copy.
                    appContext.restoreMediaTimestamp(insertedUri, mediaType, timestamp)
                }
                committed = true
                true
            } catch (e: IOException) {
                false
            } finally {
                if (!committed) {
                    targetUri?.let { uri -> runCatching { cr.delete(uri, null, null) } }
                }
            }
        }

    private fun openCloudInputStream(cloudUri: Uri): InputStream? {
        val registry = CloudFetcherRegistryHolder.registry ?: return null
        val providerName = cloudUri.authority ?: return null
        // remoteId may contain slashes (SMB/NFS/WebDAV paths like "Photos/IMG.jpg"), so
        // pathSegments.first() would truncate it to the first folder and request the directory
        // itself (STATUS_FILE_IS_A_DIRECTORY). Take the whole path instead.
        val remoteId = cloudUri.path?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return null
        val providerType = try { ProviderType.valueOf(providerName) } catch (_: Exception) { return null }
        val configId = cloudUri.getQueryParameter("cfg")?.toLongOrNull() ?: -1L
        val provider = ((if (configId > 0L) registry.getByConfigId(configId) else null)
            ?: registry.get(providerType)) as? RemoteMediaProvider ?: return null
        val url = provider.getOriginalUrl(remoteId)
        val authHeaders = provider.getAuthHeaders()
        val requestBuilder = Request.Builder().url(url).get()
        authHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val client = CloudFetcherRegistryHolder.okHttpClient ?: return null
        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        return response.body.byteStream()
    }
}

