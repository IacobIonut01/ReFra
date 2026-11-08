/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.util.categorySharedElement
import com.dot.gallery.data.dao.CategoryWithMediaCount
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.action_cancel
import com.dot.gallery.feature.classifier.generated.resources.action_delete
import com.dot.gallery.feature.classifier.generated.resources.add_category
import com.dot.gallery.feature.classifier.generated.resources.categories
import com.dot.gallery.feature.classifier.generated.resources.categories_add_all_suggestions
import com.dot.gallery.feature.classifier.generated.resources.categories_adding_body
import com.dot.gallery.feature.classifier.generated.resources.categories_adding_title
import com.dot.gallery.feature.classifier.generated.resources.category_change_cover
import com.dot.gallery.feature.classifier.generated.resources.category_change_cover_summary
import com.dot.gallery.feature.classifier.generated.resources.categories_create
import com.dot.gallery.feature.classifier.generated.resources.categories_empty_body
import com.dot.gallery.feature.classifier.generated.resources.categories_empty_title
import com.dot.gallery.feature.classifier.generated.resources.categories_filter_all
import com.dot.gallery.feature.classifier.generated.resources.categories_filter_empty
import com.dot.gallery.feature.classifier.generated.resources.categories_filter_pinned
import com.dot.gallery.feature.classifier.generated.resources.categories_filter_suggested
import com.dot.gallery.feature.classifier.generated.resources.categories_filter_yours
import com.dot.gallery.feature.classifier.generated.resources.categories_model_action
import com.dot.gallery.feature.classifier.generated.resources.categories_model_body
import com.dot.gallery.feature.classifier.generated.resources.categories_model_downloading
import com.dot.gallery.feature.classifier.generated.resources.categories_model_title
import com.dot.gallery.feature.classifier.generated.resources.categories_pause
import com.dot.gallery.feature.classifier.generated.resources.categories_section_all
import com.dot.gallery.feature.classifier.generated.resources.categories_section_other
import com.dot.gallery.feature.classifier.generated.resources.categories_section_pinned
import com.dot.gallery.feature.classifier.generated.resources.categories_sorted_summary
import com.dot.gallery.feature.classifier.generated.resources.categories_sorted_title
import com.dot.gallery.feature.classifier.generated.resources.categories_sorting_note
import com.dot.gallery.feature.classifier.generated.resources.categories_sorting_preparing
import com.dot.gallery.feature.classifier.generated.resources.categories_sorting_progress
import com.dot.gallery.feature.classifier.generated.resources.categories_sorting_title
import com.dot.gallery.feature.classifier.generated.resources.categories_suggestions_header
import com.dot.gallery.feature.classifier.generated.resources.category_delete_body
import com.dot.gallery.feature.classifier.generated.resources.category_delete_named_title
import com.dot.gallery.feature.classifier.generated.resources.category_delete_summary
import com.dot.gallery.feature.classifier.generated.resources.category_edit_summary
import com.dot.gallery.feature.classifier.generated.resources.category_kind_suggested
import com.dot.gallery.feature.classifier.generated.resources.category_kind_yours
import com.dot.gallery.feature.classifier.generated.resources.category_media_count
import com.dot.gallery.feature.classifier.generated.resources.category_pin
import com.dot.gallery.feature.classifier.generated.resources.category_pin_summary
import com.dot.gallery.feature.classifier.generated.resources.category_tile_no_matches
import com.dot.gallery.feature.classifier.generated.resources.category_tile_sorting
import com.dot.gallery.feature.classifier.generated.resources.category_unpin
import com.dot.gallery.feature.classifier.generated.resources.category_unpin_summary
import com.dot.gallery.feature.classifier.generated.resources.edit_category
import com.dot.gallery.feature.classifier.ui.components.CategoryCover
import com.dot.gallery.feature.classifier.ui.components.CategoryCoverPickerSheet
import com.dot.gallery.feature.classifier.ui.components.CategoryListRow
import com.dot.gallery.feature.classifier.ui.components.CategoryProgressCard
import com.dot.gallery.feature.classifier.ui.components.CategorySectionHeader
import com.dot.gallery.feature.classifier.ui.components.CategoryStatusBanner
import com.dot.gallery.feature.classifier.ui.components.CategoryTile
import com.dot.gallery.feature.classifier.ui.components.DashedAddTile
import com.dot.gallery.feature.classifier.ui.components.PinnedCategoryCard
import com.dot.gallery.feature.classifier.ui.components.joinedShape
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.hazeSource

private enum class CategoryFilter(val label: StringResource) {
    ALL(Res.string.categories_filter_all),
    PINNED(Res.string.categories_filter_pinned),
    YOURS(Res.string.categories_filter_yours),
    SUGGESTED(Res.string.categories_filter_suggested);

