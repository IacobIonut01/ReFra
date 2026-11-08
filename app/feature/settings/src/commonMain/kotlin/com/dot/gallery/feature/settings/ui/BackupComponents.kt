/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.backup.BackupContents
import com.dot.gallery.core.backup.BackupSection
import com.dot.gallery.core.backup.SectionProgress
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.SummaryCard
import com.dot.gallery.core.ui.components.WizardAction
import com.dot.gallery.core.ui.components.WizardScaffold
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.components.settingsContentWidth
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.backup_done
import com.dot.gallery.feature.settings.generated.resources.backup_include_albums
import com.dot.gallery.feature.settings.generated.resources.backup_include_albums_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_categories
import com.dot.gallery.feature.settings.generated.resources.backup_include_categories_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_configs
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_configs_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_favorites
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_favorites_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_prefs
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_prefs_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_collections
import com.dot.gallery.feature.settings.generated.resources.backup_include_collections_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_device_ui
import com.dot.gallery.feature.settings.generated.resources.backup_include_device_ui_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_edit_backups
import com.dot.gallery.feature.settings.generated.resources.backup_include_edit_backups_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_local_favorites
import com.dot.gallery.feature.settings.generated.resources.backup_include_local_favorites_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_metadata
import com.dot.gallery.feature.settings.generated.resources.backup_include_metadata_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_people
import com.dot.gallery.feature.settings.generated.resources.backup_include_people_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_search_history
import com.dot.gallery.feature.settings.generated.resources.backup_include_search_history_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_settings
import com.dot.gallery.feature.settings.generated.resources.backup_include_settings_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_vaults
import com.dot.gallery.feature.settings.generated.resources.backup_include_vaults_summary
import com.dot.gallery.feature.settings.generated.resources.backup_items_count
import com.dot.gallery.feature.settings.generated.resources.backup_vaults_value
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// =================================================================================================
// Shared pieces of the Create backup and Restore wizards.
// =================================================================================================

private fun positionIn(index: Int, count: Int): Position = when {
    count == 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}

/** A grouped list of section switches, each with its Outlined icon. */
@Composable
fun BackupSectionList(
    sections: List<BackupSection>,
    isSelected: (BackupSection) -> Boolean,
    onToggle: (BackupSection, Boolean) -> Unit,
    headerTitle: String? = null,
    summaryFor: @Composable (BackupSection) -> String = { stringResource(it.summaryRes()) }
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (headerTitle != null) {
            SettingsItem(item = SettingsEntity.Header(title = headerTitle))
        }
        sections.forEachIndexed { index, section ->
            val position = positionIn(index, sections.size)
            val summary = summaryFor(section)
            val pref = rememberSwitchPreference(
                section, isSelected(section), summary, position,
                title = stringResource(section.titleRes()),
                icon = section.icon(),
                summary = summary,
                isChecked = isSelected(section),
                onCheck = { onToggle(section, it) },
                screenPosition = position
            )
            SettingsItem(item = pref)
        }
    }
}

/** Individually selectable vaults, as (id, title, summary) triples, under [headerTitle]. */
@Composable
fun BackupToggleList(
    items: List<Triple<String, String, String?>>,
    isSelected: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    headerTitle: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(item = SettingsEntity.Header(title = headerTitle))
        items.forEachIndexed { index, (id, title, summary) ->
            val position = positionIn(index, items.size)
            val checked = isSelected(id)
            val pref = rememberSwitchPreference(
                id, checked, summary, position,
                title = title,
                icon = Icons.Outlined.Lock,
                summary = summary,
                isChecked = checked,
                onCheck = { onToggle(id, it) },
                screenPosition = position
            )
            SettingsItem(item = pref)
        }
    }
}

