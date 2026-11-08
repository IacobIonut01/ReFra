/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.availableRemoteTypes
import com.dot.gallery.cloud.sync.CloudIndexProgressManager
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.ui.components.CloudNote
import com.dot.gallery.cloud.ui.components.CloudPage
import com.dot.gallery.cloud.ui.components.CloudProgressBar
import com.dot.gallery.cloud.ui.components.CloudSectionLabel
import com.dot.gallery.cloud.ui.components.CloudStat
import com.dot.gallery.cloud.ui.components.CloudSummaryCard
import com.dot.gallery.cloud.ui.components.CloudTones
import com.dot.gallery.cloud.ui.components.ProviderBadge
import com.dot.gallery.cloud.ui.components.cloudContentWidth
import com.dot.gallery.cloud.ui.components.positionOf
import com.dot.gallery.cloud.ui.sync.SyncStatusUiState
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_add_account
import com.dot.gallery.generated.resources.cloud_backup
import com.dot.gallery.generated.resources.cloud_backup_account_albums
import com.dot.gallery.generated.resources.cloud_backup_account_progress
import com.dot.gallery.generated.resources.cloud_backup_all_done
import com.dot.gallery.generated.resources.cloud_backup_dashboard_title
import com.dot.gallery.generated.resources.cloud_backup_no_albums
import com.dot.gallery.generated.resources.cloud_backup_now
import com.dot.gallery.generated.resources.cloud_backup_options
import com.dot.gallery.generated.resources.cloud_backup_options_summary
import com.dot.gallery.generated.resources.cloud_backup_remaining
import com.dot.gallery.generated.resources.cloud_backup_rescan
import com.dot.gallery.generated.resources.cloud_backup_uploading_short
import com.dot.gallery.generated.resources.cloud_backup_verifying_count
import com.dot.gallery.generated.resources.cloud_backup_verifying_existing
import com.dot.gallery.generated.resources.cloud_destinations_subtitle
import com.dot.gallery.generated.resources.cloud_destinations_title
import com.dot.gallery.generated.resources.cloud_free_space
import com.dot.gallery.generated.resources.cloud_free_space_description
import com.dot.gallery.generated.resources.cloud_hub_accounts
import com.dot.gallery.generated.resources.cloud_hub_body_verified
import com.dot.gallery.generated.resources.cloud_hub_checking
import com.dot.gallery.generated.resources.cloud_hub_details
import com.dot.gallery.generated.resources.cloud_hub_empty_body
import com.dot.gallery.generated.resources.cloud_hub_empty_title
import com.dot.gallery.generated.resources.cloud_hub_error_body
import com.dot.gallery.generated.resources.cloud_hub_error_status
import com.dot.gallery.generated.resources.cloud_hub_error_title
import com.dot.gallery.generated.resources.cloud_hub_open_settings
import com.dot.gallery.generated.resources.cloud_hub_percent
import com.dot.gallery.generated.resources.cloud_hub_privacy
import com.dot.gallery.generated.resources.cloud_hub_stat_remaining
import com.dot.gallery.generated.resources.cloud_hub_stat_verified
import com.dot.gallery.generated.resources.cloud_hub_storage
import com.dot.gallery.generated.resources.cloud_hub_sync
import com.dot.gallery.generated.resources.cloud_hub_upload_activity
import com.dot.gallery.generated.resources.cloud_hub_upload_activity_summary
import com.dot.gallery.generated.resources.cloud_hub_works_with
import com.dot.gallery.generated.resources.cloud_index_notification_title
import com.dot.gallery.generated.resources.cloud_index_progress_multi
import com.dot.gallery.generated.resources.cloud_index_progress_text
import com.dot.gallery.generated.resources.cloud_offline_summary
import com.dot.gallery.generated.resources.cloud_offline_title
import com.dot.gallery.generated.resources.cloud_retry
import com.dot.gallery.generated.resources.cloud_sync_on_app_open
import com.dot.gallery.generated.resources.cloud_sync_on_app_open_summary
import com.dot.gallery.generated.resources.cloud_upload_failed_count
import com.dot.gallery.generated.resources.cloud_upload_syncing
import com.dot.gallery.generated.resources.cloud_upload_uploading_count
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The Backup & Sync page: one summary card for the whole backup (healthy, uploading, checking or
 * an account that needs attention), then the accounts, the backup options and the storage tools.
 */
