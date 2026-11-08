/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.logging.LogEntry
import com.dot.gallery.core.logging.formatLogClock
import com.dot.gallery.core.presentation.components.AppSearchField
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_cancel
import com.dot.gallery.feature.settings.generated.resources.dev_mode_by_area
import com.dot.gallery.feature.settings.generated.resources.dev_mode_recent
import com.dot.gallery.feature.settings.generated.resources.dev_pick_range
import com.dot.gallery.feature.settings.generated.resources.dev_search_hint
import com.dot.gallery.feature.settings.generated.resources.dev_session_logs
import com.dot.gallery.feature.settings.generated.resources.ok
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLogsContent(
    state: SessionLogsState,
    actions: SessionLogsActions,
) {
    val scope = rememberCoroutineScope()

    var detailEntry by remember { mutableStateOf<LogEntry?>(null) }
    val detailSheet = rememberAppBottomSheetState()
    var showRangeDialog by rememberSaveable { mutableStateOf(false) }

    fun showDetail(entry: LogEntry) {
        detailEntry = entry
        scope.launch { detailSheet.show() }
    }

    fun reportEntry(entry: LogEntry) {
        scope.launch {
            detailSheet.hide()
            actions.onReportEntry(entry)
        }
    }

    val listState = rememberLazyListState()
    // The settings{} DSL lambda is not composable — feed items and labels are
    // resolved here so LogPreference rows can be built inside it.
    val feed = remember(state.entries) { buildLogFeed(state.entries) }
    val headerLabels = feed.filterIsInstance<LogFeedItem.Header>()
        .associate { it.key to sessionLabel(it.sessionId) }
    val groupLabels = state.groups.associate { it.id to (areaLabel(it) to groupSummary(it)) }
    val groupsHeader = stringResource(Res.string.dev_mode_by_area)

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.dev_session_logs),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current),
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
                        selected = state.mode == SessionLogsMode.RECENT,
                        onClick = { actions.onModeChange(SessionLogsMode.RECENT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(Res.string.dev_mode_recent))
                    }
                    SegmentedButton(
                        selected = state.mode == SessionLogsMode.BY_AREA,
                        onClick = { actions.onModeChange(SessionLogsMode.BY_AREA) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(Res.string.dev_mode_by_area))
                    }
                }
            }

            item(key = "search") {
                AppSearchField(
                    query = state.filters.query,
                    onQueryChange = actions.onQueryChange,
                    hint = stringResource(Res.string.dev_search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item(key = "filters") {
                LogFiltersCard(
                    filters = state.filters,
                    onToggleLevel = actions.onToggleLevel,
                    onRange = { range ->
                        if (range == SessionLogsRange.CUSTOM) showRangeDialog = true
                        else actions.onRange(range)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item(key = "feed-gap") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.mode == SessionLogsMode.RECENT) {
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
                                is LogFeedItem.Header -> Header(
                                    headerLabels[item.key].orEmpty()
                                )

                                is LogFeedItem.Entry -> LogPreference(
                                    entry = item.entry,
                                    onClick = { showDetail(item.entry) },
                                    onLongClick = { actions.onCopyEntry(item.entry) }
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
                                rightText = formatLogClock(group.lastTs),
                                onClick = { actions.onToggleGroup(group.id) }
                            )
                            if (expanded) {
                                group.entries.forEach { entry ->
                                    LogPreference(
                                        entry = entry,
                                        onClick = { showDetail(entry) },
                                        onLongClick = { actions.onCopyEntry(entry) }
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
        onCopy = actions.onCopyEntry,
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
                        val start = rangeState.selectedStartDateMillis!!
                        val end = (rangeState.selectedEndDateMillis ?: start) + DAY_MINUS_1_MS
                        actions.onCustomRange(start, end)
                        showRangeDialog = false
                    }
                ) {
                    Text(stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) {
            DateRangePicker(
                state = rangeState,
                title = {
                    Text(
                        text = stringResource(Res.string.dev_pick_range),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }
    }
}

private const val DAY_MINUS_1_MS = 86_399_999L
