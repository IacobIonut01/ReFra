/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.ui.CloudAccountDeletionState
import com.dot.gallery.cloud.ui.CloudAccountsViewModel
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_provider_settings
import org.jetbrains.compose.resources.stringResource


@Composable
fun CloudProviderSettingsScreen(
    configId: Long,
    onDeleted: () -> Unit = {},
    viewModel: CloudAccountsViewModel = injectViewModel { cloudAccountsViewModel }
) {
    val configs by viewModel.accountState.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()
    val storageInfoMap by viewModel.storageInfo.collectAsStateWithLifecycle()
    val serverVersions by viewModel.serverVersions.collectAsStateWithLifecycle()
    val syncProgressMap by viewModel.syncProgress.collectAsStateWithLifecycle()
    val deletionState by viewModel.deletionState.collectAsStateWithLifecycle()
    val downloadedCopiesMap by viewModel.downloadedCopies.collectAsStateWithLifecycle()
    val removingCopies by viewModel.removingCopies.collectAsStateWithLifecycle()
    val isDeleting = deletionState == CloudAccountDeletionState.Deleting(configId)
    val deletionFailed = deletionState == CloudAccountDeletionState.Failed(configId)

    LaunchedEffect(deletionState, configId) {
        if (deletionState == CloudAccountDeletionState.Deleted(configId)) {
            onDeleted()
        }
    }

    val config = configs.find { it.id == configId }

    LaunchedEffect(configId) {
        viewModel.loadStorageInfo()
        viewModel.loadServerVersions()
        viewModel.loadDownloadedCopiesInfo()
    }

    if (config == null) {
        AccountSettingsStateScreen(
            title = stringResource(Res.string.cloud_provider_settings),
            state = if (isDeleting || deletionState == CloudAccountDeletionState.Deleted(configId)) {
                AccountSettingsLoadState.LOADING
            } else {
                AccountSettingsLoadState.ERROR
            }
        )
        return
    }

    CloudProviderSettingsContent(
        state = CloudProviderSettingsContentState(
            config = config,
            connectionState = connectionStates[config.id] ?: ConnectionState.DISCONNECTED,
            storage = storageInfoMap[config.id],
            serverVersion = serverVersions[config.id],
            syncProgress = syncProgressMap[config.id],
            copiesInfo = downloadedCopiesMap[config.id],
            removingCopies = config.id in removingCopies,
            deleting = isDeleting,
            deletionFailed = deletionFailed,
        ),
        actions = CloudProviderSettingsContentActions(
            updateConfig = { transform -> viewModel.updateConfigById(configId, transform) },
            syncNow = { viewModel.triggerSync(configId) },
            triggerDownload = { viewModel.triggerDownload(configId) },
            pendingDownloadStats = { viewModel.pendingDownloadStats(configId) },
            removeDownloadedCopies = { viewModel.removeDownloadedCopies(configId) },
            deleteServer = { viewModel.deleteServer(configId) },
            setLibraryScope = { scope -> viewModel.setLibraryScope(configId, scope) },
        ),
    )
}
