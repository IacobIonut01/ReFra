/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.cloud.ui.sync.SyncStatusViewModel
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import kotlin.math.roundToInt

/**
 * Per-service accent colors derived from the active Material theme so the ring,
 * legend and badges always track the app's color scheme.
 */
@Composable
private fun rememberServicePalette(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        listOf(
            cs.primary,
            cs.tertiary,
            cs.secondary,
            cs.error,
            cs.primaryContainer,
            cs.tertiaryContainer,
            cs.secondaryContainer,
            cs.inversePrimary
        )
    }
}

/** Status dot color for a configured/active account — a fixed green that reads on dark surfaces. */
@Composable
private fun connectedDotColor(): Color = Color(0xFF34C759)

/**
 * Maps an account's live [ConnectionState] to a status dot color + label. An errored account
 * (e.g. failed authentication) is surfaced in the error color with a "Connection error" label
 * instead of being hidden, so the service card stays on screen and the user can fix or retry it.
 */
@Composable
private fun connectionStatus(state: ConnectionState): Pair<Color, String> = when (state) {
    ConnectionState.CONNECTED ->
        connectedDotColor() to stringResource(R.string.cloud_connected)
    ConnectionState.SYNCING ->
        connectedDotColor() to stringResource(R.string.cloud_syncing)
    ConnectionState.AUTHENTICATING ->
        MaterialTheme.colorScheme.tertiary to stringResource(R.string.cloud_authenticating)
    ConnectionState.ERROR ->
        MaterialTheme.colorScheme.error to stringResource(R.string.cloud_error)
    ConnectionState.DISCONNECTED ->
        MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.cloud_disconnected)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupDashboardScreen(
    onNavigateToAlbumPicker: (configId: Long) -> Unit,
    onNavigateToServiceSettings: (configId: Long) -> Unit,
    onNavigateToBackupOptions: () -> Unit,
    onNavigateToUploadDetails: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToDestinations: () -> Unit = {},
    onNavigateToOffline: () -> Unit = {}
) {
    val backupViewModel = hiltViewModel<CloudBackupViewModel>()
    val state by backupViewModel.uiState.collectAsStateWithLifecycle()
    val runningAccounts by backupViewModel.runningAccounts.collectAsStateWithLifecycle()
    val indexState by backupViewModel.indexState.collectAsStateWithLifecycle()

    val syncViewModel = hiltViewModel<SyncStatusViewModel>()
    val syncState by syncViewModel.uiState.collectAsStateWithLifecycle()

    val uploadDetailsViewModel = hiltViewModel<UploadDetailsViewModel>()
    val uploadState by uploadDetailsViewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    // Stable color per service keyed by config id, by their display order.
    val palette = rememberServicePalette()
    val colorByConfig = state.accounts
        .mapIndexed { index, account -> account.configId to palette[index % palette.size] }
        .toMap()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffectScaled(
                    state = LocalHazeState.current,
                    style = LocalHazeBlurStyle.current
                ),
                title = { Text(stringResource(R.string.cloud_backup_dashboard_title)) },
                navigationIcon = { NavigationBackButton() },
                actions = {
                    IconButton(onClick = onNavigateToDestinations) {
                        Icon(
                            Icons.Outlined.AccountTree,
                            contentDescription = stringResource(R.string.cloud_destinations_title)
                        )
                    }
                    IconButton(
                        onClick = { backupViewModel.scanBackupStatus() },
                        enabled = !state.isScanning
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.cloud_backup_rescan))
                    }
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
        // Favor the settings DSL for list rows: this LazyColumn intentionally has NO
        // verticalArrangement spacing and NO extra horizontal content padding, so
        // SettingsItem's own group paddings (applyPaddings) are the single source of
        // truth for list spacing — exactly like BaseSettingsScreen. Custom cards add
        // their own 16dp horizontal + bottom spacing via [cardModifier].
        val cardModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDir),
                end = innerPadding.calculateEndPadding(layoutDir),
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            )
        ) {
            // Hero health bar with verified / filename-assumed / unknown provenance.
            item(key = "hero") {
                HeroDashboard(
                    accounts = state.accounts,
                    colorByConfig = colorByConfig,
                    totalAssets = state.totalAssets,
                    verifiedCount = state.verifiedCount,
                    assumedCount = state.assumedCount,
                    unknownCount = state.unknownCount,
                    isScanning = state.isScanning,
                    scanProgress = state.scanProgress,
                    uploadState = uploadState,
                    modifier = cardModifier.padding(bottom = 16.dp)
                )
            }

            // Live upload card — only present while a backup is actively running
            item(key = "active_upload") {
                AnimatedVisibility(visible = uploadState.isWorkerRunning) {
                    ActiveUploadCard(
                        state = uploadState,
                        modifier = cardModifier.padding(bottom = 16.dp)
                    )
                }
            }

            // Live indexing card — present while remote media is being cached into the DB
            item(key = "active_index") {
                AnimatedVisibility(visible = indexState.isIndexing) {
                    IndexingCard(
                        state = indexState,
                        modifier = cardModifier.padding(bottom = 16.dp)
                    )
                }
            }

            // Global backup & sync actions sit directly below the hero for at-a-glance
            // access, so the primary actions are reachable without scrolling past every
            // service card first.
            if (state.accounts.isNotEmpty()) {
                item(key = "global_actions") {
                    GlobalActionButtons(
                        isBackingUpAll = state.isUploading,
                        isSyncing = syncState.isLoading,
                        canBackup = state.enabledAlbumCount > 0,
                        onBackupAll = { backupViewModel.triggerBackup() },
                        onSyncNow = { syncViewModel.triggerSync() },
                        modifier = cardModifier.padding(bottom = 24.dp)
                    )
                }

                // "Sync when app is opened" (#1241): foreground launches enqueue a
                // one-time sync/upload/download pass for enabled accounts.
                item(key = "sync_on_app_open") {
                    var syncOnAppOpen by Settings.Misc.rememberSyncOnAppOpen()
                    SettingsItem(
                        item = SettingsEntity.SwitchPreference(
                            icon = Icons.Outlined.Sync,
                            title = stringResource(R.string.cloud_sync_on_app_open),
                            summary = stringResource(R.string.cloud_sync_on_app_open_summary),
                            isChecked = syncOnAppOpen,
                            onCheck = { syncOnAppOpen = it },
                            screenPosition = Position.Alone
                        )
                    )
                }
            }

            if (state.accounts.isEmpty() && !state.isScanning) {
                item(key = "empty") {
                    EmptyServices(onAddService = onNavigateToAccounts, modifier = cardModifier)
                }
            } else {
                // Per-service control cards
                item(key = "services_header") {
                    SettingsItem(item = SettingsEntity.Header(title = stringResource(R.string.cloud_backup_services)))
                }
                itemsIndexed(state.accounts, key = { _, account -> account.configId }) { index, account ->
                    // The last card gets extra bottom padding so the following
                    // "Global options" header isn't cramped up against it.
                    val isLast = index == state.accounts.lastIndex
                    ServiceCard(
                        account = account,
                        accentColor = colorByConfig[account.configId] ?: MaterialTheme.colorScheme.primary,
                        isRunning = account.configId in runningAccounts,
                        onToggleAutoSync = { enabled -> backupViewModel.setAutoSync(account.configId, enabled) },
                        onBackupNow = { backupViewModel.triggerBackup(account.configId) },
                        onSelectAlbums = { onNavigateToAlbumPicker(account.configId) },
                        onOpenSettings = { onNavigateToServiceSettings(account.configId) },
                        modifier = cardModifier.padding(bottom = if (isLast) 24.dp else 8.dp)
                    )
                }

                // Global controls — header + DSL preference group (the primary
                // backup & sync actions live below the hero at the top of the screen).
                item(key = "global_header") {
                    SettingsItem(item = SettingsEntity.Header(title = stringResource(R.string.cloud_backup_global_options)))
                }
                item(key = "opt_backup_options") {
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            icon = Icons.Outlined.Tune,
                            title = stringResource(R.string.cloud_backup_options),
                            summary = stringResource(R.string.cloud_backup_options_summary),
                            onClick = onNavigateToBackupOptions,
                            screenPosition = Position.Top
                        )
                    )
                }
                item(key = "opt_offline") {
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            icon = Icons.Outlined.SdStorage,
                            title = stringResource(R.string.cloud_offline_title),
                            summary = stringResource(R.string.cloud_offline_summary),
                            onClick = onNavigateToOffline,
                            screenPosition = Position.Middle
                        )
                    )
                }
                item(key = "opt_upload_details") {
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            icon = Icons.Outlined.CloudUpload,
                            title = stringResource(R.string.cloud_upload_details),
                            summary = stringResource(R.string.cloud_upload_details_idle),
                            onClick = onNavigateToUploadDetails,
                            screenPosition = Position.Bottom
                        )
                    )
                }
            }

            state.error?.let { error ->
                item(key = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = cardModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroDashboard(
    accounts: List<AccountBackupStatus>,
    colorByConfig: Map<Long, Color>,
    totalAssets: Int,
    verifiedCount: Int,
    assumedCount: Int,
    unknownCount: Int,
    isScanning: Boolean,
    scanProgress: String,
    uploadState: UploadDetailsUiState,
    modifier: Modifier = Modifier
) {
    val overallPercent = if (totalAssets > 0)
        (verifiedCount.toFloat() / totalAssets * 100f).roundToInt() else 0
    val reveal by animateFloatAsState(
        targetValue = if (isScanning) 0f else 1f,
        animationSpec = tween(700),
        label = "barReveal"
    )
    val uploadIsVerifying = uploadState.phase == CloudUploadWorker.PHASE_VERIFYING
    val heroStatus = when {
        isScanning -> scanProgress.ifEmpty { stringResource(R.string.cloud_upload_syncing) }
        uploadState.isWorkerRunning && uploadIsVerifying -> stringResource(
            R.string.cloud_backup_verifying_count,
            uploadState.checkedItems,
            uploadState.totalItems
        )
        uploadState.isWorkerRunning && uploadState.totalItems > 0 -> stringResource(
            R.string.cloud_upload_uploading_count,
            uploadState.completedItems.coerceAtMost(uploadState.totalItems),
            uploadState.totalItems
        )
        uploadState.isWorkerRunning -> stringResource(R.string.cloud_upload_syncing)
        else -> "Verified by content hash"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Headline percent + status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScanning) "—" else "$overallPercent%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = heroStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isScanning || uploadState.isWorkerRunning) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }

        // Single segmented health bar
        SegmentedHealthBar(
            accounts = accounts,
            colorByConfig = colorByConfig,
            reveal = reveal,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn(
                value = verifiedCount,
                label = "Verified",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatColumn(
                value = assumedCount,
                label = "Assumed",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatColumn(
                value = unknownCount,
                label = "Unknown",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "$totalAssets selected · Assumed means filename-only match",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        if (accounts.isNotEmpty()) {
            HealthBarLegend(accounts = accounts, colorByConfig = colorByConfig)
        }
    }
}

@Composable
private fun StatColumn(
    value: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SegmentedHealthBar(
    accounts: List<AccountBackupStatus>,
    colorByConfig: Map<Long, Color>,
    reveal: Float,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    // Weight each service by its asset count so larger libraries occupy a wider
    // segment. Empty services still get a minimal slice so they remain visible.
    val weights = accounts.map { it.totalAssets.coerceAtLeast(1) }
    val totalWeight = weights.sum().coerceAtLeast(1)

    Canvas(modifier = modifier) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)

        if (accounts.isEmpty()) {
            drawRoundRect(color = trackColor, cornerRadius = radius)
            return@Canvas
        }

        val gapPx = if (accounts.size > 1) size.height * 0.5f else 0f
        val usableWidth = (size.width - gapPx * (accounts.size - 1)).coerceAtLeast(0f)
        var x = 0f
        accounts.forEachIndexed { index, account ->
            val fraction = weights[index].toFloat() / totalWeight
            val segWidth = usableWidth * fraction
            val color = colorByConfig[account.configId] ?: Color.Gray

            // Faint track (remaining portion)
            drawRoundRect(
                color = color.copy(alpha = 0.22f),
                topLeft = Offset(x, 0f),
                size = Size(segWidth, size.height),
                cornerRadius = radius
            )
            // Solid backed-up portion
            val filledWidth = segWidth * account.progress.coerceIn(0f, 1f) * reveal
            if (filledWidth > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(filledWidth, size.height),
                    cornerRadius = radius
                )
            }
            x += segWidth + gapPx
        }
    }
}

@Composable
private fun HealthBarLegend(
    accounts: List<AccountBackupStatus>,
    colorByConfig: Map<Long, Color>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        accounts.forEach { account ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colorByConfig[account.configId] ?: Color.Gray)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = account.accountLabel,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (account.totalAssets > 0) {
                        "${account.verifiedCount} verified · ${account.assumedCount} assumed · " +
                            "${account.unknownCount} unknown"
                    } else "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Dynamic upload card shown only while a backup worker is actively running.
 * Surfaces the file currently being transferred and overall percentage so the
 * standalone Upload Details screen is no longer required for at-a-glance status.
 */
@Composable
private fun ActiveUploadCard(state: UploadDetailsUiState, modifier: Modifier = Modifier) {
    val isVerifying = state.phase == CloudUploadWorker.PHASE_VERIFYING
    val progressItems = visibleBackupProgressItems(
        state.phase,
        state.checkedItems,
        state.completedItems,
        state.failedItems
    )
    val progress = if (state.totalItems > 0)
        progressItems.toFloat() / state.totalItems else 0f
    val percent = (progress * 100f).roundToInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .animateContentSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isVerifying) {
                        stringResource(R.string.cloud_backup_verifying_existing)
                    } else {
                        stringResource(
                            R.string.cloud_upload_uploading_count,
                            (state.completedItems + 1).coerceAtMost(state.totalItems),
                            state.totalItems
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (isVerifying) {
                        stringResource(
                            R.string.cloud_backup_verifying_count,
                            state.checkedItems,
                            state.totalItems
                        )
                    } else {
                        state.currentFileName.ifEmpty { stringResource(R.string.cloud_upload_syncing) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )

        if (state.failedItems > 0) {
            Text(
                text = stringResource(R.string.cloud_upload_failed_count, state.failedItems),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Card shown while remote media is being cached ("indexed") into the local DB. Mirrors the
 * silent indexing notification so progress can be monitored inside the app too. Progress is
 * count-based/indeterminate since the provider doesn't report a total upfront.
 */
@Composable
private fun IndexingCard(
    state: com.dot.gallery.cloud.sync.CloudIndexProgressManager.IndexState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .animateContentSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cloud_index_notification_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = if (state.accountCount > 1) {
                        stringResource(
                            R.string.cloud_index_progress_multi,
                            state.accountCount,
                            state.totalIndexed
                        )
                    } else {
                        stringResource(
                            R.string.cloud_index_progress_text,
                            state.primaryLabel,
                            state.totalIndexed
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LinearProgressIndicator(
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun ServiceCard(
    account: AccountBackupStatus,
    accentColor: Color,
    isRunning: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onSelectAlbums: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header: brand icon avatar + name + connection status + auto-sync toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                ProviderBrandIcon(
                    providerType = account.providerType,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.accountLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (dotColor, statusLabel) = connectionStatus(account.connectionState)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (account.hasError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " · ${account.providerType.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Switch(
                checked = account.syncEnabled,
                onCheckedChange = onToggleAutoSync
            )
        }

        // Progress
        LinearProgressIndicator(
            progress = { account.progress },
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.18f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
        Text(
            text = if (account.enabledAlbumCount == 0) {
                stringResource(R.string.cloud_backup_no_albums)
            } else {
                "${account.verifiedCount} verified · ${account.assumedCount} assumed · " +
                    "${account.unknownCount} unknown · " + stringResource(
                        R.string.cloud_backup_account_albums,
                        account.enabledAlbumCount
                    )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardButton(
                text = if (isRunning) stringResource(R.string.cloud_backup_uploading_short)
                else stringResource(R.string.cloud_backup_now),
                icon = Icons.Outlined.CloudUpload,
                onClick = onBackupNow,
                enabled = account.enabledAlbumCount > 0,
                loading = isRunning,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = onSelectAlbums,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = stringResource(R.string.cloud_backup_albums),
                    modifier = Modifier.size(20.dp)
                )
            }
            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.cloud_backup_service_settings),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * A filled action button styled to match [com.dot.gallery.core.presentation.components.SetupButton]
 * (rounded, bold) but usable inline within a row.
 */
@Composable
private fun DashboardButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlobalActionButtons(
    isBackingUpAll: Boolean,
    isSyncing: Boolean,
    canBackup: Boolean,
    onBackupAll: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Primary actions, styled like SetupButton
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DashboardButton(
            text = if (isBackingUpAll) stringResource(R.string.cloud_backup_uploading_short)
            else stringResource(R.string.cloud_backup_all_now),
            icon = Icons.Outlined.CloudUpload,
            onClick = onBackupAll,
            enabled = canBackup,
            loading = isBackingUpAll,
            modifier = Modifier.weight(1f)
        )
        DashboardButton(
            text = if (isSyncing) stringResource(R.string.cloud_upload_syncing)
            else stringResource(R.string.cloud_sync_now),
            icon = Icons.Outlined.Sync,
            onClick = onSyncNow,
            loading = isSyncing,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmptyServices(onAddService: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.cloud_backup_no_services),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onAddService) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cloud_backup_add_service))
        }
    }
}
