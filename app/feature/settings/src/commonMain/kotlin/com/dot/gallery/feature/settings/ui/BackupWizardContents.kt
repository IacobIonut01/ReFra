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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.backup.BackupContents
import com.dot.gallery.core.backup.BackupSection
import com.dot.gallery.core.backup.BackupSelection
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.StatTile
import com.dot.gallery.core.ui.components.SummaryCard
import com.dot.gallery.core.ui.components.SummaryCardButton
import com.dot.gallery.core.ui.components.WizardAction
import com.dot.gallery.core.ui.components.WizardScaffold
import com.dot.gallery.core.ui.components.settingsContentWidth
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_cancel
import com.dot.gallery.feature.settings.generated.resources.backup_back
import com.dot.gallery.feature.settings.generated.resources.backup_choose_file
import com.dot.gallery.feature.settings.generated.resources.backup_empty
import com.dot.gallery.feature.settings.generated.resources.backup_encrypt_summary
import com.dot.gallery.feature.settings.generated.resources.backup_export_password_footnote
import com.dot.gallery.feature.settings.generated.resources.backup_hub_create
import com.dot.gallery.feature.settings.generated.resources.backup_hub_restore
import com.dot.gallery.feature.settings.generated.resources.backup_import_password_desc
import com.dot.gallery.feature.settings.generated.resources.backup_import_password_title
import com.dot.gallery.feature.settings.generated.resources.backup_include_vaults
import com.dot.gallery.feature.settings.generated.resources.backup_items_count
import com.dot.gallery.feature.settings.generated.resources.backup_made_with_version
import com.dot.gallery.feature.settings.generated.resources.backup_next
import com.dot.gallery.feature.settings.generated.resources.backup_no_file_body
import com.dot.gallery.feature.settings.generated.resources.backup_no_file_title
import com.dot.gallery.feature.settings.generated.resources.backup_password
import com.dot.gallery.feature.settings.generated.resources.backup_password_confirm
import com.dot.gallery.feature.settings.generated.resources.backup_preview_credentials
import com.dot.gallery.feature.settings.generated.resources.backup_preview_encrypted
import com.dot.gallery.feature.settings.generated.resources.backup_preview_no_credentials
import com.dot.gallery.feature.settings.generated.resources.backup_preview_plain
import com.dot.gallery.feature.settings.generated.resources.backup_protect_with_password
import com.dot.gallery.feature.settings.generated.resources.backup_reading_file
import com.dot.gallery.feature.settings.generated.resources.backup_restore
import com.dot.gallery.feature.settings.generated.resources.backup_restore_footnote
import com.dot.gallery.feature.settings.generated.resources.backup_restore_from
import com.dot.gallery.feature.settings.generated.resources.backup_restore_kept_note
import com.dot.gallery.feature.settings.generated.resources.backup_save_file
import com.dot.gallery.feature.settings.generated.resources.backup_save_footnote
import com.dot.gallery.feature.settings.generated.resources.backup_save_included
import com.dot.gallery.feature.settings.generated.resources.backup_save_protected
import com.dot.gallery.feature.settings.generated.resources.backup_save_sections_label
import com.dot.gallery.feature.settings.generated.resources.backup_save_unprotected
import com.dot.gallery.feature.settings.generated.resources.backup_save_vaults_label
import com.dot.gallery.feature.settings.generated.resources.backup_save_where
import com.dot.gallery.feature.settings.generated.resources.backup_save_where_summary
import com.dot.gallery.feature.settings.generated.resources.backup_sections_inside
import com.dot.gallery.feature.settings.generated.resources.backup_show_password
import com.dot.gallery.feature.settings.generated.resources.backup_step
import com.dot.gallery.feature.settings.generated.resources.backup_step_choose_file
import com.dot.gallery.feature.settings.generated.resources.backup_step_encrypt
import com.dot.gallery.feature.settings.generated.resources.backup_step_include
import com.dot.gallery.feature.settings.generated.resources.backup_step_review
import com.dot.gallery.feature.settings.generated.resources.backup_step_save
import com.dot.gallery.feature.settings.generated.resources.backup_title_choose_file
import com.dot.gallery.feature.settings.generated.resources.backup_title_encrypt
import com.dot.gallery.feature.settings.generated.resources.backup_title_include
import com.dot.gallery.feature.settings.generated.resources.backup_title_review
import com.dot.gallery.feature.settings.generated.resources.backup_title_save
import com.dot.gallery.feature.settings.generated.resources.backup_vault_items
import com.dot.gallery.feature.settings.generated.resources.backup_warning
import com.dot.gallery.feature.settings.generated.resources.backup_wrong_password
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class ExportStep { SELECT, ENCRYPT, SAVE }

