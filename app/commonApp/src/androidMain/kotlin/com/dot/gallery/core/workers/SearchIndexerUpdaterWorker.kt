package com.dot.gallery.core.workers

import android.content.Context
import android.graphics.ColorSpace
import androidx.compose.ui.util.fastForEachIndexed
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.domain.model.ImageEmbedding
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.app.presentation.search.helpers.SearchVisionHelper
import com.dot.gallery.core.smart.VideoFrameSampler
import com.dot.gallery.core.logging.withLogScope
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.decode.BitmapColorSpace
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarning
import com.dot.gallery.BuildConfig
import me.tatarka.inject.annotations.Inject




/** Work-tag and progress key shared by the indexer and its observers. */
private const val INDEXER_WORK_NAME = "SearchIndexerUpdater"

class SearchIndexerUpdaterWorker @Inject constructor(
    private val repository: MediaRepository,
    private val modelManager: ModelManager,
    private val cloudMediaDao: CloudMediaDao,
    private val videoFrameSampler: VideoFrameSampler,
    private val appContext: Context,
) : GalleryWorker {

    private val visionHelper by lazy { SearchVisionHelper(modelManager) }

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withLogScope("worker.search-indexer") {
            runCatching {
                WorkSignals.reportProgress(INDEXER_WORK_NAME, workData("progress" to -1f))
                if (!BuildConfig.ENABLE_INDEXING) return@withLogScope WorkerResult.Success()
                val searchModel = modelManager.activeSearchModel
                if (!modelManager.isReady(searchModel)) {
                    printInfo("ML models not installed, skipping indexing")
                    return@withLogScope WorkerResult.Success()
                }
                val decodeSize = searchModel.indexDecodeSize
                if (!currentCoroutineContext().isActive) return@withLogScope WorkerResult.Success()
                printInfo("Starting indexing media items")
                val media = repository.getCompleteMedia().map { it.data ?: emptyList() }.firstOrNull()
                val records = repository.getImageEmbeddings().firstOrNull()
                val indexedIds = records?.mapTo(HashSet()) { it.id } ?: emptySet()
                val toBeIndexed = media?.filter { mediaItem ->
                    mediaItem.id !in indexedIds
                } ?: emptyList()

                // Collect cloud media that needs indexing
                val cloudEntities = cloudMediaDao.getAllCachedAsync()
                val cloudToBeIndexed = cloudEntities.filter { entity ->
                    entity.globalMediaId !in indexedIds
                }

                val totalLocal = toBeIndexed.size
                val totalCloud = cloudToBeIndexed.size
                val totalItems = totalLocal + totalCloud

                if (totalItems == 0) {
                    printInfo("No media items to index (local: 0, cloud: 0)")
                    return@withLogScope WorkerResult.Success()
                }
                printInfo("Found $totalItems media items to index (local: $totalLocal, cloud: $totalCloud)")
                WorkSignals.reportProgress(INDEXER_WORK_NAME, workData("progress" to 0f))
                visionHelper.setupVisionSession().use { session ->
                    // Index local media
                    toBeIndexed.fastForEachIndexed { index, mediaItem ->
                        if (!currentCoroutineContext().isActive || env.isCancelled()) return@use
                        val startMillis = System.currentTimeMillis()
                        val pct = if (totalItems <= 1) 100f else ((index.toFloat() / (totalItems - 1).toFloat()) * 100f)
                        WorkSignals.reportProgress(INDEXER_WORK_NAME, workData("progress" to pct))
                        val embedding = if (mediaItem.mimeType.startsWith("video/") && searchModel.supportsVideo) {
                            val frames = videoFrameSampler.sampleFrames(mediaItem.getUri())
                            if (frames.isNotEmpty()) {
                                try {
                                    visionHelper.getVideoEmbedding(session, frames)
                                } finally {
                                    frames.forEach { it.recycle() }
                                }
                            } else null
                        } else {
                            val request = ImageRequest(appContext, mediaItem.getUri().toString()) {
                                colorSpace(BitmapColorSpace(ColorSpace.Named.SRGB))
                                size(decodeSize, decodeSize)
                                setExtra("realMimeType", mediaItem.mimeType)
                            }
                            val result = appContext.sketch.execute(request)
                            // Sketch bitmaps come from the shared pool — do NOT recycle.
                            result.image?.asBitmapOrNull()?.let { bitmap ->
                                visionHelper.getImageEmbedding(session, bitmap)
                            }
                        }
                        if (embedding != null) {
                            printInfo("Indexed local media $index/${totalItems - 1} in ${System.currentTimeMillis() - startMillis} ms")
                            repository.addImageEmbedding(
                                ImageEmbedding(
                                    id = mediaItem.id,
                                    date = mediaItem.timestamp,
                                    embedding = embedding
                                )
                            )
                        } else {
                            printInfo("Failed to produce embedding for media: ${mediaItem.id} at ${mediaItem.getUri()}")
                        }
                        yield()
                    }

                    // Index cloud media
                    cloudToBeIndexed.fastForEachIndexed { index, entity ->
                        if (!currentCoroutineContext().isActive || env.isCancelled()) return@use
                        val startMillis = System.currentTimeMillis()
                        val globalIndex = totalLocal + index
                        val pct = if (totalItems <= 1) 100f else ((globalIndex.toFloat() / (totalItems - 1).toFloat()) * 100f)
                        WorkSignals.reportProgress(INDEXER_WORK_NAME, workData("progress" to pct))
                        // Skip items that have no server-side preview (e.g. videos on a
                        // server without preview generation). Resolving the URL up front lets
                        // us avoid a doomed Sketch request that would only 404 and spam logs.
                        val provider = CloudFetcherRegistryHolder.registry?.get(entity.providerType) as? RemoteMediaProvider
                        val previewUrl = provider?.getThumbnailUrl(
                            entity.remoteId,
                            ThumbnailSize.PREVIEW,
                            entity.fileId.ifBlank { null }
                        )
                        if (previewUrl != null && previewUrl.isBlank()) {
                            printInfo("Skipping cloud media without server preview: ${entity.remoteId} (${entity.providerType})")
                            yield()
                            return@fastForEachIndexed
                        }
                        val cloudMedia = entity.toUriMedia()
                        val request = ImageRequest(appContext, cloudMedia.getUri().toString()) {
                            colorSpace(BitmapColorSpace(ColorSpace.Named.SRGB))
                            size(decodeSize, decodeSize)
                        }
                        val result = appContext.sketch.execute(request)
                        val bitmap = result.image?.asBitmapOrNull()
                        if (bitmap != null) {
                            val embedding = visionHelper.getImageEmbedding(session, bitmap)
                            printInfo("Indexed cloud media $globalIndex/${totalItems - 1} in ${System.currentTimeMillis() - startMillis} ms")
                            repository.addImageEmbedding(
                                ImageEmbedding(
                                    id = cloudMedia.id,
                                    date = cloudMedia.timestamp,
                                    embedding = embedding
                                )
                            )
                        } else {
                            printInfo("Failed to decode bitmap for cloud media: ${entity.remoteId} (${entity.providerType})")
                        }
                        yield()
                    }
                }
                if (currentCoroutineContext().isActive) {
                    printInfo("Indexing completed for $totalItems media items (local: $totalLocal, cloud: $totalCloud)")
                    WorkSignals.reportProgress(INDEXER_WORK_NAME, workData("progress" to 100f))
                } else {
                    printWarning("Indexing cancelled before completion")
                }
                return@withLogScope WorkerResult.Success()
            }.getOrElse { exception ->
                printWarning("SearchIndexerUpdaterWorker failed with exception: ${exception.message}")
                return@withLogScope WorkerResult.Failure("indexing failed: ${exception.message}")
            }
        }

    companion object {
        const val WORKER_NAME = "SearchIndexerUpdaterWorker"
    }
}

@Worker(name = SearchIndexerUpdaterWorker.WORKER_NAME)
class SearchIndexerUpdaterWorkerAdapter :
    GalleryWorkerAdapter(SearchIndexerUpdaterWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.searchIndexerUpdaterWorker.doWork(input, env)
}
