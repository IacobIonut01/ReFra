/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDetailsScreen() {
    val viewModel = hiltViewModel<UploadDetailsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffectScaled(
                    state = LocalHazeState.current,
                    style = LocalHazeBlurStyle.current
                ),
                title = { Text(stringResource(R.string.cloud_upload_details)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDir) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDir) + 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "summary") {
                val pendingText = if (!state.isWorkerRunning && state.pendingCount > 0) {
                    stringResource(
                        R.string.cloud_upload_pending_total,
                        state.pendingCount,
                        Formatter.formatShortFileSize(context, state.pendingBytes)
                    )
                } else null
                UploadSummaryCard(state = state, pendingText = pendingText)
            }

            if (state.groups.isEmpty() && !state.isWorkerRunning) {
                item(key = "empty") { EmptyPending(isScanning = state.isScanning) }
            } else {
                items(state.groups, key = { it.key }) { group ->
                    UploadGroupCard(
                        group = group,
                        sizeText = Formatter.formatShortFileSize(context, group.totalBytes)
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadSummaryCard(state: UploadDetailsUiState, pendingText: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (state.isWorkerRunning) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .animateContentSize()
            .padding(all = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isWorkerRunning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            } else {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Text(
                text = when {
                    state.isWorkerRunning && state.phase == CloudUploadWorker.PHASE_VERIFYING ->
                        stringResource(R.string.cloud_backup_verifying_existing)
                    state.isWorkerRunning -> stringResource(R.string.cloud_upload_syncing)
                    pendingText != null -> stringResource(R.string.cloud_upload_ready)
                    else -> stringResource(R.string.cloud_upload_details_not_running)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (state.isWorkerRunning && state.totalItems > 0) {
            val isVerifying = state.phase == CloudUploadWorker.PHASE_VERIFYING
            val progressItems = visibleBackupProgressItems(
                state.phase,
                state.checkedItems,
                state.completedItems,
                state.failedItems
            )
            val progress = progressItems.toFloat() / state.totalItems
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isVerifying) {
                        stringResource(
                            R.string.cloud_backup_verifying_count,
                            state.checkedItems,
                            state.totalItems
                        )
                    } else {
                        stringResource(R.string.cloud_upload_completed_count, state.completedItems)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (state.failedItems > 0) {
                    Text(
                        text = stringResource(R.string.cloud_upload_failed_count, state.failedItems),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else if (pendingText != null) {
            Text(
                text = pendingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One (account × album) group of pending uploads: a header identifying the
 * destination + item count + size estimate, and a horizontal strip of thumbnails.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun UploadGroupCard(group: UploadGroup, sizeText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                ProviderBrandIcon(
                    providerType = group.providerType,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${group.accountLabel} · ${group.albumLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.cloud_upload_group_summary, group.items.size, sizeText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.items, key = { it.mediaId }) { item ->
                GlideImage(
                    model = item.uri,
                    contentDescription = item.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

@Composable
private fun EmptyPending(isScanning: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        } else {
            Icon(
                imageVector = Icons.Outlined.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (isScanning) stringResource(R.string.cloud_upload_syncing)
            else stringResource(R.string.cloud_upload_nothing_pending),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
