/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.ui.CloudAccountDeletionState
import com.dot.gallery.cloud.ui.CloudAccountsViewModel
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudProviderSettingsScreen(
    configId: Long,
    onDeleted: () -> Unit = {},
    viewModel: CloudAccountsViewModel = hiltViewModel()
) {
    val configs by viewModel.accountState.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()
    val storageInfoMap by viewModel.storageInfo.collectAsStateWithLifecycle()
    val serverVersions by viewModel.serverVersions.collectAsStateWithLifecycle()
    val syncProgressMap by viewModel.syncProgress.collectAsStateWithLifecycle()
    val deletionState by viewModel.deletionState.collectAsStateWithLifecycle()
    val isDeleting = deletionState == CloudAccountDeletionState.Deleting(configId)
    val deletionFailed = deletionState == CloudAccountDeletionState.Failed(configId)
    val eventHandler = LocalEventHandler.current
    var showDeleteDialog by rememberSaveable(configId) { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deletionState, configId) {
        if (deletionState == CloudAccountDeletionState.Deleted(configId)) {
            showDeleteDialog = false
            onDeleted()
        }
    }

    if (showDeleteDialog || isDeleting) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.cloud_delete_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.cloud_delete_confirm))
                    if (isDeleting) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text(stringResource(R.string.cloud_delete_in_progress))
                        }
                    } else if (deletionFailed) {
                        Text(
                            text = stringResource(R.string.cloud_delete_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = { viewModel.deleteServer(configId) }
                ) {
                    Text(
                        stringResource(
                            if (deletionFailed) R.string.cloud_retry else R.string.cloud_delete_confirm_action
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { showDeleteDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val config = configs.find { it.id == configId }

    LaunchedEffect(configId) {
        viewModel.loadStorageInfo()
        viewModel.loadServerVersions()
    }

    if (config == null) {
        AccountSettingsStateScreen(
            title = stringResource(R.string.cloud_provider_settings),
            state = if (isDeleting || deletionState == CloudAccountDeletionState.Deleted(configId)) {
                AccountSettingsLoadState.LOADING
            } else {
                AccountSettingsLoadState.ERROR
            }
        )
        return
    }

    val connState = connectionStates[config.id] ?: ConnectionState.DISCONNECTED
    val storage = storageInfoMap[config.id]
    val version = serverVersions[config.id]
    val syncProgress = syncProgressMap[config.id]
    val isSyncing = syncProgress?.isSyncing == true
    val syncHeader = stringResource(R.string.cloud_sync_header)
    val syncNowSyncingTitle = stringResource(R.string.cloud_sync_now_syncing)
    val syncNowTitle = stringResource(R.string.cloud_sync_now)
    val syncNowSummary = stringResource(R.string.cloud_sync_now_summary)
    val autoSyncTitle = stringResource(R.string.cloud_auto_sync)
    val autoSyncSummary = stringResource(R.string.cloud_auto_sync_summary)
    val autoSyncIntervalTitle = stringResource(R.string.cloud_auto_sync_interval)
    val syncIntervalSummary = syncIntervalLabel(config.syncIntervalMinutes)
    val wifiOnlyTitle = stringResource(R.string.cloud_sync_wifi_only)
    val wifiOnlySummary = stringResource(R.string.cloud_wifi_only_summary)
    val backupAlbumsTitle = stringResource(R.string.cloud_backup_albums)
    val selectAlbumsSummary = stringResource(R.string.cloud_backup_select_albums)
    val backupOptionsTitle = stringResource(R.string.cloud_backup_options)
    val backupOptionsSummary = stringResource(R.string.cloud_backup_options_summary)
    val connectionHeader = stringResource(R.string.cloud_connection_header)
    val credentialsTitle = stringResource(R.string.cloud_credentials)
    val credentialsSummary = stringResource(R.string.cloud_credentials_summary)
    val networkingTitle = stringResource(R.string.cloud_networking)
    val networkingSummary = stringResource(R.string.cloud_net_auto_url_summary)
    val freeSpaceTitle = stringResource(R.string.cloud_free_space)
    val freeSpaceSummary = stringResource(R.string.cloud_free_space_description)
    val moreHeader = stringResource(R.string.cloud_settings_more_header)
    val notificationsTitle = stringResource(R.string.cloud_notifications)
    val notificationsSummary = stringResource(R.string.cloud_notif_background_backup)
    val viewerTitle = stringResource(R.string.cloud_viewer)
    val viewerSummary = stringResource(R.string.cloud_viewer_load_preview)
    val offlineTitle = stringResource(R.string.cloud_offline_title)
    val offlineSummary = stringResource(R.string.cloud_offline_summary)
    val advancedTitle = stringResource(R.string.cloud_advanced)
    val advancedSummary = stringResource(R.string.cloud_adv_troubleshooting)
    val resourceStrings = listOf(
        syncHeader,
        syncNowSyncingTitle,
        syncNowTitle,
        syncNowSummary,
        autoSyncTitle,
        autoSyncSummary,
        autoSyncIntervalTitle,
        syncIntervalSummary,
        wifiOnlyTitle,
        wifiOnlySummary,
        backupAlbumsTitle,
        selectAlbumsSummary,
        backupOptionsTitle,
        backupOptionsSummary,
        connectionHeader,
        credentialsTitle,
        credentialsSummary,
        networkingTitle,
        networkingSummary,
        freeSpaceTitle,
        freeSpaceSummary,
        moreHeader,
        notificationsTitle,
        notificationsSummary,
        viewerTitle,
        viewerSummary,
        offlineTitle,
        offlineSummary,
        advancedTitle,
        advancedSummary,
    )

    val settingsList = remember(config, isSyncing, connState, version, resourceStrings) {
        val items = mutableStateListOf<SettingsEntity>()

        // Sync section
        items.add(SettingsEntity.Header(title = syncHeader))

        items.add(
            SettingsEntity.Preference(
                title = if (isSyncing) syncNowSyncingTitle else syncNowTitle,
                summary = if (isSyncing) (syncProgress.message ?: "") else syncNowSummary,
                enabled = !isSyncing,
                onClick = { viewModel.triggerSync(config.id) },
                screenPosition = Position.Top
            )
        )
        items.add(
            SettingsEntity.SwitchPreference(
                title = autoSyncTitle,
                summary = autoSyncSummary,
                isChecked = config.syncEnabled,
                onCheck = { checked ->
                    viewModel.updateConfigById(configId) { copy(syncEnabled = checked) }
                },
                screenPosition = if (config.syncEnabled) Position.Middle else Position.Bottom
            )
        )
        if (config.syncEnabled) {
            items.add(
                SettingsEntity.Preference(
                    title = autoSyncIntervalTitle,
                    summary = syncIntervalSummary,
                    onClick = { showIntervalDialog = true },
                    screenPosition = Position.Middle
                )
            )
            items.add(
                SettingsEntity.SwitchPreference(
                    title = wifiOnlyTitle,
                    summary = wifiOnlySummary,
                    isChecked = config.wifiOnly,
                    onCheck = { checked ->
                        viewModel.updateConfigById(configId) { copy(wifiOnly = checked) }
                    },
                    screenPosition = Position.Bottom
                )
            )
        }

        // Backup albums for THIS account
        items.add(
            SettingsEntity.Preference(
                title = backupAlbumsTitle,
                summary = selectAlbumsSummary,
                onClick = {
                    eventHandler.navigate(
                        Screen.CloudUploadSettingsScreen.configId(configId)
                    )
                },
                screenPosition = Position.Top
            )
        )
        items.add(
            SettingsEntity.Preference(
                title = backupOptionsTitle,
                summary = backupOptionsSummary,
                onClick = {
                    eventHandler.navigate(Screen.BackupOptionsScreen.configId(configId))
                },
                screenPosition = Position.Bottom
            )
        )

        // Connection section
        items.add(SettingsEntity.Header(title = connectionHeader))
        items.add(
            SettingsEntity.Preference(
                title = credentialsTitle,
                summary = credentialsSummary,
                onClick = {
                    eventHandler.navigate(
                        Screen.CloudEditServerScreen.configId(configId)
                    )
                },
                screenPosition = Position.Top
            )
        )
        items.add(
            SettingsEntity.Preference(
                title = networkingTitle,
                summary = networkingSummary,
                onClick = { eventHandler.navigate(Screen.CloudNetworkingScreen.configId(configId)) },
                screenPosition = Position.Bottom
            )
        )

        // Features section
        items.add(SettingsEntity.Header(title = freeSpaceTitle))
        items.add(
            SettingsEntity.Preference(
                title = freeSpaceTitle,
                summary = freeSpaceSummary,
                onClick = { eventHandler.navigate(Screen.FreeUpSpaceScreen()) },
                screenPosition = Position.Alone
            )
        )

        // Settings section
        items.add(SettingsEntity.Header(title = moreHeader))
        items.add(
            SettingsEntity.Preference(
                title = notificationsTitle,
                summary = notificationsSummary,
                onClick = {
                    eventHandler.navigate(Screen.CloudNotificationSettingsScreen.configId(configId))
                },
                screenPosition = Position.Top
            )
        )
        items.add(
            SettingsEntity.Preference(
                title = viewerTitle,
                summary = viewerSummary,
                onClick = { eventHandler.navigate(Screen.CloudViewerSettingsScreen.configId(configId)) },
                screenPosition = Position.Middle
            )
        )
        items.add(
            SettingsEntity.Preference(
                title = offlineTitle,
                summary = offlineSummary,
                onClick = { eventHandler.navigate(Screen.CloudOfflineModeScreen()) },
                screenPosition = Position.Middle
            )
        )
        items.add(
            SettingsEntity.Preference(
                title = advancedTitle,
                summary = advancedSummary,
                onClick = { eventHandler.navigate(Screen.CloudAdvancedSettingsScreen.configId(configId)) },
                screenPosition = Position.Bottom
            )
        )

        items
    }

    BaseSettingsScreen(
        title = stringResource(R.string.cloud_provider_settings),
        topContent = {
            ProviderSettingsHeader(
                config = config,
                connState = connState,
                storage = storage,
                version = version
            )
        },
        settingsList = settingsList,
        bottomContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SetupButton(
                    text = stringResource(R.string.cloud_delete_provider),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    onClick = { showDeleteDialog = true }
                )
            }
        }
    )

    if (showIntervalDialog) {
        SyncIntervalDialog(
            currentMinutes = config.syncIntervalMinutes,
            onDismiss = { showIntervalDialog = false },
            onSelect = { minutes ->
                showIntervalDialog = false
                viewModel.updateConfigById(configId) { copy(syncIntervalMinutes = minutes) }
            }
        )
    }
}

@Composable
private fun ProviderSettingsHeader(
    config: CloudServerConfigEntity,
    connState: ConnectionState,
    storage: CloudStorageInfo?,
    version: String?
) {
    val stateText = when (connState) {
        ConnectionState.CONNECTED -> stringResource(R.string.cloud_connected)
        ConnectionState.DISCONNECTED -> stringResource(R.string.cloud_disconnected)
        ConnectionState.SYNCING -> stringResource(R.string.cloud_syncing)
        ConnectionState.AUTHENTICATING -> stringResource(R.string.cloud_authenticating)
        ConnectionState.ERROR -> stringResource(R.string.cloud_error)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(all = 20.dp)
        ) {
            // Provider icon + name + status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.displayName.ifBlank { config.providerType.displayName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Server Storage section — only shown when the provider reports usage.
            if (storage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.cloud_profile_storage),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                SettingsStorageBar(storage = storage)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.cloud_storage_used,
                        storage.usedFormatted,
                        storage.totalFormatted
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Info rows inside the card. Server version is only shown when the
            // provider actually reports one; the URL is always shown.
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(12.dp))
            if (!version.isNullOrBlank()) {
                InfoRow(
                    label = stringResource(R.string.cloud_server_version_label),
                    value = version
                )
                Spacer(Modifier.height(8.dp))
            }
            InfoRow(
                label = stringResource(R.string.cloud_server_url_label),
                value = config.serverUrl
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    // Label keeps its intrinsic width; the value takes the remaining space and is
    // end-aligned so long values (e.g. server URLs) wrap onto their own lines
    // instead of overlapping the label.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SettingsStorageBar(storage: CloudStorageInfo?) {
    val fraction = ((storage?.usedPercentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600),
        label = "storageFraction"
    )
    val pct = storage?.usedPercentage ?: 0.0
    val progressColor = when {
        pct > 90 -> MaterialTheme.colorScheme.error
        pct > 75 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val barHeight = 8.dp
    val barCornerRadius = 4.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barCornerRadius))
            .drawWithCache {
                onDrawBehind {
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = CornerRadius(barCornerRadius.toPx(), barCornerRadius.toPx())
                    )
                    drawRoundRect(
                        color = progressColor,
                        size = Size(size.width * animatedFraction, size.height),
                        cornerRadius = CornerRadius(barCornerRadius.toPx(), barCornerRadius.toPx())
                    )
                }
            }
    )
}

private val syncIntervalOptions = listOf(15, 30, 60, 360, 720, 1440)

@Composable
private fun syncIntervalLabel(minutes: Int): String = when (minutes) {
    15 -> stringResource(R.string.cloud_sync_interval_15min)
    30 -> stringResource(R.string.cloud_sync_interval_30min)
    60 -> stringResource(R.string.cloud_sync_interval_1hr)
    360 -> stringResource(R.string.cloud_sync_interval_6hr)
    720 -> stringResource(R.string.cloud_sync_interval_12hr)
    1440 -> stringResource(R.string.cloud_sync_interval_24hr)
    else -> stringResource(R.string.cloud_sync_interval_6hr)
}

@Composable
private fun SyncIntervalDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_auto_sync_interval)) },
        text = {
            Column {
                syncIntervalOptions.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (minutes == currentMinutes)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                            .clickable { onSelect(minutes) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = syncIntervalLabel(minutes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (minutes == currentMinutes)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
