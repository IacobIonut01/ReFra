/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.rememberCoroutineScope
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.generated.resources.people_settings
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.local.PersonMergeSuggestion
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cancel
import com.dot.gallery.generated.resources.cloud_people
import com.dot.gallery.generated.resources.cloud_people_unknown
import com.dot.gallery.generated.resources.cloud_person_add_name
import com.dot.gallery.generated.resources.cloud_person_edit_name
import com.dot.gallery.generated.resources.cloud_person_hide
import com.dot.gallery.generated.resources.filter_all
import com.dot.gallery.generated.resources.more_options
import com.dot.gallery.generated.resources.people_duplicates_title
import com.dot.gallery.generated.resources.people_empty_body
import com.dot.gallery.generated.resources.people_empty_title
import com.dot.gallery.generated.resources.people_filter_named
import com.dot.gallery.generated.resources.people_filter_new
import com.dot.gallery.generated.resources.people_hidden_section
import com.dot.gallery.generated.resources.people_hide_hidden
import com.dot.gallery.generated.resources.people_new_section
import com.dot.gallery.generated.resources.people_photo_count
import com.dot.gallery.generated.resources.people_regroup
import com.dot.gallery.generated.resources.people_scanning_count
import com.dot.gallery.generated.resources.people_scanning_grouping
import com.dot.gallery.generated.resources.people_scanning_title
import com.dot.gallery.generated.resources.people_setup_face_grouping
import com.dot.gallery.generated.resources.people_show_hidden
import com.dot.gallery.generated.resources.person_unhide
import com.dot.gallery.generated.resources.scan_for_people
import com.dot.gallery.injection.injectViewModel
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.hazeSource


@Composable
fun PeopleListScreen(
    onPersonClick: (PersonInfo) -> Unit,
    onSetupFaceGrouping: () -> Unit = {},
) {
    val viewModel = injectViewModel<PeopleListViewModel> {peopleListViewModel}
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    PeopleListContent(
        state = state,
        scanState = scanState,
        localScanAvailable = viewModel.localScanAvailable,
        faceSetupAvailable = viewModel.faceSetupAvailable,
        actions = PeopleListActions(
            onPersonClick = onPersonClick,
            scanForPeople = viewModel::scanForPeople,
            regroupFaces = viewModel::regroupFaces,
            setShowHidden = viewModel::setShowHidden,
            cancelScan = viewModel::cancelScan,
            hidePerson = viewModel::hidePerson,
            unhidePerson = viewModel::unhidePerson,
            confirmMerge = viewModel::confirmMerge,
            rejectMerge = viewModel::rejectMerge,
            renamePerson = viewModel::renamePerson,
            setupFaceGrouping = onSetupFaceGrouping,
            setSourceVisible = viewModel::setSourceVisible,
        )
    )
}

/**
 * People in the Library style: the page header with its count and actions, a duplicates tile,
 * filter chips, and square face cards in titled groups. Hidden people are reached through the
 * Hidden chip, which turns the ViewModel's show-hidden flag on while it is selected.
 */
@Composable
fun PeopleListContent(
    state: PeopleListUiState,
    scanState: PeopleScanUiState,
    localScanAvailable: Boolean,
    faceSetupAvailable: Boolean = false,
    actions: PeopleListActions = PeopleListActions(),
    /** Grid of face cards; the docs scenes pass one opened part-way down. */
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val settingsSheet = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    PeopleSettingsSheet(
        sheetState = settingsSheet,
        sources = peopleSources(state),
        hiddenSources = state.hiddenSources,
        hiddenPeople = state.allHiddenPeople,
        onSourceVisibleChange = actions.setSourceVisible,
        onUnhide = actions.unhidePerson,
    )
    PeopleListPage(
        state = state.withoutSources(state.hiddenSources),
        scanState = scanState,
        localScanAvailable = localScanAvailable && LOCAL_PEOPLE_SOURCE !in state.hiddenSources,
        faceSetupAvailable = faceSetupAvailable,
        actions = actions,
        onOpenSettings = { scope.launch { settingsSheet.show() } },
        gridState = gridState,
    )
}

