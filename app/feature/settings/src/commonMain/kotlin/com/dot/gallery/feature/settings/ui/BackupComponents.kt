/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.backup.BackupContents
import com.dot.gallery.core.backup.BackupSection
import com.dot.gallery.core.backup.BackupSelection
import com.dot.gallery.core.backup.SectionProgress
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.backup_done
import com.dot.gallery.feature.settings.generated.resources.backup_empty
import com.dot.gallery.feature.settings.generated.resources.backup_import_password_desc
import com.dot.gallery.feature.settings.generated.resources.backup_import_password_title
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_configs
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_configs_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_favorites
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_favorites_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_local_favorites
import com.dot.gallery.feature.settings.generated.resources.backup_include_local_favorites_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_settings
import com.dot.gallery.feature.settings.generated.resources.backup_include_settings_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_vaults
import com.dot.gallery.feature.settings.generated.resources.backup_include_vaults_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_albums
import com.dot.gallery.feature.settings.generated.resources.backup_include_albums_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_collections
import com.dot.gallery.feature.settings.generated.resources.backup_include_collections_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_categories
import com.dot.gallery.feature.settings.generated.resources.backup_include_categories_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_metadata
import com.dot.gallery.feature.settings.generated.resources.backup_include_metadata_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_search_history
import com.dot.gallery.feature.settings.generated.resources.backup_include_search_history_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_people
import com.dot.gallery.feature.settings.generated.resources.backup_include_people_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_prefs
import com.dot.gallery.feature.settings.generated.resources.backup_include_cloud_prefs_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_edit_backups
import com.dot.gallery.feature.settings.generated.resources.backup_include_edit_backups_summary
import com.dot.gallery.feature.settings.generated.resources.backup_include_device_ui
import com.dot.gallery.feature.settings.generated.resources.backup_include_device_ui_summary
import com.dot.gallery.feature.settings.generated.resources.backup_preview_encrypted
import com.dot.gallery.feature.settings.generated.resources.backup_preview_plain
import com.dot.gallery.feature.settings.generated.resources.backup_preview_credentials
import com.dot.gallery.feature.settings.generated.resources.backup_preview_no_credentials
import com.dot.gallery.feature.settings.generated.resources.backup_confirm_merge_note
import com.dot.gallery.feature.settings.generated.resources.backup_items_count
import com.dot.gallery.feature.settings.generated.resources.backup_made_with_version
import com.dot.gallery.feature.settings.generated.resources.backup_password
import com.dot.gallery.feature.settings.generated.resources.backup_restore
import com.dot.gallery.feature.settings.generated.resources.backup_restore_header
import com.dot.gallery.feature.settings.generated.resources.backup_show_password
import com.dot.gallery.feature.settings.generated.resources.backup_vaults_value
import com.dot.gallery.feature.settings.generated.resources.backup_warning
import com.dot.gallery.feature.settings.generated.resources.backup_wrong_password
import com.dot.gallery.feature.settings.generated.resources.ok

// =================================================================================================
// Shared components — used by both the export and import wizards.
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupWizardScaffold(
    title: String,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        content = content
    )
}

@Composable
fun BackupCenteredMessage(
    padding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    }
}

/** Maximum content width used to keep the wizard readable on tablets and unfolded foldables. */
private val BACKUP_CONTENT_MAX_WIDTH = 640.dp

/**
 * Scrolling content container shared by the wizard steps. Applies the scaffold insets and a
 * horizontal padding, and constrains the content to an adaptive max width that stays centered
 * on large windows (tablets, unfolded foldables) while filling the width on phones.
 */
@Composable
fun BackupScrollColumn(
    padding: PaddingValues,
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
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
                .widthIn(max = BACKUP_CONTENT_MAX_WIDTH)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content
        )
    }
}

/** Primary wizard action, rendered with the shared [SetupButton] for app-wide consistency. */
@Composable
fun BackupPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    SetupButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        applyHorizontalPadding = false,
        applyBottomPadding = false,
        applyInsets = false
    )
}

/** Secondary wizard action (e.g. Back), styled to match [SetupButton] but tonal. */
@Composable
fun BackupSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    SetupButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        applyHorizontalPadding = false,
        applyBottomPadding = false,
        applyInsets = false,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

/**
 * Renders a group of [BackupSection] toggles using the shared Settings DSL ([SettingsItem] +
 * [rememberSwitchPreference]) so the cards match the rest of the settings UI. Corner positions
 * are assigned manually since this is laid out in a scrolling Column rather than a LazyColumn.
 */
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
            SettingsItem(
                item = SettingsEntity.Header(title = headerTitle),
                applyPaddings = false
            )
        }
        sections.forEachIndexed { index, section ->
            val position = when {
                sections.size == 1 -> Position.Alone
                index == 0 -> Position.Top
                index == sections.lastIndex -> Position.Bottom
                else -> Position.Middle
            }
            val summary = summaryFor(section)
            val pref = rememberSwitchPreference(
                section, isSelected(section), summary, position,
                title = stringResource(section.titleRes()),
                summary = summary,
                isChecked = isSelected(section),
                onCheck = { onToggle(section, it) },
                screenPosition = position
            )
            SettingsItem(item = pref, applyPaddings = false)
        }
    }
}

/**
 * Renders a group of individually-selectable rows (used for picking specific vaults) with the
 * same Settings DSL styling as [BackupSectionList]. [items] are (id, title, summary) triples.
 */