/** Allows [ExportStep] to be persisted across configuration changes via rememberSaveable. */
val ExportStepSaver = Saver<ExportStep, Int>(
    save = { it.ordinal },
    restore = { ExportStep.entries[it] }
)

/** Title of the Create backup wizard, for its progress and result pages too. */
@Composable
fun exportWizardTitle(): String = stringResource(Res.string.backup_hub_create)

/** Title of the Restore backup wizard, for its progress and result pages too. */
@Composable
fun importWizardTitle(): String = stringResource(Res.string.backup_hub_restore)

private const val EXPORT_STEPS = 3
private const val IMPORT_STEPS = 2

@Composable
private fun stepLabel(step: Int, count: Int, name: StringResource): String =
    stringResource(Res.string.backup_step, step, count, stringResource(name))

// =================================================================================================
// Create backup: 1 Include → 2 Encrypt → 3 Save
// =================================================================================================

/**
 * Step 1 · Include. Vault toggling goes through [onToggle] so the caller can run the vault gate;
 * [vaultItems] list the vaults once the section is on.
 */
@Composable
fun ExportIncludeStep(
    sections: List<BackupSection>,
    isSelected: (BackupSection) -> Boolean,
    onToggle: (BackupSection, Boolean) -> Unit,
    vaultItems: List<Triple<String, String, String?>>,
    vaultIsSelected: (String) -> Boolean,
    onToggleVault: (String, Boolean) -> Unit,
    showVaultList: Boolean,
    nextEnabled: Boolean,
    onNext: () -> Unit,
    onCancel: () -> Unit,
) {
    WizardScaffold(
        title = stringResource(Res.string.backup_hub_create),
        step = 1,
        stepCount = EXPORT_STEPS,
        stepName = stepLabel(1, EXPORT_STEPS, Res.string.backup_step_include),
        heading = stringResource(Res.string.backup_title_include),
        primary = WizardAction(stringResource(Res.string.backup_next), onNext, nextEnabled),
        secondary = WizardAction(stringResource(Res.string.action_cancel), onCancel),
        onBack = onCancel,
    ) {
        BackupSectionList(sections = sections, isSelected = isSelected, onToggle = onToggle)
        if (showVaultList) {
            BackupToggleList(
                items = vaultItems,
                isSelected = vaultIsSelected,
                onToggle = onToggleVault,
                headerTitle = stringResource(Res.string.backup_include_vaults)
            )
        }
        BackupNote(icon = Icons.Outlined.Shield, text = stringResource(Res.string.backup_warning))
    }
}

/** Step 2 · Encrypt: optional password protection. */
@Composable
fun ExportEncryptStep(
    protect: Boolean,
    onProtectChange: (Boolean) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirm: String,
    onConfirmChange: (String) -> Unit,
    showPassword: Boolean,
    onShowPasswordChange: (Boolean) -> Unit,
    nextEnabled: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    WizardScaffold(
        title = stringResource(Res.string.backup_hub_create),
        step = 2,
        stepCount = EXPORT_STEPS,
        stepName = stepLabel(2, EXPORT_STEPS, Res.string.backup_step_encrypt),
        heading = stringResource(Res.string.backup_title_encrypt),
        primary = WizardAction(stringResource(Res.string.backup_next), onNext, nextEnabled),
        secondary = WizardAction(stringResource(Res.string.backup_back), onBack),
        onBack = onBack,
    ) {
        SettingsItem(
            item = SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.Shield,
                title = stringResource(Res.string.backup_protect_with_password),
                summary = stringResource(Res.string.backup_encrypt_summary),
                isChecked = protect,
                onCheck = onProtectChange,
                screenPosition = Position.Alone,
            )
        )
        if (protect) {
            Column(
                modifier = Modifier
                    .settingsContentWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BackupPasswordField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = stringResource(Res.string.backup_password),
                    visible = showPassword,
                )
                BackupPasswordField(
                    value = confirm,
                    onValueChange = onConfirmChange,
                    label = stringResource(Res.string.backup_password_confirm),
                    visible = showPassword,
                    isError = confirm.isNotEmpty() && confirm != password,
                )
            }
            SettingsItem(
                item = SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.Visibility,
                    title = stringResource(Res.string.backup_show_password),
                    isChecked = showPassword,
                    onCheck = onShowPasswordChange,
                    screenPosition = Position.Alone,
                ),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        BackupNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.backup_export_password_footnote))
    }
}

