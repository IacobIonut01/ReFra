/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.core.logging.AreaGroup
import com.dot.gallery.core.logging.LogEntry
import com.dot.gallery.core.logging.LogExporter
import com.dot.gallery.core.logging.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    enum class Mode { RECENT, BY_AREA }

    /** Named quick ranges; [CUSTOM] carries explicit start/end in [Filters]. */
    enum class Range { H1, D1, D7, ALL, CUSTOM }

    data class Filters(
        val levels: Set<LogLevel> = LogLevel.entries.toSet(),
        val query: String = "",
        val range: Range = Range.ALL,
        val rangeStart: Long? = null,
        val rangeEnd: Long? = null,
    )

    data class UiState(
        val loading: Boolean = true,
        val mode: Mode = Mode.RECENT,
        val filters: Filters = Filters(),
        val entries: List<LogEntry> = emptyList(),
        val groups: List<AreaGroup> = emptyList(),
        val expandedGroups: Set<String> = emptySet(),
        val latestErrors: List<LogEntry> = emptyList(),
        val stats: AppLog.Stats = AppLog.Stats(0, 0, 0, false),
        val totalCount: Int = 0,
        val unreportedCrashEntry: LogEntry? = null,
        val minLevel: LogLevel = AppLog.minLevel,
        val breadcrumbsEnabled: Boolean = AppLog.breadcrumbsEnabled,
    )

    private val allEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    private var unreportedCrashFile: File? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            AppLog.appendFlow.collectLatest { entry ->
                allEntries.update { (it + entry).sortedByDescending(LogEntry::ts) }
                recompute()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = AppLog.readEntries()
            allEntries.value = entries
            unreportedCrashFile = AppLog.unreportedCrash()
            val crashEntry = unreportedCrashFile?.let(AppLog::crashEntryForFile)
            _uiState.update {
                it.copy(
                    loading = false,
                    stats = AppLog.stats(),
                    latestErrors = entries.filter(LogEntry::isError).take(LATEST_ERRORS_COUNT),
                    unreportedCrashEntry = crashEntry,
                    minLevel = AppLog.minLevel,
                    breadcrumbsEnabled = AppLog.breadcrumbsEnabled,
                )
            }
            recompute()
        }
    }

    fun setMode(mode: Mode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun toggleLevel(level: LogLevel) {
        _uiState.update { state ->
            val levels = state.filters.levels.toMutableSet()
            if (!levels.add(level)) levels.remove(level)
            if (levels.isEmpty()) levels.addAll(LogLevel.entries)
            state.copy(filters = state.filters.copy(levels = levels))
        }
        recompute()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(filters = it.filters.copy(query = query)) }
        recompute()
    }

    fun setRange(range: Range, customStart: Long? = null, customEnd: Long? = null) {
        val now = System.currentTimeMillis()
        val (start, end) = when (range) {
            Range.H1 -> now - HOUR_MS to now
            Range.D1 -> now - DAY_MS to now
            Range.D7 -> now - WEEK_MS to now
            Range.ALL -> null to null
            Range.CUSTOM -> customStart to customEnd
        }
        _uiState.update {
            it.copy(
                filters = it.filters.copy(range = range, rangeStart = start, rangeEnd = end)
            )
        }
        recompute()
    }

    fun toggleGroup(id: String) {
        _uiState.update { state ->
            val expanded = state.expandedGroups.toMutableSet()
            if (!expanded.add(id)) expanded.remove(id)
            state.copy(expandedGroups = expanded)
        }
    }

    fun clearLogs() {
        AppLog.clearLogs()
        allEntries.value = emptyList()
        _uiState.update {
            it.copy(
                stats = AppLog.stats(),
                latestErrors = emptyList(),
                unreportedCrashEntry = null,
            )
        }
        unreportedCrashFile = null
        recompute()
    }

    /** Wipes the log store and turns collection off. The caller flips the pref. */
    fun disableDeveloperMode() {
        AppLog.wipe()
        AppLog.setEnabled(false)
        allEntries.value = emptyList()
        _uiState.value = UiState(loading = false)
    }

    fun setMinLevel(level: LogLevel) {
        AppLog.minLevel = level
        _uiState.update { it.copy(minLevel = level) }
    }

    fun setBreadcrumbsEnabled(value: Boolean) {
        AppLog.breadcrumbsEnabled = value
        _uiState.update { it.copy(breadcrumbsEnabled = value) }
    }

    fun logTestError() {
        AppLog.log(
            LogLevel.ERROR, "developer-menu", "Test error logged from developer menu",
            RuntimeException("Test exception — safe to ignore"),
            ctx = mapOf("origin" to "developer_menu", "action" to "test_error"),
        )
    }

    fun markCrashReported() {
        unreportedCrashFile?.let(AppLog::markCrashReported)
        unreportedCrashFile = null
        _uiState.update {
            it.copy(unreportedCrashEntry = null, stats = AppLog.stats())
        }
    }

    // --- Export (entries already sanitized; callers pass filtered or group lists) ---

    suspend fun exportClipboardText(
        entries: List<LogEntry>,
        forGithub: Boolean = false,
    ): String = withContext(Dispatchers.Default) {
        LogExporter.clipboardText(context, entries, forGithub)
    }

    suspend fun exportShareFile(entries: List<LogEntry>): File =
        withContext(Dispatchers.IO) {
            LogExporter.shareBundle(context, entries, AppLog.crashFiles())
        }

    suspend fun issueUrlFor(entry: LogEntry?): String = withContext(Dispatchers.Default) {
        val title = if (entry != null) {
            "[BUG] ${entry.message.take(60)}"
        } else {
            "[BUG] Issue reported from ReFra"
        }
        LogExporter.issueUrl(title, LogExporter.issueBody(context, entry))
    }

    private fun recompute() {
        val state = _uiState.value
        val f = state.filters
        val filtered = allEntries.value.filter { e ->
            e.logLevel in f.levels &&
                    (f.rangeStart == null || e.ts >= f.rangeStart) &&
                    (f.rangeEnd == null || e.ts <= f.rangeEnd) &&
                    (f.query.isBlank() ||
                            e.message.contains(f.query, ignoreCase = true) ||
                            e.tag.contains(f.query, ignoreCase = true) ||
                            e.ctx?.values?.any { it.contains(f.query, ignoreCase = true) } == true)
        }
        _uiState.update {
            it.copy(
                entries = filtered,
                groups = AppLog.groupedByArea(filtered),
                totalCount = allEntries.value.size,
            )
        }
    }

    private companion object {
        const val LATEST_ERRORS_COUNT = 5
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
        const val WEEK_MS = 604_800_000L
    }
}
