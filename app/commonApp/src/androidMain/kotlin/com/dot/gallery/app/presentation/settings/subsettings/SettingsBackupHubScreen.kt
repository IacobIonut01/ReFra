/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.feature.settings.ui.BackupHubActions
import com.dot.gallery.feature.settings.ui.SettingsBackupScreen
import com.dot.gallery.injection.injectViewModel

@Composable
fun SettingsBackupHubScreen(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val viewModel = injectViewModel<BackupHubViewModel> { backupHubViewModel }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsBackupScreen(
        state = state,
        actions = BackupHubActions(
            onExport = onExport,
            onImport = onImport,
            onCleanNow = viewModel::cleanNow,
            onAutoCleanChange = viewModel::setAutoClean
        )
    )
}