/** Step 3 · Save: what the file will hold, then the system save screen. */
@Composable
fun ExportSaveStep(
    fileName: String,
    protected: Boolean,
    includedTitles: List<String>,
    sectionCount: Int,
    totalSections: Int,
    /** Vaults going into the file, or null when the Vaults section is off. */
    vaultCount: Int?,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    WizardScaffold(
        title = stringResource(Res.string.backup_hub_create),
        step = 3,
        stepCount = EXPORT_STEPS,
        stepName = stepLabel(3, EXPORT_STEPS, Res.string.backup_step_save),
        heading = stringResource(Res.string.backup_title_save),
        primary = WizardAction(stringResource(Res.string.backup_save_file), onSave),
        secondary = WizardAction(stringResource(Res.string.backup_back), onBack),
        onBack = onBack,
    ) {
        SummaryCard(
            icon = Icons.Outlined.Save,
            title = fileName,
            body = stringResource(
                if (protected) Res.string.backup_save_protected else Res.string.backup_save_unprotected,
                sectionCount,
                totalSections,
            ),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    value = sectionCount.toString(),
                    label = stringResource(Res.string.backup_save_sections_label),
                    modifier = Modifier.weight(1f),
                )
                if (vaultCount != null) {
                    StatTile(
                        value = vaultCount.toString(),
                        label = stringResource(Res.string.backup_save_vaults_label),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        SettingsItem(
            item = SettingsEntity.Preference(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(Res.string.backup_save_included),
                summary = includedTitles.joinToString(", "),
                screenPosition = Position.Top,
            )
        )
        SettingsItem(
            item = SettingsEntity.Preference(
                icon = Icons.Outlined.Folder,
                title = stringResource(Res.string.backup_save_where),
                summary = stringResource(Res.string.backup_save_where_summary),
                screenPosition = Position.Bottom,
            )
        )
        BackupNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.backup_save_footnote))
    }
}

// =================================================================================================
// Restore: 1 Choose file → 2 Review
// =================================================================================================

/** What step 1 of the restore wizard is waiting on. */
sealed interface ImportChooseState {
    data object NoFile : ImportChooseState
    data object Reading : ImportChooseState
    data class NeedsPassword(val wrongPassword: Boolean) : ImportChooseState
}

/** Step 1 · Choose file: pick the archive, wait while it's read, enter its password. */
@Composable
fun ImportChooseStep(
    state: ImportChooseState,
    onPickFile: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val wrong = (state as? ImportChooseState.NeedsPassword)?.wrongPassword == true
    LaunchedEffect(wrong) { if (wrong) password = "" }
    val needsPassword = state is ImportChooseState.NeedsPassword

    WizardScaffold(
        title = stringResource(Res.string.backup_hub_restore),
        step = 1,
        stepCount = IMPORT_STEPS,
        stepName = stepLabel(1, IMPORT_STEPS, Res.string.backup_step_choose_file),
        heading = stringResource(Res.string.backup_title_choose_file),
        primary = WizardAction(
            label = stringResource(Res.string.backup_next),
            onClick = { onSubmitPassword(password) },
            enabled = needsPassword && password.isNotBlank(),
        ),
        secondary = WizardAction(stringResource(Res.string.action_cancel), onCancel),
        onBack = onCancel,
    ) {
        when (state) {
            ImportChooseState.NoFile -> ChooseFileCard(onPickFile)
            ImportChooseState.Reading -> ChooseFileCard(onPickFile = null)
            is ImportChooseState.NeedsPassword -> {
                SummaryCard(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(Res.string.backup_import_password_title),
                    body = stringResource(Res.string.backup_import_password_desc),
                ) {
                    BackupPasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(Res.string.backup_password),
                        visible = showPassword,
                        isError = wrong,
                        supportingText = if (wrong) stringResource(Res.string.backup_wrong_password) else null,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                }
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        icon = Icons.Outlined.Visibility,
                        title = stringResource(Res.string.backup_show_password),
                        isChecked = showPassword,
                        onCheck = { showPassword = it },
                        screenPosition = Position.Alone,
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        BackupNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.backup_restore_footnote))
    }
}

