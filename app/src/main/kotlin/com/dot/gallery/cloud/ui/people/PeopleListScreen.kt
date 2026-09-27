/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.local.PersonMergeSuggestion
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.data.data_source.SmartScanPhase
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    onPersonClick: (PersonInfo) -> Unit
) {
    val viewModel = hiltViewModel<PeopleListViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    var showMergeReview by remember { mutableStateOf(false) }
    var renamingPerson by remember { mutableStateOf<PersonInfo?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    val localCount = state.namedPeople.size + state.newPeople.size
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffectScaled(
                    state = LocalHazeState.current,
                    style = LocalHazeBlurStyle.current
                ),
                title = {
                    Column {
                        Text(stringResource(R.string.cloud_people))
                        if (localCount > 0 || state.scannedPhotoCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.people_count_photos,
                                    localCount,
                                    state.scannedPhotoCount
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = { NavigationBackButton() },
                actions = {
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = null
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false }
                        ) {
                            if (viewModel.localScanAvailable && !scanState.running) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.scan_for_people)) },
                                    onClick = {
                                        overflowOpen = false
                                        viewModel.scanForPeople()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.people_regroup)) },
                                    onClick = {
                                        overflowOpen = false
                                        viewModel.regroupFaces()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (state.showHidden) R.string.people_hide_hidden
                                            else R.string.people_show_hidden
                                        )
                                    )
                                },
                                onClick = {
                                    overflowOpen = false
                                    viewModel.setShowHidden(!state.showHidden)
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        when {
            state.isLoading -> {
                LoadingMedia(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            localCount == 0 && state.providerSections.isEmpty() -> {
                EmptyPeople(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    canScan = viewModel.localScanAvailable,
                    isScanning = scanState.running,
                    onScan = { viewModel.scanForPeople() }
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDir) + 16.dp,
                        end = innerPadding.calculateEndPadding(layoutDir) + 16.dp,
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (scanState.running) {
                        item(key = "scan_card", span = { GridItemSpan(maxLineSpan) }) {
                            PeopleScanCard(
                                scanState = scanState,
                                onCancel = { viewModel.cancelScan() }
                            )
                        }
                    }
                    if (state.mergeSuggestions.isNotEmpty()) {
                        item(key = "duplicates_card", span = { GridItemSpan(maxLineSpan) }) {
                            DuplicatesCard(
                                suggestions = state.mergeSuggestions,
                                onClick = { showMergeReview = true }
                            )
                        }
                    }
                    if (state.namedPeople.isNotEmpty()) {
                        item(key = "header_named", span = { GridItemSpan(maxLineSpan) }) {
                            PeopleSectionHeader(
                                title = stringResource(R.string.cloud_people),
                                count = state.namedPeople.size
                            )
                        }
                        items(state.namedPeople, key = { it.accountKey }) { person ->
                            PersonGridItem(
                                person = person,
                                onClick = { onPersonClick(person) },
                                onRename = { renamingPerson = person },
                                onHide = { viewModel.hidePerson(person) }
                            )
                        }
                    }
                    if (state.newPeople.isNotEmpty()) {
                        item(key = "header_new", span = { GridItemSpan(maxLineSpan) }) {
                            PeopleSectionHeader(
                                title = stringResource(R.string.people_new_section),
                                count = state.newPeople.size
                            )
                        }
                        items(state.newPeople, key = { it.accountKey }) { person ->
                            PersonGridItem(
                                person = person,
                                showAddName = true,
                                onClick = { onPersonClick(person) },
                                onRename = { renamingPerson = person },
                                onHide = { viewModel.hidePerson(person) }
                            )
                        }
                    }
                    if (state.showHidden && state.hiddenPeople.isNotEmpty()) {
                        item(key = "header_hidden", span = { GridItemSpan(maxLineSpan) }) {
                            PeopleSectionHeader(
                                title = stringResource(R.string.people_hidden_section),
                                count = state.hiddenPeople.size
                            )
                        }
                        items(state.hiddenPeople, key = { it.accountKey }) { person ->
                            PersonGridItem(
                                person = person,
                                onClick = { onPersonClick(person) },
                                onRename = { renamingPerson = person },
                                onUnhide = { viewModel.unhidePerson(person) }
                            )
                        }
                    }
                    state.providerSections.forEach { section ->
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "header_${section.key}"
                        ) {
                            PeopleSectionHeader(title = section.title, count = section.people.size)
                        }
                        items(section.people, key = { it.accountKey }) { person ->
                            PersonGridItem(
                                person = person,
                                onClick = { onPersonClick(person) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMergeReview) {
        MergeReviewSheet(
            suggestions = state.mergeSuggestions,
            onDismiss = { showMergeReview = false },
            onSamePerson = { viewModel.confirmMerge(it) },
            onDifferent = { viewModel.rejectMerge(it) }
        )
    }

    renamingPerson?.let { person ->
        RenamePersonSheet(
            initialName = person.name,
            titleRes = if (person.name.isBlank()) R.string.cloud_person_add_name
            else R.string.cloud_person_edit_name,
            onDismiss = { renamingPerson = null },
            onSave = { viewModel.renamePerson(person, it) }
        )
    }
}

/** Determinate scan-status card replacing the old toolbar spinner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleScanCard(
    scanState: PeopleScanUiState,
    onCancel: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = when {
                        scanState.phase == SmartScanPhase.FACE_CLUSTER ->
                            stringResource(R.string.people_scanning_grouping)
                        scanState.total > 0 -> stringResource(
                            R.string.people_scanning_progress,
                            scanState.processed,
                            scanState.total
                        )
                        else -> stringResource(R.string.scan_for_people)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
            if (scanState.total > 0 && scanState.phase != SmartScanPhase.FACE_CLUSTER) {
                LinearProgressIndicator(
                    progress = {
                        (scanState.processed.toFloat() / scanState.total.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** "Possible duplicates" entry card opening the merge-review sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicatesCard(
    suggestions: List<PersonMergeSuggestion>,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.GroupWork,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.people_duplicates_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.people_duplicates_summary,
                        suggestions.size,
                        suggestions.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonGridItem(
    person: PersonInfo,
    showAddName: Boolean = false,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onUnhide: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            PersonAvatar(
                person = person,
                size = 96.dp,
                modifier = Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onRename != null || onHide != null || onUnhide != null) {
                        { menuOpen = true }
                    } else null
                )
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onRename != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (person.name.isBlank()) R.string.cloud_person_add_name
                                    else R.string.cloud_person_edit_name
                                )
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                }
                if (onHide != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cloud_person_hide)) },
                        onClick = {
                            menuOpen = false
                            onHide()
                        }
                    )
                }
                if (onUnhide != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.people_hide_hidden)) },
                        onClick = {
                            menuOpen = false
                            onUnhide()
                        }
                    )
                }
            }
        }
        if (showAddName && person.name.isBlank()) {
            Text(
                text = stringResource(R.string.cloud_person_add_name),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onRename?.invoke() }
            )
        } else {
            Text(
                text = person.name.ifBlank { stringResource(R.string.cloud_people_unknown) },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (person.assetCount > 0) {
            Text(
                text = stringResource(R.string.people_photo_count, person.assetCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyPeople(
    modifier: Modifier = Modifier,
    canScan: Boolean = false,
    isScanning: Boolean = false,
    onScan: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(
            modifier = Modifier.size(128.dp),
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.cloud_people_empty),
            style = MaterialTheme.typography.titleLarge
        )
        if (canScan) {
            Button(onClick = onScan, enabled = !isScanning) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.scan_for_people),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
