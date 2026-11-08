/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.ui.sync.SyncStatusViewModel
import com.dot.gallery.injection.injectViewModel


@Composable
fun CloudBackupDashboardScreen(
    onNavigateToAlbumPicker: (configId: Long) -> Unit,
    onNavigateToServiceSettings: (configId: Long) -> Unit,
    onNavigateToBackupOptions: () -> Unit,
    onNavigateToUploadDetails: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToDestinations: () -> Unit = {},
    onNavigateToOffline: () -> Unit = {},
    onNavigateToFreeUpSpace: () -> Unit = {}
) {
    val backupViewModel = injectViewModel<CloudBackupViewModel> {cloudBackupViewModel}
    val state by backupViewModel.uiState.collectAsStateWithLifecycle()
    val runningAccounts by backupViewModel.runningAccounts.collectAsStateWithLifecycle()
    val indexState by backupViewModel.indexState.collectAsStateWithLifecycle()

    val syncViewModel = injectViewModel<SyncStatusViewModel> {syncStatusViewModel}
    val syncState by syncViewModel.uiState.collectAsStateWithLifecycle()

    val uploadDetailsViewModel = injectViewModel<UploadDetailsViewModel> {uploadDetailsViewModel}
    val uploadState by uploadDetailsViewModel.uiState.collectAsStateWithLifecycle()

    CloudBackupDashboardContent(
        state = state,
        runningAccounts = runningAccounts,
        indexState = indexState,
        syncState = syncState,
        uploadState = uploadState,
        onScanBackupStatus = backupViewModel::scanBackupStatus,
        onSetAutoSync = backupViewModel::setAutoSync,
        onBackup = backupViewModel::triggerBackup,
        onSyncNow = syncViewModel::triggerSync,
        onNavigateToAlbumPicker = onNavigateToAlbumPicker,
        onNavigateToServiceSettings = onNavigateToServiceSettings,
        onNavigateToBackupOptions = onNavigateToBackupOptions,
        onNavigateToUploadDetails = onNavigateToUploadDetails,
        onNavigateToAccounts = onNavigateToAccounts,
        onNavigateToDestinations = onNavigateToDestinations,
        onNavigateToOffline = onNavigateToOffline,
        onNavigateToFreeUpSpace = onNavigateToFreeUpSpace,
    )
}
