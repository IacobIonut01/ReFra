/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.NavigationBackButton
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.backup_export
import com.dot.gallery.feature.settings.generated.resources.backup_export_card_desc
import com.dot.gallery.feature.settings.generated.resources.backup_hub_auto_clean
import com.dot.gallery.feature.settings.generated.resources.backup_hub_auto_clean_summary
import com.dot.gallery.feature.settings.generated.resources.backup_hub_clean_now
import com.dot.gallery.feature.settings.generated.resources.backup_hub_cleaning
import com.dot.gallery.feature.settings.generated.resources.backup_hub_created
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_checked
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_issues
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_ok
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_title
import com.dot.gallery.feature.settings.generated.resources.backup_hub_create
import com.dot.gallery.feature.settings.generated.resources.backup_hub_restore
import com.dot.gallery.feature.settings.generated.resources.backup_hub_last_backup
import com.dot.gallery.feature.settings.generated.resources.backup_hub_last_restore
import com.dot.gallery.feature.settings.generated.resources.backup_hub_never
import com.dot.gallery.feature.settings.generated.resources.backup_hub_never_restore
import com.dot.gallery.feature.settings.generated.resources.backup_hub_restored
import com.dot.gallery.feature.settings.generated.resources.backup_hub_running
import com.dot.gallery.feature.settings.generated.resources.backup_import
import com.dot.gallery.feature.settings.generated.resources.backup_import_card_desc
import com.dot.gallery.feature.settings.generated.resources.backup_intro
import com.dot.gallery.feature.settings.generated.resources.backup_warning
import com.dot.gallery.feature.settings.generated.resources.settings_backup

/** Everything the hub needs, already formatted so this screen stays platform-independent. */
data class BackupHubState(
    val lastExportLabel: String? = null,
    val lastExportSize: String? = null,
    val lastRestoreLabel: String? = null,
    val lastRestoreResult: String? = null,
    val healthCheckedLabel: String? = null,
    val orphanCount: Int = 0,
    val autoClean: Boolean = true,
    val cleaning: Boolean = false,
    val runningLabel: String? = null
)

data class BackupHubActions(
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onCleanNow: () -> Unit,
    val onAutoCleanChange: (Boolean) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(
    state: BackupHubState,
    actions: BackupHubActions
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.settings_backup)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.backup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                state.runningLabel?.let { label ->
                    HubInfoCard(
                        icon = Icons.Outlined.FileUpload,
                        title = label,
                        body = stringResource(Res.string.backup_hub_running, label)
                    )
                }

                BackupBigActionCard(
                    icon = Icons.Outlined.FileUpload,
                    title = stringResource(Res.string.backup_hub_create),
                    description = state.lastExportLabel?.let {
                        stringResource(Res.string.backup_hub_created, it) +
                            (state.lastExportSize?.takeIf { size -> size.isNotBlank() }?.let { size -> " · $size" } ?: "")
                    } ?: stringResource(Res.string.backup_export_card_desc),
                    onClick = actions.onExport
                )
                BackupBigActionCard(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(Res.string.backup_hub_restore),
                    description = state.lastRestoreLabel?.let {
                        stringResource(Res.string.backup_hub_restored, it) +
                            (state.lastRestoreResult?.takeIf { result -> result.isNotBlank() }?.let { result -> " · $result" } ?: "")
                    } ?: stringResource(Res.string.backup_import_card_desc),
                    onClick = actions.onImport
                )

                DataHealthCard(state = state, actions = actions)

                BackupNoticeCard(
                    icon = Icons.Outlined.Shield,
                    text = stringResource(Res.string.backup_warning)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DataHealthCard(
    state: BackupHubState,
    actions: BackupHubActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (state.orphanCount == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.backup_hub_health_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = if (state.orphanCount == 0) {
                stringResource(Res.string.backup_hub_health_ok)
            } else {
                stringResource(Res.string.backup_hub_health_issues, state.orphanCount)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.healthCheckedLabel?.let {
            Text(
                text = stringResource(Res.string.backup_hub_health_checked, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(Res.string.backup_hub_auto_clean),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(Res.string.backup_hub_auto_clean_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.autoClean, onCheckedChange = actions.onAutoCleanChange)
        }
        Button(onClick = actions.onCleanNow, enabled = !state.cleaning) {
            if (state.cleaning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.backup_hub_cleaning))
            } else {
                Text(stringResource(Res.string.backup_hub_clean_now))
            }
        }
    }
}

@Composable
private fun HubInfoCard(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun BackupBigActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