@Composable
fun CloudBackupDashboardContent(
    state: BackupUiState,
    runningAccounts: Set<Long>,
    indexState: CloudIndexProgressManager.IndexState,
    syncState: SyncStatusUiState,
    uploadState: UploadDetailsUiState,
    onScanBackupStatus: () -> Unit,
    onSetAutoSync: (Long, Boolean) -> Unit,
    onBackup: (Long) -> Unit,
    onSyncNow: () -> Unit,
    onNavigateToAlbumPicker: (configId: Long) -> Unit,
    onNavigateToServiceSettings: (configId: Long) -> Unit,
    onNavigateToBackupOptions: () -> Unit,
    onNavigateToUploadDetails: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToDestinations: () -> Unit = {},
    onNavigateToOffline: () -> Unit = {},
    onNavigateToFreeUpSpace: () -> Unit = {},
) {
    val isEmpty = state.accounts.isEmpty() && !state.isScanning
    CloudPage(
        title = stringResource(Res.string.cloud_backup_dashboard_title),
        actions = if (isEmpty) null else {
            {
                GalleryPageTopBarAction(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = stringResource(Res.string.cloud_backup_rescan),
                    onClick = onScanBackupStatus,
                    enabled = !state.isScanning,
                )
            }
        },
    ) {
        if (isEmpty) {
            item(key = "empty") { EmptyBackup(onAddAccount = onNavigateToAccounts) }
            return@CloudPage
        }

        item(key = "summary") {
            BackupSummary(
                state = state,
                indexState = indexState,
                syncState = syncState,
                uploadState = uploadState,
                onBackupAll = { onBackup(-1L) },
                onSyncNow = onSyncNow,
                onOpenSettings = onNavigateToServiceSettings,
                onOpenDetails = onNavigateToUploadDetails,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        state.error?.let { error ->
            item(key = "error") {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.cloudContentWidth().padding(8.dp)
                )
            }
        }

        item(key = "accounts_label") { CloudSectionLabel(title = stringResource(Res.string.cloud_hub_accounts)) }
        val accountRows = state.accounts.size + 1
        state.accounts.forEachIndexed { index, account ->
            item(key = "account_${account.configId}") {
                AccountRow(
                    account = account,
                    isRunning = account.configId in runningAccounts,
                    position = positionOf(index, accountRows),
                    onClick = { onNavigateToServiceSettings(account.configId) },
                    onToggleAutoSync = { onSetAutoSync(account.configId, it) },
                )
            }
        }
        item(key = "add_account") {
            val providers = remember { ProviderType.availableRemoteTypes().joinToString { it.displayName } }
            SettingsItem(
                item = SettingsEntity.Preference(
                    icon = Icons.Outlined.Add,
                    title = stringResource(Res.string.cloud_add_account),
                    summary = providers,
                    screenPosition = positionOf(accountRows - 1, accountRows),
                    onClick = onNavigateToAccounts,
                )
            )
        }

        item(key = "backup_label") { CloudSectionLabel(title = stringResource(Res.string.cloud_backup)) }
        item(key = "backup_options") {
            SettingsItem(
                item = SettingsEntity.Preference(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(Res.string.cloud_backup_options),
                    summary = stringResource(Res.string.cloud_backup_options_summary),
                    screenPosition = Position.Top,
                    onClick = onNavigateToBackupOptions,
                )
            )
        }
        // "Sync when app is opened": foreground launches enqueue a one-time
        // sync/upload/download pass for enabled accounts.
        item(key = "sync_on_app_open") {
            var syncOnAppOpen by Settings.Misc.rememberSyncOnAppOpen()
            SettingsItem(
                item = SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(Res.string.cloud_sync_on_app_open),
                    summary = stringResource(Res.string.cloud_sync_on_app_open_summary),
                    isChecked = syncOnAppOpen,
                    onCheck = { syncOnAppOpen = it },
                    screenPosition = Position.Middle,
                )
            )
        }
        item(key = "upload_activity") {
            val failed = uploadState.failedItems
            SettingsItem(
                item = SettingsEntity.Preference(
                    icon = Icons.Outlined.History,
                    title = stringResource(Res.string.cloud_hub_upload_activity),
                    summary = stringResource(Res.string.cloud_hub_upload_activity_summary),
                    summaryAnnotated = if (failed > 0) buildAnnotatedString {
                        append(stringResource(Res.string.cloud_hub_upload_activity_summary))
                        append('\n')
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                            append(stringResource(Res.string.cloud_upload_failed_count, failed))
                        }
                    } else null,
                    screenPosition = Position.Bottom,
                    onClick = onNavigateToUploadDetails,
                )
            )
        }

        item(key = "storage_label") { CloudSectionLabel(title = stringResource(Res.string.cloud_hub_storage)) }
        item(key = "free_up_space") {
            SettingsItem(
                item = SettingsEntity.Preference(
                    icon = Icons.Outlined.CleaningServices,
                    title = stringResource(Res.string.cloud_free_space),
                    summary = stringResource(Res.string.cloud_free_space_description),
                    screenPosition = Position.Top,
                    onClick = onNavigateToFreeUpSpace,
                )
            )
        }
        item(key = "offline") {
            SettingsItem(
                item = SettingsEntity.Preference(
                    icon = Icons.Outlined.OfflinePin,
                    title = stringResource(Res.string.cloud_offline_title),
                    summary = stringResource(Res.string.cloud_offline_summary),
                    screenPosition = Position.Middle,
                    onClick = onNavigateToOffline,
                )
            )
        }
        item(key = "destinations") {
            SettingsItem(
                item = SettingsEntity.Preference(
                    icon = Icons.Outlined.AccountTree,
                    title = stringResource(Res.string.cloud_destinations_title),
                    summary = stringResource(Res.string.cloud_destinations_subtitle),
                    screenPosition = Position.Bottom,
                    onClick = onNavigateToDestinations,
                )
            )
        }
        item(key = "privacy") {
            CloudNote(icon = Icons.Outlined.VerifiedUser, text = stringResource(Res.string.cloud_hub_privacy))
        }
    }
}

