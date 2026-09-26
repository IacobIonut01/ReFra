/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.R
import com.dot.gallery.core.logging.AreaGroup
import com.dot.gallery.core.logging.Breadcrumb
import com.dot.gallery.core.logging.LogEntry
import com.dot.gallery.core.logging.LogLevel
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val LOG_ROW_TIME_FORMAT get() = SimpleDateFormat("HH:mm:ss", Locale.US)
internal val LOG_DETAIL_TIME_FORMAT get() = SimpleDateFormat("MMM d, HH:mm:ss.SSS", Locale.US)
private val SESSION_FORMAT_IN get() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
private val SESSION_FORMAT_OUT get() = SimpleDateFormat("MMM d, HH:mm", Locale.US)

internal const val CRASH_SESSION_KEY = "__crashes__"

internal sealed interface FeedItem {
    data class Header(val sessionId: String?, val key: String) : FeedItem
    data class Entry(val entry: LogEntry, val key: String) : FeedItem
}

internal fun buildLogFeed(entries: List<LogEntry>): List<FeedItem> {
    val out = ArrayList<FeedItem>(entries.size + 8)
    var lastSession: String? = null
    var headerSeq = 0
    entries.forEachIndexed { index, e ->
        val session = e.session ?: CRASH_SESSION_KEY
        if (session != lastSession) {
            out += FeedItem.Header(sessionId = e.session, key = "header-${headerSeq++}")
            lastSession = session
        }
        out += FeedItem.Entry(e, key = "entry-$index-${e.ts}")
    }
    return out
}

@Composable
internal fun logLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.VERBOSE, LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO -> MaterialTheme.colorScheme.primary
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR, LogLevel.CRASH -> MaterialTheme.colorScheme.error
    LogLevel.EVENT -> MaterialTheme.colorScheme.secondary
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024f)
    else -> "$bytes B"
}

@Composable
internal fun sessionLabel(sessionId: String?): String = if (sessionId == null) {
    stringResource(R.string.dev_crash_logs)
} else {
    val parsed = runCatching {
        SESSION_FORMAT_OUT.format(SESSION_FORMAT_IN.parse(sessionId)!!)
    }.getOrDefault(sessionId)
    stringResource(R.string.dev_session_header, parsed)
}

@Composable
internal fun areaLabel(group: AreaGroup): String = when (group.kind) {
    AreaGroup.Kind.UNGROUPED -> stringResource(R.string.dev_ungrouped)
    AreaGroup.Kind.SCREEN -> group.id
        .removeSuffix("Screen")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")

    else -> group.id
}

@Composable
internal fun groupSummary(group: AreaGroup): String {
    val parts = buildList {
        if (group.errorCount > 0) {
            add(stringResource(R.string.dev_errors_count, group.errorCount))
        }
        if (group.warnCount > 0) {
            add(stringResource(R.string.dev_warnings_count, group.warnCount))
        }
        add(stringResource(R.string.dev_group_counts, group.entries.size))
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun LogFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !selected -> MaterialTheme.colorScheme.surfaceContainerLowest
            accentColor != null -> accent.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "chipBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            if (accentColor != null) accent else MaterialTheme.colorScheme.onPrimaryContainer
        } else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipContent"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        label = "chipBorder"
    )
    val shape = RoundedCornerShape(100)
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.Checkbox,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(shape)
                .background(backgroundColor, shape)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogFiltersCard(
    filters: DeveloperViewModel.Filters,
    onToggleLevel: (LogLevel) -> Unit,
    onRange: (DeveloperViewModel.Range) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LogLevel.entries.forEach { level ->
                    LogFilterChip(
                        label = level.name.lowercase()
                            .replaceFirstChar { it.titlecase(Locale.US) },
                        selected = level in filters.levels,
                        onClick = { onToggleLevel(level) },
                        accentColor = logLevelColor(level)
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val ranges = listOf(
                    DeveloperViewModel.Range.H1 to R.string.dev_range_1h,
                    DeveloperViewModel.Range.D1 to R.string.dev_range_24h,
                    DeveloperViewModel.Range.D7 to R.string.dev_range_7d,
                    DeveloperViewModel.Range.ALL to R.string.dev_range_all,
                    DeveloperViewModel.Range.CUSTOM to R.string.dev_range_custom,
                )
                ranges.forEach { (range, label) ->
                    LogFilterChip(
                        label = stringResource(label),
                        selected = filters.range == range,
                        onClick = { onRange(range) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyLogs(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.dev_empty_logs),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 24.dp, horizontal = 16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogEntryDetailSheet(
    state: AppBottomSheetState,
    entry: LogEntry?,
    onCopy: (LogEntry) -> Unit,
    onReport: (LogEntry) -> Unit
) {
    val scope = rememberCoroutineScope()
    if (state.isVisible && entry != null) {
        val color = logLevelColor(entry.logLevel)
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = { scope.launch { state.hide() } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = entry.logLevel.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = entry.tag,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = LOG_DETAIL_TIME_FORMAT.format(Date(entry.ts)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SelectionContainer {
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                val location = listOfNotNull(entry.screen, entry.scope)
                    .joinToString(" · ")
                if (location.isNotEmpty()) {
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                entry.ctx?.takeIf { it.isNotEmpty() }?.let { ctx ->
                    DetailSectionTitle(stringResource(R.string.dev_context))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            ctx.forEach { (k, v) ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = k,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = v,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                entry.stackTrace?.let { trace ->
                    DetailSectionTitle(stringResource(R.string.dev_stacktrace))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        SelectionContainer {
                            Text(
                                text = trace,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                entry.crumbs?.takeIf { it.isNotEmpty() }?.let { crumbs ->
                    DetailSectionTitle(stringResource(R.string.dev_breadcrumbs_before))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            crumbs.forEach { crumb ->
                                BreadcrumbRow(crumb)
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                ) {
                    FilledTonalButton(onClick = { onCopy(entry) }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(16.dp)
                        )
                        Text(stringResource(R.string.dev_copy_entry))
                    }
                    if (entry.isError) {
                        FilledTonalButton(onClick = { onReport(entry) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp)
                            )
                            Text(stringResource(R.string.dev_report_error))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun BreadcrumbRow(crumb: Breadcrumb) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = LOG_ROW_TIME_FORMAT.format(Date(crumb.ts)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = crumb.label + crumb.ctx
                ?.takeIf { it.isNotEmpty() }
                ?.let { ctx -> "  " + ctx.entries.joinToString(" ") { "${it.key}=${it.value}" } }
                .orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 10.dp)
                .alpha(0.9f)
        )
    }
}
