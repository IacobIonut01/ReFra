/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import com.dot.gallery.domain.util.dot

import android.content.Context
import android.os.Build
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import com.dot.gallery.core.work.setProgress
import com.dot.gallery.core.work.workScheduler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.MediaCategory
import com.dot.gallery.app.presentation.search.helpers.SearchVisionHelper
import com.dot.gallery.core.logging.withLogScope
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskPriority
import dev.brewkits.kmpworkmanager.background.domain.TaskRequest
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import com.dot.gallery.cloud.core.matches
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarning
import com.dot.gallery.core.logging.printError
import me.tatarka.inject.annotations.Inject


/**
 * Worker that classifies media into categories using EmbeddingGemma 2 embeddings.
 *
 * This worker:
 * 1. Loads all categories and their search terms
 * 2. Generates text embeddings for category search terms
 * 3. Compares image embeddings with category embeddings
 * 4. Associates media with categories based on similarity threshold
 */
class CategoryWorker @Inject constructor(
    private val database: InternalDatabase,
    private val modelManager: ModelManager,
    private val appContext: Context,
) : GalleryWorker {

    private val visionHelper by lazy { SearchVisionHelper(modelManager) }

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withLogScope("worker.category") {
            runCatching {
                printInfo("CategoryWorker starting classification")

                // Check if classification is disabled
                val noClassification = Settings.Misc.getSetting(appContext, Settings.Misc.NO_CLASSIFICATION, false)
                    .firstOrNull() ?: false
                if (noClassification) {
                    printInfo("CategoryWorker: Classification is disabled")
                    return@withLogScope WorkerResult.Success()
                }

                if (!modelManager.isReady(modelManager.activeSearchModel)) {
                    printInfo("CategoryWorker: ML models not installed, skipping")
                    return@withLogScope WorkerResult.Success()
                }

                env.setProgress(WORK_NAME, workData(KEY_PROGRESS to 0f, KEY_STATUS to "Initializing..."), percent = 0)

                val categoryDao = database.getCategoryDao()
                val embeddingDao = database.getImageEmbeddingDao()

                // Get all categories
                var categories = categoryDao.getAllCategoriesAsync()

                // If no categories exist, initialize with default categories
                if (categories.isEmpty()) {
                    printInfo("CategoryWorker: No categories found, initializing defaults")
                    categoryDao.insertCategories(Category.DEFAULT_CATEGORIES)
                    categories = categoryDao.getAllCategoriesAsync()
                }

                if (categories.isEmpty()) {
                    printInfo("CategoryWorker: Still no categories, aborting")
                    return@withLogScope WorkerResult.Success()
                }

                // Get all image embeddings for the active engine. Rows written by the
                // other search model (different dimension) are excluded — a dot product
                // across dimensions would corrupt scores and can crash.
                val dimension = modelManager.activeSearchModel.embeddingDimension
                var imageEmbeddings = (embeddingDao.getRecords().firstOrNull() ?: emptyList())
                    .filter { it.embedding.size == dimension }
                if (imageEmbeddings.isEmpty()) {
                    printInfo("CategoryWorker: No image embeddings found, starting search indexer...")
                    env.setProgress(WORK_NAME, workData(KEY_PROGRESS to 0f, KEY_STATUS to "Starting image indexer..."), percent = 0)

                    startSearchIndexer()

                    // Wait for the search indexer to complete (with timeout)
                    val scheduler = workScheduler()
                    var attempts = 0
                    val maxAttempts = 600 // 10 minutes max wait (600 * 1 second)

                    while (attempts < maxAttempts && currentCoroutineContext().isActive && !env.isCancelled()) {
                        delay(1000) // Wait 1 second between checks
                        attempts++

                        val tasks = scheduler.queryTasks(tags = setOf(SEARCH_INDEXER_TAG))
                        val isRunning = tasks.any { it.state is TaskState.Running || it.state is TaskState.Enqueued }

                        if (!isRunning) {
                            // Check if we now have embeddings
                            imageEmbeddings = (embeddingDao.getRecords().firstOrNull() ?: emptyList())
                                .filter { it.embedding.size == dimension }
                            if (imageEmbeddings.isNotEmpty()) {
                                printInfo("CategoryWorker: Search indexer completed, found ${imageEmbeddings.size} embeddings")
                                break
                            } else {
                                printWarning("CategoryWorker: Search indexer finished but no embeddings found")
                                env.setProgress(WORK_NAME, workData(KEY_PROGRESS to 100f, KEY_STATUS to "No images to classify"), percent = 100)
                                return@withLogScope WorkerResult.Success()
                            }
                        }

                        // Update progress to show we're waiting
                        if (attempts % 5 == 0) {
                            val progress = WorkSignals.latestProgress(SEARCH_INDEXER_TAG)
                                ?.let { WorkInput.of(it).getFloat("progress", 0f) } ?: 0f
                            env.setProgress(WORK_NAME, workData(
                                KEY_PROGRESS to (progress * 0.5f).coerceIn(0f, 50f), // Use first 50% for indexing
                                KEY_STATUS to "Indexing images... ${progress.toInt()}%"
                            ), percent = (progress * 0.5f).toInt().coerceIn(0, 50))
                        }
                    }

                    if (imageEmbeddings.isEmpty()) {
                        printWarning("CategoryWorker: Timed out waiting for search indexer")
                        env.setProgress(WORK_NAME, workData(KEY_PROGRESS to 100f, KEY_STATUS to "Indexer timed out"), percent = 100)
                        return@withLogScope WorkerResult.Success()
                    }
                }

                // Exclude videos from categorisation when the user opted in. Embeddings
                // carry no mimeType, so video ids are resolved through the media mirrors.
                val excludeVideos = Settings.Misc.getSetting(
                    appContext, Settings.Misc.CATEGORIES_EXCLUDE_VIDEOS, false
                ).firstOrNull() ?: false
                if (excludeVideos) {
                    val videoIds = categoryDao.getVideoMediaIds().toHashSet()
                    if (videoIds.isNotEmpty()) {
                        val before = imageEmbeddings.size
                        imageEmbeddings = imageEmbeddings.filter { it.id !in videoIds }
                        printInfo("CategoryWorker: excluded ${before - imageEmbeddings.size} video embeddings")
                    }
                }

                printInfo("CategoryWorker: Processing ${categories.size} categories and ${imageEmbeddings.size} images")

                // Set up text session for generating category embeddings
                val textSession = visionHelper.setupTextSession()

                val throttler = ProgressThrottler()
                val totalSteps = categories.size

                textSession.use { session ->
                    categories.fastForEachIndexed { categoryIndex, category ->
                        if (!currentCoroutineContext().isActive || env.isCancelled()) return@use

                        val pct = ((categoryIndex.toFloat() / totalSteps.toFloat()) * 100f).coerceIn(0f, 99f)
                        throttler.emit(pct.toInt()) {
                            WorkSignals.reportProgress(WORK_NAME, workData(
                                KEY_PROGRESS to it.toFloat(),
                                KEY_STATUS to "Processing ${category.name}...",
                                KEY_CURRENT_CATEGORY to category.name
                            ))
                        }

                        // Generate text embedding for the category's search terms (if any).
                        // A stored embedding from the other engine (wrong dimension) is
                        // discarded and recomputed.
                        val categoryEmbedding = if (category.searchTerms.isNotBlank()) {
                            category.embedding?.takeIf { it.size == dimension } ?: run {
                                val embedding = visionHelper.getTextEmbedding(session, category.searchTerms)
                                categoryDao.updateCategory(category.copy(
                                    embedding = embedding,
                                    updatedAt = System.currentTimeMillis()
                                ))
                                embedding
                            }
                        } else null

                        // Collect reference image embeddings for image-to-image matching
                        val refIdSet = category.referenceImageIds.toSet()
                        val refEmbeddings = if (refIdSet.isNotEmpty()) {
                            imageEmbeddings.filter { it.id in refIdSet }
                        } else emptyList()

                        if (categoryEmbedding == null && refEmbeddings.isEmpty()) {
                            printInfo("CategoryWorker: Category '${category.name}' has no text or reference images, skipping")
                            return@fastForEachIndexed
                        }

                        // Find matching media — the stored threshold is a normalized
                        // sensitivity; map it onto the active engine's cosine band.
                        val threshold = modelManager.activeSearchModel
                            .thresholdFromSensitivity(category.threshold)
                        val matchingMedia = mutableListOf<MediaCategory>()

                        imageEmbeddings.fastForEach { imageEmbedding ->
                            // Skip reference images themselves
                            if (imageEmbedding.id in refIdSet) return@fastForEach

                            var bestScore = 0f

                            // Text-to-image similarity
                            if (categoryEmbedding != null) {
                                bestScore = maxOf(bestScore, categoryEmbedding.dot(imageEmbedding.embedding))
                            }

                            // Image-to-image similarity (against each reference)
                            refEmbeddings.fastForEach { ref ->
                                bestScore = maxOf(bestScore, ref.embedding.dot(imageEmbedding.embedding))
                            }

                            if (bestScore >= threshold) {
                                matchingMedia.add(
                                    MediaCategory(
                                        mediaId = imageEmbedding.id,
                                        categoryId = category.id,
                                        similarityScore = bestScore
                                    )
                                )
                            }
                        }

                        printInfo("CategoryWorker: Category '${category.name}' matched ${matchingMedia.size} media items")

                        // Update the database with the matches
                        if (matchingMedia.isNotEmpty()) {
                            categoryDao.reclassifyMediaForCategory(category.id, matchingMedia)
                        }
                    }
                }

                // Clean up orphaned media-category entries (media that no longer exists)
                val validMediaIds = imageEmbeddings.fastMap { it.id }
                categoryDao.cleanupOrphanedMediaCategories(validMediaIds)

                env.setProgress(WORK_NAME, workData(KEY_PROGRESS to 100f, KEY_STATUS to "Complete"), percent = 100)
                printInfo("CategoryWorker: Classification complete")

                return@withLogScope WorkerResult.Success()
            }.getOrElse { exception ->
                printWarning("CategoryWorker failed: ${exception.message}")
                printError("worker.category", "category classification failed", exception)
                return@withLogScope WorkerResult.Failure("category classification failed: ${exception.message}")
            }
        }

    /**
     * Starts the search indexer to create image embeddings
     */
    private suspend fun startSearchIndexer() {
        // expedited on API 31+ via a single-step HIGH-priority chain (standalone
        // enqueues never expedite under kmpworkmanager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            workScheduler().beginWith(
                TaskRequest(
                    workerClassName = SearchIndexerUpdaterWorker.WORKER_NAME,
                    priority = TaskPriority.HIGH,
                    tags = setOf(SEARCH_INDEXER_TAG),
                )
            ).withId(SEARCH_INDEXER_TAG, ExistingPolicy.KEEP).enqueue()
        } else {
            workScheduler().enqueue(
                id = SEARCH_INDEXER_TAG,
                trigger = TaskTrigger.OneTime(),
                workerClassName = SearchIndexerUpdaterWorker.WORKER_NAME,
                policy = ExistingPolicy.KEEP,
                tags = setOf(SEARCH_INDEXER_TAG),
            )
        }
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_CURRENT_CATEGORY = "current_category"
        const val WORK_NAME = "CategoryWorker"
        const val TAG = "CategoryClassifier"
        const val WORKER_NAME = "CategoryWorker"
        private const val SEARCH_INDEXER_TAG = "SearchIndexerUpdater"
    }
}

