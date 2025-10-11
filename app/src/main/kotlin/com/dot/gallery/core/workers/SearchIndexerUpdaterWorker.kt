package com.dot.gallery.core.workers

import android.content.Context
import android.graphics.ColorSpace
import androidx.compose.ui.util.fastForEachIndexed
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.search.helpers.SearchVisionHelper
import com.dot.gallery.feature_node.presentation.search.util.centerCrop
import com.dot.gallery.feature_node.presentation.util.printInfo
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.decode.BitmapColorSpace
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

@HiltWorker
class SearchIndexerUpdaterWorker @AssistedInject constructor(
    private val repository: MediaRepository,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val visionHelper by lazy { SearchVisionHelper(appContext) }

    override suspend fun doWork(): Result = runCatching {
        if (!BuildConfig.ENABLE_INDEXING) return Result.success()
        delay(5000)
        if (!currentCoroutineContext().isActive) return Result.success()
        printInfo("Starting indexing media items")
        val media = repository.getCompleteMedia().map { it.data ?: emptyList() }.firstOrNull()
        val records = repository.getImageEmbeddings().firstOrNull()
        val toBeIndexed = media?.filter { mediaItem ->
            records?.none { it.id == mediaItem.id } ?: true
        } ?: emptyList()
        if (toBeIndexed.isEmpty()) {
            printInfo("No media items to index")
            return Result.success()
        }
        printInfo("Found ${toBeIndexed.size} media items to index")
        setProgress(workDataOf("progress" to 0))
        visionHelper.setupVisionSession().use { session ->
            val throttler = ProgressThrottler()
            val total = toBeIndexed.size
            toBeIndexed.fastForEachIndexed { index, mediaItem ->
                if (!currentCoroutineContext().isActive || isStopped) return@use
                val startMillis = System.currentTimeMillis()
                // Progress calculation guarded for single-item case
                val pct: Int =
                    if (total <= 1) 100 else ((index.toFloat() / (total - 1).toFloat()) * 100f).toInt()
                throttler.emit(pct) { setProgress(workDataOf("progress" to it)) }
                val request = ImageRequest(appContext, mediaItem.getUri().toString()) {
                    colorSpace(BitmapColorSpace(ColorSpace.Named.SRGB))
                    size(224, 224)
                }
                val result = appContext.sketch.execute(request)
                val bitmap = result.image?.asBitmapOrNull()
                if (bitmap != null) {
                    val rawBitmap = centerCrop(bitmap, 224)
                    val embedding = visionHelper.getImageEmbedding(session, rawBitmap)
                    printInfo("Indexed media item $index/${total - 1} in ${System.currentTimeMillis() - startMillis} ms")
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
                // Cooperative yield to avoid starving other coroutines
                yield()
            }
        }
        if (currentCoroutineContext().isActive) {
            printInfo("Indexing completed for ${toBeIndexed.size} media items")
            setProgress(workDataOf("progress" to 100))
        } else {
            printWarning("Indexing cancelled before completion")
        }
        return Result.success()
    }.getOrElse { exception ->
        printWarning("SearchIndexerUpdaterWorker failed with exception: ${exception.message}")
        return Result.failure()
    }

}