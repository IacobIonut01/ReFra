@file:Suppress("PrivatePropertyName")

package com.dot.gallery.core

import android.content.Context
import android.graphics.ColorSpace
import androidx.compose.ui.util.fastForEachIndexed
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.search.helpers.SearchVisionHelper
import com.dot.gallery.feature_node.presentation.search.util.centerCrop
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.decode.BitmapColorSpace
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

@HiltWorker
class SearchIndexerUpdaterWorker @AssistedInject constructor(
    private val repository: MediaRepository,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val visionHelper by lazy { SearchVisionHelper(appContext) }

    override suspend fun doWork(): Result {
        printDebug("Starting indexing media items")
        val media = repository.getCompleteMedia().map { it.data ?: emptyList() }.firstOrNull()
        val records = repository.getImageEmbeddings().firstOrNull()
        val toBeIndexed = media?.filter { mediaItem ->
            records?.none { it.id == mediaItem.id } ?: true
        } ?: emptyList()
        if (toBeIndexed.isEmpty()) {
            printDebug("No media items to index")
            return Result.success()
        }
        printDebug("Found ${toBeIndexed.size} media items to index")
        setProgress(workDataOf("progress" to 0))
        visionHelper.setupVisionSession().use { session ->
            toBeIndexed.fastForEachIndexed { index, mediaItem ->
                val startMillis = System.currentTimeMillis()
                printWarning("SearchIndexerUpdaterWorker Processing item $index")
                setProgress(workDataOf("progress" to (index / (toBeIndexed.size - 1).toFloat()) * 100f))
                val request = ImageRequest(appContext, mediaItem.getUri().toString()) {
                    colorSpace(BitmapColorSpace(ColorSpace.Named.SRGB))
                    size(224, 224)
                }
                val result = appContext.sketch.execute(request)
                val bitmap = result.image?.asBitmapOrNull()
                if (bitmap != null) {
                    val rawBitmap = centerCrop(bitmap, 224)
                    val embedding = visionHelper.getImageEmbedding(session, rawBitmap)
                    printDebug("Processed media item $index in ${System.currentTimeMillis() - startMillis} ms")
                    repository.addImageEmbedding(
                        ImageEmbedding(
                            id = mediaItem.id,
                            date = mediaItem.timestamp,
                            embedding = embedding
                        )
                    )
                } else {
                    printDebug("Failed to decode bitmap for media: ${mediaItem.id} at ${mediaItem.getUri()}")
                }
            }
        }
        printDebug("Indexing completed for ${toBeIndexed.size} media items")
        setProgress(workDataOf("progress" to 100f))
        return Result.success()
    }

}

