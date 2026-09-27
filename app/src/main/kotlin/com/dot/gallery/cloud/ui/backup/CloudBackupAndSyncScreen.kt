/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.ui.sync.SyncStatusViewModel
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupAndSyncScreen(
    onNavigateToAlbumPicker: (configId: Long) -> Unit,
    onNavigateToBackupOptions: () -> Unit,
    onNavigateToUploadDetails: () -> Unit
) {
    val backupViewModel = hiltViewModel<CloudBackupViewModel>()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()

    val syncViewModel = hiltViewModel<SyncStatusViewModel>()
    val syncState by syncViewModel.uiState.collectAsStateWithLifecycle()
    val synced by syncViewModel.syncedMedia.collectAsStateWithLifecycle()
    val remoteOnly by syncViewModel.remoteOnlyMedia.collectAsStateWithLifecycle()
    val pendingUpload by syncViewModel.pendingUploadMedia.collectAsStateWithLifecycle()

    val perAccountHeader = stringResource(R.string.cloud_backup_per_account)
    val accountSummaries = mutableMapOf<Long, String>()
    backupState.accounts.forEach { account ->
        accountSummaries[account.configId] = stringResource(
            R.string.cloud_backup_account_progress,
            account.backedUpCount,
            account.totalAssets,
        ) + " · " + stringResource(
            R.string.cloud_backup_account_albums,
            account.enabledAlbumCount,
        )
    }
    val backupHeader = stringResource(R.string.cloud_backup)
    val selectAlbumsTitle = stringResource(R.string.cloud_backup_select_albums)
    val noAlbumsSummary = stringResource(R.string.cloud_backup_no_albums)
    val albumsCountSummary = stringResource(
        R.string.cloud_backup_albums_count,
        backupState.enabledAlbumCount,
    )
    val optionsTitle = stringResource(R.string.cloud_backup_options)
    val optionsSummary = stringResource(R.string.cloud_backup_options_summary)
    val actionsHeader = stringResource(R.string.cloud_backup_actions)
    val syncingTitle = stringResource(R.string.cloud_upload_syncing)
    val startTitle = stringResource(R.string.cloud_backup_start)
    val startSummary = stringResource(R.string.cloud_backup_start_summary)
    val rescanTitle = stringResource(R.string.cloud_backup_rescan)
    val rescanSummary = stringResource(R.string.cloud_backup_rescan_summary)
    val uploadDetailsTitle = stringResource(R.string.cloud_upload_details)
    val uploadDetailsSummary = stringResource(R.string.cloud_upload_details_idle)
    val syncStatusHeader = stringResource(R.string.cloud_sync_status)
    val totalCachedTitle = stringResource(R.string.cloud_sync_total_cached)
    val totalCachedSummary = stringResource(R.string.cloud_sync_items_count, syncState.totalCached)
    val syncedTitle = stringResource(R.string.cloud_sync_synced)
    val syncedSummary = stringResource(R.string.cloud_sync_synced_count, synced.size)
    val remoteOnlyTitle = stringResource(R.string.cloud_sync_remote_only)
    val remoteOnlySummary = stringResource(R.string.cloud_sync_remote_count, remoteOnly.size)
    val pendingUploadTitle = stringResource(R.string.cloud_sync_pending_upload)
    val pendingUploadSummary = stringResource(R.string.cloud_sync_pending_count, pendingUpload.size)
    val syncNowTitle = stringResource(R.string.cloud_sync_now)
    val syncNowSummary = stringResource(R.string.cloud_sync_now_summary)
    val resourceStrings = listOf(
        perAccountHeader,
        backupHeader,
        selectAlbumsTitle,
        noAlbumsSummary,
        albumsCountSummary,
        optionsTitle,
        optionsSummary,
        actionsHeader,
        syncingTitle,
        startTitle,
        startSummary,
        rescanTitle,
        rescanSummary,
        uploadDetailsTitle,
        uploadDetailsSummary,
        syncStatusHeader,
        totalCachedTitle,
        totalCachedSummary,
        syncedTitle,
        syncedSummary,
        remoteOnlyTitle,
        remoteOnlySummary,
        pendingUploadTitle,
        pendingUploadSummary,
        syncNowTitle,
        syncNowSummary,
    )

    val settingsList: SnapshotStateList<SettingsEntity> = remember(
        backupState,
        syncState,
        synced.size,
        remoteOnly.size,
        pendingUpload.size,
        accountSummaries,
        resourceStrings,
    ) {
        buildList {
            // === BACKUP SECTION ===
            // Album selection is per-account: each cloud picks the local albums it
            // backs up, so destinations are never ambiguous.
            if (backupState.accounts.isNotEmpty()) {
                add(SettingsEntity.Header(title = perAccountHeader))
                backupState.accounts.forEachIndexed { index, account ->
                    add(
                        SettingsEntity.Preference(
                            title = account.accountLabel,
                            summary = accountSummaries.getValue(account.configId),
                            onClick = { onNavigateToAlbumPicker(account.configId) },
                            screenPosition = if (index == 0) Position.Top else Position.Middle
                        )
                    )
                }
            } else {
                add(SettingsEntity.Header(title = backupHeader))
                add(
                    SettingsEntity.Preference(
                        title = selectAlbumsTitle,
                        summary = if (backupState.enabledAlbumCount == 0) noAlbumsSummary
                        else albumsCountSummary,
                        onClick = { onNavigateToAlbumPicker(-1L) },
                        screenPosition = Position.Top
                    )
                )
            }
            add(
                SettingsEntity.Preference(
                    title = optionsTitle,
                    summary = optionsSummary,
                    onClick = onNavigateToBackupOptions,
                    screenPosition = Position.Bottom
                )
            )

            // Backup actions
            add(SettingsEntity.Header(title = actionsHeader))
            add(
                SettingsEntity.Preference(
                    title = if (backupState.isUploading) syncingTitle else startTitle,
                    summary = startSummary,
                    enabled = !backupState.isUploading && backupState.enabledAlbumCount > 0,
                    onClick = { backupViewModel.triggerBackup() },
                    screenPosition = Position.Top
                )
            )
            add(
                SettingsEntity.Preference(
                    title = rescanTitle,
                    summary = rescanSummary,
                    enabled = !backupState.isScanning,
                    onClick = { backupViewModel.scanBackupStatus() },
                    screenPosition = Position.Middle
                )
            )
            add(
                SettingsEntity.Preference(
                    title = uploadDetailsTitle,
                    summary = uploadDetailsSummary,
                    onClick = onNavigateToUploadDetails,
                    screenPosition = Position.Bottom
                )
            )

            // === SYNC STATUS SECTION ===
            add(SettingsEntity.Header(title = syncStatusHeader))
            add(
                SettingsEntity.Preference(
                    title = totalCachedTitle,
                    summary = totalCachedSummary,
                    screenPosition = Position.Top
                )
            )
            add(
                SettingsEntity.Preference(
                    title = syncedTitle,
                    summary = syncedSummary,
                    screenPosition = Position.Middle
                )
            )
            add(
                SettingsEntity.Preference(
                    title = remoteOnlyTitle,
                    summary = remoteOnlySummary,
                    screenPosition = Position.Middle
                )
            )
            add(
                SettingsEntity.Preference(
                    title = pendingUploadTitle,
                    summary = pendingUploadSummary,
                    screenPosition = Position.Bottom
                )
            )

            // Sync action
            add(SettingsEntity.Header(title = ""))
            add(
                SettingsEntity.Preference(
                    title = if (syncState.isLoading) syncingTitle else syncNowTitle,
                    summary = syncNowSummary,
                    enabled = !syncState.isLoading,
                    onClick = { syncViewModel.triggerSync() },
                    screenPosition = Position.Alone
                )
            )
        }.toMutableStateList()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffectScaled(
                    state = LocalHazeState.current,
                    style = LocalHazeBlurStyle.current
                ),
                title = { Text(stringResource(R.string.cloud_backup_and_sync)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp
            )
        ) {
            // Hero status card
            item {
                BackupStatusCard(
                    isScanning = backupState.isScanning,
                    isUploading = backupState.isUploading,
                    remainderCount = backupState.remainderCount,
                    totalAssets = backupState.totalAssets,
                    backedUpCount = backupState.backedUpCount,
                    scanProgress = backupState.scanProgress
                )
            }

            // Error messages
            backupState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            syncState.lastSyncError?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Settings items
            itemsIndexed(settingsList) { _, item ->
                SettingsItem(item)
            }
        }
    }
}

