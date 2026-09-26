/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.logging.LogEntry
import com.dot.gallery.core.logging.LogExporter
import com.dot.gallery.core.presentation.components.AppSearchField
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.settings.components.settings
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLogsScreen(
    viewModel: DeveloperViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var detailEntry by remember { mutableStateOf<LogEntry?>(null) }
    val detailSheet = rememberAppBottomSheetState()
    var showRangeDialog by rememberSaveable { mutableStateOf(false) }

    val entryCopiedToast = stringResource(R.string.dev_entry_copied)
    val copiedToast = stringResource(R.string.dev_copied_toast)

    fun showDetail(entry: LogEntry) {
        detailEntry = entry
        scope.launch { detailSheet.show() }
    }

    fun copyEntry(entry: LogEntry) {
        clipboard.setText(AnnotatedString(LogExporter.entryText(entry)))
        Toast.makeText(context, entryCopiedToast, Toast.LENGTH_SHORT).show()
    }

    fun reportEntry(entry: LogEntry) {
        scope.launch {
            detailSheet.hide()
            clipboard.setText(
                AnnotatedString(viewModel.exportClipboardText(state.entries, forGithub = true))
            )
            Toast.makeText(context, copiedToast, Toast.LENGTH_LONG).show()
            uriHandler.openUri(viewModel.issueUrlFor(entry))
        }
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    // The settings{} DSL lambda is not composable — feed items and labels are
    // resolved here so LogPreference rows can be built inside it.
    val feed = remember(state.entries) { buildLogFeed(state.entries) }
    val headerLabels = feed.filterIsInstance<FeedItem.Header>()
        .associate { it.key to sessionLabel(it.sessionId) }
    val groupLabels = state.groups.associate { it.id to (areaLabel(it) to groupSummary(it)) }
    val groupsHeader = stringResource(R.string.dev_mode_by_area)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.dev_session_logs)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp
            )
        ) {
            item(key = "mode") {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SegmentedButton(
                        selected = state.mode == DeveloperViewModel.Mode.RECENT,
                        onClick = { viewModel.setMode(DeveloperViewModel.Mode.RECENT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.dev_mode_recent))
                    }
                    SegmentedButton(
                        selected = state.mode == DeveloperViewModel.Mode.BY_AREA,
                        onClick = { viewModel.setMode(DeveloperViewModel.Mode.BY_AREA) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.dev_mode_by_area))
                    }
                }
            }

            item(key = "search") {
                AppSearchField(
                    query = state.filters.query,
                    onQueryChange = viewModel::setQuery,
                    hint = stringResource(R.string.dev_search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item(key = "filters") {
                LogFiltersCard(
                    filters = state.filters,
                    onToggleLevel = viewModel::toggleLevel,
                    onRange = { range ->
                        if (range == DeveloperViewModel.Range.CUSTOM) showRangeDialog = true
                        else viewModel.setRange(range)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item(key = "feed-gap") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.mode == DeveloperViewModel.Mode.RECENT) {
                if (feed.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        EmptyLogs(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    settings {
                        feed.forEach { item ->
                            when (item) {
                                is FeedItem.Header -> Header(
                                    headerLabels[item.key].orEmpty()
                                )

                                is FeedItem.Entry -> LogPreference(
                                    entry = item.entry,
                                    onClick = { showDetail(item.entry) },
                                    onLongClick = { copyEntry(item.entry) }
                                )
                            }
                        }
                    }
                }
            } else {
                if (state.groups.isEmpty() && !state.loading) {
                    item(key = "empty-groups") {
                        EmptyLogs(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    settings {
                        Header(groupsHeader)
                        state.groups.forEach { group ->
                            val expanded = group.id in state.expandedGroups
                            val (label, summary) = groupLabels[group.id] ?: (group.id to "")
                            Preference(
                                title = label,
                                icon = if (expanded) {
                                    Icons.Outlined.KeyboardArrowDown
                                } else {
                                    Icons.Outlined.KeyboardArrowRight
                                },
                                summary = summary,
                                rightText = SimpleDateFormat("HH:mm", Locale.US)
                                    .format(Date(group.lastTs)),
                                onClick = { viewModel.toggleGroup(group.id) }
                            )
                            if (expanded) {
                                group.entries.forEach { entry ->
                                    LogPreference(
                                        entry = entry,
                                        onClick = { showDetail(entry) },
                                        onLongClick = { copyEntry(entry) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LogEntryDetailSheet(
        state = detailSheet,
        entry = detailEntry,
        onCopy = ::copyEntry,
        onReport = ::reportEntry
    )

    if (showRangeDialog) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangeDialog = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null,
                    onClick = {
                        val end = (rangeState.selectedEndDateMillis
                            ?: rangeState.selectedStartDateMillis)!! + DAY_MINUS_1_MS
                        viewModel.setRange(
                            DeveloperViewModel.Range.CUSTOM,
                            customStart = rangeState.selectedStartDateMillis,
                            customEnd = end
                        )
                        showRangeDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DateRangePicker(
                state = rangeState,
                title = {
                    Text(
                        text = stringResource(R.string.dev_pick_range),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }
    }
}

private const val DAY_MINUS_1_MS = 86_399_999L
