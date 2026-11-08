/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.ui.components.CloudPage
import com.dot.gallery.cloud.ui.components.CloudProgressBar
import com.dot.gallery.cloud.ui.components.CloudSummaryCard
import com.dot.gallery.cloud.ui.components.CloudTones
import com.dot.gallery.cloud.ui.components.ProviderBadge
import com.dot.gallery.cloud.ui.components.cloudContentWidth
import com.dot.gallery.cloud.ui.components.positionOf
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.ui.components.RadioSettingsItem
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.data.dao.PendingDownloadInfo
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.cloud_adv_troubleshooting
import com.dot.gallery.generated.resources.cloud_advanced
import com.dot.gallery.generated.resources.cloud_authenticating
import com.dot.gallery.generated.resources.cloud_auto_sync
import com.dot.gallery.generated.resources.cloud_auto_sync_interval
import com.dot.gallery.generated.resources.cloud_auto_sync_summary
import com.dot.gallery.generated.resources.cloud_backup_albums
import com.dot.gallery.generated.resources.cloud_backup_options
import com.dot.gallery.generated.resources.cloud_backup_options_summary
import com.dot.gallery.generated.resources.cloud_backup_select_albums
import com.dot.gallery.generated.resources.cloud_connected
import com.dot.gallery.generated.resources.cloud_connection_header
import com.dot.gallery.generated.resources.cloud_credentials
import com.dot.gallery.generated.resources.cloud_credentials_summary
import com.dot.gallery.generated.resources.cloud_delete
import com.dot.gallery.generated.resources.cloud_delete_confirm
import com.dot.gallery.generated.resources.cloud_delete_confirm_action
import com.dot.gallery.generated.resources.cloud_delete_confirm_title
import com.dot.gallery.generated.resources.cloud_delete_failed
import com.dot.gallery.generated.resources.cloud_delete_in_progress
import com.dot.gallery.generated.resources.cloud_disconnected
import com.dot.gallery.generated.resources.cloud_download_remote
import com.dot.gallery.generated.resources.cloud_download_remote_confirm
import com.dot.gallery.generated.resources.cloud_download_remote_confirm_loading
import com.dot.gallery.generated.resources.cloud_download_remote_summary
import com.dot.gallery.generated.resources.cloud_download_videos
import com.dot.gallery.generated.resources.cloud_download_videos_summary
import com.dot.gallery.generated.resources.cloud_error
import com.dot.gallery.generated.resources.cloud_free_space
import com.dot.gallery.generated.resources.cloud_free_space_description
import com.dot.gallery.generated.resources.cloud_net_auto_url_summary
import com.dot.gallery.generated.resources.cloud_networking
import com.dot.gallery.generated.resources.cloud_notif_background_backup
import com.dot.gallery.generated.resources.cloud_notifications
import com.dot.gallery.generated.resources.cloud_offline_summary
import com.dot.gallery.generated.resources.cloud_offline_title
import com.dot.gallery.generated.resources.cloud_remove_account
import com.dot.gallery.generated.resources.cloud_remove_account_note
import com.dot.gallery.generated.resources.cloud_remove_downloaded_copies
import com.dot.gallery.generated.resources.cloud_remove_downloaded_copies_confirm
import com.dot.gallery.generated.resources.cloud_remove_downloaded_copies_removing
import com.dot.gallery.generated.resources.cloud_remove_downloaded_copies_summary
import com.dot.gallery.generated.resources.cloud_retry
import com.dot.gallery.generated.resources.cloud_settings_backup_downloads
import com.dot.gallery.generated.resources.cloud_settings_more_header
import com.dot.gallery.generated.resources.cloud_settings_storage_of
import com.dot.gallery.generated.resources.cloud_sync_header
import com.dot.gallery.generated.resources.cloud_sync_interval_12hr
import com.dot.gallery.generated.resources.cloud_sync_interval_15min
import com.dot.gallery.generated.resources.cloud_sync_interval_1hr
import com.dot.gallery.generated.resources.cloud_sync_interval_24hr
import com.dot.gallery.generated.resources.cloud_sync_interval_30min
import com.dot.gallery.generated.resources.cloud_sync_interval_6hr
import com.dot.gallery.generated.resources.cloud_sync_interval_desc
import com.dot.gallery.generated.resources.cloud_sync_now
import com.dot.gallery.generated.resources.cloud_sync_now_summary
import com.dot.gallery.generated.resources.cloud_sync_now_syncing
import com.dot.gallery.generated.resources.cloud_sync_wifi_only
import com.dot.gallery.generated.resources.cloud_syncing
import com.dot.gallery.generated.resources.cloud_viewer
import com.dot.gallery.generated.resources.cloud_viewer_load_preview
import com.dot.gallery.generated.resources.cloud_wifi_only_summary
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Stateless per-account settings surface: the summary card, the Sync, Backup and downloads,
 * Connection and More groups, the sync-interval sheet and the three dialogs (download
 * confirmation, remove copies, remove account). [CloudProviderSettingsScreen] collects the
 * ViewModel state and delegates here; the docs capture tier feeds a sample account instead.
 *
 * [initialDialog] only seeds the existing remember flags — the production
 * default NONE reproduces the screen exactly as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudProviderSettingsContent(
    state: CloudProviderSettingsContentState,
    actions: CloudProviderSettingsContentActions,
    initialDialog: CloudProviderSettingsDialog = CloudProviderSettingsDialog.NONE,
) {
    val config = state.config
    val eventHandler = LocalEventHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val intervalSheet = rememberAppBottomSheetState()
    var showDeleteDialog by rememberSaveable(config.id) {
        mutableStateOf(initialDialog == CloudProviderSettingsDialog.DELETE_ACCOUNT)
    }
    var showDownloadEnableDialog by rememberSaveable(config.id) {
        mutableStateOf(initialDialog == CloudProviderSettingsDialog.DOWNLOAD)
    }
    var showRemoveCopiesDialog by rememberSaveable(config.id) {
        mutableStateOf(initialDialog == CloudProviderSettingsDialog.REMOVE_DOWNLOADS)
    }
    LaunchedEffect(Unit) {
        if (initialDialog == CloudProviderSettingsDialog.SYNC_INTERVAL) intervalSheet.show()
    }

    if (showDeleteDialog || state.deleting) {
        AlertDialog(
            onDismissRequest = { if (!state.deleting) showDeleteDialog = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
            title = { Text(stringResource(Res.string.cloud_delete_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(Res.string.cloud_delete_confirm))
                    if (state.deleting) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text(stringResource(Res.string.cloud_delete_in_progress))
                        }
                    } else if (state.deletionFailed) {
                        Text(
                            text = stringResource(Res.string.cloud_delete_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.deleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = { actions.deleteServer() }
                ) {
                    Text(
                        stringResource(
                            if (state.deletionFailed) Res.string.cloud_retry else Res.string.cloud_delete_confirm_action
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.deleting,
                    onClick = { showDeleteDialog = false }
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    val syncProgress = state.syncProgress
    val isSyncing = syncProgress?.isSyncing == true
    val copiesInfo = state.copiesInfo
    val copiesCount = copiesInfo?.itemCount ?: 0
    val isRemovingCopies = state.removingCopies
    val showRemoveRow = copiesCount > 0 || isRemovingCopies
    val removeCopiesTitle = stringResource(Res.string.cloud_remove_downloaded_copies)
    val downloadRemoteTitle = stringResource(Res.string.cloud_download_remote)

    val syncRows = buildList {
        add(
            SettingsEntity.Preference(
                icon = Icons.Outlined.Sync,
                title = stringResource(if (isSyncing) Res.string.cloud_sync_now_syncing else Res.string.cloud_sync_now),
                summary = if (isSyncing) syncProgress.message?.ifEmpty { null }
                else stringResource(Res.string.cloud_sync_now_summary),
                enabled = !isSyncing,
                onClick = { actions.syncNow() },
            )
        )
        add(
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.Schedule,
                title = stringResource(Res.string.cloud_auto_sync),
                summary = stringResource(Res.string.cloud_auto_sync_summary),
                isChecked = config.syncEnabled,
                onCheck = { checked -> actions.updateConfig { copy(syncEnabled = checked) } },
            )
        )
        // Interval and Wi-Fi only only apply while auto-sync runs.
        if (config.syncEnabled) {
            add(
                SettingsEntity.Preference(
                    icon = Icons.Outlined.History,
                    title = stringResource(Res.string.cloud_auto_sync_interval),
                    summary = syncIntervalLabel(config.syncIntervalMinutes),
                    onClick = { scope.launch { intervalSheet.show() } },
                )
            )
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.Wifi,
                    title = stringResource(Res.string.cloud_sync_wifi_only),
                    summary = stringResource(Res.string.cloud_wifi_only_summary),
                    isChecked = config.wifiOnly,
                    onCheck = { checked -> actions.updateConfig { copy(wifiOnly = checked) } },
                )
            )
        }
    }

    val backupRows = buildList {
        add(
            SettingsEntity.Preference(
                icon = Icons.Outlined.PhotoLibrary,
                title = stringResource(Res.string.cloud_backup_albums),
                summary = stringResource(Res.string.cloud_backup_select_albums),
                onClick = { eventHandler.navigate(Screen.CloudUploadSettingsScreen.configId(config.id)) },
            )
        )
        add(
            SettingsEntity.Preference(
                icon = Icons.Outlined.Tune,
                title = stringResource(Res.string.cloud_backup_options),
                summary = stringResource(Res.string.cloud_backup_options_summary),
                onClick = { eventHandler.navigate(Screen.BackupOptionsScreen.configId(config.id)) },
            )
        )
        add(
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.Download,
                title = downloadRemoteTitle,
                summary = stringResource(Res.string.cloud_download_remote_summary),
                isChecked = config.downloadRemoteEnabled,
                onCheck = { checked ->
                    if (checked) showDownloadEnableDialog = true
                    else actions.updateConfig { copy(downloadRemoteEnabled = false) }
                },
            )
        )
        if (config.downloadRemoteEnabled) {
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.Videocam,
                    title = stringResource(Res.string.cloud_download_videos),
                    summary = stringResource(Res.string.cloud_download_videos_summary),
                    isChecked = config.downloadVideos,
                    onCheck = { checked -> actions.updateConfig { copy(downloadVideos = checked) } },
                )
            )
        }
        if (showRemoveRow) {
            add(
                SettingsEntity.Preference(
                    icon = Icons.Outlined.DeleteSweep,
                    title = removeCopiesTitle,
                    summary = if (isRemovingCopies) {
                        stringResource(Res.string.cloud_remove_downloaded_copies_removing)
                    } else {
                        stringResource(
                            Res.string.cloud_remove_downloaded_copies_summary,
                            copiesCount,
                            Formatter.formatFileSize(context, copiesInfo?.totalBytes ?: 0L)
                        )
                    },
                    enabled = copiesCount > 0 && !isRemovingCopies,
                    onClick = { showRemoveCopiesDialog = true },
                )
            )
        }
    }

    val connectionRows = listOf(
        SettingsEntity.Preference(
            icon = Icons.Outlined.Key,
            title = stringResource(Res.string.cloud_credentials),
            summary = stringResource(Res.string.cloud_credentials_summary),
            onClick = { eventHandler.navigate(Screen.CloudEditServerScreen.configId(config.id)) },
        ),
        SettingsEntity.Preference(
            icon = Icons.Outlined.Lan,
            title = stringResource(Res.string.cloud_networking),
            summary = stringResource(Res.string.cloud_net_auto_url_summary),
            onClick = { eventHandler.navigate(Screen.CloudNetworkingScreen.configId(config.id)) },
        ),
    )

    val moreRows = listOf(
        SettingsEntity.Preference(
            icon = Icons.Outlined.CleaningServices,
            title = stringResource(Res.string.cloud_free_space),
            summary = stringResource(Res.string.cloud_free_space_description),
            onClick = { eventHandler.navigate(Screen.FreeUpSpaceScreen.configId(config.id)) },
        ),
        SettingsEntity.Preference(
            icon = Icons.Outlined.Notifications,
            title = stringResource(Res.string.cloud_notifications),
            summary = stringResource(Res.string.cloud_notif_background_backup),
            onClick = { eventHandler.navigate(Screen.CloudNotificationSettingsScreen.configId(config.id)) },
        ),
        SettingsEntity.Preference(
            icon = Icons.Outlined.Image,
            title = stringResource(Res.string.cloud_viewer),
            summary = stringResource(Res.string.cloud_viewer_load_preview),
            onClick = { eventHandler.navigate(Screen.CloudViewerSettingsScreen.configId(config.id)) },
        ),
        SettingsEntity.Preference(
            icon = Icons.Outlined.OfflinePin,
            title = stringResource(Res.string.cloud_offline_title),
            summary = stringResource(Res.string.cloud_offline_summary),
            onClick = { eventHandler.navigate(Screen.CloudOfflineModeScreen()) },
        ),
        SettingsEntity.Preference(
            icon = Icons.Outlined.Settings,
            title = stringResource(Res.string.cloud_advanced),
            summary = stringResource(Res.string.cloud_adv_troubleshooting),
            onClick = { eventHandler.navigate(Screen.CloudAdvancedSettingsScreen.configId(config.id)) },
        ),
    )

    val groups = listOf(
        stringResource(Res.string.cloud_sync_header) to syncRows,
        stringResource(Res.string.cloud_settings_backup_downloads) to backupRows,
        stringResource(Res.string.cloud_connection_header) to connectionRows,
        stringResource(Res.string.cloud_settings_more_header) to moreRows,
    )

    CloudPage(title = config.displayName.ifBlank { config.providerType.displayName }) {
        item(key = "summary") {
            AccountSummary(
                config = config,
                connState = state.connectionState,
                storage = state.storage,
                version = state.serverVersion,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        groups.forEach { (header, rows) ->
            item(key = "header_$header") { SettingsItem(item = SettingsEntity.Header(title = header)) }
            items(rows.withPositions(), key = { "row_${header}_${it.title}" }) { SettingsItem(item = it) }
        }
        item(key = "remove") {
            Column(
                modifier = Modifier.cloudContentWidth().padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.cloud_remove_account))
                }
                Text(
                    text = stringResource(Res.string.cloud_remove_account_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    ModalSheet(
        sheetState = intervalSheet,
        title = stringResource(Res.string.cloud_auto_sync_interval),
        subtitle = stringResource(Res.string.cloud_sync_interval_desc),
        content = {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                syncIntervalOptions.forEachIndexed { index, minutes ->
                    RadioSettingsItem(
                        title = syncIntervalLabel(minutes),
                        selected = minutes == config.syncIntervalMinutes,
                        screenPosition = positionOf(index, syncIntervalOptions.size),
                        backgroundColor = if (minutes == config.syncIntervalMinutes) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        onClick = {
                            actions.updateConfig { copy(syncIntervalMinutes = minutes) }
                            scope.launch { intervalSheet.hide() }
                        },
                    )
                }
            }
        }
    )

    if (showDownloadEnableDialog) {
        val stats by produceState<PendingDownloadInfo?>(initialValue = null, config.id) {
            value = actions.pendingDownloadStats()
        }
        AlertDialog(
            onDismissRequest = { showDownloadEnableDialog = false },
            title = { Text(downloadRemoteTitle) },
            text = {
                Text(
                    stats?.let {
                        stringResource(
                            Res.string.cloud_download_remote_confirm,
                            it.itemCount,
                            Formatter.formatFileSize(context, it.totalBytes)
                        )
                    } ?: stringResource(Res.string.cloud_download_remote_confirm_loading)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadEnableDialog = false
                        actions.updateConfig { copy(downloadRemoteEnabled = true) }
                        actions.triggerDownload()
                    }
                ) {
                    Text(downloadRemoteTitle)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadEnableDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    if (showRemoveCopiesDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveCopiesDialog = false },
            title = { Text(removeCopiesTitle) },
            text = {
                Text(
                    stringResource(
                        Res.string.cloud_remove_downloaded_copies_confirm,
                        copiesCount,
                        Formatter.formatFileSize(context, copiesInfo?.totalBytes ?: 0L)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        actions.removeDownloadedCopies()
                        showRemoveCopiesDialog = false
                    }
                ) {
                    Text(stringResource(Res.string.cloud_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveCopiesDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

/** Gives each row of a group its Top/Middle/Bottom shape. */
private fun List<SettingsEntity>.withPositions(): List<SettingsEntity> = mapIndexed { index, item ->
    val position = positionOf(index, size)
    when (item) {
        is SettingsEntity.Preference -> item.copy(screenPosition = position)
        is SettingsEntity.SwitchPreference -> item.copy(screenPosition = position)
        else -> item
    }
}

/** Connection state, provider and server, plus the server's storage when it reports usage. */
@Composable
private fun AccountSummary(
    config: CloudServerConfigEntity,
    connState: ConnectionState,
    storage: CloudStorageInfo?,
    version: String?,
    modifier: Modifier = Modifier,
) {
    val stateText = when (connState) {
        ConnectionState.CONNECTED -> stringResource(Res.string.cloud_connected)
        ConnectionState.DISCONNECTED -> stringResource(Res.string.cloud_disconnected)
        ConnectionState.SYNCING -> stringResource(Res.string.cloud_syncing)
        ConnectionState.AUTHENTICATING -> stringResource(Res.string.cloud_authenticating)
        ConnectionState.ERROR -> stringResource(Res.string.cloud_error)
    }
    val host = remember(config.serverUrl) {
        config.serverUrl.substringAfter("://").trimEnd('/')
    }
    val tone = when (connState) {
        ConnectionState.ERROR -> CloudTones.error
        ConnectionState.DISCONNECTED -> CloudTones.secondary
        else -> CloudTones.primary
    }
    CloudSummaryCard(
        icon = when (connState) {
            ConnectionState.ERROR -> Icons.Outlined.Warning
            ConnectionState.DISCONNECTED -> Icons.Outlined.CloudOff
            else -> Icons.Outlined.CloudDone
        },
        title = stateText,
        body = listOfNotNull(
            config.providerType.displayName + (version?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""),
            host.ifBlank { null },
        ).joinToString(" · "),
        tone = tone,
        // The provider's own mark (ProviderBrandIcon) on its usual tinted square, so full-colour
        // logos keep their colours instead of sitting on the solid accent.
        badge = { ProviderBadge(config.providerType) },
        modifier = modifier,
    ) {
        if (storage != null) {
            val pct = storage.usedPercentage
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = storage.usedFormatted,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = stringResource(Res.string.cloud_settings_storage_of, storage.totalFormatted),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline()
                    )
                }
                CloudProgressBar(
                    progress = (pct / 100.0).toFloat(),
                    color = when {
                        pct > 90 -> MaterialTheme.colorScheme.error
                        pct > 75 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

private val syncIntervalOptions = listOf(15, 30, 60, 360, 720, 1440)

@Composable
private fun syncIntervalLabel(minutes: Int): String = when (minutes) {
    15 -> stringResource(Res.string.cloud_sync_interval_15min)
    30 -> stringResource(Res.string.cloud_sync_interval_30min)
    60 -> stringResource(Res.string.cloud_sync_interval_1hr)
    360 -> stringResource(Res.string.cloud_sync_interval_6hr)
    720 -> stringResource(Res.string.cloud_sync_interval_12hr)
    1440 -> stringResource(Res.string.cloud_sync_interval_24hr)
    else -> stringResource(Res.string.cloud_sync_interval_6hr)
}