    fun matches(category: CategoryWithMediaCount): Boolean = when (this) {
        ALL -> true
        PINNED -> category.isPinned
        YOURS -> category.isUserCreated
        SUGGESTED -> !category.isUserCreated
    }
}

/**
 * Categories hub in the Library style: status banner (or scan progress), filter chips, a row of
 * pinned cards and the pinch-to-zoom album grid ending in a New category tile. Long-press opens an actions
 * sheet. Without a Smart search model the page explains how to get one instead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CategoriesContent(
    state: CategoriesState,
    actions: CategoriesActions,
    lastCellIndex: MutableState<Int>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    var filter by rememberSaveable { mutableStateOf(CategoryFilter.ALL) }
    var sheetCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var coverCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Set when suggestions are added from the empty state, so the page shows that work started
    // before the scan run registers and the first category fills.
    var addingSuggestions by rememberSaveable { mutableStateOf(false) }
    var lastCellIndexState by lastCellIndex
    var canScroll by rememberSaveable { mutableStateOf(true) }
    val pinchState = rememberGridPinchZoomState(
        cellsList = albumCellsList,
        initialCellsIndex = lastCellIndexState,
    )
    LaunchedEffect(pinchState.isZooming) {
        canScroll = !pinchState.isZooming
        lastCellIndexState = albumCellsList.indexOf(pinchState.currentCells)
    }
    val thumbnails = remember(state.mediaState.media) { state.mediaState.media.associateBy { it.id } }
    val categories = state.categoriesWithCount
    val showEmpty = categories.isEmpty() && !state.mediaState.isLoading
    LaunchedEffect(categories.isNotEmpty()) {
        if (categories.isNotEmpty()) addingSuggestions = false
    }
    val hazeState = LocalHazeState.current
    val gridScrolled = rememberScrolledUnderTopBar(pinchState.gridState)

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
            GalleryPageTopBar(
                title = stringResource(Res.string.categories),
                count = categories.size.takeIf { it > 0 }?.toString(),
                hazeState = hazeState,
                // Only the category grid scrolls; the model and empty states sit still.
                scrolled = gridScrolled && !state.needsSearchModel && !showEmpty,
                actions = if (state.needsSearchModel || showEmpty) null else {
                    {
                        GalleryPageTopBarAction(
                            icon = Icons.Outlined.Add,
                            contentDescription = stringResource(Res.string.add_category),
                            onClick = actions.onCreateCategory,
                        )
                    }
                },
            )
            }
        },
    ) { padding ->
        // The page's only haze source, around every branch so the bar always has content to blur.
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
        when {
            state.needsSearchModel -> NeedsSearchModel(
                downloading = state.isModelDownloading,
                onSetUp = actions.onSetUpSmartSearch,
                modifier = Modifier.padding(padding),
            )
            showEmpty -> EmptyCategories(
                state = state,
                adding = addingSuggestions,
                onCreate = actions.onCreateCategory,
                onAddSuggestion = { template ->
                    addingSuggestions = true
                    actions.onAddSuggestion(template)
                },
                onAddAll = {
                    addingSuggestions = true
                    actions.onAddAllSuggestions()
                },
                onPause = actions.onPauseScan,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val layoutDirection = LocalLayoutDirection.current
                val pinned = categories.filter { it.isPinned }
                val showPinnedRow = filter == CategoryFilter.ALL && pinned.isNotEmpty()
                val gridItems = when {
                    showPinnedRow -> categories.filterNot { it.isPinned }
                    else -> categories.filter(filter::matches)
                }
                GridPinchZoomLayout(
                    state = pinchState,
                    indicatorTopPadding = padding.calculateTopPadding() + 16.dp,
                ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = gridCells,
                    userScrollEnabled = canScroll,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    fullSpan("status") {
                        StatusBlock(state = state, actions = actions)
                    }
                    fullSpan("filters") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CategoryFilter.entries) { entry ->
                                GalleryFilterChip(
                                    label = stringResource(entry.label),
                                    selected = filter == entry,
                                    onClick = { filter = entry },
                                )
                            }
                        }
                    }
                    if (showPinnedRow) {
                        fullSpan("pinned_header") {
                            CategorySectionHeader(
                                title = stringResource(Res.string.categories_section_pinned),
                                count = pinned.size.toString(),
                            )
                        }
                        fullSpan("pinned_row") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(pinned, key = { it.id }) { category ->
                                    PinnedCategoryCard(
                                        name = category.name,
                                        meta = tileMeta(category, state.isScanning),
                                        media = category.thumbnailMediaId?.let(thumbnails::get),
                                        onClick = { actions.onOpenCategory(category.id) },
                                        onLongClick = { sheetCategoryId = category.id },
                                        modifier = with(sharedTransitionScope) {
                                            Modifier.categorySharedElement(
                                                categoryId = category.id,
                                                animatedVisibilityScope = animatedContentScope,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    fullSpan("grid_header") {
                        CategorySectionHeader(
                            title = stringResource(
                                if (showPinnedRow) Res.string.categories_section_other else Res.string.categories_section_all
                            ),
                            count = gridItems.size.toString(),
                        )
                    }
                    items(items = gridItems, key = { it.id }) { category ->
                        CategoryTile(
                            name = category.name,
                            meta = tileMeta(category, state.isScanning),
                            media = category.thumbnailMediaId?.let(thumbnails::get),
                            pinned = category.isPinned,
                            sorting = state.isScanning && category.mediaCount == 0,
                            onClick = { actions.onOpenCategory(category.id) },
                            onLongClick = { sheetCategoryId = category.id },
                            modifier = Modifier
                                .pinchItem(key = category.id.toString())
                                .animateItem(),
                            coverModifier = with(sharedTransitionScope) {
                                Modifier.categorySharedElement(
                                    categoryId = category.id,
                                    animatedVisibilityScope = animatedContentScope,
                                )
                            },
                        )
                    }
                    if (gridItems.isEmpty()) {
                        fullSpan("filter_empty") {
                            Text(
                                text = stringResource(Res.string.categories_filter_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                    if (filter == CategoryFilter.ALL || filter == CategoryFilter.YOURS) {
                        item(key = "new_category") {
                            DashedAddTile(
                                label = stringResource(Res.string.categories_create),
                                onClick = actions.onCreateCategory,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            )
                        }
                    }
                }
                }
            }
        }
        }
    }

    val sheetCategory = categories.find { it.id == sheetCategoryId }
    if (sheetCategory != null) {
        CategoryActionsSheet(
            category = sheetCategory,
            media = sheetCategory.thumbnailMediaId?.let(thumbnails::get),
            isScanning = state.isScanning,
            onDismiss = { sheetCategoryId = null },
            onTogglePin = {
                actions.onTogglePin(sheetCategory.id, !sheetCategory.isPinned)
                sheetCategoryId = null
            },
            onEdit = {
                sheetCategoryId = null
                actions.onEditCategory(sheetCategory.id)
            },
            onChangeCover = {
                sheetCategoryId = null
                coverCategoryId = sheetCategory.id
                actions.onCoverPickerChanged(sheetCategory.id)
            },
            onDelete = {
                sheetCategoryId = null
                deleteCategoryId = sheetCategory.id
            },
        )
    }

    val coverCategory = categories.find { it.id == coverCategoryId }
    if (coverCategory != null) {
        val close = {
            coverCategoryId = null
            actions.onCoverPickerChanged(null)
        }
        CategoryCoverPickerSheet(
            media = state.coverCandidates,
            selectedId = coverCategory.coverMediaId,
            onPick = { mediaId ->
                actions.onSetCover(coverCategory.id, mediaId)
                close()
            },
            onUseAutomatic = {
                actions.onSetCover(coverCategory.id, null)
                close()
            },
            onDismiss = close,
        )
    }

    val deleteCategory = categories.find { it.id == deleteCategoryId }
    if (deleteCategory != null) {
        DeleteCategoryDialog(
            name = deleteCategory.name,
            onConfirm = {
                actions.onDeleteCategory(deleteCategory.id)
                deleteCategoryId = null
            },
            onDismiss = { deleteCategoryId = null },
        )
    }
}

private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun tileMeta(category: CategoryWithMediaCount, isScanning: Boolean): String = when {
    category.mediaCount > 0 -> stringResource(Res.string.category_media_count, category.mediaCount)
    isScanning -> stringResource(Res.string.category_tile_sorting)
    else -> stringResource(Res.string.category_tile_no_matches)
}

@Composable
private fun StatusBlock(state: CategoriesState, actions: CategoriesActions) {
    if (state.isScanning) {
        CategoryProgressCard(
            icon = Icons.Outlined.Sync,
            title = stringResource(Res.string.categories_sorting_title),
            summary = if (state.scanTotal > 0) {
                stringResource(Res.string.categories_sorting_progress, state.scanProcessed, state.scanTotal)
            } else {
                stringResource(Res.string.categories_sorting_preparing)
            },
            progress = state.scanProgress,
            note = stringResource(Res.string.categories_sorting_note),
            trailing = {
                TextButton(onClick = actions.onPauseScan) {
                    Text(stringResource(Res.string.categories_pause))
                }
            },
        )
    } else {
        CategoryStatusBanner(
            icon = Icons.Outlined.AutoAwesome,
            title = stringResource(Res.string.categories_sorted_title, state.distinctMediaCount.toString()),
            summary = stringResource(Res.string.categories_sorted_summary),
            onClick = actions.onOpenSettings,
        )
    }
}

/** Centred hero used by the empty and missing-model states. */
@Composable
private fun HeroColumn(
    icon: ImageVector,
    tonal: Boolean,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    if (tonal) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = if (tonal) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyCategories(
    state: CategoriesState,
    adding: Boolean,
    onCreate: () -> Unit,
    onAddSuggestion: (Category) -> Unit,
    onAddAll: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Generated categories stay hidden until they hold a photo, so after adding suggestions the
    // page keeps this layout and shows the sorting run in place of the buttons.
    val working = adding || state.isScanning
    HeroColumn(
        icon = Icons.Outlined.Category,
        tonal = false,
        title = stringResource(if (working) Res.string.categories_adding_title else Res.string.categories_empty_title),
        body = stringResource(if (working) Res.string.categories_adding_body else Res.string.categories_empty_body),
        modifier = modifier,
    ) {
        if (working) {
            CategoryProgressCard(
                icon = Icons.Outlined.Sync,
                title = stringResource(Res.string.categories_sorting_title),
                summary = if (state.isScanning && state.scanTotal > 0) {
                    stringResource(Res.string.categories_sorting_progress, state.scanProcessed, state.scanTotal)
                } else {
                    stringResource(Res.string.categories_sorting_preparing)
                },
                progress = state.scanProgress.takeIf { state.isScanning },
                note = stringResource(Res.string.categories_sorting_note),
                modifier = Modifier.widthIn(max = 480.dp),
                trailing = if (state.isScanning) {
                    {
                        TextButton(onClick = onPause) { Text(stringResource(Res.string.categories_pause)) }
                    }
                } else null,
            )
        } else {
            Button(onClick = onCreate, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.categories_create))
            }
            Text(
                text = stringResource(Res.string.categories_suggestions_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp, bottom = 4.dp),
            )
            FlowRow(
                modifier = Modifier.widthIn(max = 480.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Category.DEFAULT_CATEGORIES.forEach { template ->
                    GalleryFilterChip(label = template.name, selected = false, onClick = { onAddSuggestion(template) })
                }
            }
            TextButton(onClick = onAddAll) {
                Text(stringResource(Res.string.categories_add_all_suggestions))
            }
        }
    }
}

