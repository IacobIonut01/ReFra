/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.app.presentation.search.MainSearchBar
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.feature.editor.adjustments.Crop
import com.dot.gallery.core.presentation.util.remove
import com.dot.gallery.cloud.ui.people.PersonSelectionSheet
import com.dot.gallery.core.presentation.components.MediaScreen
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.editor.generated.resources.Res as ResFeatureEditor
import com.dot.gallery.feature.editor.generated.resources.type_blur
import com.dot.gallery.feature.editor.generated.resources.type_mosaic
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.action_save
import com.dot.gallery.generated.resources.cancel
import com.dot.gallery.generated.resources.cloud_people_unknown
import com.dot.gallery.generated.resources.cloud_person_add_birthday
import com.dot.gallery.generated.resources.cloud_person_add_name
import com.dot.gallery.generated.resources.cloud_person_blur_everywhere
import com.dot.gallery.generated.resources.cloud_person_blur_everywhere_summary
import com.dot.gallery.generated.resources.cloud_person_blurring_progress
import com.dot.gallery.generated.resources.cloud_person_detail_title
import com.dot.gallery.generated.resources.cloud_person_edit_name
import com.dot.gallery.generated.resources.cloud_person_hide
import com.dot.gallery.generated.resources.cloud_person_merge
import com.dot.gallery.generated.resources.cloud_person_name_hint
import com.dot.gallery.generated.resources.cloud_person_photo_count
import com.dot.gallery.generated.resources.cloud_person_remove_media_confirm
import com.dot.gallery.generated.resources.cloud_person_set_cover
import com.dot.gallery.generated.resources.people_merge_into_confirm
import com.dot.gallery.generated.resources.person_delete
import com.dot.gallery.generated.resources.person_delete_confirm
import com.dot.gallery.generated.resources.person_delete_summary
import com.dot.gallery.generated.resources.person_delete_title
import com.dot.gallery.generated.resources.person_face_remove
import com.dot.gallery.generated.resources.person_face_remove_confirm
import com.dot.gallery.generated.resources.person_faces_hint
import com.dot.gallery.generated.resources.person_faces_section
import com.dot.gallery.generated.resources.person_hidden_banner
import com.dot.gallery.generated.resources.person_hidden_banner_action
import com.dot.gallery.generated.resources.person_similar_hint
import com.dot.gallery.generated.resources.person_similar_people
import com.dot.gallery.generated.resources.person_stats_range
import com.dot.gallery.generated.resources.person_stats_single
import com.dot.gallery.generated.resources.person_unhide


