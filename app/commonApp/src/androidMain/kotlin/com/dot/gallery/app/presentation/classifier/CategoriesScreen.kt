/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.classifier

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import kotlinx.coroutines.flow.flowOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.data.dao.CategoryWithMediaCount
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.classifier.CategoriesViewModel
import com.dot.gallery.feature.classifier.ui.CategoriesActions
import com.dot.gallery.feature.classifier.ui.CategoriesContent
import com.dot.gallery.feature.classifier.ui.CategoriesState


/**
 * App shell for `:feature:classifier`'s [CategoriesContent] — collects state in
 * NavigationComp and wires route navigation through `Screen`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    categoriesWithCount: List<CategoryWithMediaCount>,
    distinctMediaCount: Int,
    mediaState: MediaState<UriMedia>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val eventHandler = LocalEventHandler.current
    val scan by viewModel.categoryScan.collectAsStateWithLifecycle()
    val scanProgress by viewModel.categoryWorkerProgress.collectAsStateWithLifecycle()
    val searchModelStatus by viewModel.searchModelStatus.collectAsStateWithLifecycle()
    val run = scan
    var coverCategoryId by remember { mutableStateOf<Long?>(null) }
    val coverIds by remember(coverCategoryId) {
        coverCategoryId?.let { viewModel.mediaIdsInCategory(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val coverCandidates = remember(coverIds, mediaState.media) {
        coverIds?.toSet()?.let { ids -> mediaState.media.filter { it.id in ids } }
    }
    CategoriesContent(
        state = CategoriesState(
            categoriesWithCount = categoriesWithCount,
            distinctMediaCount = distinctMediaCount,
            mediaState = mediaState,
            isScanning = run != null,
            scanProgress = if (run != null && scanProgress > 0f) scanProgress / 100f else null,
            scanProcessed = run?.processedMedia ?: 0,
            scanTotal = run?.totalMedia ?: 0,
            searchModelStatus = searchModelStatus,
            coverCandidates = coverCandidates,
        ),
        actions = CategoriesActions(
            onOpenCategory = { categoryId ->
                eventHandler.navigate(Screen.CategoryViewScreen.categoryId(categoryId))
            },
            onEditCategory = { categoryId ->
                eventHandler.navigate(Screen.CategoryEditorScreen.edit(categoryId))
            },
            onCreateCategory = {
                eventHandler.navigate(Screen.CategoryEditorScreen.create())
            },
            onOpenSettings = {
                eventHandler.navigate(Screen.CategoriesSettingsScreen())
            },
            onTogglePin = viewModel::toggleCategoryPinned,
            onDeleteCategory = viewModel::deleteCategory,
            onAddSuggestion = viewModel::addSuggestedCategory,
            onAddAllSuggestions = viewModel::startCategoryClassification,
            onPauseScan = viewModel::stopCategoryClassification,
            onCoverPickerChanged = { coverCategoryId = it },
            onSetCover = viewModel::setCategoryCover,
            onSetUpSmartSearch = {
                eventHandler.navigate(Screen.IntelligenceFeatureScreen.feature("SMART_SEARCH"))
            },
        ),
        lastCellIndex = rememberAlbumGridSize(),
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )
}
