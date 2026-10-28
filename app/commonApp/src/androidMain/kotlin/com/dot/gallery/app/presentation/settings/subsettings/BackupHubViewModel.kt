/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.backup.BackupJobController
import com.dot.gallery.core.backup.BackupJobSnapshot
import com.dot.gallery.core.backup.BackupJobStatus
import com.dot.gallery.core.backup.DataHealthController
import com.dot.gallery.feature.settings.ui.BackupHubState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class BackupHubViewModel @Inject constructor(
    private val jobs: BackupJobController,
    private val health: DataHealthController
) : ViewModel() {

    private val cleaning = MutableStateFlow(false)

    private data class Partial(
        val job: BackupJobSnapshot?,
        val lastExport: BackupJobSnapshot?,
        val lastRestore: BackupJobSnapshot?,
        val report: com.dot.gallery.core.backup.DataHealthReport?,
        val autoClean: Boolean
    )

    val state: StateFlow<BackupHubState> = combine(
        jobs.job,
        jobs.lastExport,
        jobs.lastRestore,
        health.report,
        health.autoClean
    ) { job, lastExport, lastRestore, report, autoClean ->
        Partial(job, lastExport, lastRestore, report, autoClean)
    }.combine(cleaning) { partial, isCleaning ->
        BackupHubState(
            lastExportLabel = partial.lastExport?.let { formatDate(it.updatedAt) },
            lastExportSize = partial.lastExport?.let { formatBytes(it.payloadBytes) },
            lastRestoreLabel = partial.lastRestore?.let { formatDate(it.updatedAt) },
            lastRestoreResult = partial.lastRestore?.let { restoreSummary(it) },
            healthCheckedLabel = partial.report?.checkedAt?.takeIf { it > 0 }?.let(::formatDate),
            orphanCount = partial.report?.total ?: 0,
            autoClean = partial.autoClean,
            cleaning = isCleaning,
            runningLabel = partial.job?.takeIf { !it.isFinished }?.let(::progressLabel)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupHubState())

    init {
        viewModelScope.launch { health.refresh() }
    }

    fun cleanNow() {
        if (cleaning.value) return
        viewModelScope.launch {
            cleaning.value = true
            runCatching { health.cleanNow() }
            cleaning.value = false
        }
    }

    fun setAutoClean(enabled: Boolean) {
        viewModelScope.launch { health.setAutoClean(enabled) }
    }

    private fun restoreSummary(job: BackupJobSnapshot): String {
        val restored = job.sectionResults.values.sumOf { it.restored }
        val skipped = job.sectionResults.values.sumOf { it.skipped }
        return when (job.status) {
            BackupJobStatus.FAILED -> job.error ?: "Failed"
            BackupJobStatus.CANCELLED -> "Cancelled"
            else -> "$restored restored" + if (skipped > 0) ", $skipped skipped" else ""
        }
    }

    private fun progressLabel(job: BackupJobSnapshot): String {
        val section = job.progress.values.maxByOrNull { it.current }
        return when {
            section == null -> if (job.kind.name == "EXPORT") "Preparing backup…" else "Preparing restore…"
            section.total > 0 -> "${section.section?.name ?: ""} ${section.current}/${section.total}"
            else -> section.section?.name ?: "Working…"
        }
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(millis))

    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
