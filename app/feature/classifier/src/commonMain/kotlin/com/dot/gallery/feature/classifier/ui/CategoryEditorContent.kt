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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.MediaGridView
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.action_cancel
import com.dot.gallery.feature.classifier.generated.resources.action_delete
import com.dot.gallery.feature.classifier.generated.resources.add_reference_photos
import com.dot.gallery.feature.classifier.generated.resources.add_short
import com.dot.gallery.feature.classifier.generated.resources.best_match
import com.dot.gallery.feature.classifier.generated.resources.best_matches_summary
import com.dot.gallery.feature.classifier.generated.resources.category_name
import com.dot.gallery.feature.classifier.generated.resources.category_name_hint
import com.dot.gallery.feature.classifier.generated.resources.create
import com.dot.gallery.feature.classifier.generated.resources.describe_content
import com.dot.gallery.feature.classifier.generated.resources.describe_content_example
import com.dot.gallery.feature.classifier.generated.resources.edit_category
import com.dot.gallery.feature.classifier.generated.resources.examples_label
import com.dot.gallery.feature.classifier.generated.resources.examples_none
import com.dot.gallery.feature.classifier.generated.resources.examples_summary
import com.dot.gallery.feature.classifier.generated.resources.fine_tune
import com.dot.gallery.feature.classifier.generated.resources.fine_tune_summary
import com.dot.gallery.feature.classifier.generated.resources.matching_photos
import com.dot.gallery.feature.classifier.generated.resources.more_accurate
import com.dot.gallery.feature.classifier.generated.resources.more_results
import com.dot.gallery.feature.classifier.generated.resources.new_category
import com.dot.gallery.feature.classifier.generated.resources.no_matching_photos
import com.dot.gallery.feature.classifier.generated.resources.quick_start
import com.dot.gallery.feature.classifier.generated.resources.reference_images_count
import com.dot.gallery.feature.classifier.generated.resources.remove_reference_cd
import com.dot.gallery.feature.classifier.generated.resources.save
import com.dot.gallery.feature.classifier.generated.resources.see_all_matches
import com.dot.gallery.feature.classifier.generated.resources.sensitivity
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_custom
import com.dot.gallery.feature.classifier.generated.resources.wizard_back
import com.dot.gallery.feature.classifier.generated.resources.wizard_describe_title
import com.dot.gallery.feature.classifier.generated.resources.wizard_early_matches
import com.dot.gallery.feature.classifier.generated.resources.wizard_early_matches_hint
import com.dot.gallery.feature.classifier.generated.resources.wizard_examples_body
import com.dot.gallery.feature.classifier.generated.resources.wizard_examples_tip_body
import com.dot.gallery.feature.classifier.generated.resources.wizard_examples_tip_title
import com.dot.gallery.feature.classifier.generated.resources.wizard_examples_title
import com.dot.gallery.feature.classifier.generated.resources.wizard_next
import com.dot.gallery.feature.classifier.generated.resources.wizard_quick_start_body
import com.dot.gallery.feature.classifier.generated.resources.wizard_step
import com.dot.gallery.feature.classifier.generated.resources.wizard_step_describe
import com.dot.gallery.feature.classifier.generated.resources.wizard_step_examples
import com.dot.gallery.feature.classifier.generated.resources.wizard_step_strictness
import com.dot.gallery.feature.classifier.generated.resources.wizard_strictness_title
import com.dot.gallery.feature.classifier.ui.components.CategoryCard
import com.dot.gallery.feature.classifier.ui.components.CategoryListRow
import com.dot.gallery.feature.classifier.ui.components.DashedAddTile
import com.dot.gallery.feature.classifier.ui.components.SensitivityPreset
import com.dot.gallery.feature.classifier.ui.components.StrictnessSelector
import com.dot.gallery.feature.classifier.ui.components.WizardStepBar
import com.dot.gallery.feature.classifier.ui.search.ImageSearchPickerSheet
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

private const val WIZARD_STEPS = 3

private val stepNames: List<StringResource> = listOf(
    Res.string.wizard_step_describe,
    Res.string.wizard_step_examples,
    Res.string.wizard_step_strictness,
)
private val stepTitles: List<StringResource> = listOf(
    Res.string.wizard_describe_title,
    Res.string.wizard_examples_title,
    Res.string.wizard_strictness_title,
)

