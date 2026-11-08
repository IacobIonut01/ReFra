/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.classifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.settings.ui.CategoriesSettingsActions
import com.dot.gallery.feature.settings.ui.CategoriesSettingsContent
import com.dot.gallery.feature.settings.ui.CategoriesSettingsState
import com.dot.gallery.handover.HandoverConfig
import kotlinx.coroutines.launch
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.classifier.CategoriesViewModel

@Composable
fun CategoriesSettingsScreen() {
    val viewModel = injectViewModel<CategoriesViewModel> {categoriesViewModel}
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { DataStoreSettingsStore(context.activeDataStore) }

    val categoryScan by viewModel.categoryScan.collectAsStateWithLifecycle()
    val categoryWorkerProgress by viewModel.categoryWorkerProgress.collectAsStateWithLifecycle()
    val categoriesWithCount by viewModel.categoriesWithCount.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val matchedCount by viewModel.distinctClassifiedMediaCount.collectAsStateWithLifecycle()
    val embeddedCount by viewModel.imageEmbeddingCount.collectAsStateWithLifecycle()
    val lastScanAt by viewModel.lastCategoryScanAt.collectAsStateWithLifecycle()
    // Classification runs on stored image_embeddings — a configured remote
    // inference server produces them without local models.
    val handoverReady by remember {
        HandoverConfig.configuredFlow(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    var videoPurgeCount by remember { mutableStateOf<Int?>(null) }

    CategoriesSettingsContent(
        state = CategoriesSettingsState(
            categoryScan = categoryScan,
            workerProgress = categoryWorkerProgress,
            hasCategories = categoriesWithCount.isNotEmpty(),
            modelsReady = modelStatus == ModelStatus.READY || handoverReady,
            videoPurgeCount = videoPurgeCount,
            categoryCount = categoriesWithCount.size,
            matchedCount = matchedCount,
            unmatchedCount = (embeddedCount - matchedCount).coerceAtLeast(0),
            lastScanAt = lastScanAt,
        ),
        actions = CategoriesSettingsActions(
            onStartScan = viewModel::startCategoryClassification,
            onStopScan = viewModel::stopCategoryClassification,
            onResetCategories = viewModel::resetCategories,
            onRequestExcludeVideos = {
                scope.launch {
                    val count = viewModel.getVideoCategoryMembershipCount()
                    if (count > 0) {
                        videoPurgeCount = count
                    } else {
                        settingsStore.put(SettingsKeys.categoriesExcludeVideos, true)
                    }
                }
            },
            onConfirmVideoPurge = {
                videoPurgeCount = null
                scope.launch {
                    settingsStore.put(SettingsKeys.categoriesExcludeVideos, true)
                    viewModel.purgeVideoCategoryMemberships()
                }
            },
            onDismissVideoPurge = { videoPurgeCount = null },
        ),
    )
}