@Composable
private fun NeedsSearchModel(downloading: Boolean, onSetUp: () -> Unit, modifier: Modifier = Modifier) {
    HeroColumn(
        icon = Icons.Outlined.ImageSearch,
        tonal = true,
        title = stringResource(Res.string.categories_model_title),
        body = stringResource(
            if (downloading) Res.string.categories_model_downloading else Res.string.categories_model_body
        ),
        modifier = modifier,
    ) {
        Button(onClick = onSetUp, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.categories_model_action))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryActionsSheet(
    category: CategoryWithMediaCount,
    media: UriMedia?,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CategoryCover(
                    media = media,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                    iconSize = 28.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(category.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "${tileMeta(category, isScanning)} · ${
                            stringResource(if (category.isUserCreated) Res.string.category_kind_yours else Res.string.category_kind_suggested)
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val rows: List<@Composable (Shape) -> Unit> = listOf(
                { shape ->
                    CategoryListRow(
                        icon = Icons.Outlined.PushPin,
                        title = stringResource(if (category.isPinned) Res.string.category_unpin else Res.string.category_pin),
                        summary = stringResource(
                            if (category.isPinned) Res.string.category_unpin_summary else Res.string.category_pin_summary
                        ),
                        shape = shape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = onTogglePin,
                    )
                },
                { shape ->
                    CategoryListRow(
                        icon = Icons.Outlined.Edit,
                        title = stringResource(Res.string.edit_category),
                        summary = stringResource(Res.string.category_edit_summary),
                        shape = shape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = onEdit,
                    )
                },
                { shape ->
                    CategoryListRow(
                        icon = Icons.Outlined.Image,
                        title = stringResource(Res.string.category_change_cover),
                        summary = stringResource(Res.string.category_change_cover_summary),
                        shape = shape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = onChangeCover,
                    )
                },
                { shape ->
                    CategoryListRow(
                        icon = Icons.Outlined.Delete,
                        title = stringResource(Res.string.action_delete),
                        summary = stringResource(Res.string.category_delete_summary),
                        shape = shape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        destructive = true,
                        onClick = onDelete,
                    )
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEachIndexed { index, row -> row(joinedShape(index, rows.size)) }
            }
        }
    }
}

/** Shared delete confirmation for the hub sheet and the editor. */
@Composable
fun DeleteCategoryDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Res.string.category_delete_named_title, name)) },
        text = { Text(stringResource(Res.string.category_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