@Worker(name = CategoryWorker.WORKER_NAME)
class CategoryWorkerAdapter : GalleryWorkerAdapter(CategoryWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.categoryWorker.doWork(input, env)
}

/**
 * Extension function to start category classification
 */
suspend fun BackgroundTaskScheduler.startCategoryClassification() {
    enqueue(
        id = CategoryWorker.WORK_NAME,
        trigger = TaskTrigger.OneTime(),
        workerClassName = CategoryWorker.WORKER_NAME,
        constraints = Constraints(),
        policy = ExistingPolicy.REPLACE,
        tags = setOf(CategoryWorker.TAG),
    )
}

/**
 * Extension function to stop category classification
 */
fun BackgroundTaskScheduler.stopCategoryClassification() {
    cancel(CategoryWorker.WORK_NAME)
}

/**
 * Extension function to reclassify a single category
 */
suspend fun BackgroundTaskScheduler.reclassifyCategory(categoryId: Long) {
    enqueue(
        id = "${CategoryWorker.WORK_NAME}_$categoryId",
        trigger = TaskTrigger.OneTime(),
        workerClassName = CategoryWorker.WORKER_NAME,
        constraints = Constraints(),
        inputJson = workDataJson("categoryId" to categoryId),
        policy = ExistingPolicy.REPLACE,
        tags = setOf(CategoryWorker.TAG, "reclassify_$categoryId"),
    )
}
