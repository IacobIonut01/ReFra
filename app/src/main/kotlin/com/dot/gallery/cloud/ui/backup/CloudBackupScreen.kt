/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onNavigateToAlbumPicker: (configId: Long) -> Unit,
    onNavigateToBackupOptions: () -> Unit,
    onNavigateToUploadDetails: () -> Unit
) {
    val viewModel = hiltViewModel<CloudBackupViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                title = { Text(stringResource(R.string.cloud_backup)) },
                navigationIcon = { NavigationBackButton() },
                actions = {
                    IconButton(onClick = onNavigateToBackupOptions) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDir) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDir) + 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (state.remainderCount == 0 && !state.isScanning)
                                Icons.Outlined.CheckCircle else Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (state.isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(
                                text = state.scanProgress.ifEmpty { stringResource(R.string.cloud_upload_syncing) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Text(
                                text = if (state.remainderCount == 0) stringResource(R.string.cloud_backup_all_done)
                                else stringResource(R.string.cloud_backup_remaining, state.remainderCount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Stats
            if (!state.isScanning && state.totalAssets > 0) {
                item {
                    val progress = if (state.totalAssets > 0)
                        state.backedUpCount.toFloat() / state.totalAssets else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(stringResource(R.string.cloud_backup_total), state.totalAssets.toString())
                        StatItem(stringResource(R.string.cloud_backup_backed_up), state.backedUpCount.toString())
                        StatItem(stringResource(R.string.cloud_backup_remaining_label), state.remainderCount.toString())
                    }
                }
            }

            // Per-account breakdown
            if (state.accounts.size > 1) {
                item {
                    Text(
                        text = stringResource(R.string.cloud_backup_per_account),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(state.accounts, key = { it.configId }) { account ->
                    AccountBackupRow(
                        account = account,
                        onClick = { onNavigateToAlbumPicker(account.configId) }
                    )
                }
            }

            // Album selection
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cloud_backup_albums)) },
                    supportingContent = {
                        Text(
                            if (state.enabledAlbumCount == 0) stringResource(R.string.cloud_backup_no_albums)
                            else stringResource(R.string.cloud_backup_albums_count, state.enabledAlbumCount)
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                )
            }
            item {
                SetupButton(
                    text = stringResource(R.string.cloud_backup_select_albums),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    onClick = { onNavigateToAlbumPicker(-1L) }
                )
            }

            // Actions
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SetupButton(
                    text = if (state.isUploading) stringResource(R.string.cloud_upload_syncing)
                           else stringResource(R.string.cloud_backup_start),
                    enabled = !state.isUploading && state.enabledAlbumCount > 0,
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    onClick = { viewModel.triggerBackup() }
                )
            }
            item {
                SetupButton(
                    text = stringResource(R.string.cloud_backup_rescan),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    enabled = !state.isScanning,
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    onClick = { viewModel.scanBackupStatus() }
                )
            }

            state.error?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountBackupRow(
    account: AccountBackupStatus,
    onClick: () -> Unit
) {
    val progress = if (account.totalAssets > 0)
        account.backedUpCount.toFloat() / account.totalAssets else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = account.accountLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.cloud_backup_account_albums, account.enabledAlbumCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
        Text(
            text = stringResource(
                R.string.cloud_backup_account_progress,
                account.backedUpCount,
                account.totalAssets
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