/** The "No file chosen" card; a null [onPickFile] shows the reading state instead. */
@Composable
private fun ChooseFileCard(onPickFile: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .settingsContentWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (onPickFile == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(
            text = stringResource(if (onPickFile == null) Res.string.backup_reading_file else Res.string.backup_no_file_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onPickFile != null) {
            Text(
                text = stringResource(Res.string.backup_no_file_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            SummaryCardButton(
                label = stringResource(Res.string.backup_choose_file),
                icon = Icons.Outlined.FolderOpen,
                filled = true,
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Step 2 · Review: the archive's details, then what to restore. */
@Composable
fun ImportReviewStep(
    sections: List<BackupSection>,
    contents: BackupContents,
    selection: BackupSelection,
    onToggle: (BackupSection, Boolean) -> Unit,
    onToggleVault: (String, Boolean) -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
) {
    val anySelected = sections.any { selection.isOn(it) } &&
        (!selection.vaults || sections.any { it != BackupSection.VAULTS && selection.isOn(it) } ||
            contents.vaults.any { selection.isVaultSelected(it.uuid) })
    WizardScaffold(
        title = stringResource(Res.string.backup_hub_restore),
        step = 2,
        stepCount = IMPORT_STEPS,
        stepName = stepLabel(2, IMPORT_STEPS, Res.string.backup_step_review),
        heading = stringResource(Res.string.backup_title_review),
        primary = WizardAction(stringResource(Res.string.backup_restore), onRestore, anySelected),
        secondary = WizardAction(stringResource(Res.string.backup_back), onBack),
        onBack = onBack,
    ) {
        val archive = stringResource(
            if (contents.encrypted) Res.string.backup_preview_encrypted else Res.string.backup_preview_plain
        )
        SummaryCard(
            icon = Icons.AutoMirrored.Outlined.NoteAdd,
            title = stringResource(
                Res.string.backup_restore_from,
                (contents.exportedAt / 1000).formatEpochSec("MMM d, yyyy"),
            ),
            body = if (contents.appVersionName.isNotBlank()) {
                stringResource(Res.string.backup_made_with_version, contents.appVersionName) + " · " + archive
            } else archive,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    value = sections.size.toString(),
                    label = stringResource(Res.string.backup_sections_inside),
                    modifier = Modifier.weight(1f),
                )
                if (contents.has(BackupSection.VAULTS)) {
                    StatTile(
                        value = contents.vaultMediaCount.toString(),
                        label = stringResource(Res.string.backup_vault_items),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (sections.isEmpty()) {
            BackupNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.backup_empty))
        } else {
            BackupSectionList(
                sections = sections,
                isSelected = { selection.isOn(it) },
                onToggle = onToggle,
                summaryFor = { contents.countLabel(it) }
            )
            if (selection.vaults && contents.vaults.isNotEmpty()) {
                BackupToggleList(
                    items = contents.vaults.map {
                        Triple(it.uuid, it.name, stringResource(Res.string.backup_items_count, it.mediaCount))
                    },
                    isSelected = { selection.isVaultSelected(it) },
                    onToggle = onToggleVault,
                    headerTitle = stringResource(Res.string.backup_include_vaults)
                )
            }
        }
        val vaultNote = if (contents.has(BackupSection.VAULTS)) {
            " " + stringResource(
                if (contents.credentialIncluded) Res.string.backup_preview_credentials
                else Res.string.backup_preview_no_credentials
            ) + "."
        } else ""
        BackupNote(
            icon = Icons.Outlined.Info,
            text = stringResource(Res.string.backup_restore_kept_note) + vaultNote,
        )
    }
}

/** Filled 16 dp password field in the sheet style. */
@Composable
private fun BackupPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    isError: Boolean = false,
    supportingText: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
