/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.common.MediaScreen
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalGlideComposeApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun PersonDetailScreen(
    metadataState: State<MediaMetadataState>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val viewModel = hiltViewModel<PersonDetailViewModel>()
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

    val personName = state.person?.name?.ifBlank {
        stringResource(R.string.cloud_people_unknown)
    } ?: stringResource(R.string.cloud_person_detail_title)

    val eventHandler = LocalEventHandler.current

    MediaScreen(
        albumName = personName,
        customDateHeader = stringResource(R.string.cloud_person_photo_count, mediaState.value.media.size),
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
        navActionsContent = { _, _ -> },
        selectionSheetContent = if (viewModel.isLocalPerson) {
            {
                val selector = LocalMediaSelector.current
                PersonSelectionSheet(
                    allMedia = mediaState.value,
                    personName = personName,
                    onRemoveFromPerson = { ids ->
                        selector.clearSelection()
                        viewModel.removeMediaFromPerson(ids) { eventHandler.navigateUp() }
                    }
                )
            }
        } else null,
        aboveGridContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PersonHeader(
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
        onActivityResult = { },
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
                    text = stringResource(R.string.cloud_person_edit_name),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    placeholder = { Text(stringResource(R.string.cloud_person_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                SetupButton(
                    text = stringResource(R.string.action_save),
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
            initialSelectedDateMillis = parseBirthDateMillis(state.person?.birthDate)
        )
        DatePickerDialog(
            onDismissRequest = { showBirthdayPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val formatted = formatBirthDate(millis)
                        viewModel.updateBirthDate(formatted)
                    }
                    showBirthdayPicker = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthdayPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
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
                    text = stringResource(R.string.cloud_person_blur_everywhere),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.cloud_person_blur_everywhere_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                SetupButton(
                    text = stringResource(R.string.type_blur),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    onClick = {
                        viewModel.blurEverywhere(useMosaic = false)
                        showBlurDialog = false
                    }
                )
                SetupButton(
                    text = stringResource(R.string.type_mosaic),
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
                    text = stringResource(R.string.cloud_person_merge),
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
                            if (candidate.thumbnailUrl != null) {
                                GlideImage(
                                    model = candidate.thumbnailUrl.toUri(),
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
                                    stringResource(R.string.cloud_people_unknown)
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
                    text = stringResource(R.string.cloud_person_set_cover),
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
                            GlideImage(
                                model = face.imageUri.toUri(),
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
                            GlideImage(
                                model = media.getUri(),
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

private fun parseBirthDateMillis(birthDate: String?): Long? {
    if (birthDate.isNullOrBlank()) return null
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(birthDate)?.time
    } catch (_: Exception) { null }
}

private fun formatBirthDate(millis: Long): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(millis))
}

private fun formatBirthDateDisplay(birthDate: String): String {
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(birthDate)
        parsed?.let { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(it) } ?: birthDate
    } catch (_: Exception) { birthDate }
}

private fun personDateRange(media: List<Media.UriMedia>): Pair<String, String>? {
    val stamps = media.map { it.definedTimestamp * 1000L }.filter { it > 0 }
    if (stamps.isEmpty()) return null
    val format = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
    return format.format(java.util.Date(stamps.min())) to format.format(java.util.Date(stamps.max()))
}

/** Small action chip used by the single-scroll action row in [PersonHeader]. */
@Composable
private fun PersonActionChip(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PersonHeader(
    state: PersonDetailUiState,
    isLocalPerson: Boolean = false,
    photoCount: Int = 0,
    dateRange: Pair<String, String>? = null,
    blurProgress: Pair<Int, Int>? = null,
    onRenameClick: () -> Unit,
    onBirthdayClick: () -> Unit,
    onHideClick: () -> Unit = {},
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
                GlideImage(
                    model = thumbnailUrl.toUri(),
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
                        contentDescription = stringResource(R.string.cloud_person_set_cover),
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
                text = state.person?.name?.ifBlank { stringResource(R.string.cloud_person_add_name) }
                    ?: stringResource(R.string.cloud_person_add_name),
                style = MaterialTheme.typography.headlineSmall
            )
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.cloud_person_edit_name),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))

        // Stats line — "14 photos · Mar 2021 – Jan 2026".
        if (photoCount > 0) {
            val countText = stringResource(R.string.cloud_person_photo_count, photoCount)
            Text(
                text = when {
                    dateRange == null -> countText
                    dateRange.first == dateRange.second ->
                        stringResource(R.string.person_stats_single, countText, dateRange.first)
                    else -> stringResource(
                        R.string.person_stats_range,
                        countText, dateRange.first, dateRange.second
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

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
                        ?: stringResource(R.string.cloud_person_add_birthday)
                )
            }
            if (isLocalPerson) {
                item {
                    PersonActionChip(
                        onClick = onSetCoverClick,
                        icon = Icons.Outlined.Image,
                        label = stringResource(R.string.cloud_person_set_cover)
                    )
                }
                if (canMerge) {
                    item {
                        PersonActionChip(
                            onClick = onMergeClick,
                            icon = Icons.Outlined.Merge,
                            label = stringResource(R.string.cloud_person_merge)
                        )
                    }
                }
                item {
                    PersonActionChip(
                        onClick = onHideClick,
                        icon = Icons.Outlined.VisibilityOff,
                        label = stringResource(R.string.cloud_person_hide)
                    )
                }
                item {
                    PersonActionChip(
                        onClick = onBlurEverywhereClick,
                        icon = Icons.Outlined.BlurOn,
                        label = stringResource(R.string.cloud_person_blur_everywhere)
                    )
                }
            }
        }
        if (blurProgress != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.cloud_person_blurring_progress,
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
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
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
            text = stringResource(R.string.person_faces_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = stringResource(R.string.person_faces_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(faces, key = { it.faceId }) { face ->
                GlideImage(
                    model = face.imageUri.toUri(),
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
            title = { Text(stringResource(R.string.person_face_remove)) },
            text = { Text(stringResource(R.string.person_face_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoval = null
                    onRemoveFace(face)
                }) {
                    Text(stringResource(R.string.cloud_person_remove_media_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = null }) {
                    Text(stringResource(R.string.cancel))
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
            text = stringResource(R.string.person_similar_people),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = stringResource(R.string.person_similar_hint, currentName),
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
                        text = other.name.ifBlank { stringResource(R.string.cloud_people_unknown) },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }
    }

    confirmMerge?.let { other ->
        val otherName = other.name.ifBlank { stringResource(R.string.cloud_people_unknown) }
        AlertDialog(
            onDismissRequest = { confirmMerge = null },
            title = { Text(stringResource(R.string.cloud_person_merge)) },
            text = {
                Text(stringResource(R.string.people_merge_into_confirm, otherName, currentName))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmMerge = null
                    onMerge(other)
                }) {
                    Text(stringResource(R.string.cloud_person_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMerge = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