@Composable
fun BackupToggleList(
    items: List<Triple<String, String, String?>>,
    isSelected: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    headerTitle: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(
            item = SettingsEntity.Header(title = headerTitle),
            applyPaddings = false
        )
        items.forEachIndexed { index, (id, title, summary) ->
            val position = when {
                items.size == 1 -> Position.Alone
                index == 0 -> Position.Top
                index == items.lastIndex -> Position.Bottom
                else -> Position.Middle
            }
            val checked = isSelected(id)
            val pref = rememberSwitchPreference(
                id, checked, summary, position,
                title = title,
                summary = summary,
                isChecked = checked,
                onCheck = { onToggle(id, it) },
                screenPosition = position
            )
            SettingsItem(item = pref, applyPaddings = false)
        }
    }
}

@Composable
fun BackupProgressContent(
    padding: PaddingValues,
    sections: List<BackupSection>,
    progress: Map<BackupSection, SectionProgress>,
    header: String
) {
    BackupScrollColumn(padding) {
        Spacer(Modifier.height(4.dp))
        Text(text = header, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        sections.forEach { section ->
            BackupSectionProgressCard(section, progress[section])
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BackupSectionProgressCard(
    section: BackupSection,
    progress: SectionProgress?
) {
    val done = progress?.done == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                else -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(section.titleRes()),
                style = MaterialTheme.typography.titleSmall
            )
            if (!done && progress != null && progress.total > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
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

@Composable
fun BackupStatusContent(
    padding: PaddingValues,
    success: Boolean,
    title: String,
    rows: List<Pair<String, String>>,
    note: String?,
    onDone: () -> Unit,
    doneLabel: String? = null
) {
    // Use a neutral surface with onSurface text for strong, theme-consistent contrast;
    // the success/error state is conveyed by the accent-tinted icon and title only.
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val icon: ImageVector = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline

    BackupScrollColumn(padding, verticalSpacing = 16.dp) {
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(container)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
            }
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
                    Text(
                        value,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer
                    )
                }
            }
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = labelColor)
            }
        }
        BackupPrimaryButton(
            text = doneLabel ?: stringResource(Res.string.backup_done),
            onClick = onDone
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ----- Import steps (shared, param-driven) -------------------------------------------------------

@Composable
fun ImportPasswordContent(
    padding: PaddingValues,
    wrongPassword: Boolean,
    onSubmit: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(wrongPassword) { if (wrongPassword) password = "" }

    BackupScrollColumn(padding) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(Res.string.backup_import_password_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = stringResource(Res.string.backup_import_password_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.backup_password)) },
            singleLine = true,
            isError = wrongPassword,
            supportingText = if (wrongPassword) {
                { Text(stringResource(Res.string.backup_wrong_password)) }
            } else null,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showPassword, onCheckedChange = { showPassword = it })
            Text(stringResource(Res.string.backup_show_password), modifier = Modifier.padding(start = 4.dp))
        }
        BackupPrimaryButton(
            text = stringResource(Res.string.ok),
            enabled = password.isNotBlank(),
            onClick = { onSubmit(password) }
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ImportSelectContent(
    padding: PaddingValues,
    sections: List<BackupSection>,
    contents: BackupContents,
    selection: BackupSelection,
    onToggle: (BackupSection, Boolean) -> Unit,
    onToggleVault: (String, Boolean) -> Unit,
    onRestore: () -> Unit
) {
    val anySelected = sections.any { selection.isOn(it) } &&
        (!selection.vaults || sections.any { it != BackupSection.VAULTS && selection.isOn(it) } ||
            contents.vaults.any { selection.isVaultSelected(it.uuid) })
    BackupScrollColumn(padding) {
        Spacer(Modifier.height(4.dp))
        if (contents.appVersionName.isNotBlank()) {
            Text(
                text = stringResource(Res.string.backup_made_with_version, contents.appVersionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Text(
            text = stringResource(
                if (contents.encrypted) Res.string.backup_preview_encrypted
                else Res.string.backup_preview_plain
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (contents.has(BackupSection.VAULTS)) {
            Text(
                text = stringResource(
                    if (contents.credentialIncluded) Res.string.backup_preview_credentials
                    else Res.string.backup_preview_no_credentials
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Text(
            text = stringResource(Res.string.backup_confirm_merge_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (sections.isEmpty()) {
            Text(
                text = stringResource(Res.string.backup_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            BackupSectionList(
                sections = sections,
                isSelected = { selection.isOn(it) },
                onToggle = onToggle,
                headerTitle = stringResource(Res.string.backup_restore_header),
                summaryFor = { contents.countLabel(it) }
            )
            if (selection.vaults && contents.vaults.isNotEmpty()) {
                BackupToggleList(
                    items = contents.vaults.map {
                        Triple(
                            it.uuid,
                            it.name,
                            stringResource(Res.string.backup_items_count, it.mediaCount)
                        )
                    },
                    isSelected = { selection.isVaultSelected(it) },
                    onToggle = onToggleVault,
                    headerTitle = stringResource(Res.string.backup_include_vaults)
                )
            }
        }
        BackupNoticeCard(
            icon = Icons.Outlined.Shield,
            text = stringResource(Res.string.backup_warning)
        )
        BackupPrimaryButton(
            text = stringResource(Res.string.backup_restore),
            enabled = anySelected,
            onClick = onRestore
        )
        Spacer(Modifier.height(16.dp))
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

@Composable
fun BackupContents.countLabel(section: BackupSection): String = when (section) {
    BackupSection.VAULTS -> stringResource(Res.string.backup_vaults_value, vaultCount, vaultMediaCount)
    else -> stringResource(Res.string.backup_items_count, count(section))
}
