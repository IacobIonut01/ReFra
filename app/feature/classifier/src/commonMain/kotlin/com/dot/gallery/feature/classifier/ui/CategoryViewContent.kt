/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.MainSearchBar
import com.dot.gallery.core.presentation.components.MediaScreen
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.action_delete
import com.dot.gallery.feature.classifier.generated.resources.category_add_examples
import com.dot.gallery.feature.classifier.generated.resources.category_change_cover
import com.dot.gallery.feature.classifier.generated.resources.category_how_it_matches_summary
import com.dot.gallery.feature.classifier.generated.resources.category_loosen
import com.dot.gallery.feature.classifier.generated.resources.category_media_count
import com.dot.gallery.feature.classifier.generated.resources.category_no_matches_body
import com.dot.gallery.feature.classifier.generated.resources.category_no_matches_title
import com.dot.gallery.feature.classifier.generated.resources.category_pin
import com.dot.gallery.feature.classifier.generated.resources.category_stats_range
import com.dot.gallery.feature.classifier.generated.resources.category_stats_single
import com.dot.gallery.feature.classifier.generated.resources.category_still_sorting
import com.dot.gallery.feature.classifier.generated.resources.category_still_sorting_note
import com.dot.gallery.feature.classifier.generated.resources.category_tile_no_matches
import com.dot.gallery.feature.classifier.generated.resources.category_tune
import com.dot.gallery.feature.classifier.generated.resources.category_unpin
import com.dot.gallery.feature.classifier.generated.resources.categories_sorting_preparing
import com.dot.gallery.feature.classifier.generated.resources.edit_category
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_custom
import com.dot.gallery.feature.classifier.ui.components.CategoryActionChip
import com.dot.gallery.feature.classifier.ui.components.CategoryCover
import com.dot.gallery.feature.classifier.ui.components.CategoryCoverPickerSheet
import com.dot.gallery.feature.classifier.ui.components.CategoryProgressCard
import com.dot.gallery.feature.classifier.ui.components.SensitivityPreset
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource


/**
 * Media grid for a single category — supports both the legacy string route
 * ([CategoryViewState.legacyCategory] non-null) and the id-based route
 * ([CategoryViewState.categoryId] > 0). The id route opens with a Person-style header: cover,
 * name, stats, action chips and a "how it matches" card (or scan progress while sorting).
 *
 * @param mediaState resolved per route (`mediaByCategory` / `mediaByCategoryId`)
 * @param mediaViewerRoute route prefix for the viewer — only used when
 * [CategoryViewActions.onOpenMedia] does not handle the click (it always does).
 * @param selectionSheetContent platform selection overlay slot.
 * @param lastCellIndex / [lastMosaicCellIndex] persisted grid cell indices
 * (platform store — app-side).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Media> CategoryViewContent(
    state: CategoryViewState,
    actions: CategoryViewActions,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    mediaViewerRoute: String,
    isScrolling: MutableState<Boolean>,
    lastCellIndex: MutableState<Int>,
    lastMosaicCellIndex: MutableState<Int>,
    selectionSheetContent: @Composable BoxScope.(
        allMedia: MediaState<T>,
        selectedMediaList: SnapshotStateList<T>,
    ) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    LaunchedEffect(state.legacyCategory, state.categoryId) {
        if (state.isLegacy) {
            if (state.legacyCategory.isNullOrEmpty()) actions.onInvalidCategory()
        } else {
            if (state.categoryId <= 0) actions.onInvalidCategory()
            else actions.onLoadCategory(state.categoryId)
        }
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }
    val category = state.category
    val count = mediaState.value.media.size

    MediaScreen(
        albumName = state.displayName,
        mediaState = mediaState,
        metadataState = metadataState,
        target = state.target,
        mediaViewerRoute = mediaViewerRoute,
        isScrolling = isScrolling,
        // The id route opens with the category header, which carries the name.
        detailHeader = !state.isLegacy && category != null,
        aboveGridContent = if (state.isLegacy || category == null) null else {
            {
                CategoryHeader(
                    state = state,
                    category = category,
                    photoCount = count,
                    actions = actions,
                    onDelete = { showDeleteDialog = true },
                    onChangeCover = { showCoverPicker = true },
                )
            }
        },
        emptyContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!state.isLegacy && category != null) {
                    CategoryHeader(
                        state = state,
                        category = category,
                        photoCount = 0,
                        actions = actions,
                        onDelete = { showDeleteDialog = true },
                        onChangeCover = { showCoverPicker = true },
                    )
                    if (!state.isScanning) NoMatches(category = category, actions = actions)
                } else {
                    Text(
                        text = stringResource(Res.string.category_tile_no_matches),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
        },
        selectionSheetContent = selectionSheetContent,
        customViewingNavigation = { media ->
            actions.onOpenMedia(media.id)
        },
        lastCellIndex = lastCellIndex,
        lastMosaicCellIndex = lastMosaicCellIndex,
        searchBar = { scrolling, menuItems ->
            MainSearchBar(
                isScrolling = scrolling,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                onSearchClick = actions.onSearchClick,
                menuItems = menuItems,
            )
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope
    )

    if (showCoverPicker && category != null) {
        CategoryCoverPickerSheet(
            media = mediaState.value.media,
            selectedId = category.coverMediaId,
            onPick = { mediaId ->
                actions.onSetCover(category.id, mediaId)
                showCoverPicker = false
            },
            onUseAutomatic = {
                actions.onSetCover(category.id, null)
                showCoverPicker = false
            },
            onDismiss = { showCoverPicker = false },
        )
    }

    if (showDeleteDialog && category != null) {
        DeleteCategoryDialog(
            name = category.name,
            onConfirm = {
                showDeleteDialog = false
                actions.onDeleteCategory(category.id)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun CategoryHeader(
    state: CategoryViewState,
    category: Category,
    photoCount: Int,
    actions: CategoryViewActions,
    onDelete: () -> Unit,
    onChangeCover: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryCover(
            media = state.cover,
            contentDescription = category.name,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(40.dp))
                .then(if (photoCount > 0) Modifier.clickable(onClick = onChangeCover) else Modifier),
            iconSize = 48.dp,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { actions.onEditCategory(category.id) }
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(Res.string.edit_category),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        val countText = if (photoCount > 0) stringResource(Res.string.category_media_count, photoCount)
        else stringResource(Res.string.category_tile_no_matches)
        val range = state.dateRange
        Text(
            text = when {
                range == null || photoCount == 0 -> countText
                range.first == range.second -> stringResource(Res.string.category_stats_single, countText, range.first)
                else -> stringResource(Res.string.category_stats_range, countText, range.first, range.second)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategoryActionChip(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(Res.string.edit_category),
                    onClick = { actions.onEditCategory(category.id) },
                )
            }
            item {
                CategoryActionChip(
                    icon = Icons.Outlined.PushPin,
                    label = stringResource(if (category.isPinned) Res.string.category_unpin else Res.string.category_pin),
                    onClick = { actions.onTogglePin(category.id, !category.isPinned) },
                )
            }
            if (photoCount > 0) {
                item {
                    CategoryActionChip(
                        icon = Icons.Outlined.Image,
                        label = stringResource(Res.string.category_change_cover),
                        onClick = onChangeCover,
                    )
                }
            }
            item {
                CategoryActionChip(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(Res.string.action_delete),
                    onClick = onDelete,
                    destructive = true,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.isScanning) {
            CategoryProgressCard(
                icon = Icons.Outlined.Sync,
                title = stringResource(Res.string.category_still_sorting),
                summary = state.scanProgress?.let { "${(it * 100).roundToInt()}%" }
                    ?: stringResource(Res.string.categories_sorting_preparing),
                progress = state.scanProgress,
                note = stringResource(Res.string.category_still_sorting_note),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            HowItMatchesCard(category = category, onTune = { actions.onEditCategory(category.id) })
        }
    }
}

@Composable
private fun HowItMatchesCard(category: Category, onTune: () -> Unit) {
    val preset = SensitivityPreset.fromThreshold(category.threshold)
    val presetLabel = if (preset != null) stringResource(preset.labelRes)
    else stringResource(Res.string.sensitivity_custom, (category.threshold * 100).roundToInt())
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (category.searchTerms.isNotBlank()) {
                Text(
                    text = "“${category.searchTerms}”",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(Res.string.category_how_it_matches_summary, category.referenceImageIds.size, presetLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onTune) { Text(stringResource(Res.string.category_tune)) }
    }
}

@Composable
private fun NoMatches(category: Category, actions: CategoryViewActions) {
    val looser = SensitivityPreset.fromThreshold(category.threshold)?.looser
        ?: SensitivityPreset.BROAD.takeIf { category.threshold > it.threshold + 0.01f }
    Column(
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.category_no_matches_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.category_no_matches_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        if (looser != null) {
            FilledTonalButton(
                onClick = { actions.onChangeThreshold(category.id, looser.threshold) },
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.category_loosen))
            }
        }
        TextButton(onClick = { actions.onEditCategory(category.id) }) {
            Text(stringResource(Res.string.category_add_examples))
        }
    }
}