/**
 * Category editor. Create mode is a three-step wizard (describe, example photos, strictness);
 * edit mode shows the same sections on one scrolling page with Delete in the top bar. The live
 * preview, the reference-photo picker and the all-matches sheet are shared by both.
 *
 * @param lastCellIndex persisted media-grid cell index for the all-matches sheet.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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

    LaunchedEffect(state.categoryId) {
        state.categoryId?.takeIf { it > 0 }?.let(actions.onLoadCategory)
    }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRefImagePicker by remember { mutableStateOf(false) }
    var showPreviewSheet by remember { mutableStateOf(false) }

    val wizard = !state.isEditMode
    val goBack: () -> Unit = {
        if (wizard && step > 0) step-- else actions.onNavigateBack()
    }
    BackHandler(enabled = wizard && step > 0) { step-- }

    val save: () -> Unit = {
        keyboardController?.hide()
        actions.onSave { actions.onNavigateBack() }
    }
    val canAdvance = when {
        !wizard -> state.isValid && !state.isSaving
        // A description or example photos is enough, so the name alone opens step 2.
        step == 0 -> state.categoryName.isNotBlank()
        step == 1 -> true
        else -> state.isValid && !state.isSaving
    }

    val layoutDirection = LocalLayoutDirection.current
    val hazeState = LocalHazeState.current
    val scrollState = rememberScrollState()
    val scrolled = rememberScrolledUnderTopBar(scrollState)
    Scaffold(
        modifier = Modifier.padding(
            start = paddingValues.calculateStartPadding(layoutDirection),
            end = paddingValues.calculateEndPadding(layoutDirection),
        ),
        topBar = {
            Column {
                GalleryPageTopBar(
                    title = stringResource(if (wizard) Res.string.new_category else Res.string.edit_category),
                    onBack = goBack,
                    hazeState = hazeState,
                    scrolled = scrolled,
                    actions = if (wizard) null else {
                        {
                            GalleryPageTopBarAction(
                                icon = Icons.Outlined.Delete,
                                contentDescription = stringResource(Res.string.action_delete),
                                onClick = { showDeleteDialog = true },
                            )
                        }
                    },
                )
                if (wizard) {
                    WizardStepBar(
                        step = step + 1,
                        total = WIZARD_STEPS,
                        modifier = Modifier
                            .then(if (scrolled) Modifier else Modifier.background(MaterialTheme.colorScheme.surface))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = goBack, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(if (wizard && step > 0) Res.string.wizard_back else Res.string.action_cancel))
                }
                val last = !wizard || step == WIZARD_STEPS - 1
                Button(
                    onClick = { if (last) save() else { keyboardController?.hide(); step++ } },
                    enabled = canAdvance,
                    modifier = Modifier.height(48.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (wizard && last) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    if (state.isSaving || (wizard && last)) Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            when {
                                !wizard -> Res.string.save
                                last -> Res.string.create
                                else -> Res.string.wizard_next
                            }
                        )
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 632.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (wizard) {
                WizardHeader(step = step)
                when (step) {
                    0 -> {
                        DescribeFields(state = state, actions = actions)
                        if (state.categoryName.isBlank() && state.searchTerms.isBlank()) {
                            QuickStart(onTemplateSelected = actions.onApplyTemplate)
                        } else if (state.searchTerms.isNotBlank()) {
                            EarlyMatchesCard(state = state)
                        }
                    }
                    1 -> {
                        Text(
                            text = stringResource(Res.string.wizard_examples_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ExamplePhotos(
                            state = state,
                            onAdd = { showRefImagePicker = true },
                            onRemove = actions.onRemoveReferenceImage,
                            tileSize = null,
                        )
                        CategoryListRow(
                            icon = Icons.Outlined.CenterFocusWeak,
                            title = stringResource(Res.string.wizard_examples_tip_title),
                            summary = stringResource(Res.string.wizard_examples_tip_body),
                            shape = RoundedCornerShape(24.dp),
                        )
                    }
                    else -> StrictnessSection(state = state, actions = actions, onSeeAll = { showPreviewSheet = true })
                }
            } else {
                DescribeFields(state = state, actions = actions)
                SectionLabel(
                    title = stringResource(Res.string.examples_label),
                    description = if (state.referenceImageIds.isEmpty()) stringResource(Res.string.examples_none)
                    else stringResource(Res.string.examples_summary, state.referenceImageIds.size),
                )
                ExamplePhotos(
                    state = state,
                    onAdd = { showRefImagePicker = true },
                    onRemove = actions.onRemoveReferenceImage,
                    tileSize = 84,
                )
                SectionLabel(title = stringResource(Res.string.sensitivity))
                StrictnessSection(state = state, actions = actions, onSeeAll = { showPreviewSheet = true })
            }
        }
    }

    if (showDeleteDialog) {
        DeleteCategoryDialog(
            name = state.categoryName,
            onConfirm = {
                showDeleteDialog = false
                actions.onDelete { actions.onNavigateBack() }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showRefImagePicker) {
        ImageSearchPickerSheet(
            onMediaSelected = { media ->
                showRefImagePicker = false
                actions.onAddReferenceImage(media.id)
            },
            onDismiss = { showRefImagePicker = false }
        )
    }

    if (showPreviewSheet && state.previewMedia.isNotEmpty()) {
        AllMatchesSheet(
            previewCount = state.previewCount,
            previewMediaState = previewMediaState,
            metadataState = metadataState,
            isScrolling = isScrolling,
            lastCellIndex = lastCellIndex,
            onDismiss = { showPreviewSheet = false },
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
        )
    }
}

@Composable
private fun WizardHeader(step: Int) {
    Column(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.wizard_step, step + 1, WIZARD_STEPS, stringResource(stepNames[step])),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(stepTitles[step]),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionLabel(title: String, description: String? = null) {
    Column(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (description != null) {
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    singleLine: Boolean,
    capitalization: KeyboardCapitalization,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 4,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
    )
}

@Composable
private fun DescribeFields(state: CategoryEditorState, actions: CategoryEditorActions) {
    EditorTextField(
        value = state.categoryName,
        onValueChange = actions.onNameChange,
        label = stringResource(Res.string.category_name),
        placeholder = stringResource(Res.string.category_name_hint),
        singleLine = true,
        capitalization = KeyboardCapitalization.Words,
        imeAction = ImeAction.Next,
    )
    EditorTextField(
        value = state.searchTerms,
        onValueChange = actions.onSearchTermsChange,
        label = stringResource(Res.string.describe_content),
        placeholder = stringResource(Res.string.describe_content_example),
        singleLine = false,
        capitalization = KeyboardCapitalization.None,
        imeAction = ImeAction.Done,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickStart(onTemplateSelected: (Category) -> Unit) {
    SectionLabel(
        title = stringResource(Res.string.quick_start),
        description = stringResource(Res.string.wizard_quick_start_body),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Category.DEFAULT_CATEGORIES.forEach { template ->
            GalleryFilterChip(label = template.name, selected = false, onClick = { onTemplateSelected(template) })
        }
    }
}

@Composable
private fun EarlyMatchesCard(state: CategoryEditorState) {
    CategoryCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (state.previewCount > 0) stringResource(Res.string.wizard_early_matches, state.previewCount)
                else stringResource(Res.string.no_matching_photos),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        if (state.previewMedia.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.previewMedia.take(5).forEach { media ->
                    Thumbnail(media = media, modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                }
                repeat((5 - state.previewMedia.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            text = stringResource(Res.string.wizard_early_matches_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Thumbnail(media: UriMedia, modifier: Modifier) {
    LocalMediaImageRenderer.current.RenderImage(
        modifier = modifier,
        model = media.getUri(),
        contentScale = ContentScale.Crop,
        contentDescription = null,
        signature = media,
    )
}

/**
 * Reference photos with a remove button each, followed by the dashed Add tile. [tileSize] null
 * lays them out three to a row across the full width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamplePhotos(
    state: CategoryEditorState,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    tileSize: Int?,
) {
    val mediaMap = remember(state.allMedia) { state.allMedia.associateBy { it.id } }
    val references = state.referenceImageIds.mapNotNull { id -> mediaMap[id]?.let { id to it } }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (tileSize == null) 3 else Int.MAX_VALUE,
    ) {
        val tileModifier = if (tileSize == null) Modifier.weight(1f).aspectRatio(1f) else Modifier.size(tileSize.dp)
        references.forEach { (id, media) ->
            Box(modifier = tileModifier.clip(RoundedCornerShape(20.dp))) {
                Thumbnail(media = media, modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = { onRemove(id) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(Res.string.remove_reference_cd),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        DashedAddTile(
            label = stringResource(if (tileSize == null) Res.string.add_reference_photos else Res.string.add_short),
            onClick = onAdd,
            modifier = tileModifier,
            cornerRadius = 20.dp,
        )
        if (tileSize == null) {
            val filled = (references.size + 1) % 3
            if (filled != 0) repeat(3 - filled) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (tileSize == null && references.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.reference_images_count, references.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun StrictnessSection(
    state: CategoryEditorState,
    actions: CategoryEditorActions,
    onSeeAll: () -> Unit,
) {
    val preset = SensitivityPreset.fromThreshold(state.threshold)
    val percent = (state.threshold * 100).roundToInt()
    var showFineTune by rememberSaveable { mutableStateOf(preset == null) }

    StrictnessSelector(selected = preset, onSelect = { actions.onThresholdChange(it.threshold) })
    Text(
        text = if (preset != null) stringResource(preset.descRes) else stringResource(Res.string.sensitivity_custom, percent),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    FineTuneCard(
        expanded = showFineTune,
        threshold = state.threshold,
        onToggle = { showFineTune = !showFineTune },
        onThresholdChange = actions.onThresholdChange,
    )
    PreviewCard(state = state, onSeeAll = onSeeAll)
}

@Composable
private fun FineTuneCard(
    expanded: Boolean,
    threshold: Float,
    onToggle: () -> Unit,
    onThresholdChange: (Float) -> Unit,
) {
    val percent = (threshold * 100).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.fine_tune), style = MaterialTheme.typography.titleSmall)
                if (!expanded) {
                    Text(
                        text = stringResource(Res.string.fine_tune_summary, percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                Text("$percent%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Slider(
                    value = threshold,
                    onValueChange = onThresholdChange,
                    valueRange = Category.MIN_THRESHOLD..Category.MAX_THRESHOLD,
                    steps = 12,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Res.string.more_results), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(Res.string.more_accurate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(state: CategoryEditorState, onSeeAll: () -> Unit) {
    CategoryCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.matching_photos),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else if (state.previewCount > 0) {
                Text(
                    text = state.previewCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (state.previewMedia.isEmpty()) {
            if (!state.isLoading) {
                Text(
                    text = stringResource(Res.string.no_matching_photos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            PreviewGrid(media = state.previewMedia.take(6))
            if (state.previewCount > 0) {
                TextButton(onClick = onSeeAll) {
                    Text(stringResource(Res.string.see_all_matches, state.previewCount))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PreviewGrid(media: List<UriMedia>) {
    media.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { item ->
                Thumbnail(media = item, modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AllMatchesSheet(
    previewCount: Int,
    previewMediaState: State<MediaState<UriMedia>>,
    metadataState: State<MediaMetadataState>,
    isScrolling: MutableState<Boolean>,
    lastCellIndex: MutableState<Int>,
    onDismiss: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.statusBarsPadding().fillMaxSize()) {
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(Res.string.best_match), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(Res.string.best_matches_summary, previewCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GalleryPageTopBarAction(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.clip(CircleShape),
                )
            }
            var lastCellIndexState by lastCellIndex
            val pinchState = rememberGridPinchZoomState(
                cellsList = cellsList,
                initialCellsIndex = lastCellIndexState,
                gridState = rememberLazyGridState(cacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)),
            )
            GridPinchZoomLayout(state = pinchState) {
                val sheetMediaState = remember { derivedStateOf { previewMediaState.value.copy(isLoading = false) } }
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
                    emptyContent = { EmptyMedia(title = stringResource(Res.string.no_matching_photos)) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    // Shared-element transitions cannot cross into a ModalBottomSheet's
                    // separate window/subcomposition; enabling them here crashes on scroll
                    // with "LookaheadDelegate has not been measured yet".
                    allowSharedElements = false
                )
            }
        }
    }
}
