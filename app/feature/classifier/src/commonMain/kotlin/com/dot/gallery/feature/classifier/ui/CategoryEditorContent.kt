/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.MediaGridView
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.classifier.ui.search.ImageSearchPickerSheet
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.action_cancel
import com.dot.gallery.feature.classifier.generated.resources.action_delete
import com.dot.gallery.feature.classifier.generated.resources.add_reference_photos
import com.dot.gallery.feature.classifier.generated.resources.best_match
import com.dot.gallery.feature.classifier.generated.resources.category_created
import com.dot.gallery.feature.classifier.generated.resources.category_deleted
import com.dot.gallery.feature.classifier.generated.resources.category_name
import com.dot.gallery.feature.classifier.generated.resources.category_name_hint
import com.dot.gallery.feature.classifier.generated.resources.category_updated
import com.dot.gallery.feature.classifier.generated.resources.create
import com.dot.gallery.feature.classifier.generated.resources.delete_category_confirmation
import com.dot.gallery.feature.classifier.generated.resources.delete_category_title
import com.dot.gallery.feature.classifier.generated.resources.describe_content
import com.dot.gallery.feature.classifier.generated.resources.describe_content_example
import com.dot.gallery.feature.classifier.generated.resources.describe_content_hint
import com.dot.gallery.feature.classifier.generated.resources.edit_category
import com.dot.gallery.feature.classifier.generated.resources.fine_tune
import com.dot.gallery.feature.classifier.generated.resources.matching_preview
import com.dot.gallery.feature.classifier.generated.resources.more_accurate
import com.dot.gallery.feature.classifier.generated.resources.more_results
import com.dot.gallery.feature.classifier.generated.resources.new_category
import com.dot.gallery.feature.classifier.generated.resources.no_matching_photos
import com.dot.gallery.feature.classifier.generated.resources.quick_start
import com.dot.gallery.feature.classifier.generated.resources.quick_start_subtitle
import com.dot.gallery.feature.classifier.generated.resources.reference_images
import com.dot.gallery.feature.classifier.generated.resources.reference_images_count
import com.dot.gallery.feature.classifier.generated.resources.reference_images_hint
import com.dot.gallery.feature.classifier.generated.resources.save
import com.dot.gallery.feature.classifier.generated.resources.see_all_matches
import com.dot.gallery.feature.classifier.generated.resources.sensitivity
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_balanced
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_balanced_desc
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_broad
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_broad_desc
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_exact
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_exact_desc
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_strict
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_strict_desc


/**
 * Unified screen for creating and editing categories.
 * Replaces both the add- and edit-category screens with improved UX:
 * - Natural language description instead of "search terms"
 * - Inline preview grid with top matches
 * - Template suggestion chips for quick-start
 * - Humanized sensitivity presets instead of raw threshold slider
 * - Post-save snackbar feedback
 *
 * @param lastCellIndex persisted media-grid cell index for the preview sheet
 * (platform store — app-side).
 */