/** A quiet icon + 12 sp line inside a wizard body (one per step). */
@Composable
fun BackupNote(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .settingsContentWidth()
            .padding(start = 8.dp, end = 8.dp, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Running export or restore: one row per section with its progress. */
@Composable
fun BackupProgressScreen(
    title: String,
    sections: List<BackupSection>,
    progress: Map<BackupSection, SectionProgress>,
    header: String,
) {
    WizardScaffold(title = title, heading = header) {
        sections.forEachIndexed { index, section ->
            BackupSectionProgressRow(section, progress[section], positionIn(index, sections.size))
        }
    }
}

@Composable
private fun BackupSectionProgressRow(
    section: BackupSection,
    progress: SectionProgress?,
    position: Position,
) {
    val done = progress?.done == true
    val shape = when (position) {
        Position.Alone -> RoundedCornerShape(24.dp)
        Position.Top -> RoundedCornerShape(24.dp, 24.dp, 8.dp, 8.dp)
        Position.Middle -> RoundedCornerShape(8.dp)
        Position.Bottom -> RoundedCornerShape(8.dp, 8.dp, 24.dp, 24.dp)
    }
    Row(
        modifier = Modifier
            .settingsContentWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(section.titleRes()), style = MaterialTheme.typography.titleSmall)
            if (!done && progress != null && progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    text = "${progress.current} / ${progress.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Finished or failed export / restore: a result card with per-section counts. */
@Composable
fun BackupStatusScreen(
    title: String,
    success: Boolean,
    heading: String,
    rows: List<Pair<String, String>>,
    note: String?,
    onDone: () -> Unit,
    doneLabel: String? = null,
) {
    WizardScaffold(
        title = title,
        primary = WizardAction(doneLabel ?: stringResource(Res.string.backup_done), onDone),
    ) {
        SummaryCard(
            icon = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            title = heading,
            body = null,
            containerColor = if (success) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (success) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
        ) {
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        note?.let { BackupNote(icon = Icons.Outlined.ErrorOutline, text = it) }
    }
}

// ----- Section helpers --------------------------------------------------------------------------

fun BackupSection.titleRes(): StringResource = when (this) {
    BackupSection.SETTINGS -> Res.string.backup_include_settings
    BackupSection.LOCAL_FAVORITES -> Res.string.backup_include_local_favorites
    BackupSection.CLOUD_FAVORITES -> Res.string.backup_include_cloud_favorites
    BackupSection.CLOUD_CONFIGS -> Res.string.backup_include_cloud_configs
    BackupSection.VAULTS -> Res.string.backup_include_vaults
    BackupSection.ALBUMS -> Res.string.backup_include_albums
    BackupSection.COLLECTIONS -> Res.string.backup_include_collections
    BackupSection.CATEGORIES -> Res.string.backup_include_categories
    BackupSection.METADATA -> Res.string.backup_include_metadata
    BackupSection.SEARCH_HISTORY -> Res.string.backup_include_search_history
    BackupSection.PEOPLE -> Res.string.backup_include_people
    BackupSection.CLOUD_PREFS -> Res.string.backup_include_cloud_prefs
    BackupSection.EDIT_BACKUPS -> Res.string.backup_include_edit_backups
    BackupSection.DEVICE_UI -> Res.string.backup_include_device_ui
}

fun BackupSection.summaryRes(): StringResource = when (this) {
    BackupSection.SETTINGS -> Res.string.backup_include_settings_summary
    BackupSection.LOCAL_FAVORITES -> Res.string.backup_include_local_favorites_summary
    BackupSection.CLOUD_FAVORITES -> Res.string.backup_include_cloud_favorites_summary
    BackupSection.CLOUD_CONFIGS -> Res.string.backup_include_cloud_configs_summary
    BackupSection.VAULTS -> Res.string.backup_include_vaults_summary
    BackupSection.ALBUMS -> Res.string.backup_include_albums_summary
    BackupSection.COLLECTIONS -> Res.string.backup_include_collections_summary
    BackupSection.CATEGORIES -> Res.string.backup_include_categories_summary
    BackupSection.METADATA -> Res.string.backup_include_metadata_summary
    BackupSection.SEARCH_HISTORY -> Res.string.backup_include_search_history_summary
    BackupSection.PEOPLE -> Res.string.backup_include_people_summary
    BackupSection.CLOUD_PREFS -> Res.string.backup_include_cloud_prefs_summary
    BackupSection.EDIT_BACKUPS -> Res.string.backup_include_edit_backups_summary
    BackupSection.DEVICE_UI -> Res.string.backup_include_device_ui_summary
}

/** The Outlined icon of a section's row. */
fun BackupSection.icon(): ImageVector = when (this) {
    BackupSection.SETTINGS -> Icons.Outlined.Settings
    BackupSection.LOCAL_FAVORITES -> Icons.Outlined.StarOutline
    BackupSection.CLOUD_FAVORITES -> Icons.Outlined.Cloud
    BackupSection.CLOUD_CONFIGS -> Icons.Outlined.Dns
    BackupSection.VAULTS -> Icons.Outlined.Lock
    BackupSection.ALBUMS -> Icons.Outlined.PhotoAlbum
    BackupSection.COLLECTIONS -> Icons.Outlined.Collections
    BackupSection.CATEGORIES -> Icons.Outlined.Category
    BackupSection.METADATA -> Icons.Outlined.Place
    BackupSection.SEARCH_HISTORY -> Icons.AutoMirrored.Outlined.ManageSearch
    BackupSection.PEOPLE -> Icons.Outlined.Face
    BackupSection.CLOUD_PREFS -> Icons.Outlined.Tune
    BackupSection.EDIT_BACKUPS -> Icons.Outlined.History
    BackupSection.DEVICE_UI -> Icons.Outlined.Dashboard
}

@Composable
fun BackupContents.countLabel(section: BackupSection): String = when (section) {
    BackupSection.VAULTS -> stringResource(Res.string.backup_vaults_value, vaultCount, vaultMediaCount)
    else -> stringResource(Res.string.backup_items_count, count(section))
}
