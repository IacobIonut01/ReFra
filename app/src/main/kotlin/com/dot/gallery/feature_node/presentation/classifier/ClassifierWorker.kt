package com.dot.gallery.feature_node.presentation.classifier

import android.content.Context
import android.graphics.ColorSpace
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaVersion
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.mediaStoreVersion
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.github.panpf.sketch.BitmapImage
import com.github.panpf.sketch.SingletonSketch
import com.github.panpf.sketch.decode.BitmapColorSpace
import com.github.panpf.sketch.request.ImageRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltWorker
class ClassifierWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    private val repository: MediaRepository,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), ImageClassifierHelper.ClassifierListener {

    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            printWarning("ClassifierWorker retrieving media")
            val blacklisted = database.getBlacklistDao().getBlacklistedAlbumsAsync()
            var media = database.getMediaDao().getMedia()
                .filterNot { item -> blacklisted.any { it.matchesMedia(item) } }
            if (media.isEmpty()) {
                printWarning("ClassifierWorker media is empty, let's try and update the database")
                val mediaVersion = appContext.mediaStoreVersion
                printWarning("ClassifierWorker Force-updating database to version $mediaVersion")
                database.getMediaDao().setMediaVersion(MediaVersion(mediaVersion))
                val fetchedMedia =
                    repository.getMedia().map { it.data ?: emptyList() }.firstOrNull()
                fetchedMedia?.let {
                    database.getMediaDao().updateMedia(it)
                    database.getClassifierDao().deleteDeclassifiedImages(it.fastMap { m -> m.id })
                }
            }
            media = database.getMediaDao().getMedia()
                .filterNot { item -> blacklisted.any { it.matchesMedia(item) } }
            printWarning("ClassifierWorker allowed media size: ${media.size}")

            printWarning("ClassifierWorker cleaning up declassified results")
            database.getClassifierDao().deleteDeclassifiedImages(media.fastMap { it.id })

            printWarning("ClassifierWorker retrieving already checked media")
            val classified = database.getClassifierDao().getCheckedMedia()
            printWarning("ClassifierWorker classified media size: ${classified.size}")
            media =
                media.filterNot { item -> classified.any { it.id == item.id && it.timestamp == item.timestamp } }
            if (media.isEmpty()) {
                printWarning("ClassifierWorker media is empty, we can abort")
                setProgress(workDataOf("progress" to 100))
                return@withContext Result.success()
            }
            printWarning("ClassifierWorker unclassified media size: ${media.size}")
            setProgress(workDataOf("progress" to 0))

            printWarning("ClassifierWorker Setting up image classifier")
            val helper = ImageClassifierHelper(
                context = appContext,
                imageClassifierListener = this@ClassifierWorker
            )
            printWarning("ClassifierWorker Setting up sketch")
            val sketch = SingletonSketch.get(appContext)
            printWarning("ClassifierWorker Starting image classification for ${media.size} items")
            media.fastForEachIndexed { index, item ->
                printWarning("ClassifierWorker Processing item $index")
                setProgress(workDataOf("progress" to (index / (media.size - 1).toFloat()) * 100f))
                val request = ImageRequest(appContext, item.uri.toString()) {
                    colorSpace(BitmapColorSpace(ColorSpace.Named.SRGB))
                }
                val result = sketch.execute(request)
                val bitmap = (result.image as? BitmapImage)?.bitmap
                if (bitmap != null) {
                    printWarning("ClassifierWorker Obtained bitmap...processing")
                } else {
                    printWarning("ClassifierWorker Bitmap is null, skipping")
                    return@fastForEachIndexed
                }
                try {
                    helper.classify(bitmap) { results, time ->
                        printWarning("ClassifierWorker Classification [in $time ms] results: $results")
                        if (results.isEmpty()) {
                            printWarning("ClassifierWorker No results obtained, inserting as null")
                        }
                        launch(Dispatchers.IO) {
                            printWarning("ClassifierWorker Inserting media into database")
                            database.getClassifierDao().insertClassifiedMedia(
                                Media.ClassifiedMedia(
                                    category = if (results.isNotEmpty()) results.first().displayName else null,
                                    score = if (results.isNotEmpty()) results.first().score else 0f,
                                    id = item.id,
                                    label = item.label,
                                    uri = item.uri,
                                    path = item.path,
                                    relativePath = item.relativePath,
                                    albumID = item.albumID,
                                    albumLabel = item.albumLabel,
                                    timestamp = item.timestamp,
                                    expiryTimestamp = item.expiryTimestamp,
                                    takenTimestamp = item.takenTimestamp,
                                    fullDate = item.fullDate,
                                    mimeType = item.mimeType,
                                    favorite = item.favorite,
                                    trashed = item.trashed,
                                    size = item.size,
                                    duration = item.duration
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    printWarning("ClassifierWorker Error at item $index")
                    e.printStackTrace()
                    return@fastForEachIndexed
                }
            }
            helper.clearImageClassifier()
            setProgress(workDataOf("progress" to 100f))
        }

        return Result.success()
    }

    override fun onError(error: String) {
        printWarning("ClassifierWorker ImageClassifierHelper Error: $error")
    }

}

fun WorkManager.startClassification(indexStart: Int = 0, size: Int = 50) {
    val inputData = Data.Builder()
        .putInt("chunkIndexStart", indexStart)
        .putInt("chunkSize", size)
        .build()
    val uniqueWork = OneTimeWorkRequestBuilder<ClassifierWorker>()
        .addTag("ImageClassifier")
        .setConstraints(
            Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()
        )
        .setInputData(inputData)
        .build()

    enqueueUniqueWork(
        "ClassifierWorker_${indexStart}_$size",
        ExistingWorkPolicy.REPLACE,
        uniqueWork
    )
}

fun WorkManager.stopClassification() {
    cancelAllWorkByTag("ImageClassifier")
}