@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun CategoryEditorContent(
    state: CategoryEditorState,
    actions: CategoryEditorActions,
    previewMediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    isScrolling: MutableState<Boolean>,
    lastCellIndex: MutableState<Int>,
    paddingValues: PaddingValues,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.categoryId) {
        state.categoryId?.takeIf { it > 0 }?.let(actions.onLoadCategory)
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRefImagePicker by remember { mutableStateOf(false) }
    var showPreviewSheet by remember { mutableStateOf(false) }
    var showFineTune by rememberSaveable { mutableStateOf(false) }
    var lastCellIndexState by lastCellIndex

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        flingAnimationSpec = null
    )

    // Snackbar messages
    val categoryCreatedMsg = stringResource(Res.string.category_created)
    val categoryUpdatedMsg = stringResource(Res.string.category_updated)
    val categoryDeletedMsg = stringResource(Res.string.category_deleted)

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.delete_category_title)) },
            text = { Text(stringResource(Res.string.delete_category_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        actions.onDelete {
                            scope.launch {
                                snackbarHostState.showSnackbar(categoryDeletedMsg)
                            }
                            actions.onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    // Full preview bottom sheet
    if (showPreviewSheet && state.previewMedia.isNotEmpty()) {
        val previewSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        ModalBottomSheet(
            onDismissRequest = { showPreviewSheet = false },
            sheetState = previewSheetState,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxSize()
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.best_match),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                // Full grid using the same MediaGridView as search results
                val sheetDpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
                val sheetPinchState = rememberGridPinchZoomState(
                    cellsList = cellsList,
                    initialCellsIndex = lastCellIndexState,
                    gridState = rememberLazyGridState(cacheWindow = sheetDpCacheWindow)
                )
                GridPinchZoomLayout(state = sheetPinchState) {
                    val sheetMediaState = remember {
                        derivedStateOf { previewMediaState.value.copy(isLoading = false) }
                    }
                    MediaGridView(
                        mediaState = sheetMediaState,
                        metadataState = metadataState,
                        allowSelection = false,
                        showSearchBar = false,
                        enableStickyHeaders = false,
                        paddingValues = PaddingValues(bottom = 128.dp),
                        canScroll = true,
                        allowHeaders = false,

                        isScrolling = isScrolling,
                        emptyContent = {
                            EmptyMedia(title = stringResource(Res.string.no_matching_photos))
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        // Shared-element transitions cannot cross into a ModalBottomSheet's
                        // separate window/subcomposition; enabling them here crashes on scroll
                        // with "LookaheadDelegate has not been measured yet" (#1019).
                        allowSharedElements = false
                    )
                }
            }
        }
    }

    // Reference image picker sheet (reuses the search screen's image picker)
    if (showRefImagePicker) {
        ImageSearchPickerSheet(
            onMediaSelected = { media ->
                showRefImagePicker = false
                actions.onAddReferenceImage(media.id)
            },
            onDismiss = { showRefImagePicker = false }
        )
    }

    Scaffold(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffectScaled(
                    state = LocalHazeState.current,
                    style = LocalHazeBlurStyle.current
                ),
                title = {
                    Text(
                        text = if (state.isEditMode) stringResource(Res.string.edit_category)
                        else stringResource(Res.string.new_category),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    NavigationBackButton(forcedAction = actions.onNavigateBack)
                },
                actions = {
                    if (state.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(Res.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.isValid && !state.isSaving,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        keyboardController?.hide()
                        actions.onSave {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (state.isEditMode) categoryUpdatedMsg else categoryCreatedMsg
                                )
                            }
                            actions.onNavigateBack()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = if (state.isEditMode) stringResource(Res.string.save) else stringResource(Res.string.create),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 128.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ============ 1. Category Name ============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.category_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = state.categoryName,
                    onValueChange = actions.onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.category_name_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )
            }

            // ============ 2. Quick Start Suggestions (create mode only, when name is empty) ============
            if (!state.isEditMode && state.categoryName.isBlank() && state.searchTerms.isBlank()) {
                TemplateChipsSection(
                    onTemplateSelected = actions.onApplyTemplate
                )
            }

            // ============ 3. Description / Search Terms ============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.describe_content),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.describe_content_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.searchTerms,
                    onValueChange = actions.onSearchTermsChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.describe_content_example),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done
                    )
                )
            }

            // ============ 4. Reference Images (image-to-image) ============
            ReferenceImagesSection(
                referenceImageIds = state.referenceImageIds,
                allMedia = state.allMedia,
                onAddClick = { showRefImagePicker = true },
                onRemove = actions.onRemoveReferenceImage,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // ============ 5. Sensitivity Presets ============
            SensitivitySection(
                threshold = state.threshold,
                onThresholdChange = actions.onThresholdChange,
                showFineTune = showFineTune,
                onToggleFineTune = { showFineTune = !showFineTune },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // ============ 6. Inline Preview Grid ============
            InlinePreviewSection(
                previewMedia = state.previewMedia,
                previewCount = state.previewCount,
                isLoading = state.isLoading,
                searchTerms = state.searchTerms,
                onCardClick = {
                    if (state.previewCount > 0) showPreviewSheet = true
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ============ Template Chips ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateChipsSection(
    onTemplateSelected: (Category) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.quick_start),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(Res.string.quick_start_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Category.DEFAULT_CATEGORIES.forEach { template ->
                FilterChip(
                    selected = false,
                    onClick = { onTemplateSelected(template) },
                    label = {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        enabled = true,
                        selected = false
                    )
                )
            }
        }
    }
}

// ============ Sensitivity Section ============

private enum class SensitivityPreset(
    val threshold: Float,
    val labelRes: StringResource,
    val descRes: StringResource
) {
    BROAD(0.05f, Res.string.sensitivity_broad, Res.string.sensitivity_broad_desc),
    BALANCED(Category.DEFAULT_THRESHOLD, Res.string.sensitivity_balanced, Res.string.sensitivity_balanced_desc),
    STRICT(0.5f, Res.string.sensitivity_strict, Res.string.sensitivity_strict_desc),
    EXACT(0.75f, Res.string.sensitivity_exact, Res.string.sensitivity_exact_desc);

    companion object {
        fun fromThreshold(threshold: Float): SensitivityPreset? {
            return entries.find { kotlin.math.abs(it.threshold - threshold) < 0.01f }
        }
    }
}

@Composable
private fun SensitivitySection(
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    showFineTune: Boolean,
    onToggleFineTune: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedPreset = SensitivityPreset.fromThreshold(threshold)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.sensitivity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onToggleFineTune) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(Res.string.fine_tune),
                    tint = if (showFineTune) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Preset chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SensitivityPreset.entries.forEach { preset ->
                val isSelected = selectedPreset == preset
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                            else Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp)
                                )
                        )
                        .clickable { onThresholdChange(preset.threshold) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(preset.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(preset.descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                    )
                }
            }
        }

        // Fine-tune slider (advanced, toggled)
        AnimatedVisibility(visible = showFineTune) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.fine_tune),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(threshold * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = threshold,
                    onValueChange = onThresholdChange,
                    valueRange = Category.MIN_THRESHOLD..Category.MAX_THRESHOLD,
                    steps = 12,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.more_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(Res.string.more_accurate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============ Inline Preview Section ============

@Composable
private fun InlinePreviewSection(
    previewMedia: List<UriMedia>,
    previewCount: Int,
    isLoading: Boolean,
    searchTerms: String,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageRenderer = LocalMediaImageRenderer.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = previewCount > 0, onClick = onCardClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.matching_preview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            if (previewCount > 0 && !isLoading) {
                Text(
                    text = previewCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (searchTerms.isBlank()) {
            Text(
                text = stringResource(Res.string.describe_content_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (previewMedia.isEmpty() && !isLoading) {
            Text(
                text = stringResource(Res.string.no_matching_photos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (previewMedia.isNotEmpty()) {
            // Inline 3x2 thumbnail grid
            val displayMedia = previewMedia.take(6)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((displayMedia.size / 3 + if (displayMedia.size % 3 > 0) 1 else 0) * 120).dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                items(
                    items = displayMedia,
                    key = { it.id }
                ) { media ->
                    imageRenderer.RenderImage(
                        model = media.getUri(),
                        contentDescription = null,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        signature = media
                    )
                }
            }

            // "See all" indicator
            if (previewCount > 6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.see_all_matches, previewCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ============ Reference Images Section ============

@Composable
private fun ReferenceImagesSection(
    referenceImageIds: List<Long>,
    allMedia: List<UriMedia>,
    onAddClick: () -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageRenderer = LocalMediaImageRenderer.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.reference_images),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.reference_images_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilterChip(
                selected = false,
                onClick = onAddClick,
                label = {
                    Text(
                        text = if (referenceImageIds.isEmpty()) stringResource(Res.string.add_reference_photos)
                        else stringResource(Res.string.reference_images_count, referenceImageIds.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        if (referenceImageIds.isNotEmpty()) {
            val mediaMap = remember(allMedia) { allMedia.associateBy { it.id } }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                referenceImageIds.forEach { id ->
                    val media = mediaMap[id]
                    if (media != null) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            imageRenderer.RenderImage(
                                model = media.getUri(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                signature = media
                            )
                            // Remove badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .clickable { onRemove(id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(Res.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
