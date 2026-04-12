package com.dot.gallery.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Legacy ClassifierWorker that now delegates to the new CategoryWorker.
 * This worker is kept for backward compatibility but uses the new CLIP-based
 * category classification system instead of the old ONNX mobile_ica model.
 * 
 * @deprecated Use CategoryWorker directly via startCategoryClassification()
 */
@HiltWorker
class ClassifierWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        printWarning("ClassifierWorker: Delegating to new CategoryWorker system")
        
        // Start the new category classification worker
        WorkManager.getInstance(appContext).startCategoryClassification()
        
        setProgress(workDataOf("progress" to 100))
        return Result.success()
    }
}

/**
 * @deprecated Use startCategoryClassification() instead
 */
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

/**
 * @deprecated Use stopCategoryClassification() instead
 */
fun WorkManager.stopClassification() {
    cancelAllWorkByTag("ImageClassifier")
}
