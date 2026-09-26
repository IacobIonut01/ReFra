/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.core.logging.LogEntry
import com.dot.gallery.core.logging.LogExporter
import com.dot.gallery.core.logging.LogLevel
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionLayout
import com.dot.gallery.feature_node.presentation.common.components.OptionLayoutStyle
import com.dot.gallery.feature_node.presentation.settings.components.settings
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.components.ConfirmationSheet
import kotlinx.coroutines.launch

private sealed interface PendingConfirm {
    data object Clear : PendingConfirm
    data object Disable : PendingConfirm
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: DeveloperViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val eventHandler = LocalEventHandler.current

    var detailEntry by remember { mutableStateOf<LogEntry?>(null) }
    val detailSheet = rememberAppBottomSheetState()
    val confirmSheet = rememberAppBottomSheetState()
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var showMinLevelDialog by rememberSaveable { mutableStateOf(false) }

    var developerMode by Settings.Misc.rememberDeveloperMode()
    var breadcrumbsPref by Settings.Misc.rememberDevLogBreadcrumbs()
    var minLevelPref by Settings.Misc.rememberDevLogMinLevel()

    val copiedToast = stringResource(R.string.dev_copied_toast)
    val logsCopiedToast = stringResource(R.string.dev_logs_copied)
    val entryCopiedToast = stringResource(R.string.dev_entry_copied)
    val clearedToast = stringResource(R.string.dev_logs_cleared)
    val disabledToast = stringResource(R.string.dev_dev_mode_disabled)
    val testDoneToast = stringResource(R.string.dev_test_error_done)

    fun showDetail(entry: LogEntry) {
        detailEntry = entry
        scope.launch { detailSheet.show() }
    }