/**
 * The page's summary card. An account in error takes it over first, then a running upload or
 * status check; otherwise it shows how much of the selected library is verified.
 */
@Composable
private fun BackupSummary(
    state: BackupUiState,
    indexState: CloudIndexProgressManager.IndexState,
    syncState: SyncStatusUiState,
    uploadState: UploadDetailsUiState,
    onBackupAll: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: (Long) -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failing = state.accounts.firstOrNull { it.hasError }
    when {
        failing != null -> CloudSummaryCard(
            icon = Icons.Outlined.Warning,
            title = stringResource(Res.string.cloud_hub_error_title, failing.accountLabel),
            body = stringResource(Res.string.cloud_hub_error_body),
            tone = CloudTones.error,
            modifier = modifier,
        ) {
            SummaryActions(
                primaryLabel = stringResource(Res.string.cloud_hub_open_settings),
                onPrimary = { onOpenSettings(failing.configId) },
                secondaryLabel = stringResource(Res.string.cloud_retry),
                secondaryIcon = Icons.Outlined.Sync,
                secondaryLoading = syncState.isLoading,
                onSecondary = onSyncNow,
            )
        }

        uploadState.isWorkerRunning -> {
            val verifying = uploadState.phase == CloudUploadWorker.PHASE_VERIFYING
            val done = visibleBackupProgressItems(
                uploadState.phase,
                uploadState.checkedItems,
                uploadState.completedItems,
                uploadState.failedItems
            )
            val total = uploadState.totalItems
            val progress = if (total > 0) done.toFloat() / total else null
            CloudSummaryCard(
                icon = Icons.Outlined.CloudUpload,
                title = when {
                    verifying -> stringResource(Res.string.cloud_backup_verifying_count, uploadState.checkedItems, total)
                    total > 0 -> stringResource(
                        Res.string.cloud_upload_uploading_count,
                        (uploadState.completedItems + 1).coerceAtMost(total),
                        total
                    )
                    else -> stringResource(Res.string.cloud_upload_syncing)
                },
                body = if (verifying) stringResource(Res.string.cloud_backup_verifying_existing)
                else uploadState.currentFileName.ifEmpty { null },
                modifier = modifier,
            ) {
                ProgressBlock(
                    progress = progress,
                    start = progress?.let { stringResource(Res.string.cloud_hub_percent, (it * 100).roundToInt()) },
                    end = uploadState.failedItems.takeIf { it > 0 }
                        ?.let { stringResource(Res.string.cloud_upload_failed_count, it) },
                )
                IndexingLine(indexState)
                SummaryActions(
                    primaryLabel = stringResource(Res.string.cloud_hub_details),
                    primaryIcon = Icons.Outlined.History,
                    onPrimary = onOpenDetails,
                    secondaryLabel = stringResource(Res.string.cloud_hub_sync),
                    secondaryIcon = Icons.Outlined.Sync,
                    secondaryLoading = syncState.isLoading,
                    onSecondary = onSyncNow,
                )
            }
        }

        state.isScanning -> CloudSummaryCard(
            icon = Icons.Outlined.CloudUpload,
            title = stringResource(Res.string.cloud_hub_checking),
            body = state.scanProgress.ifEmpty { null },
            modifier = modifier,
        ) {
            CloudProgressBar(progress = null, trackColor = MaterialTheme.colorScheme.surfaceContainerLowest)
            IndexingLine(indexState)
        }

        else -> {
            val percent = if (state.totalAssets > 0)
                (state.verifiedCount.toFloat() / state.totalAssets * 100f).roundToInt() else 0
            val allDone = state.remainderCount == 0 && state.totalAssets > 0
            CloudSummaryCard(
                icon = if (allDone) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload,
                title = if (allDone) stringResource(Res.string.cloud_backup_all_done)
                else stringResource(Res.string.cloud_backup_remaining, state.remainderCount),
                body = stringResource(Res.string.cloud_hub_body_verified, percent),
                modifier = modifier,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CloudStat(
                        value = "%,d".format(state.verifiedCount),
                        label = stringResource(Res.string.cloud_hub_stat_verified),
                        modifier = Modifier.weight(1f)
                    )
                    CloudStat(
                        value = "%,d".format(state.remainderCount),
                        label = stringResource(Res.string.cloud_hub_stat_remaining),
                        modifier = Modifier.weight(1f)
                    )
                }
                IndexingLine(indexState)
                SummaryActions(
                    primaryLabel = if (state.isUploading) stringResource(Res.string.cloud_backup_uploading_short)
                    else stringResource(Res.string.cloud_backup_now),
                    primaryIcon = Icons.Outlined.CloudUpload,
                    primaryEnabled = state.enabledAlbumCount > 0,
                    primaryLoading = state.isUploading,
                    onPrimary = onBackupAll,
                    secondaryLabel = stringResource(Res.string.cloud_hub_sync),
                    secondaryIcon = Icons.Outlined.Sync,
                    secondaryLoading = syncState.isLoading,
                    onSecondary = onSyncNow,
                )
            }
        }
    }
}