@Composable
private fun BackupStatusCard(
    isScanning: Boolean,
    isUploading: Boolean,
    remainderCount: Int,
    totalAssets: Int,
    backedUpCount: Int,
    scanProgress: String
) {
    val allDone = remainderCount == 0 && !isScanning

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
                .padding(all = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (allDone) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allDone) Icons.Outlined.CheckCircle else Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (allDone) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(Modifier.height(12.dp))

            // Status text
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = scanProgress.ifEmpty { stringResource(R.string.cloud_upload_syncing) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (allDone) stringResource(R.string.cloud_backup_all_done)
                    else stringResource(R.string.cloud_backup_remaining, remainderCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Progress bar + stats
            if (!isScanning && totalAssets > 0) {
                Spacer(Modifier.height(16.dp))

                val progress = backedUpCount.toFloat() / totalAssets
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 600),
                    label = "backupProgress"
                )
                val progressColor = if (allDone) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .drawWithCache {
                            onDrawBehind {
                                drawRoundRect(
                                    color = trackColor,
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                                drawRoundRect(
                                    color = progressColor,
                                    size = Size(size.width * animatedProgress, size.height),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(stringResource(R.string.cloud_backup_total), totalAssets.toString())
                    StatItem(stringResource(R.string.cloud_backup_backed_up), backedUpCount.toString())
                    StatItem(stringResource(R.string.cloud_backup_remaining_label), remainderCount.toString())
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