@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun PersonDetailScreen(
    metadataState: State<MediaMetadataState>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val viewModel = injectViewModel<PersonDetailViewModel> {personDetailViewModel(it)}
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaState = viewModel.mediaState.collectAsStateWithLifecycle()
    val blurProgress by viewModel.blurProgress.collectAsStateWithLifecycle()
    val mergeCandidates by viewModel.mergeCandidates.collectAsStateWithLifecycle()
    val personMedia by viewModel.personMedia.collectAsStateWithLifecycle()
    val personFaces by viewModel.personFaces.collectAsStateWithLifecycle()
    val similarPeople by viewModel.similarPeople.collectAsStateWithLifecycle()
    var showRenameSheet by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }
    var showBirthdayPicker by remember { mutableStateOf(false) }
    var showBlurDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showCoverDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val personName = state.person?.name?.ifBlank {
        stringResource(Res.string.cloud_people_unknown)
    } ?: stringResource(Res.string.cloud_person_detail_title)

    val eventHandler = LocalEventHandler.current

    MediaScreen(
        albumName = personName,
        customDateHeader = stringResource(Res.string.cloud_person_photo_count, mediaState.value.media.size),
        mediaState = mediaState,
        metadataState = metadataState,
        target = "person_${state.person?.id}",
        customViewingNavigation = state.person?.let { person ->
            { media ->
                eventHandler.navigate(
                    Screen.MediaViewScreen.idAndPerson(
                        id = media.id,
                        configId = person.serverConfigId,
                        personId = person.id
                    )
                )
            }
        },
        mediaViewerRoute = Screen.MediaViewScreen.route,
        navActionsContent = { },
        selectionSheetContent = { allMedia, selectedMediaList ->
            if (viewModel.isLocalPerson) {
                val selector = LocalMediaSelector.current
                PersonSelectionSheet(
                    allMedia = allMedia,
                    personName = personName,
                    onRemoveFromPerson = { ids ->
                        selector.clearSelection()
                        viewModel.removeMediaFromPerson(ids) { eventHandler.navigateUp() }
                    }
                )
            } else {
                SelectionSheet(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    allMedia = allMedia,
                    selectedMedia = selectedMediaList
                )
            }
        },
        aboveGridContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PersonHeaderContent(
                    state = state,
                    isLocalPerson = viewModel.isLocalPerson,
                    photoCount = personMedia.size,
                    dateRange = remember(personMedia) { personDateRange(personMedia) },
                    blurProgress = blurProgress,
                    onRenameClick = {
                        editNameText = state.person?.name ?: ""
                        showRenameSheet = true
                    },
                    onBirthdayClick = { showBirthdayPicker = true },
                    onHideClick = { viewModel.hidePerson { eventHandler.navigateUp() } },
                    onUnhideClick = { viewModel.unhidePerson() },
                    onDeleteClick = { showDeleteDialog = true },
                    onBlurEverywhereClick = { showBlurDialog = true },
                    canMerge = mergeCandidates.isNotEmpty(),
                    onMergeClick = { showMergeDialog = true },
                    onSetCoverClick = { showCoverDialog = true }
                )
                if (viewModel.isLocalPerson) {
                    PersonFaceStrip(
                        faces = personFaces,
                        onRemoveFace = { face ->
                            viewModel.removeFace(face.faceId) { eventHandler.navigateUp() }
                        }
                    )
                    SimilarPeopleRow(
                        people = similarPeople,
                        currentName = personName,
                        onMerge = { viewModel.mergeSimilarIntoCurrent(it.id) }
                    )
                }
            }
        },
        lastCellIndex = rememberGridSize(),
        lastMosaicCellIndex = rememberMosaicGridSize(),
        searchBar = { scrolling, menuItems ->
            MainSearchBar(
                isScrolling = scrolling,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                menuItems = menuItems,
            )
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )

    // Rename bottom sheet with IME padding
    if (showRenameSheet) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        val focusRequester = remember { FocusRequester() }

        ModalBottomSheet(
            onDismissRequest = { showRenameSheet = false },
            sheetState = sheetState,
            modifier = Modifier.imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.cloud_person_edit_name),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    placeholder = { Text(stringResource(Res.string.cloud_person_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                SetupButton(
                    text = stringResource(Res.string.action_save),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    onClick = {
                        viewModel.updateName(editNameText)
                        showRenameSheet = false
                    }
                )
            }
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }

    // Birthday date picker dialog
    if (showBirthdayPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.person?.birthDate?.let(::pickerMillisFromBirthDate)
        )
        DatePickerDialog(
            onDismissRequest = { showBirthdayPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val formatted = birthDateFromPickerMillis(millis)
                        viewModel.updateBirthDate(formatted)
                    }
                    showBirthdayPicker = false
                }) {
                    Text(stringResource(Res.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthdayPicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Choose blur vs mosaic for the "blur everywhere" batch.
    if (showBlurDialog) {
        ModalBottomSheet(onDismissRequest = { showBlurDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.cloud_person_blur_everywhere),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(Res.string.cloud_person_blur_everywhere_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                SetupButton(
                    text = stringResource(ResFeatureEditor.string.type_blur),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    onClick = {
                        viewModel.blurEverywhere(useMosaic = false)
                        showBlurDialog = false
                    }
                )
                SetupButton(
                    text = stringResource(ResFeatureEditor.string.type_mosaic),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    onClick = {
                        viewModel.blurEverywhere(useMosaic = true)
                        showBlurDialog = false
                    }
                )
            }
        }
    }

    // Merge into another on-device person.
    if (showMergeDialog) {
        ModalBottomSheet(onDismissRequest = { showMergeDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.cloud_person_merge),
                    style = MaterialTheme.typography.titleLarge
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mergeCandidates, key = { it.accountKey }) { candidate ->
                        Column(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    viewModel.mergeInto(candidate.id) { eventHandler.navigateUp() }
                                    showMergeDialog = false
                                }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val candidateThumb = candidate.thumbnailUrl
                            if (candidateThumb != null) {
                                AsyncImage(
                                    request = ComposableImageRequest(candidateThumb),
                                    contentDescription = candidate.name,
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Person, null,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = candidate.name.ifBlank {
                                    stringResource(Res.string.cloud_people_unknown)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete the whole person group — photos are kept; faces are suppressed so
    // re-grouping can't resurrect it (#1262).
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(Res.string.person_delete_title, personName)) },
            text = { Text(stringResource(Res.string.person_delete_summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePerson { eventHandler.navigateUp() }
                    }
                ) {
                    Text(
                        text = stringResource(Res.string.person_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    // Choose a new cover — detected face crops make tighter covers than full photos.
    if (showCoverDialog) {
        ModalBottomSheet(onDismissRequest = { showCoverDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.cloud_person_set_cover),
                    style = MaterialTheme.typography.titleLarge
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (personFaces.isNotEmpty()) {
                        items(personFaces, key = { it.faceId }) { face ->
                            AsyncImage(
                                request = ComposableImageRequest(face.imageUri),
                                contentDescription = null,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        viewModel.setCoverFace(face)
                                        showCoverDialog = false
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        items(personMedia, key = { it.id }) { media ->
                            AsyncImage(
                                request = ComposableImageRequest(media.getUri().toString()),
                                contentDescription = null,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        viewModel.setCover(media)
                                        showCoverDialog = false
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBirthDateDisplay(birthDate: String): String {
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(birthDate)
        parsed?.let { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(it) } ?: birthDate
    } catch (_: Exception) { birthDate }
}

private fun personDateRange(media: List<UriMedia>): Pair<String, String>? {
    val stamps = media.map { it.definedTimestamp * 1000L }.filter { it > 0 }
    if (stamps.isEmpty()) return null
    val format = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
    return format.format(java.util.Date(stamps.min())) to format.format(java.util.Date(stamps.max()))
}

/** Small action chip used by the single-scroll action row in [PersonHeaderContent]. */
@Composable
private fun PersonActionChip(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = if (destructive) {
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                iconContentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface,
                iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonHeaderContent(
    state: PersonDetailUiState,
    isLocalPerson: Boolean = false,
    photoCount: Int = 0,
    dateRange: Pair<String, String>? = null,
    blurProgress: Pair<Int, Int>? = null,
    onRenameClick: () -> Unit,
    onBirthdayClick: () -> Unit,
    onHideClick: () -> Unit = {},
    onUnhideClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onBlurEverywhereClick: () -> Unit = {},
    canMerge: Boolean = false,
    onMergeClick: () -> Unit = {},
    onSetCoverClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar — tappable cover picker for on-device people, with an edit badge.
        Box(contentAlignment = Alignment.BottomEnd) {
            val avatarModifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .let { base ->
                    if (isLocalPerson) base.clickable(onClick = onSetCoverClick) else base
                }
            val thumbnailUrl = state.person?.thumbnailUrl
            if (thumbnailUrl != null) {
                AsyncImage(
                    request = ComposableImageRequest(thumbnailUrl),
                    contentDescription = state.person.name,
                    modifier = avatarModifier,
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Person, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isLocalPerson) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onSetCoverClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = stringResource(Res.string.cloud_person_set_cover),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Name — prominent, tappable to rename.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onRenameClick)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = state.person?.name?.ifBlank { stringResource(Res.string.cloud_person_add_name) }
                    ?: stringResource(Res.string.cloud_person_add_name),
                style = MaterialTheme.typography.headlineSmall
            )
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(Res.string.cloud_person_edit_name),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))

        // Stats line — "14 photos · Mar 2021 – Jan 2026".
        if (photoCount > 0) {
            val countText = stringResource(Res.string.cloud_person_photo_count, photoCount)
            Text(
                text = when {
                    dateRange == null -> countText
                    dateRange.first == dateRange.second ->
                        stringResource(Res.string.person_stats_single, countText, dateRange.first)
                    else -> stringResource(
                        Res.string.person_stats_range,
                        countText, dateRange.first, dateRange.second
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Hidden state — a tonal banner with a direct way back, since a hidden
        // person is otherwise invisible everywhere in the app (#1262).
        if (state.person?.hidden == true) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(Res.string.person_hidden_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onUnhideClick) {
                        Text(stringResource(Res.string.person_hidden_banner_action))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // One scrolling row of actions — no more stacked chip rows.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                PersonActionChip(
                    onClick = onBirthdayClick,
                    icon = Icons.Outlined.Cake,
                    label = state.person?.birthDate?.let { formatBirthDateDisplay(it) }
                        ?: stringResource(Res.string.cloud_person_add_birthday)
                )
            }
            if (isLocalPerson) {
                item {
                    PersonActionChip(
                        onClick = onSetCoverClick,
                        icon = Icons.Outlined.Image,
                        label = stringResource(Res.string.cloud_person_set_cover)
                    )
                }
                if (canMerge) {
                    item {
                        PersonActionChip(
                            onClick = onMergeClick,
                            icon = Icons.Outlined.Merge,
                            label = stringResource(Res.string.cloud_person_merge)
                        )
                    }
                }
                if (state.person?.hidden == true) {
                    item {
                        PersonActionChip(
                            onClick = onUnhideClick,
                            icon = Icons.Outlined.Visibility,
                            label = stringResource(Res.string.person_unhide)
                        )
                    }
                } else {
                    item {
                        PersonActionChip(
                            onClick = onHideClick,
                            icon = Icons.Outlined.VisibilityOff,
                            label = stringResource(Res.string.cloud_person_hide)
                        )
                    }
                }
                item {
                    PersonActionChip(
                        onClick = onBlurEverywhereClick,
                        icon = Icons.Outlined.BlurOn,
                        label = stringResource(Res.string.cloud_person_blur_everywhere)
                    )
                }
                item {
                    PersonActionChip(
                        onClick = onDeleteClick,
                        icon = Icons.Outlined.Delete,
                        label = stringResource(Res.string.person_delete),
                        destructive = true
                    )
                }
            }
        }
        if (blurProgress != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    Res.string.cloud_person_blurring_progress,
                    blurProgress.first,
                    blurProgress.second
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Horizontal strip of the person's actual detected faces — cluster purity at a
 * glance. Long-press a face to remove it (durable EXCLUDE link).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonFaceStrip(
    faces: List<FaceCropItem>,
    onRemoveFace: (FaceCropItem) -> Unit
) {
    if (faces.isEmpty()) return
    var confirmRemoval by remember { mutableStateOf<FaceCropItem?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.person_faces_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = stringResource(Res.string.person_faces_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(faces, key = { it.faceId }) { face ->
                AsyncImage(
                    request = ComposableImageRequest(face.imageUri),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { confirmRemoval = face }
                        ),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    confirmRemoval?.let { face ->
        AlertDialog(
            onDismissRequest = { confirmRemoval = null },
            title = { Text(stringResource(Res.string.person_face_remove)) },
            text = { Text(stringResource(Res.string.person_face_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoval = null
                    onRemoveFace(face)
                }) {
                    Text(stringResource(Res.string.cloud_person_remove_media_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

/**
 * "Looks similar to" row — people the clusterer thinks might be the same person.
 * Tapping asks for a merge confirmation; confirming merges them into the
 * currently open person (keeping this identity).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimilarPeopleRow(
    people: List<PersonInfo>,
    currentName: String,
    onMerge: (PersonInfo) -> Unit
) {
    if (people.isEmpty()) return
    var confirmMerge by remember { mutableStateOf<PersonInfo?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.person_similar_people),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = stringResource(Res.string.person_similar_hint, currentName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(people, key = { it.accountKey }) { other ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.combinedClickable(
                        onClick = { confirmMerge = other }
                    )
                ) {
                    PersonAvatar(person = other, size = 64.dp)
                    Text(
                        text = other.name.ifBlank { stringResource(Res.string.cloud_people_unknown) },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }
    }

    confirmMerge?.let { other ->
        val otherName = other.name.ifBlank { stringResource(Res.string.cloud_people_unknown) }
        AlertDialog(
            onDismissRequest = { confirmMerge = null },
            title = { Text(stringResource(Res.string.cloud_person_merge)) },
            text = {
                Text(stringResource(Res.string.people_merge_into_confirm, otherName, currentName))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmMerge = null
                    onMerge(other)
                }) {
                    Text(stringResource(Res.string.cloud_person_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMerge = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}
