/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.Footnote
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.StatTile
import com.dot.gallery.core.ui.components.SummaryCard
import com.dot.gallery.core.ui.components.SummaryCardButton
import com.dot.gallery.core.ui.components.settingsContentWidth
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.backup_hub_auto_clean
import com.dot.gallery.feature.settings.generated.resources.backup_hub_auto_clean_summary
import com.dot.gallery.feature.settings.generated.resources.backup_hub_backup_size
import com.dot.gallery.feature.settings.generated.resources.backup_hub_clean_now
import com.dot.gallery.feature.settings.generated.resources.backup_hub_clean_now_summary
import com.dot.gallery.feature.settings.generated.resources.backup_hub_cleaning
import com.dot.gallery.feature.settings.generated.resources.backup_hub_create
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_checked
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_description
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_issues
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_ok
import com.dot.gallery.feature.settings.generated.resources.backup_hub_health_title
import com.dot.gallery.feature.settings.generated.resources.backup_hub_last_backup_on
import com.dot.gallery.feature.settings.generated.resources.backup_hub_never
import com.dot.gallery.feature.settings.generated.resources.backup_hub_restore_short
import com.dot.gallery.feature.settings.generated.resources.backup_hub_restored
import com.dot.gallery.feature.settings.generated.resources.backup_hub_running
import com.dot.gallery.feature.settings.generated.resources.backup_hub_sections_saved
import com.dot.gallery.feature.settings.generated.resources.backup_intro
import com.dot.gallery.feature.settings.generated.resources.backup_warning
import com.dot.gallery.feature.settings.generated.resources.settings_backup
import org.jetbrains.compose.resources.stringResource

/** Everything the hub needs, already formatted so this screen stays platform-independent. */
data class BackupHubState(
    val lastExportLabel: String? = null,
    val lastExportSize: String? = null,
    /** Sections the last backup included. */
    val lastExportSections: Int? = null,
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

private const val CleanNowTag = "backup-clean-now"

/**
 * Backup & restore hub: a summary card for the last backup with Create backup / Restore, the
 * Data health group (status, automatic and one-off cleanup) and the vault-media footnote.
 */
@Composable
fun SettingsBackupScreen(
    state: BackupHubState,
    actions: BackupHubActions,
    listState: LazyListState = rememberLazyListState(),
) {
    val healthHeader = stringResource(Res.string.backup_hub_health_title)
    val healthDescription = stringResource(Res.string.backup_hub_health_description)
    val healthTitle = if (state.orphanCount == 0) stringResource(Res.string.backup_hub_health_ok)
    else stringResource(Res.string.backup_hub_health_issues, state.orphanCount)
    val healthSummary = state.healthCheckedLabel?.let { stringResource(Res.string.backup_hub_health_checked, it) }
    val autoCleanTitle = stringResource(Res.string.backup_hub_auto_clean)
    val autoCleanSummary = stringResource(Res.string.backup_hub_auto_clean_summary)
    val cleanTitle = stringResource(Res.string.backup_hub_clean_now)
    val cleanSummary = stringResource(
        if (state.cleaning) Res.string.backup_hub_cleaning else Res.string.backup_hub_clean_now_summary
    )
    val rows = remember(
        healthHeader, healthTitle, healthSummary, autoCleanTitle, autoCleanSummary,
        cleanTitle, cleanSummary, state.orphanCount, state.autoClean, state.cleaning, actions,
    ) {
        mutableStateListOf<SettingsEntity>(
            SettingsEntity.Header(healthHeader),
            SettingsEntity.Preference(
                icon = if (state.orphanCount == 0) Icons.Outlined.Storage else Icons.Outlined.Warning,
                title = healthTitle,
                summary = healthSummary,
                screenPosition = Position.Top,
            ),
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.Schedule,
                title = autoCleanTitle,
                summary = autoCleanSummary,
                isChecked = state.autoClean,
                onCheck = actions.onAutoCleanChange,
                screenPosition = Position.Middle,
            ),
            SettingsEntity.Preference(
                icon = Icons.Outlined.CleaningServices,
                title = cleanTitle,
                summary = cleanSummary,
                enabled = !state.cleaning,
                onClick = actions.onCleanNow,
                screenPosition = Position.Bottom,
                tag = CleanNowTag,
            ),
        )
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.settings_backup),
        listState = listState,
        topContent = { BackupHubCard(state, actions) },
        settingsList = rows,
        settingsBuilder = { item, _, modifier ->
            when {
                item.isHeader && item.title == healthHeader -> Column(modifier) {
                    SettingsItem(item = item)
                    Text(
                        text = healthDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .settingsContentWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    )
                }
                item.tag == CleanNowTag -> SettingsItem(
                    item = item,
                    modifier = modifier,
                    customTrailingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                else -> SettingsItem(item = item, modifier = modifier)
            }
        },
        bottomContent = {
            Footnote(icon = Icons.Outlined.Shield, text = stringResource(Res.string.backup_warning))
        },
    )
}

@Composable
private fun BackupHubCard(state: BackupHubState, actions: BackupHubActions) {
    val body = listOfNotNull(
        state.runningLabel?.let { stringResource(Res.string.backup_hub_running, it) },
        state.lastRestoreLabel?.let { label ->
            stringResource(Res.string.backup_hub_restored, label) +
                (state.lastRestoreResult?.takeIf(String::isNotBlank)?.let { " · $it" } ?: "")
        },
    ).joinToString("\n").ifEmpty {
        if (state.lastExportLabel == null) stringResource(Res.string.backup_intro) else ""
    }.ifEmpty { null }
    SummaryCard(
        icon = Icons.Outlined.CloudUpload,
        title = state.lastExportLabel?.let { stringResource(Res.string.backup_hub_last_backup_on, it) }
            ?: stringResource(Res.string.backup_hub_never),
        body = body,
        modifier = Modifier.padding(bottom = 20.dp),
    ) {
        if (state.lastExportLabel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.lastExportSections?.let {
                    StatTile(
                        value = it.toString(),
                        label = stringResource(Res.string.backup_hub_sections_saved),
                        modifier = Modifier.weight(1f),
                    )
                }
                state.lastExportSize?.takeIf(String::isNotBlank)?.let {
                    StatTile(
                        value = it,
                        label = stringResource(Res.string.backup_hub_backup_size),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCardButton(
                label = stringResource(Res.string.backup_hub_create),
                icon = Icons.Outlined.Upload,
                filled = true,
                onClick = actions.onExport,
                modifier = Modifier.weight(1f),
            )
            SummaryCardButton(
                label = stringResource(Res.string.backup_hub_restore_short),
                icon = Icons.Outlined.Download,
                onClick = actions.onImport,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