@Composable
private fun PeopleListPage(
    state: PeopleListUiState,
    scanState: PeopleScanUiState,
    localScanAvailable: Boolean,
    faceSetupAvailable: Boolean,
    actions: PeopleListActions,
    onOpenSettings: () -> Unit,
    gridState: LazyGridState,
) {
    var showMergeReview by remember { mutableStateOf(false) }
    var renamingPerson by remember { mutableStateOf<PersonInfo?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(PeopleFilter.All.encode()) }

    val localCount = state.namedPeople.size + state.newPeople.size
    // Hidden people count toward the header so the totals stay truthful.
    val peopleCount = localCount + state.hiddenPeopleCount
    val filters = availablePeopleFilters(state)
    val activeFilter = resolvePeopleFilter(PeopleFilter.decode(selectedFilter), filters)
    val selectFilter: (PeopleFilter) -> Unit = { filter ->
        val wasHidden = activeFilter.kind == PeopleFilter.Kind.HIDDEN
        val isHidden = filter.kind == PeopleFilter.Kind.HIDDEN
        if (isHidden && !state.showHidden) actions.setShowHidden(true)
        if (wasHidden && !isHidden && state.showHidden) actions.setShowHidden(false)
        selectedFilter = filter.encode()
    }
    val sections = peopleSections(state, activeFilter)
    val hazeState = LocalHazeState.current
    val gridScrolled = rememberScrolledUnderTopBar(gridState)

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
                PeopleTopBar(
                    count = peopleCount,
                    state = state,
                    canScan = localScanAvailable && !scanState.running,
                    actions = actions,
                    onOpenSettings = onOpenSettings,
                    hazeState = hazeState,
                    scrolled = gridScrolled,
                )
            }
        }
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        // The page's only haze source, around every branch.
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
        when {
            state.isLoading -> {
                LoadingMedia(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            // Hidden people keep this page alive — they are the only way to
            // unhide, so "no visible people" must not collapse to EmptyPeople.
            localCount == 0 && state.hiddenPeopleCount == 0 &&
                state.providerSections.isEmpty() -> {
                EmptyPeople(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    canScan = localScanAvailable,
                    canSetUp = faceSetupAvailable,
                    isScanning = scanState.running,
                    onScan = actions.scanForPeople,
                    onSetUp = actions.setupFaceGrouping,
                )
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDir) + 16.dp,
                        end = innerPadding.calculateEndPadding(layoutDir) + 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (scanState.running) {
                        item(key = "scan_card", span = { GridItemSpan(maxLineSpan) }) {
                            PeopleScanCard(scanState = scanState, onCancel = actions.cancelScan)
                        }
                    }
                    if (state.mergeSuggestions.isNotEmpty()) {
                        item(key = "duplicates_tile", span = { GridItemSpan(maxLineSpan) }) {
                            DuplicatesTile(
                                suggestions = state.mergeSuggestions,
                                onClick = { showMergeReview = true }
                            )
                        }
                    }
                    if (filters.size > 2) {
                        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                            PeopleFilterChips(
                                filters = filters,
                                selected = activeFilter,
                                providerTitle = { key ->
                                    state.providerSections.firstOrNull { it.key == key }?.title.orEmpty()
                                },
                                onSelect = selectFilter,
                            )
                        }
                    }
                    sections.forEach { section ->
                        // A chip already names its group, so only the All view titles them.
                        if (activeFilter.kind == PeopleFilter.Kind.ALL) {
                            item(key = "header_${section.key}", span = { GridItemSpan(maxLineSpan) }) {
                                PeopleGroupHeader(
                                    title = section.title ?: sectionTitle(section.kind),
                                    count = section.people.size
                                )
                            }
                        }
                        items(section.people, key = { "${section.key}_${it.accountKey}" }) { person ->
                            val local = section.kind != PeopleFilter.Kind.PROVIDER
                            val hidden = section.kind == PeopleFilter.Kind.HIDDEN
                            PersonCard(
                                person = person,
                                showAddName = section.kind == PeopleFilter.Kind.NEW,
                                onClick = { actions.onPersonClick(person) },
                                onRename = if (local) ({ renamingPerson = person }) else null,
                                onHide = if (local && !hidden) ({ actions.hidePerson(person) }) else null,
                                onUnhide = if (hidden) ({ actions.unhidePerson(person) }) else null,
                            )
                        }
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
            onSamePerson = { actions.confirmMerge(it) },
            onDifferent = { actions.rejectMerge(it) }
        )
    }

    renamingPerson?.let { person ->
        RenamePersonSheet(
            initialName = person.name,
            titleRes = if (person.name.isBlank()) Res.string.cloud_person_add_name
            else Res.string.cloud_person_edit_name,
            onDismiss = { renamingPerson = null },
            onSave = { actions.renamePerson(person, it) }
        )
    }
}

@Composable
private fun sectionTitle(kind: PeopleFilter.Kind): String = stringResource(
    when (kind) {
        PeopleFilter.Kind.NEW -> Res.string.people_new_section
        PeopleFilter.Kind.HIDDEN -> Res.string.people_hidden_section
        else -> Res.string.people_filter_named
    }
)

