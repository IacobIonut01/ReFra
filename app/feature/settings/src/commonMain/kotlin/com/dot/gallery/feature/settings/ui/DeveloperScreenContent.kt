/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.logging.LogEntry
import com.dot.gallery.core.logging.LogLevel
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionLayout
import com.dot.gallery.core.presentation.components.OptionLayoutStyle
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_cancel
import com.dot.gallery.feature.settings.generated.resources.dev_action_clear
import com.dot.gallery.feature.settings.generated.resources.dev_action_copy
import com.dot.gallery.feature.settings.generated.resources.dev_action_report
import com.dot.gallery.feature.settings.generated.resources.dev_action_share
import com.dot.gallery.feature.settings.generated.resources.dev_breadcrumbs
import com.dot.gallery.feature.settings.generated.resources.dev_breadcrumbs_summary
import com.dot.gallery.feature.settings.generated.resources.dev_clear_confirm_summary
import com.dot.gallery.feature.settings.generated.resources.dev_clear_confirm_title
import com.dot.gallery.feature.settings.generated.resources.dev_collection_header
import com.dot.gallery.feature.settings.generated.resources.dev_disable
import com.dot.gallery.feature.settings.generated.resources.dev_disable_confirm_summary
import com.dot.gallery.feature.settings.generated.resources.dev_disable_confirm_title
import com.dot.gallery.feature.settings.generated.resources.dev_disable_summary
import com.dot.gallery.feature.settings.generated.resources.dev_latest_errors
import com.dot.gallery.feature.settings.generated.resources.dev_logs_header
import com.dot.gallery.feature.settings.generated.resources.dev_menu_title
import com.dot.gallery.feature.settings.generated.resources.dev_min_level
import com.dot.gallery.feature.settings.generated.resources.dev_min_level_summary
import com.dot.gallery.feature.settings.generated.resources.dev_no_errors
import com.dot.gallery.feature.settings.generated.resources.dev_session_logs
import com.dot.gallery.feature.settings.generated.resources.dev_session_logs_summary
import com.dot.gallery.feature.settings.generated.resources.dev_test_error
import com.dot.gallery.feature.settings.generated.resources.dev_test_error_summary
import com.dot.gallery.feature.settings.generated.resources.dev_tools_header
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState

private sealed interface PendingConfirm {
    data object Clear : PendingConfirm
    data object Disable : PendingConfirm
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreenContent(
    state: DeveloperScreenState,
    actions: DeveloperScreenActions,
) {
    val scope = rememberCoroutineScope()

    var detailEntry by remember { mutableStateOf<LogEntry?>(null) }
    val detailSheet = rememberAppBottomSheetState()
    val confirmSheet = rememberAppBottomSheetState()
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var showMinLevelDialog by rememberSaveable { mutableStateOf(false) }

    fun showDetail(entry: LogEntry) {
        detailEntry = entry
        scope.launch { detailSheet.show() }
    }

    val listState = rememberLazyListState()

    // Resolved here because the settings{} DSL scope is not composable
    val logsHeader = stringResource(Res.string.dev_logs_header)
    val sessionLogsTitle = stringResource(Res.string.dev_session_logs)
    val sessionLogsSummary = stringResource(Res.string.dev_session_logs_summary)
    val latestErrorsHeader = stringResource(Res.string.dev_latest_errors)
    val noErrorsTitle = stringResource(Res.string.dev_no_errors)
    val collectionHeader = stringResource(Res.string.dev_collection_header)
    val minLevelTitle = stringResource(Res.string.dev_min_level)
    val minLevelSummary = stringResource(Res.string.dev_min_level_summary)
    val breadcrumbsTitle = stringResource(Res.string.dev_breadcrumbs)
    val breadcrumbsSummary = stringResource(Res.string.dev_breadcrumbs_summary)
    val toolsHeader = stringResource(Res.string.dev_tools_header)
    val testErrorTitle = stringResource(Res.string.dev_test_error)
    val testErrorSummary = stringResource(Res.string.dev_test_error_summary)
    val disableTitle = stringResource(Res.string.dev_disable)
    val disableSummary = stringResource(Res.string.dev_disable_summary)

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.dev_menu_title),
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
            item(key = "hero") {
                StatusHero(
                    stats = state.stats,
                    totalEntries = state.totalCount,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            state.unreportedCrashEntry?.let { crash ->
                item(key = "crash-banner") {
                    CrashBanner(
                        entry = crash,
                        onReport = { actions.onReportBug(crash) },
                        onDismiss = actions.onMarkCrashReported,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            item(key = "actions") {
                val tileColor = MaterialTheme.colorScheme.surfaceContainer
                val tiles = listOf(
                    OptionItem(
                        icon = Icons.Outlined.BugReport,
                        text = stringResource(Res.string.dev_action_report),
                        containerColor = tileColor,
                        onClick = { actions.onReportBug(state.latestErrors.firstOrNull()) }
                    ),
                    OptionItem(
                        icon = Icons.Outlined.Share,
                        text = stringResource(Res.string.dev_action_share),
                        containerColor = tileColor,
                        onClick = { actions.onShareZip(state.entries) }
                    ),
                    OptionItem(
                        icon = Icons.Outlined.ContentCopy,
                        text = stringResource(Res.string.dev_action_copy),
                        containerColor = tileColor,
                        onClick = { actions.onCopyEntries(state.entries) }
                    ),
                    OptionItem(
                        icon = Icons.Outlined.DeleteSweep,
                        text = stringResource(Res.string.dev_action_clear),
                        containerColor = tileColor,
                        onClick = {
                            pendingConfirm = PendingConfirm.Clear
                            scope.launch { confirmSheet.show() }
                        }
                    )
                )
                OptionLayout(
                    optionList = tiles.toMutableStateList(),
                    style = OptionLayoutStyle.Grid,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item(key = "logs-gap") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            settings {
                Header(logsHeader)
                Preference(
                    title = sessionLogsTitle,
                    icon = Icons.AutoMirrored.Outlined.Article,
                    summary = sessionLogsSummary,
                    onClick = actions.onOpenSessionLogs
                )

                Header(latestErrorsHeader)
                if (state.latestErrors.isEmpty()) {
                    Preference(title = noErrorsTitle)
                } else {
                    state.latestErrors.forEach { entry ->
                        LogPreference(
                            entry = entry,
                            onClick = { showDetail(entry) },
                            onLongClick = { actions.onCopyEntry(entry) }
                        )
                    }
                }

                Header(collectionHeader)
                Preference(
                    title = minLevelTitle,
                    summary = minLevelSummary,
                    rightText = state.minLevel.name,
                    onClick = { showMinLevelDialog = true }
                )
                SwitchPreference(
                    title = breadcrumbsTitle,
                    summary = breadcrumbsSummary,
                    isChecked = state.breadcrumbsEnabled,
                    onCheck = actions.onToggleBreadcrumbs
                )

                Header(toolsHeader)
                Preference(
                    title = testErrorTitle,
                    summary = testErrorSummary,
                    onClick = actions.onTestError
                )
                Preference(
                    title = disableTitle,
                    summary = disableSummary,
                    onClick = {
                        pendingConfirm = PendingConfirm.Disable
                        scope.launch { confirmSheet.show() }
                    }
                )
            }
        }
    }

    LogEntryDetailSheet(
        state = detailSheet,
        entry = detailEntry,
        onCopy = actions.onCopyEntry,
        onReport = { entry ->
            scope.launch { detailSheet.hide() }
            actions.onReportBug(entry)
        }
    )

    when (pendingConfirm) {
        PendingConfirm.Clear -> ConfirmationSheet(
            state = confirmSheet,
            title = stringResource(Res.string.dev_clear_confirm_title),
            summary = stringResource(Res.string.dev_clear_confirm_summary),
            confirmText = stringResource(Res.string.dev_action_clear),
            onConfirm = actions.onClearLogs
        )

        PendingConfirm.Disable -> ConfirmationSheet(
            state = confirmSheet,
            title = stringResource(Res.string.dev_disable_confirm_title),
            summary = stringResource(Res.string.dev_disable_confirm_summary),
            confirmText = stringResource(Res.string.dev_disable),
            onConfirm = actions.onDisableDeveloperMode
        )

        null -> Unit
    }

    if (showMinLevelDialog) {
        AlertDialog(
            onDismissRequest = { showMinLevelDialog = false },
            title = { Text(stringResource(Res.string.dev_min_level)) },
            text = {
                Column {
                    LogLevel.entries.forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    actions.onSetMinLevel(level)
                                    showMinLevelDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = state.minLevel == level,
                                onClick = null
                            )
                            Text(
                                text = level.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMinLevelDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}
