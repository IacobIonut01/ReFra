/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.sync

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen() {
    val viewModel = hiltViewModel<SyncStatusViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val synced by viewModel.syncedMedia.collectAsStateWithLifecycle()
    val remoteOnly by viewModel.remoteOnlyMedia.collectAsStateWithLifecycle()
    val pendingUpload by viewModel.pendingUploadMedia.collectAsStateWithLifecycle()
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
                title = { Text(stringResource(R.string.cloud_sync_status)) },
                navigationIcon = { NavigationBackButton() },
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
                start = innerPadding.calculateStartPadding(layoutDir),
                end = innerPadding.calculateEndPadding(layoutDir),
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cloud_sync_total_cached)) },
                    supportingContent = { Text(stringResource(R.string.cloud_sync_items_count, state.totalCached)) },
                    leadingContent = { Icon(Icons.Outlined.CloudDone, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cloud_sync_synced)) },
                    supportingContent = { Text(stringResource(R.string.cloud_sync_synced_count, synced.size)) },
                    leadingContent = { Icon(Icons.Outlined.CloudDone, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cloud_sync_remote_only)) },
                    supportingContent = { Text(stringResource(R.string.cloud_sync_remote_count, remoteOnly.size)) },
                    leadingContent = { Icon(Icons.Outlined.CloudOff, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cloud_sync_pending_upload)) },
                    supportingContent = { Text(stringResource(R.string.cloud_sync_pending_count, pendingUpload.size)) },
                    leadingContent = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) }
                )
            }

            state.lastSyncError?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (remoteOnly.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    Button(
                        onClick = { viewModel.downloadAll() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Outlined.CloudDone, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.cloud_download_all))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                Button(
                    onClick = { viewModel.triggerSync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Outlined.Sync, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        if (state.isLoading) stringResource(R.string.cloud_upload_syncing)
                        else stringResource(R.string.cloud_sync_now)
                    )
                }
            }
        }
    }
}
