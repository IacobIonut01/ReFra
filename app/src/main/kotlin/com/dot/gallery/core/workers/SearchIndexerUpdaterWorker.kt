package com.dot.gallery.core.workers

import android.content.Context
import android.graphics.ColorSpace
import androidx.compose.ui.util.fastForEachIndexed
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.BuildConfig
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.search.helpers.SearchVisionHelper

import com.dot.gallery.feature_node.presentation.util.printInfo
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.decode.BitmapColorSpace
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.currentCoroutineContext

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

@HiltWorker
class SearchIndexerUpdaterWorker @AssistedInject constructor(
    private val repository: MediaRepository,
    private val modelManager: ModelManager,
    private val cloudMediaDao: CloudMediaDao,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val visionHelper by lazy { SearchVisionHelper(modelManager) }

    override suspend fun doWork(): Result = withLogScope("worker.search-indexer") { runCatching {
        setProgress(workDataOf("progress" to -1f))
        if (!BuildConfig.ENABLE_INDEXING) return@withLogScope Result.success()
        if (!modelManager.isReady(ModelGroup.SEARCH)) {
            printInfo("ML models not installed, skipping indexing")
            return@withLogScope Result.success()
        }
        if (!currentCoroutineContext().isActive) return@withLogScope Result.success()
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
            return@withLogScope Result.success()
        }
        printInfo("Found $totalItems media items to index (local: $totalLocal, cloud: $totalCloud)")
        setProgress(workDataOf("progress" to 0f))
        visionHelper.setupVisionSession().use { session ->
            // Index local media
            toBeIndexed.fastForEachIndexed { index, mediaItem ->
                if (!currentCoroutineContext().isActive || isStopped) return@use
                val startMillis = System.currentTimeMillis()
                val pct = if (totalItems <= 1) 100f else ((index.toFloat() / (totalItems - 1).toFloat()) * 100f)
                setProgress(workDataOf("progress" to pct))
                val request = ImageRequest(appContext, mediaItem.getUri().toString()) {
                    colorSpace(BitmapColorSpace(ColorSpace.Named.SRGB))
                    size(224, 224)
                    setExtra("realMimeType", mediaItem.mimeType)
                }
                val result = appContext.sketch.execute(request)
                val bitmap = result.image?.asBitmapOrNull()
                if (bitmap != null) {
                    val embedding = visionHelper.getImageEmbedding(session, bitmap)
                    printInfo("Indexed local media $index/${totalItems - 1} in ${System.currentTimeMillis() - startMillis} ms")
                    repository.addImageEmbedding(
                        ImageEmbedding(
                            id = mediaItem.id,
                            date = mediaItem.timestamp,
                            embedding = embedding
                        )
                    )
                } else {
                    printInfo("Failed to decode bitmap for media: ${mediaItem.id} at ${mediaItem.getUri()}")
                }
                yield()
            }

            // Index cloud media
            cloudToBeIndexed.fastForEachIndexed { index, entity ->
                if (!currentCoroutineContext().isActive || isStopped) return@use
                val startMillis = System.currentTimeMillis()
                val globalIndex = totalLocal + index
                val pct = if (totalItems <= 1) 100f else ((globalIndex.toFloat() / (totalItems - 1).toFloat()) * 100f)
                setProgress(workDataOf("progress" to pct))
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
                    size(224, 224)
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
            setProgress(workDataOf("progress" to 100f))
        } else {
            printWarning("Indexing cancelled before completion")
        }
        return@withLogScope Result.success()
    }.getOrElse { exception ->
        printWarning("SearchIndexerUpdaterWorker failed with exception: ${exception.message}")
        return@withLogScope Result.failure()
    } }

}