@Composable
private fun PeopleTopBar(
    count: Int,
    state: PeopleListUiState,
    canScan: Boolean,
    actions: PeopleListActions,
    onOpenSettings: () -> Unit,
    hazeState: HazeState,
    scrolled: Boolean,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    GalleryPageTopBar(
        title = stringResource(Res.string.cloud_people),
        count = count.takeIf { it > 0 }?.toString(),
        hazeState = hazeState,
        scrolled = scrolled,
        actions = {
            GalleryPageTopBarAction(
                icon = Icons.Outlined.Tune,
                contentDescription = stringResource(Res.string.people_settings),
                onClick = onOpenSettings,
            )
            if (canScan) {
                GalleryPageTopBarAction(
                    icon = Icons.Outlined.PersonSearch,
                    contentDescription = stringResource(Res.string.scan_for_people),
                    onClick = actions.scanForPeople,
                )
            }
            Box {
                GalleryPageTopBarAction(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(Res.string.more_options),
                    onClick = { overflowOpen = true },
                )
                DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    if (canScan) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.people_regroup)) },
                            onClick = {
                                overflowOpen = false
                                actions.regroupFaces()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (state.showHidden) Res.string.people_hide_hidden
                                    else Res.string.people_show_hidden
                                )
                            )
                        },
                        onClick = {
                            overflowOpen = false
                            actions.setShowHidden(!state.showHidden)
                        }
                    )
                }
            }
        },
    )
}

/** Scan status: what is happening, a 12 dp track, and Cancel. */
@Composable
private fun PeopleScanCard(
    scanState: PeopleScanUiState,
    onCancel: () -> Unit
) {
    val grouping = scanState.phase == SmartScanPhase.FACE_CLUSTER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (grouping) Res.string.people_scanning_grouping else Res.string.people_scanning_title
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
                if (!grouping && scanState.total > 0) {
                    Text(
                        text = stringResource(Res.string.people_scanning_count, scanState.processed, scanState.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.cancel))
            }
        }
        val trackModifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
        if (scanState.total > 0 && !grouping) {
            LinearProgressIndicator(
                progress = {
                    (scanState.processed.toFloat() / scanState.total.toFloat()).coerceIn(0f, 1f)
                },
                modifier = trackModifier,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(modifier = trackModifier)
        }
    }
}

/** "Possible duplicates" as a Library shortcut tile, opening the merge-review sheet. */
@Composable
private fun DuplicatesTile(
    suggestions: List<PersonMergeSuggestion>,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = Icons.Outlined.GroupWork, contentDescription = null)
            Text(
                text = stringResource(Res.string.people_duplicates_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(text = suggestions.size.toString(), style = MaterialTheme.typography.labelLarge)
            Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun PeopleFilterChips(
    filters: List<PeopleFilter>,
    selected: PeopleFilter,
    providerTitle: (String) -> String,
    onSelect: (PeopleFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            GalleryFilterChip(
                label = when (filter.kind) {
                    PeopleFilter.Kind.ALL -> stringResource(Res.string.filter_all)
                    PeopleFilter.Kind.NAMED -> stringResource(Res.string.people_filter_named)
                    PeopleFilter.Kind.NEW -> stringResource(Res.string.people_filter_new)
                    PeopleFilter.Kind.HIDDEN -> stringResource(Res.string.people_hidden_section)
                    PeopleFilter.Kind.PROVIDER -> providerTitle(filter.key.orEmpty())
                },
                selected = filter == selected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

/** Group title with its count on the baseline, like the Albums heading. */
@Composable
private fun PeopleGroupHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.alignByBaseline(),
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            modifier = Modifier.alignByBaseline(),
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Square face crop with 24 dp corners, the name and the photo count below — the album card shape. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonCard(
    person: PersonInfo,
    showAddName: Boolean = false,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onUnhide: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Column {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (onRename != null || onHide != null || onUnhide != null) {
                            { menuOpen = true }
                        } else null
                    ),
                contentAlignment = Alignment.Center
            ) {
                val thumbnailUrl = person.thumbnailUrl
                if (thumbnailUrl != null) {
                    AsyncImage(
                        request = ComposableImageRequest(thumbnailUrl),
                        contentDescription = person.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onRename != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (person.name.isBlank()) Res.string.cloud_person_add_name
                                    else Res.string.cloud_person_edit_name
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
                        text = { Text(stringResource(Res.string.cloud_person_hide)) },
                        onClick = {
                            menuOpen = false
                            onHide()
                        }
                    )
                }
                if (onUnhide != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.person_unhide)) },
                        onClick = {
                            menuOpen = false
                            onUnhide()
                        }
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
            if (showAddName && person.name.isBlank()) {
                Text(
                    text = stringResource(Res.string.cloud_person_add_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onRename?.invoke() }
                )
            } else {
                Text(
                    text = person.name.ifBlank { stringResource(Res.string.cloud_people_unknown) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (person.assetCount > 0) {
                Text(
                    text = stringResource(Res.string.people_photo_count, person.assetCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyPeople(
    modifier: Modifier = Modifier,
    canScan: Boolean = false,
    canSetUp: Boolean = false,
    isScanning: Boolean = false,
    onScan: () -> Unit = {},
    onSetUp: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp).padding(bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PersonSearch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }
        Text(
            text = stringResource(Res.string.people_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.people_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        when {
            canScan -> Button(
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = onScan,
                enabled = !isScanning
            ) {
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
                    text = stringResource(Res.string.scan_for_people),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            canSetUp -> Button(modifier = Modifier.heightIn(min = 48.dp), onClick = onSetUp) {
                Text(text = stringResource(Res.string.people_setup_face_grouping))
            }
        }
    }
}
