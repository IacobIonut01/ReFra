/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.subsettings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.backup.BackupContents
import com.dot.gallery.core.backup.BackupJobKind
import com.dot.gallery.core.backup.BackupJobController
import com.dot.gallery.core.backup.BackupJobSnapshot
import com.dot.gallery.core.backup.BackupJobStatus
import com.dot.gallery.core.backup.BackupPasswordException
import com.dot.gallery.core.backup.BackupSection
import com.dot.gallery.core.backup.BackupSelection
import com.dot.gallery.core.backup.ConfigBackupManager
import com.dot.gallery.core.backup.SectionProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


class BackupRestoreViewModel @Inject constructor(
    private val backupManager: ConfigBackupManager,
    private val jobRuntime: BackupJobController
) : ViewModel() {

    /** Selection of which categories to include in export / apply on import. */
    private val _selection = MutableStateFlow(BackupSelection())
    val selection: StateFlow<BackupSelection> = _selection.asStateFlow()

    fun updateSelection(transform: BackupSelection.() -> BackupSelection) {
        _selection.value = _selection.value.transform()
    }

    /** Per-section progress for the currently running export/import. */
    private val _progress = MutableStateFlow<Map<BackupSection, SectionProgress>>(emptyMap())
    val progress: StateFlow<Map<BackupSection, SectionProgress>> = _progress.asStateFlow()

    init {
        viewModelScope.launch {
            jobRuntime.job.collect { snapshot ->
                if (snapshot == null || snapshot.status != BackupJobStatus.RUNNING) return@collect
                _progress.value = snapshot.progress.mapValues { (_, value) ->
                    SectionProgress(value.current, value.total)
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Export wizard
    // -----------------------------------------------------------------------------------------

    sealed interface ExportPhase {
        /** Picking sections / encryption options. */
        data object Configuring : ExportPhase
        data object Running : ExportPhase
        data class Done(val result: ConfigBackupManager.ExportResult) : ExportPhase
        data class Failed(val message: String) : ExportPhase
    }

    private val _exportPhase = MutableStateFlow<ExportPhase>(ExportPhase.Configuring)
    val exportPhase: StateFlow<ExportPhase> = _exportPhase.asStateFlow()

    private var exportObserver: kotlinx.coroutines.Job? = null

    fun startExport(destination: Uri, password: String?) {
        if (_exportPhase.value is ExportPhase.Running) return
        _progress.value = emptyMap()
        _exportPhase.value = ExportPhase.Running
        jobRuntime.startExport(destination, _selection.value, password)
        observeExport()
    }

    private fun observeExport() {
        exportObserver?.cancel()
        exportObserver = viewModelScope.launch {
            jobRuntime.job.collect { snapshot -> handleExport(snapshot) }
        }
    }

    private fun handleExport(snapshot: BackupJobSnapshot?) {
        if (snapshot == null || snapshot.kind != BackupJobKind.EXPORT) return
        when (snapshot.status) {
            BackupJobStatus.RUNNING -> _exportPhase.value = ExportPhase.Running
            BackupJobStatus.COMPLETED, BackupJobStatus.PARTIAL -> {
                _exportPhase.value = ExportPhase.Done(
                    jobRuntime.lastExportResult.value ?: snapshot.toExportResult()
                )
                exportObserver?.cancel()
            }
            BackupJobStatus.FAILED -> {
                _exportPhase.value = ExportPhase.Failed(snapshot.error ?: "Export failed")
                exportObserver?.cancel()
            }
            BackupJobStatus.CANCELLED -> {
                _exportPhase.value = ExportPhase.Failed("Export cancelled")
                exportObserver?.cancel()
            }
        }
    }

    fun resetExport() {
        jobRuntime.clearFinished()
        _exportPhase.value = ExportPhase.Configuring
        _progress.value = emptyMap()
    }

    // -----------------------------------------------------------------------------------------
    // Import wizard
    // -----------------------------------------------------------------------------------------

    sealed interface ImportPhase {
        /** Waiting for the user to pick a backup file. */
        data object PickingFile : ImportPhase
        /** Reading the backup manifest. */
        data object Inspecting : ImportPhase
        /** The backup is encrypted and needs a password. [wrongPassword] is true after a failed attempt. */
        data class NeedsPassword(val source: Uri, val wrongPassword: Boolean = false) : ImportPhase
        /** Manifest read; user picks what to restore. */
        data class Configuring(
            val source: Uri,
            val password: String?,
            val contents: BackupContents
        ) : ImportPhase
        data object Running : ImportPhase
        data class Done(val result: ConfigBackupManager.ImportResult) : ImportPhase
        data class Failed(val message: String) : ImportPhase
    }

    private val _importPhase = MutableStateFlow<ImportPhase>(ImportPhase.PickingFile)
    val importPhase: StateFlow<ImportPhase> = _importPhase.asStateFlow()

    private var importObserver: kotlinx.coroutines.Job? = null

    init {
        // Reattach when the screen is recreated while a worker keeps running.
        val active = jobRuntime.job.value
        if (active?.status == BackupJobStatus.RUNNING) {
            when (active.kind) {
                BackupJobKind.EXPORT -> {
                    _progress.value = active.progress.mapValues { (_, value) ->
                        SectionProgress(value.current, value.total)
                    }
                    _exportPhase.value = ExportPhase.Running
                    observeExport()
                }
                BackupJobKind.RESTORE -> {
                    _progress.value = active.progress.mapValues { (_, value) ->
                        SectionProgress(value.current, value.total)
                    }
                    _importPhase.value = ImportPhase.Running
                    observeImport()
                }
            }
        }
    }

    /** Called once the user has picked a backup file via SAF. */
    fun onImportFilePicked(source: Uri) {
        viewModelScope.launch {
            _importPhase.value = ImportPhase.Inspecting
            val probe = backupManager.probeBackup(source).getOrElse {
                _importPhase.value = ImportPhase.Failed(it.message ?: "Not a ReFra backup")
                return@launch
            }
            // Only an actually-encrypted archive may ask for a password. Vault contents never
            // imply archive encryption.
            if (probe.encrypted) {
                _importPhase.value = ImportPhase.NeedsPassword(source)
            } else {
                inspect(source, null)
            }
        }
    }

    fun submitImportPassword(password: String) {
        val source = (_importPhase.value as? ImportPhase.NeedsPassword)?.source ?: return
        viewModelScope.launch {
            _importPhase.value = ImportPhase.Inspecting
            inspect(source, password)
        }
    }

    private suspend fun inspect(source: Uri, password: String?) {
        val result = backupManager.inspectBackup(source, password)
        _importPhase.value = result.fold(
            onSuccess = { contents ->
                // Default-select only the sections that actually exist in the backup.
                _selection.value = BackupSelection().mergeDefault(contents)
                ImportPhase.Configuring(source, password, contents)
            },
            onFailure = {
                if (it is BackupPasswordException) {
                    ImportPhase.NeedsPassword(source, wrongPassword = true)
                } else {
                    ImportPhase.Failed(it.message ?: "Import failed")
                }
            }
        )
    }

    fun startImport() {
        val configuring = _importPhase.value as? ImportPhase.Configuring ?: return
        _progress.value = emptyMap()
        _importPhase.value = ImportPhase.Running
        jobRuntime.startRestore(configuring.source, _selection.value, configuring.password)
        observeImport()
    }

    private fun observeImport() {
        importObserver?.cancel()
        importObserver = viewModelScope.launch {
            jobRuntime.job.collect { snapshot -> handleImport(snapshot) }
        }
    }

    private fun handleImport(snapshot: BackupJobSnapshot?) {
        if (snapshot == null || snapshot.kind != BackupJobKind.RESTORE) return
        when (snapshot.status) {
            BackupJobStatus.RUNNING -> _importPhase.value = ImportPhase.Running
            BackupJobStatus.COMPLETED, BackupJobStatus.PARTIAL -> {
                val result = jobRuntime.lastImportResult.value
                if (result != null) {
                    _importPhase.value = ImportPhase.Done(result)
                } else {
                    // Process death lost the rich result; show the durable summary.
                    _importPhase.value = ImportPhase.Done(snapshot.toImportSummary())
                }
                importObserver?.cancel()
            }
            BackupJobStatus.FAILED -> {
                _importPhase.value = ImportPhase.Failed(snapshot.error ?: "Import failed")
                importObserver?.cancel()
            }
            BackupJobStatus.CANCELLED -> {
                _importPhase.value = ImportPhase.Failed("Restore cancelled")
                importObserver?.cancel()
            }
        }
    }

    fun cancelRunningJob() {
        jobRuntime.cancel()
    }

    fun resetImport() {
        jobRuntime.clearFinished()
        _importPhase.value = ImportPhase.PickingFile
        _progress.value = emptyMap()
    }
}

private fun BackupJobSnapshot.toExportResult(): ConfigBackupManager.ExportResult {
    val counts = progress.mapValues { it.value.total }
    return ConfigBackupManager.ExportResult(
        settingsCount = counts[BackupSection.SETTINGS] ?: 0,
        localFavoritesCount = counts[BackupSection.LOCAL_FAVORITES] ?: 0,
        cloudFavoritesCount = counts[BackupSection.CLOUD_FAVORITES] ?: 0,
        cloudConfigsCount = counts[BackupSection.CLOUD_CONFIGS] ?: 0,
        vaultCount = counts[BackupSection.VAULTS] ?: 0,
        vaultMediaCount = 0,
        sectionCounts = counts,
        payloadBytes = payloadBytes,
        encrypted = encrypted
    )
}

private fun BackupJobSnapshot.toImportSummary(): ConfigBackupManager.ImportResult {
    val sections = sectionResults
    return ConfigBackupManager.ImportResult(
        settingsRestored = sections[BackupSection.SETTINGS]?.restored ?: 0,
        settingsSkipped = sections[BackupSection.SETTINGS]?.skipped ?: 0,
        cloudFavoritesRestored = sections[BackupSection.CLOUD_FAVORITES]?.restored ?: 0,
        cloudFavoritesSkipped = sections[BackupSection.CLOUD_FAVORITES]?.skipped ?: 0,
        cloudConfigsRestored = sections[BackupSection.CLOUD_CONFIGS]?.restored ?: 0,
        cloudConfigsSkipped = sections[BackupSection.CLOUD_CONFIGS]?.skipped ?: 0,
        cloudConfigsRequiringAuthentication = 0,
        vaultsRestored = sections[BackupSection.VAULTS]?.restored ?: 0,
        vaultsSkipped = sections[BackupSection.VAULTS]?.skipped ?: 0,
        vaultMediaRestored = 0,
        vaultMediaSkipped = 0,
        pendingLocalFavoriteUris = emptyList(),
        issues = emptyList(),
        sectionResults = sections
    )
}
