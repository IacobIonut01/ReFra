/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.classifier

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.feature.classifier.ui.CategoryViewActions
import com.dot.gallery.feature.classifier.ui.CategoryViewState
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.feature.classifier.ui.CategoryViewContent
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.classifier.CategoryViewModel
import com.dot.gallery.feature.classifier.CategoriesViewModel
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.definedTimestamp

/** First and last month of [media], formatted for the category header. */
private fun categoryDateRange(media: List<UriMedia>): Pair<String, String>? {
    val stamps = media.map { it.definedTimestamp * 1000L }.filter { it > 0 }
    if (stamps.isEmpty()) return null
    val format = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
    return format.format(java.util.Date(stamps.min())) to format.format(java.util.Date(stamps.max()))
}


/**
 * Legacy CategoryViewScreen for string-based categories — app shell for
 * `:feature:classifier`'s [CategoryViewContent].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryViewScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    metadataState: State<MediaMetadataState>,
    category: String
) {
    val eventHandler = LocalEventHandler.current
    val viewModel = injectViewModel<CategoryViewModel> {categoryViewModel}.apply {
        this.category = category
    }

    val mediaState = viewModel.mediaByCategory.collectAsStateWithLifecycle()

    CategoryViewContent(
        state = CategoryViewState(legacyCategory = category),
        actions = CategoryViewActions(
            onInvalidCategory = { eventHandler.navigateUp() },
            onLoadCategory = {},
            onEditCategory = {},
            onOpenMedia = { mediaId ->
                eventHandler.navigate(Screen.MediaViewScreen.idAndCategory(mediaId, category))
            },
            onSearchClick = { eventHandler.navigate(Screen.SearchScreen()) },
        ),
        mediaState = mediaState,
        metadataState = metadataState,
        mediaViewerRoute = Screen.MediaViewScreen.route,
        isScrolling = remember { mutableStateOf(false) },
        lastCellIndex = rememberGridSize(),
        lastMosaicCellIndex = rememberMosaicGridSize(),
        selectionSheetContent = { allMedia, selectedMediaList ->
            SelectionSheet(
                modifier = Modifier.align(Alignment.BottomEnd),
                allMedia = allMedia,
                selectedMedia = selectedMediaList
            )
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope
    )
}

/**
 * New CategoryViewScreen for ID-based categories (new embedding system) —
 * app shell for `:feature:classifier`'s [CategoryViewContent].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryViewScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    metadataState: State<MediaMetadataState>,
    categoryId: Long
) {
    val eventHandler = LocalEventHandler.current
    val viewModel = injectViewModel<CategoryViewModel> {categoryViewModel}

    val categoriesViewModel = injectViewModel<CategoriesViewModel> {categoriesViewModel}

    val mediaState = viewModel.mediaByCategoryId.collectAsStateWithLifecycle()
    val category by viewModel.categoryDetails.collectAsStateWithLifecycle()
    val scan by categoriesViewModel.categoryScan.collectAsStateWithLifecycle()
    val scanProgress by categoriesViewModel.categoryWorkerProgress.collectAsStateWithLifecycle()
    val media = mediaState.value.media
    val dateRange = remember(media) { categoryDateRange(media) }

    CategoryViewContent(
        state = CategoryViewState(
            categoryId = categoryId,
            categoryName = category?.name ?: "",
            category = category,
            cover = media.find { it.id == category?.coverMediaId } ?: media.firstOrNull(),
            dateRange = dateRange,
            isScanning = scan != null,
            scanProgress = if (scan != null && scanProgress > 0f) scanProgress / 100f else null,
        ),
        actions = CategoryViewActions(
            onInvalidCategory = { eventHandler.navigateUp() },
            onLoadCategory = viewModel::setCategoryId,
            onEditCategory = { id ->
                eventHandler.navigate(Screen.CategoryEditorScreen.edit(id))
            },
            onOpenMedia = { mediaId ->
                eventHandler.navigate(Screen.MediaViewScreen.idAndCategoryId(mediaId, categoryId))
            },
            onSearchClick = { eventHandler.navigate(Screen.SearchScreen()) },
            onTogglePin = categoriesViewModel::toggleCategoryPinned,
            onDeleteCategory = { id ->
                categoriesViewModel.deleteCategory(id)
                eventHandler.navigateUp()
            },
            onChangeThreshold = categoriesViewModel::updateCategoryThreshold,
            onSetCover = categoriesViewModel::setCategoryCover,
        ),
        mediaState = mediaState,
        metadataState = metadataState,
        mediaViewerRoute = Screen.MediaViewScreen.route,
        isScrolling = remember { mutableStateOf(false) },
        lastCellIndex = rememberGridSize(),
        lastMosaicCellIndex = rememberMosaicGridSize(),
        selectionSheetContent = { allMedia, selectedMediaList ->
            SelectionSheet(
                modifier = Modifier.align(Alignment.BottomEnd),
                allMedia = allMedia,
                selectedMedia = selectedMediaList
            )
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope
    )
}
