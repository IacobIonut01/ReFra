/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import android.os.Build
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.core.Settings
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.MediaCategory
import com.dot.gallery.feature_node.presentation.search.helpers.SearchVisionHelper
import com.dot.gallery.feature_node.presentation.search.util.dot
import com.dot.gallery.feature_node.presentation.util.printInfo
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive

/**
 * Worker that classifies media into categories using CLIP embeddings.
 * 
 * This worker:
 * 1. Loads all categories and their search terms
 * 2. Generates text embeddings for category search terms
 * 3. Compares image embeddings with category embeddings
 * 4. Associates media with categories based on similarity threshold
 */
@HiltWorker
class CategoryWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val visionHelper by lazy { SearchVisionHelper(appContext) }

    override suspend fun doWork(): Result = runCatching {
        printInfo("CategoryWorker starting classification")
        
        // Check if classification is disabled
        val noClassification = Settings.Misc.getSetting(appContext, Settings.Misc.NO_CLASSIFICATION, false)
            .firstOrNull() ?: false
        if (noClassification) {
            printInfo("CategoryWorker: Classification is disabled")
            return Result.success()
        }

        setProgress(workDataOf(KEY_PROGRESS to 0f, KEY_STATUS to "Initializing..."))

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
            return Result.success()
        }

        // Get all image embeddings
        var imageEmbeddings = embeddingDao.getRecords().firstOrNull() ?: emptyList()
        if (imageEmbeddings.isEmpty()) {
            printInfo("CategoryWorker: No image embeddings found, starting search indexer...")
            setProgress(workDataOf(KEY_PROGRESS to 0f, KEY_STATUS to "Starting image indexer..."))
            
            // Start the search indexer
            startSearchIndexer()
            
            // Wait for the search indexer to complete (with timeout)
            val workManager = WorkManager.getInstance(appContext)
            var attempts = 0
            val maxAttempts = 600 // 10 minutes max wait (600 * 1 second)
            
            while (attempts < maxAttempts && currentCoroutineContext().isActive && !isStopped) {
                delay(1000) // Wait 1 second between checks
                attempts++
                
                val workInfos = workManager.getWorkInfosByTag("SearchIndexerUpdater").get()
                val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                
                if (!isRunning) {
                    // Check if we now have embeddings
                    imageEmbeddings = embeddingDao.getRecords().firstOrNull() ?: emptyList()
                    if (imageEmbeddings.isNotEmpty()) {
                        printInfo("CategoryWorker: Search indexer completed, found ${imageEmbeddings.size} embeddings")
                        break
                    } else {
                        printWarning("CategoryWorker: Search indexer finished but no embeddings found")
                        setProgress(workDataOf(KEY_PROGRESS to 100f, KEY_STATUS to "No images to classify"))
                        return Result.success()
                    }
                }
                
                // Update progress to show we're waiting
                if (attempts % 5 == 0) {
                    val progress = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                        ?.progress?.getFloat("progress", 0f) ?: 0f
                    setProgress(workDataOf(
                        KEY_PROGRESS to (progress * 0.5f).coerceIn(0f, 50f), // Use first 50% for indexing
                        KEY_STATUS to "Indexing images... ${progress.toInt()}%"
                    ))
                }
            }
            
            if (imageEmbeddings.isEmpty()) {
                printWarning("CategoryWorker: Timed out waiting for search indexer")
                setProgress(workDataOf(KEY_PROGRESS to 100f, KEY_STATUS to "Indexer timed out"))
                return Result.success()
            }
        }

        printInfo("CategoryWorker: Processing ${categories.size} categories and ${imageEmbeddings.size} images")

        // Set up text session for generating category embeddings
        val textSession = visionHelper.setupTextSession()

        val throttler = ProgressThrottler()
        val totalSteps = categories.size
        
        textSession.use { session ->
            categories.fastForEachIndexed { categoryIndex, category ->
                if (!currentCoroutineContext().isActive || isStopped) return@use
                
                val pct = ((categoryIndex.toFloat() / totalSteps.toFloat()) * 100f).coerceIn(0f, 99f)
                throttler.emit(pct.toInt()) {
                    setProgress(workDataOf(
                        KEY_PROGRESS to it.toFloat(),
                        KEY_STATUS to "Processing ${category.name}...",
                        KEY_CURRENT_CATEGORY to category.name
                    ))
                }

                // Generate text embedding for the category's search terms
                val categoryEmbedding = category.embedding ?: run {
                    val embedding = visionHelper.getTextEmbedding(session, category.searchTerms)
                    // Save the embedding for future use - use full entity update to properly apply TypeConverters
                    categoryDao.updateCategory(category.copy(
                        embedding = embedding,
                        updatedAt = System.currentTimeMillis()
                    ))
                    embedding
                }

                // Find matching media
                val matchingMedia = mutableListOf<MediaCategory>()
                
                imageEmbeddings.fastForEach { imageEmbedding ->
                    val similarity = categoryEmbedding.dot(imageEmbedding.embedding)
                    
                    if (similarity >= category.threshold) {
                        matchingMedia.add(
                            MediaCategory(
                                mediaId = imageEmbedding.id,
                                categoryId = category.id,
                                similarityScore = similarity
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

        setProgress(workDataOf(KEY_PROGRESS to 100f, KEY_STATUS to "Complete"))
        printInfo("CategoryWorker: Classification complete")
        
        return Result.success()
    }.getOrElse { exception ->
        printWarning("CategoryWorker failed: ${exception.message}")
        exception.printStackTrace()
        return Result.failure()
    }

    /**
     * Starts the search indexer to create image embeddings
     */
    private fun startSearchIndexer() {
        val workManager = WorkManager.getInstance(appContext)
        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val searchIndexerWork = OneTimeWorkRequestBuilder<SearchIndexerUpdaterWorker>()
            .setConstraints(constraints)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .addTag("SearchIndexerUpdater")
            .build()

        workManager.enqueueUniqueWork(
            "SearchIndexerUpdater",
            ExistingWorkPolicy.KEEP,
            searchIndexerWork
        )
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_CURRENT_CATEGORY = "current_category"
        const val WORK_NAME = "CategoryWorker"
        const val TAG = "CategoryClassifier"
    }
}

/**
 * Extension function to start category classification
 */
fun WorkManager.startCategoryClassification() {
    val request = OneTimeWorkRequestBuilder<CategoryWorker>()
        .addTag(CategoryWorker.TAG)
        .setConstraints(
            Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()
        )
        .build()
    
    enqueueUniqueWork(
        CategoryWorker.WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request
    )
}

/**
 * Extension function to stop category classification
 */
fun WorkManager.stopCategoryClassification() {
    cancelUniqueWork(CategoryWorker.WORK_NAME)
}

/**
 * Extension function to reclassify a single category
 */
fun WorkManager.reclassifyCategory(categoryId: Long) {
    val request = OneTimeWorkRequestBuilder<CategoryWorker>()
        .addTag(CategoryWorker.TAG)
        .addTag("reclassify_$categoryId")
        .setInputData(workDataOf("categoryId" to categoryId))
        .setConstraints(
            Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()
        )
        .build()
    
    enqueueUniqueWork(
        "${CategoryWorker.WORK_NAME}_$categoryId",
        ExistingWorkPolicy.REPLACE,
        request
    )
}
