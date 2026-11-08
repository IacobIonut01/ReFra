package com.dot.gallery.core.workers

import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.workDataJson
import com.dot.gallery.core.work.workScheduler
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.core.logging.printWarning
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import dev.brewkits.kmpworkmanager.background.domain.WorkerProgress
import me.tatarka.inject.annotations.Inject



/**
 * Legacy ClassifierWorker that now delegates to the new CategoryWorker.
 * This worker is kept for backward compatibility but uses the EmbeddingGemma-based
 * category classification system instead of the old ONNX mobile_ica model.
 *
 * @deprecated Use CategoryWorker directly via startCategoryClassification()
 */
class ClassifierWorker @Inject constructor() : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withLogScope("worker.classifier") {
            printWarning("ClassifierWorker: Delegating to new CategoryWorker system")

            // Start the new category classification worker
            workScheduler().startCategoryClassification()

            env.progressListener?.onProgressUpdate(WorkerProgress(100))
            WorkerResult.Success()
        }

    companion object {
        const val WORKER_NAME = "ClassifierWorker"
    }
}

@Worker(name = ClassifierWorker.WORKER_NAME)
class ClassifierWorkerAdapter : GalleryWorkerAdapter(ClassifierWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.classifierWorker.doWork(input, env)
}

/**
 * @deprecated Use startCategoryClassification() instead
 */
suspend fun BackgroundTaskScheduler.startClassification(indexStart: Int = 0, size: Int = 50) {
    enqueue(
        id = "ClassifierWorker_${indexStart}_$size",
        trigger = TaskTrigger.OneTime(),
        workerClassName = ClassifierWorker.WORKER_NAME,
        // androidx requiresStorageNotLow=true ↔ absence of ALLOW_LOW_STORAGE (the default)
        constraints = Constraints(),
        inputJson = workDataJson("chunkIndexStart" to indexStart, "chunkSize" to size),
        policy = ExistingPolicy.REPLACE,
        tags = setOf("ImageClassifier"),
    )
}

/**
 * @deprecated Use stopCategoryClassification() instead
 */
fun BackgroundTaskScheduler.stopClassification() {
    cancelByTag("ImageClassifier")
}