@Composable
private fun ProgressBlock(progress: Float?, start: String?, end: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CloudProgressBar(progress = progress, trackColor = MaterialTheme.colorScheme.surfaceContainerLowest)
        if (start != null || end != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = start.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (end != null) {
                    Text(text = end, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Remote media being cached into the local index, shown inside the summary card. */
@Composable
private fun IndexingLine(indexState: CloudIndexProgressManager.IndexState) {
    if (!indexState.isIndexing) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.cloud_index_notification_title) + " · " + if (indexState.accountCount > 1) {
                stringResource(Res.string.cloud_index_progress_multi, indexState.accountCount, indexState.totalIndexed)
            } else {
                stringResource(Res.string.cloud_index_progress_text, indexState.primaryLabel, indexState.totalIndexed)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        CloudProgressBar(progress = null, height = 4.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    }
}

@Composable
private fun SummaryActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryIcon: ImageVector? = null,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
    secondaryIcon: ImageVector? = null,
    secondaryLoading: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled && !primaryLoading,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            ButtonContent(primaryLabel, primaryIcon, primaryLoading, MaterialTheme.colorScheme.onPrimary)
        }
        FilledTonalButton(
            onClick = onSecondary,
            enabled = !secondaryLoading,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            ButtonContent(secondaryLabel, secondaryIcon, secondaryLoading, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ButtonContent(label: String, icon: ImageVector?, loading: Boolean, progressColor: Color) {
    if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = progressColor)
    } else if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    if (loading || icon != null) Box(Modifier.size(8.dp))
    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun AccountRow(
    account: AccountBackupStatus,
    isRunning: Boolean,
    position: Position,
    onClick: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (statusColor, status) = when {
        account.hasError -> colors.error to stringResource(Res.string.cloud_hub_error_status)
        account.connectionState == ConnectionState.AUTHENTICATING || isRunning ->
            colors.primary to stringResource(Res.string.cloud_backup_uploading_short)
        account.enabledAlbumCount == 0 -> colors.onSurfaceVariant to stringResource(Res.string.cloud_backup_no_albums)
        else -> colors.primary to stringResource(
            Res.string.cloud_backup_account_progress, account.verifiedCount, account.totalAssets
        ) + " · " + stringResource(Res.string.cloud_backup_account_albums, account.enabledAlbumCount)
    }
    val summary = buildAnnotatedString {
        append(account.providerType.displayName)
        append('\n')
        withStyle(SpanStyle(color = statusColor)) { append(status) }
    }
    SettingsItem(
        item = SettingsEntity.Preference(
            icon = Icons.Outlined.CloudUpload,
            title = account.accountLabel,
            summary = summary.text,
            summaryAnnotated = summary,
            screenPosition = position,
            onClick = onClick,
        ),
        customIcon = { _, _, _ -> ProviderBadge(providerType = account.providerType) },
        customTrailingContent = {
            Switch(checked = account.syncEnabled, onCheckedChange = onToggleAutoSync)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyBackup(onAddAccount: () -> Unit) {
    val providers = remember { ProviderType.availableRemoteTypes() }
    Column(
        modifier = Modifier
            .cloudContentWidth()
            .padding(horizontal = 16.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = stringResource(Res.string.cloud_hub_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.cloud_hub_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onAddAccount, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Box(Modifier.size(8.dp))
            Text(stringResource(Res.string.cloud_add_account))
        }
        Text(
            text = stringResource(Res.string.cloud_hub_works_with),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            providers.forEach { type ->
                GalleryFilterChip(label = type.displayName, selected = false, onClick = onAddAccount)
            }
        }
    }
}
