/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.uri.emptyUri
import com.dot.gallery.feature.ignored.generated.resources.Res
import com.dot.gallery.feature.ignored.generated.resources.action_cancel
import com.dot.gallery.feature.ignored.generated.resources.action_save
import com.dot.gallery.feature.ignored.generated.resources.album_items_count
import com.dot.gallery.feature.ignored.generated.resources.continue_string
import com.dot.gallery.feature.ignored.generated.resources.continue_with_count
import com.dot.gallery.feature.ignored.generated.resources.ignored_action_back
import com.dot.gallery.feature.ignored.generated.resources.ignored_album_already_ignored
import com.dot.gallery.feature.ignored.generated.resources.ignored_confirm_albums
import com.dot.gallery.feature.ignored.generated.resources.ignored_confirm_pattern
import com.dot.gallery.feature.ignored.generated.resources.ignored_example_dcim
import com.dot.gallery.feature.ignored.generated.resources.ignored_example_screenshots
import com.dot.gallery.feature.ignored.generated.resources.ignored_example_whatsapp
import com.dot.gallery.feature.ignored.generated.resources.ignored_examples_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_hide_from
import com.dot.gallery.feature.ignored.generated.resources.ignored_hide_from_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_kind_pattern
import com.dot.gallery.feature.ignored.generated.resources.ignored_location_albums
import com.dot.gallery.feature.ignored.generated.resources.ignored_location_timeline
import com.dot.gallery.feature.ignored.generated.resources.ignored_matches_title
import com.dot.gallery.feature.ignored.generated.resources.ignored_method_albums
import com.dot.gallery.feature.ignored.generated.resources.ignored_method_pattern
import com.dot.gallery.feature.ignored.generated.resources.ignored_name_label
import com.dot.gallery.feature.ignored.generated.resources.ignored_option_albums_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_option_both
import com.dot.gallery.feature.ignored.generated.resources.ignored_option_both_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_option_timeline_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_pattern_in_use
import com.dot.gallery.feature.ignored.generated.resources.ignored_pattern_invalid
import com.dot.gallery.feature.ignored.generated.resources.ignored_pattern_label
import com.dot.gallery.feature.ignored.generated.resources.ignored_pattern_matches
import com.dot.gallery.feature.ignored.generated.resources.ignored_pattern_no_matches
import com.dot.gallery.feature.ignored.generated.resources.ignored_rule_name
import com.dot.gallery.feature.ignored.generated.resources.ignored_search_albums
import com.dot.gallery.feature.ignored.generated.resources.ignored_search_empty
import com.dot.gallery.feature.ignored.generated.resources.ignored_selection_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_setup_choose_title
import com.dot.gallery.feature.ignored.generated.resources.ignored_setup_edit_albums_title
import com.dot.gallery.feature.ignored.generated.resources.ignored_setup_edit_where_title
import com.dot.gallery.feature.ignored.generated.resources.ignored_setup_step
import com.dot.gallery.feature.ignored.generated.resources.ignored_setup_where_title
import com.dot.gallery.feature.ignored.generated.resources.matched_more_count
import com.dot.gallery.feature.ignored.generated.resources.setup_type_regex_example_second_subtitle
import com.dot.gallery.feature.ignored.generated.resources.setup_type_regex_example_third_subtitle
import com.dot.gallery.feature.ignored.generated.resources.setup_type_regex_example_title
import com.dot.gallery.feature.ignored.generated.resources.setup_type_regex_first_subtitle
import com.dot.gallery.feature.ignored.generated.resources.unknown_album
import com.dot.gallery.feature.ignored.ui.IgnoredPreviewHost
import com.dot.gallery.feature.ignored.ui.components.AlbumTile
import com.dot.gallery.feature.ignored.ui.components.GroupedRow
import com.dot.gallery.feature.ignored.ui.components.IgnoredSectionLabel
import com.dot.gallery.feature.ignored.ui.components.MethodSegment
import com.dot.gallery.feature.ignored.ui.components.MethodToggle
import com.dot.gallery.feature.ignored.ui.components.OptionRow
import com.dot.gallery.feature.ignored.ui.components.RuleThumbnail
import com.dot.gallery.feature.ignored.ui.components.SheetButton
import com.dot.gallery.feature.ignored.ui.components.SheetButtonStyle
import com.dot.gallery.feature.ignored.ui.components.positionOf
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.RegularExpression
import com.dot.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val SETUP_STEP_COUNT = 2
private const val MAX_MATCH_CHIPS = 12

