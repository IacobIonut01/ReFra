package com.dot.gallery.core.workers

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import com.dot.gallery.cloud.util.CloudMediaDownloader
import com.dot.gallery.core.Settings
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.core.util.ext.selectedModifiedTimestamp
import com.dot.gallery.core.util.ext.copyToCancellable
import com.dot.gallery.core.util.ext.isVerifiedMediaCopy
import com.dot.gallery.core.util.ext.mediaDateModified
import com.dot.gallery.core.util.ext.mediaSize
import com.dot.gallery.core.util.ext.restoreMediaTimestamp
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.core.logging.withLogScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Album
import com.dot.gallery.core.presentation.util.update
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.uri.scheme
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.core.logging.printError
import com.dot.gallery.domain.util.resolveMediaStoreVolume
import me.tatarka.inject.annotations.Inject





suspend fun <T : Media> BackgroundTaskScheduler.copyMedia(vararg sets: Pair<T, String>): List<String> {
    if (sets.isEmpty()) return emptyList()
    return sets.toList().chunked(32).mapIndexed { index, chunk ->
        val uris = chunk.map { it.first.getUri().toString() }.toTypedArray()
        val paths = chunk.map { it.second }.toTypedArray()
        val mimeTypes = chunk.map { it.first.mimeType }.toTypedArray()
        val labels = chunk.map { it.first.label }.toTypedArray()
        val id = "MediaCopyWorker_${index + 1}_${chunk.size}_${java.util.UUID.randomUUID()}"

        enqueue(
            id = id,
            trigger = TaskTrigger.OneTime(),
            workerClassName = MediaCopyWorker.WORKER_NAME,
            inputJson = workDataJson(
                "uris" to uris,
                "paths" to paths,
                "mimeTypes" to mimeTypes,
                "labels" to labels
            ),
            tags = setOf("MediaCopyWorker", "MediaCopyWorker_${index + 1}_${chunk.size}"),
        )
        id
    }
}


suspend fun BackgroundTaskScheduler.copyMedia(
    from: UriMedia,
    path: String,
) = copyMedia(from to path)

class MediaCopyWorker @Inject constructor(
    private val appContext: Context,
) : GalleryWorker {

    companion object {
        const val KEY_TARGET_URI = "target_uri"
        const val WORKER_NAME = "MediaCopyWorker"
        private const val MAX_CONCURRENT_COPIES = 4
        const val PROGRESS_WORK_NAME = "MediaCopyWorker"
    }

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult = withLogScope("worker.media-copy") { withContext(Dispatchers.IO) {
        val uris = input.getStringArray("uris") ?: return@withContext WorkerResult.Failure("missing uris")
        val paths = input.getStringArray("paths") ?: return@withContext WorkerResult.Failure("missing paths")
        val mimeTypes = input.getStringArray("mimeTypes")
        val labels = input.getStringArray("labels")
        if (uris.size != paths.size) return@withContext WorkerResult.Failure("uris/paths length mismatch")

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
                    if (!currentCoroutineContext().isActive || env.isCancelled()) return@withPermit null
                    val uri = uriStr.toUri()
                    val mime = mimeTypes?.getOrNull(idx)
                    val label = labels?.getOrNull(idx)
                    val result = copyOne(uri, relPath, mime, label, updateModifiedDate) { delta ->
                        if (bytesTotal.get() > 0L) {
                            val newTotal = bytesCopied.addAndGet(delta.toLong())
                            val pctBytes = ((newTotal.toFloat() / bytesTotal.get().toFloat()) * 100f).toInt().coerceIn(0, 100)
                            throttler.emit(pctBytes) { value -> WorkSignals.reportProgress(PROGRESS_WORK_NAME, workData("progress" to value)) }
                        }
                    }
                    val done = completed.incrementAndGet()
                    if (bytesTotal.get() == 0L) {
                        val pct = ((done.toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
                        throttler.emit(pct) { value -> WorkSignals.reportProgress(PROGRESS_WORK_NAME, workData("progress" to value)) }
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
            results.all { it != null } -> {
                if (isActive) {
                    WorkSignals.reportProgress(PROGRESS_WORK_NAME, workData("progress" to 100))
                }
                val output = if (results.size == 1) {
                    workData(KEY_TARGET_URI to results.single().toString())
                } else {
                    null
                }
                WorkerResult.Success(data = output)
            }

            results.any { it == null } -> WorkerResult.Failure("one or more copies failed")
            else -> WorkerResult.Failure("copy failed")
        }
    } }

    private suspend fun copyOne(
        src: Uri,
        destPath: String,
        mimeTypeHint: String? = null,
        labelHint: String? = null,
        updateModifiedDate: Boolean,
        onBytesCopied: suspend (Int) -> Unit = {}
    ): Uri? = withContext(Dispatchers.IO) {
            val cr: ContentResolver = appContext.contentResolver
            var targetUri: Uri? = null
            var committed = false
            try {
                val isCloudUri = src.scheme == "cloud"
                val (volumeName, relPath) = resolveMediaStoreVolume(destPath)
                val mediaType = if (isCloudUri) {
                    mimeTypeHint ?: "image/jpeg"
                } else {
                    cr.getType(src) ?: return@withContext null
                }
                val displayName = copyDisplayName(
                    labelHint = labelHint,
                    uriSegmentFallback = if (isCloudUri) {
                        src.pathSegments.firstOrNull()?.take(12)
                    } else {
                        src.lastPathSegment
                    }
                )
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
                ) ?: return@withContext null
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
                insertedUri
            } catch (e: IOException) {
                printError(
                    tag = "MediaCopyWorker",
                    message = "Media copy failed",
                    throwable = e,
                    ctx = mapOf(
                        "src_scheme" to (src.scheme ?: "unknown"),
                        "dest_dir" to destPath.substringBeforeLast('/', ""),
                        "mime" to (mimeTypeHint ?: "auto"),
                    ),
                )
                null
            } finally {
                if (!committed) {
                    targetUri?.let { uri -> runCatching { cr.delete(uri, null, null) } }
                }
            }
        }

    private fun openCloudInputStream(cloudUri: Uri): InputStream? =
        CloudMediaDownloader.downloadCloudMediaExact(cloudUri)
}


/**
 * Display name for a copied media row. `content://` URIs expose only the MediaStore row
 * id via [Uri.getLastPathSegment] — using it named every copy "123456" (#1358). The
 * media's real [Media.label] travels in the work input, so prefer it; fall back to the
 * URI segment only for cloud URIs that arrive without a label.
 */
internal fun copyDisplayName(labelHint: String?, uriSegmentFallback: String?): String =
    labelHint ?: uriSegmentFallback ?: "media"

@Worker(name = MediaCopyWorker.WORKER_NAME)
class MediaCopyWorkerAdapter : GalleryWorkerAdapter(MediaCopyWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.mediaCopyWorker.doWork(input, env)
}