    fun copyEntries(entries: List<LogEntry>, forGithub: Boolean, toast: String) {
        scope.launch {
            clipboard.setText(AnnotatedString(viewModel.exportClipboardText(entries, forGithub)))
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareZip(entries: List<LogEntry>) {
        scope.launch {
            val file = viewModel.exportShareFile(entries)
            runCatching {
                context.startActivity(
                    Intent.createChooser(
                        LogExporter.shareIntent(context, file),
                        context.getString(R.string.share)
                    )
                )
            }
        }
    }

    /** Copies the full (filtered) log text for the issue body, then opens GitHub. */
    fun reportBug(entry: LogEntry?) {
        scope.launch {
            clipboard.setText(
                AnnotatedString(viewModel.exportClipboardText(state.entries, forGithub = true))
            )
            Toast.makeText(context, copiedToast, Toast.LENGTH_LONG).show()
            uriHandler.openUri(viewModel.issueUrlFor(entry))
            if (entry?.logLevel == LogLevel.CRASH) viewModel.markCrashReported()
        }
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Resolved here because the settings{} DSL scope is not composable
    val logsHeader = stringResource(R.string.dev_logs_header)
    val sessionLogsTitle = stringResource(R.string.dev_session_logs)
    val sessionLogsSummary = stringResource(R.string.dev_session_logs_summary)
    val latestErrorsHeader = stringResource(R.string.dev_latest_errors)
    val noErrorsTitle = stringResource(R.string.dev_no_errors)
    val collectionHeader = stringResource(R.string.dev_collection_header)
    val minLevelTitle = stringResource(R.string.dev_min_level)
    val minLevelSummary = stringResource(R.string.dev_min_level_summary)
    val breadcrumbsTitle = stringResource(R.string.dev_breadcrumbs)
    val breadcrumbsSummary = stringResource(R.string.dev_breadcrumbs_summary)
    val toolsHeader = stringResource(R.string.dev_tools_header)
    val testErrorTitle = stringResource(R.string.dev_test_error)
    val testErrorSummary = stringResource(R.string.dev_test_error_summary)
    val disableTitle = stringResource(R.string.dev_disable)
    val disableSummary = stringResource(R.string.dev_disable_summary)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.dev_menu_title)) },
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
                        onReport = { reportBug(crash) },
                        onDismiss = { viewModel.markCrashReported() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            item(key = "actions") {
                val tileColor = MaterialTheme.colorScheme.surfaceContainer
                val actions = listOf(
                    OptionItem(
                        icon = Icons.Outlined.BugReport,
                        text = stringResource(R.string.dev_action_report),
                        containerColor = tileColor,
                        onClick = { reportBug(state.latestErrors.firstOrNull()) }
                    ),
                    OptionItem(
                        icon = Icons.Outlined.Share,
                        text = stringResource(R.string.dev_action_share),
                        containerColor = tileColor,
                        onClick = { shareZip(state.entries) }
                    ),
                    OptionItem(
                        icon = Icons.Outlined.ContentCopy,
                        text = stringResource(R.string.dev_action_copy),
                        containerColor = tileColor,
                        onClick = { copyEntries(state.entries, false, logsCopiedToast) }
                    ),
                    OptionItem(
                        icon = Icons.Outlined.DeleteSweep,
                        text = stringResource(R.string.dev_action_clear),
                        containerColor = tileColor,
                        onClick = {
                            pendingConfirm = PendingConfirm.Clear
                            scope.launch { confirmSheet.show() }
                        }
                    )
                )
                OptionLayout(
                    optionList = actions.toMutableStateList(),
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
                    onClick = { eventHandler.navigate(Screen.SessionLogsScreen()) }
                )

                Header(latestErrorsHeader)
                if (state.latestErrors.isEmpty()) {
                    Preference(title = noErrorsTitle)
                } else {
                    state.latestErrors.forEach { entry ->
                        LogPreference(
                            entry = entry,
                            onClick = { showDetail(entry) },
                            onLongClick = {
                                clipboard.setText(AnnotatedString(LogExporter.entryText(entry)))
                                Toast.makeText(context, entryCopiedToast, Toast.LENGTH_SHORT)
                                    .show()
                            }
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
                    onCheck = { value ->
                        breadcrumbsPref = value
                        viewModel.setBreadcrumbsEnabled(value)
                    }
                )

                Header(toolsHeader)
                Preference(
                    title = testErrorTitle,
                    summary = testErrorSummary,
                    onClick = {
                        viewModel.logTestError()
                        Toast.makeText(context, testDoneToast, Toast.LENGTH_SHORT).show()
                    }
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
        onCopy = { entry ->
            clipboard.setText(AnnotatedString(LogExporter.entryText(entry)))
            Toast.makeText(context, entryCopiedToast, Toast.LENGTH_SHORT).show()
        },
        onReport = { entry ->
            scope.launch { detailSheet.hide() }
            reportBug(entry)
        }
    )

    when (pendingConfirm) {
        PendingConfirm.Clear -> ConfirmationSheet(
            state = confirmSheet,
            title = stringResource(R.string.dev_clear_confirm_title),
            summary = stringResource(R.string.dev_clear_confirm_summary),
            confirmText = stringResource(R.string.dev_action_clear),
            onConfirm = {
                viewModel.clearLogs()
                Toast.makeText(context, clearedToast, Toast.LENGTH_SHORT).show()
            }
        )

        PendingConfirm.Disable -> ConfirmationSheet(
            state = confirmSheet,
            title = stringResource(R.string.dev_disable_confirm_title),
            summary = stringResource(R.string.dev_disable_confirm_summary),
            confirmText = stringResource(R.string.dev_disable),
            onConfirm = {
                viewModel.disableDeveloperMode()
                developerMode = false
                Toast.makeText(context, disabledToast, Toast.LENGTH_SHORT).show()
                eventHandler.navigateUp()
            }
        )

        null -> Unit
    }

    if (showMinLevelDialog) {
        AlertDialog(
            onDismissRequest = { showMinLevelDialog = false },
            title = { Text(stringResource(R.string.dev_min_level)) },
            text = {
                Column {
                    LogLevel.entries.forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setMinLevel(level)
                                    minLevelPref = level.name
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
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun StatusHero(
    stats: AppLog.Stats,
    totalEntries: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dev_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.dev_hero_collecting),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeroStat(
                    value = stats.sessionCount.toString(),
                    label = stringResource(R.string.dev_stat_sessions)
                )
                HeroStat(
                    value = totalEntries.toString(),
                    label = stringResource(R.string.dev_stat_entries)
                )
                HeroStat(
                    value = formatBytes(stats.totalBytes),
                    label = stringResource(R.string.dev_stat_size)
                )
                HeroStat(
                    value = stats.crashCount.toString(),
                    label = stringResource(R.string.dev_stat_crashes),
                    valueColor = if (stats.crashCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CrashBanner(
    entry: LogEntry,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Report,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.dev_crash_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                text = stringResource(R.string.dev_crash_banner_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dev_mark_reported))
                }
                FilledTonalButton(onClick = onReport) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.dev_action_report))
                }
            }
        }
    }
}