/**
 * Stateless sheet for creating or editing an ignore rule. Step one picks albums or types a path
 * pattern; step two chooses where the albums are hidden and names the rule. The app provides
 * [uiState] (driven by `IgnoredSetupViewModel`) and funnels [onAction] back to it; [onDismiss]
 * runs whenever the sheet closes so the caller can reset the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun IgnoredSetupSheet(
    sheetState: AppBottomSheetState,
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    BackHandler(sheetState.isVisible && uiState.canGoBack) {
        onAction(IgnoredSetupAction.NavigateBack)
    }

    if (sheetState.isVisible) {
        ModalBottomSheet(
            // The sheet fills the height, so keep its top edge below the status bar.
            modifier = Modifier.statusBarsPadding(),
            sheetState = sheetState.sheetState,
            onDismissRequest = close,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            IgnoredSetupSheetContent(
                uiState = uiState,
                onAction = { action ->
                    when (action) {
                        IgnoredSetupAction.Cancel -> close()
                        IgnoredSetupAction.Confirm -> {
                            onAction(action)
                            scope.launch { sheetState.hide() }
                        }
                        else -> onAction(action)
                    }
                }
            )
        }
    }
}

@Composable
fun IgnoredSetupSheetContent(
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.ScreenHorizontal)
    ) {
        AnimatedContent(
            targetState = uiState.currentStep,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "ignoredSetupStep"
        ) { step ->
            when (step) {
                SetupStep.CHOOSE -> ChooseStep(uiState, onAction)
                SetupStep.WHERE -> WhereStep(uiState, onAction)
            }
        }
        SetupActions(uiState, onAction)
    }
}

@Composable
private fun SetupHeader(stepNumber: Int?, title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.Small, end = Spacing.Small, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
    ) {
        if (stepNumber != null) {
            Text(
                text = stringResource(Res.string.ignored_setup_step, stepNumber, SETUP_STEP_COUNT),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChooseStep(uiState: IgnoredSetupUiState, onAction: (IgnoredSetupAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SetupHeader(
            stepNumber = if (uiState.isEditing) null else 1,
            title = stringResource(
                if (uiState.isEditing) Res.string.ignored_setup_edit_albums_title
                else Res.string.ignored_setup_choose_title
            )
        )
        MethodToggle {
            MethodSegment(
                icon = Icons.Outlined.FolderCopy,
                label = stringResource(Res.string.ignored_method_albums),
                selected = uiState.method == IgnoreMethod.ALBUMS,
                onClick = { onAction(IgnoredSetupAction.SetMethod(IgnoreMethod.ALBUMS)) }
            )
            MethodSegment(
                icon = GalleryIcons.RegularExpression,
                label = stringResource(Res.string.ignored_method_pattern),
                selected = uiState.method == IgnoreMethod.PATTERN,
                onClick = { onAction(IgnoredSetupAction.SetMethod(IgnoreMethod.PATTERN)) }
            )
        }
        Spacer(Modifier.height(12.dp))
        when (uiState.method) {
            IgnoreMethod.ALBUMS -> AlbumPicker(uiState, onAction)
            IgnoreMethod.PATTERN -> PatternEditor(uiState, onAction)
        }
    }
}

@Composable
private fun AlbumPicker(uiState: IgnoredSetupUiState, onAction: (IgnoredSetupAction) -> Unit) {
    // Text fields keep their own state so typing never waits on the ViewModel round trip.
    var query by remember { mutableStateOf(uiState.search) }
    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = {
                query = it
                onAction(IgnoredSetupAction.SetSearch(it))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(Res.string.ignored_search_albums)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
        )
        val albums = uiState.visibleAlbums
        if (albums.isEmpty() && query.isNotBlank()) {
            Text(
                text = stringResource(Res.string.ignored_search_empty, query.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.Large)
            )
        }
        val alreadyIgnored = stringResource(Res.string.ignored_album_already_ignored)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = Spacing.Medium, bottom = Spacing.Small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = albums, key = { it.key }) { album ->
                val disabled = uiState.isIgnoredElsewhere(album.id)
                AlbumTile(
                    album = album,
                    countLabel = if (disabled) alreadyIgnored
                    else stringResource(Res.string.album_items_count, album.count.toInt()),
                    selected = album.id in uiState.selectedAlbumIds,
                    disabled = disabled,
                    onClick = { onAction(IgnoredSetupAction.ToggleAlbum(album)) }
                )
            }
        }
    }
}

private data class PatternExample(val description: StringResource, val pattern: StringResource)

private val patternExamples = listOf(
    PatternExample(Res.string.ignored_example_screenshots, Res.string.setup_type_regex_first_subtitle),
    PatternExample(Res.string.ignored_example_whatsapp, Res.string.setup_type_regex_example_second_subtitle),
    PatternExample(Res.string.ignored_example_dcim, Res.string.setup_type_regex_example_third_subtitle),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternEditor(uiState: IgnoredSetupUiState, onAction: (IgnoredSetupAction) -> Unit) {
    var text by remember { mutableStateOf(uiState.regex) }
    val setText: (String) -> Unit = {
        text = it
        onAction(IgnoredSetupAction.SetRegex(it))
    }
    val error = uiState.patternError
    val matches = uiState.patternMatches
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.Small)
    ) {
        item(key = "field") {
            OutlinedTextField(
                value = text,
                onValueChange = setText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.ignored_pattern_label)) },
                placeholder = { Text(stringResource(Res.string.setup_type_regex_first_subtitle)) },
                singleLine = true,
                isError = error != null,
                shape = RoundedCornerShape(Spacing.Medium),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                supportingText = when {
                    error == PatternError.INVALID -> {
                        { Text(stringResource(Res.string.ignored_pattern_invalid)) }
                    }
                    error == PatternError.IN_USE -> {
                        { Text(stringResource(Res.string.ignored_pattern_in_use)) }
                    }
                    text.isBlank() -> null
                    matches.isEmpty() -> {
                        { Text(stringResource(Res.string.ignored_pattern_no_matches)) }
                    }
                    else -> {
                        {
                            Text(
                                text = pluralStringResource(Res.plurals.ignored_pattern_matches, matches.size, matches.size),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
        if (error == null && matches.isNotEmpty()) {
            item(key = "matches_label") {
                IgnoredSectionLabel(title = stringResource(Res.string.ignored_matches_title))
            }
            item(key = "matches") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    matches.take(MAX_MATCH_CHIPS).forEach { album -> MatchChip(album) }
                    if (matches.size > MAX_MATCH_CHIPS) {
                        Text(
                            text = stringResource(Res.string.matched_more_count, matches.size - MAX_MATCH_CHIPS),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = Spacing.Small)
                        )
                    }
                }
            }
        }
        item(key = "examples_label") {
            IgnoredSectionLabel(
                title = stringResource(Res.string.setup_type_regex_example_title),
                description = stringResource(Res.string.ignored_examples_summary)
            )
        }
        patternExamples.forEachIndexed { index, example ->
            item(key = "example_$index") {
                val pattern = stringResource(example.pattern)
                GroupedRow(
                    position = positionOf(index, patternExamples.size),
                    onClick = { setText(pattern) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = stringResource(example.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pattern,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchChip(album: Album) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = Spacing.ExtraSmall, top = Spacing.ExtraSmall, bottom = Spacing.ExtraSmall, end = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            LocalMediaImageRenderer.current.RenderImage(
                model = album.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                signature = album
            )
        }
        Text(
            text = album.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WhereStep(uiState: IgnoredSetupUiState, onAction: (IgnoredSetupAction) -> Unit) {
    var name by remember { mutableStateOf(uiState.name) }
    val options = listOf(
        Triple(IgnoredAlbum.ALBUMS_ONLY, Icons.Outlined.PhotoAlbum, Res.string.ignored_location_albums to Res.string.ignored_option_albums_summary),
        Triple(IgnoredAlbum.TIMELINE_ONLY, Icons.Outlined.PhotoLibrary, Res.string.ignored_location_timeline to Res.string.ignored_option_timeline_summary),
        Triple(IgnoredAlbum.ALBUMS_AND_TIMELINE, Icons.Outlined.Layers, Res.string.ignored_option_both to Res.string.ignored_option_both_summary),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.Small)
    ) {
        item(key = "header") {
            SetupHeader(
                stepNumber = if (uiState.isEditing) null else SETUP_STEP_COUNT,
                title = stringResource(
                    if (uiState.isEditing) Res.string.ignored_setup_edit_where_title
                    else Res.string.ignored_setup_where_title
                )
            )
        }
        item(key = "selection") { SelectionCard(uiState) }
        item(key = "where_label") {
            IgnoredSectionLabel(
                title = stringResource(Res.string.ignored_hide_from),
                description = stringResource(Res.string.ignored_hide_from_summary)
            )
        }
        options.forEachIndexed { index, (location, icon, texts) ->
            item(key = "where_$location") {
                OptionRow(
                    icon = icon,
                    title = stringResource(texts.first),
                    summary = stringResource(texts.second),
                    selected = uiState.location == location,
                    position = positionOf(index, options.size),
                    onClick = { onAction(IgnoredSetupAction.SetLocation(location)) },
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        item(key = "name_label") {
            IgnoredSectionLabel(title = stringResource(Res.string.ignored_rule_name))
        }
        item(key = "name") {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onAction(IgnoredSetupAction.SetName(it))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.ignored_name_label)) },
                placeholder = { Text(selectionTitle(uiState), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = RoundedCornerShape(Spacing.Medium)
            )
        }
    }
}

@Composable
private fun selectionTitle(uiState: IgnoredSetupUiState): String = when (uiState.method) {
    IgnoreMethod.PATTERN -> uiState.regex
    IgnoreMethod.ALBUMS -> uiState.selectedAlbums.joinToString { it.label }
        .ifEmpty { stringResource(Res.string.unknown_album) }
}

/** What the rule will hide, over the primary container. */
@Composable
private fun SelectionCard(uiState: IgnoredSetupUiState) {
    val isPattern = uiState.method == IgnoreMethod.PATTERN
    val albums = uiState.matchedAlbums
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = Spacing.Medium, top = Spacing.Medium, end = 20.dp, bottom = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        if (isPattern || albums.size < 2) {
            RuleThumbnail(album = albums.firstOrNull(), isPattern = isPattern, extraCount = 0, size = 64.dp, cornerRadius = 20.dp)
        } else {
            Box(modifier = Modifier.size(width = 86.dp, height = 64.dp)) {
                RuleThumbnail(album = albums[0], isPattern = false, extraCount = 0, size = 64.dp, cornerRadius = 20.dp)
                RuleThumbnail(
                    album = albums[1],
                    isPattern = false,
                    extraCount = albums.size - 2,
                    size = 64.dp,
                    cornerRadius = 20.dp,
                    modifier = Modifier.offset(x = 22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = selectionTitle(uiState),
                style = if (isPattern) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isPattern) {
                    pluralStringResource(Res.plurals.ignored_kind_pattern, albums.size, albums.size)
                } else {
                    val items = albums.sumOf { it.count }.toInt()
                    pluralStringResource(Res.plurals.ignored_selection_summary, albums.size, albums.size, items)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SetupActions(uiState: IgnoredSetupUiState, onAction: (IgnoredSetupAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.Small, bottom = Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.canGoBack) {
            SheetButton(
                text = stringResource(Res.string.ignored_action_back),
                onClick = { onAction(IgnoredSetupAction.NavigateBack) },
                style = SheetButtonStyle.Tonal,
                modifier = Modifier.weight(1f)
            )
        } else {
            SheetButton(
                text = stringResource(Res.string.action_cancel),
                onClick = { onAction(IgnoredSetupAction.Cancel) },
                style = SheetButtonStyle.Tonal,
                modifier = Modifier.weight(1f)
            )
        }
        val isLastStep = uiState.currentStep == SetupStep.WHERE
        val selectedCount = uiState.selectedAlbumIds.size
        val continueText = stringResource(Res.string.continue_string)
        SheetButton(
            text = when {
                !isLastStep && uiState.method == IgnoreMethod.ALBUMS && selectedCount > 0 ->
                    stringResource(Res.string.continue_with_count, continueText, selectedCount)
                !isLastStep -> continueText
                uiState.isEditing -> stringResource(Res.string.action_save)
                uiState.method == IgnoreMethod.PATTERN -> stringResource(Res.string.ignored_confirm_pattern)
                else -> pluralStringResource(Res.plurals.ignored_confirm_albums, selectedCount, selectedCount)
            },
            onClick = {
                onAction(if (isLastStep) IgnoredSetupAction.Confirm else IgnoredSetupAction.NavigateNext)
            },
            enabled = uiState.canContinue,
            modifier = Modifier.weight(1f)
        )
    }
}

// ========== Previews ==========

private val previewAlbums = listOf(
    Album(id = 1, label = "Camera", uri = emptyUri, pathToThumbnail = "/storage/emulated/0/DCIM/Camera/a.jpg", relativePath = "DCIM/Camera/", timestamp = 0, count = 3204),
    Album(id = 2, label = "Screenshots", uri = emptyUri, pathToThumbnail = "/storage/emulated/0/Pictures/Screenshots/a.png", relativePath = "Pictures/Screenshots/", timestamp = 0, count = 842),
    Album(id = 3, label = "Road trips", uri = emptyUri, pathToThumbnail = "/storage/emulated/0/Pictures/Road trips/a.jpg", relativePath = "Pictures/Road trips/", timestamp = 0, count = 518),
    Album(id = 4, label = "Telegram Images", uri = emptyUri, pathToThumbnail = "/storage/emulated/0/Telegram/Telegram Images/a.jpg", relativePath = "Telegram/Telegram Images/", timestamp = 0, count = 211),
)

@Preview
@Composable
private fun ChooseAlbumsPreview() {
    IgnoredPreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                albums = previewAlbums,
                selectedAlbumIds = listOf(3),
                ignoredAlbums = listOf(IgnoredAlbum(id = 2, location = IgnoredAlbum.ALBUMS_ONLY))
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun PathPatternPreview() {
    IgnoredPreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                albums = previewAlbums,
                method = IgnoreMethod.PATTERN,
                regex = ".*/Telegram/.*"
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun WherePreview() {
    IgnoredPreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.WHERE,
                albums = previewAlbums,
                selectedAlbumIds = listOf(1, 3)
            ),
            onAction = {}
        )
    }
}
