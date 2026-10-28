/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

/**
 * Feature-facing control surface for the background export/restore job. The implementation lives
 * in `:app:commonApp`; feature modules depend only on this interface (and therefore never on the
 * worker framework).
 */
interface BackupJobController {
    val job: StateFlow<BackupJobSnapshot?>

    /** Most recently finished job of each kind, kept across [clearFinished]. */
    val lastExport: StateFlow<BackupJobSnapshot?>
    val lastRestore: StateFlow<BackupJobSnapshot?>
    val lastImportResult: StateFlow<ConfigBackupManager.ImportResult?>
    val lastExportResult: StateFlow<ConfigBackupManager.ExportResult?>

    fun startExport(destination: Uri, selection: BackupSelection, password: String?)
    fun startRestore(source: Uri, selection: BackupSelection, password: String?)
    fun cancel()
    fun clearFinished()
}
