/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.duplicates

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.duplicates_add_folder
import com.dot.gallery.generated.resources.duplicates_check_large
import com.dot.gallery.generated.resources.duplicates_check_large_summary
import com.dot.gallery.generated.resources.duplicates_checking_header
import com.dot.gallery.generated.resources.duplicates_keep_folders
import com.dot.gallery.generated.resources.duplicates_keep_folders_summary
import com.dot.gallery.generated.resources.duplicates_remove_folder
import com.dot.gallery.generated.resources.duplicates_remove_folders
import com.dot.gallery.generated.resources.duplicates_remove_folders_summary
import com.dot.gallery.generated.resources.duplicates_reset
import com.dot.gallery.generated.resources.duplicates_reset_summary
import com.dot.gallery.generated.resources.duplicates_settings
import com.dot.gallery.injection.injectViewModel
import org.jetbrains.compose.resources.stringResource

private enum class FolderList { KEEP, REMOVE }

/** A picked folder as the relative path the rules match, such as `DCIM/Camera`. */
internal fun pickedFolderPath(uri: Uri): String? = runCatching {
    DocumentsContract.getTreeDocumentId(uri).substringAfter(':').trim('/').ifEmpty { null }
}.onFailure {
    printWarn("duplicates.settings", "could not read the picked folder", ctx = mapOf("error" to it.toString()))
}.getOrNull()

@Composable
fun DuplicateSettingsScreen() {
    val viewModel = injectViewModel<DuplicateSettingsViewModel> { duplicateSettingsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var picking by remember { mutableStateOf<FolderList?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = uri?.let(::pickedFolderPath)
        if (path != null) {
            when (picking) {
                FolderList.KEEP -> viewModel.addKeepFolder(path)
                FolderList.REMOVE -> viewModel.addRemoveFolder(path)
                null -> Unit
            }
        }
        picking = null
    }

    val checkingHeader = stringResource(Res.string.duplicates_checking_header)
    val largeTitle = stringResource(Res.string.duplicates_check_large)
    val largeSummary = stringResource(Res.string.duplicates_check_large_summary)
    val resetTitle = stringResource(Res.string.duplicates_reset)
    val resetSummary = stringResource(Res.string.duplicates_reset_summary)
    val settingsList = remember(state, checkingHeader, largeTitle, largeSummary, resetTitle, resetSummary) {
        listOf(
            SettingsEntity.Header(title = checkingHeader),
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.VideoLibrary,
                title = largeTitle,
                summary = largeSummary,
                enabled = state.loaded,
                isChecked = state.checkLargeFiles,
                onCheck = { viewModel.setCheckLargeFiles(it) },
                screenPosition = Position.Top,
            ),
            SettingsEntity.Preference(
                icon = Icons.Outlined.RestartAlt,
                title = resetTitle,
                summary = resetSummary,
                enabled = state.loaded,
                onClick = { viewModel.reset() },
                screenPosition = Position.Bottom,
            ),
        ).toMutableStateList()
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.duplicates_settings),
        settingsList = settingsList,
        topContent = {
            Column {
                FolderSection(
                    title = stringResource(Res.string.duplicates_keep_folders),
                    summary = stringResource(Res.string.duplicates_keep_folders_summary),
                    folders = state.keepFolders,
                    onRemove = viewModel::removeKeepFolder,
                    onAdd = {
                        picking = FolderList.KEEP
                        picker.launch(null)
                    },
                )
                FolderSection(
                    title = stringResource(Res.string.duplicates_remove_folders),
                    summary = stringResource(Res.string.duplicates_remove_folders_summary),
                    folders = state.removeFolders,
                    onRemove = viewModel::removeRemoveFolder,
                    onAdd = {
                        picking = FolderList.REMOVE
                        picker.launch(null)
                    },
                )
            }
        },
    )
}

/** A settings header, then a card holding the folder chips and an Add folder chip. */
@Composable
private fun FolderSection(
    title: String,
    summary: String,
    folders: List<String>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    SettingsItem(item = SettingsEntity.Header(title = title))
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            folders.forEach { folder ->
                PillChip(
                    label = folder,
                    trailingIcon = Icons.Outlined.Close,
                    description = stringResource(Res.string.duplicates_remove_folder, folder),
                    onClick = { onRemove(folder) },
                )
            }
            PillChip(
                label = stringResource(Res.string.duplicates_add_folder),
                leadingIcon = Icons.Outlined.Add,
                accent = true,
                onClick = onAdd,
            )
        }
    }
}

/**
 * The app's filled pill chip, as on the Timeline filters: 32 dp tall inside a 48 dp target.
 * An [accent] chip uses the primary container, for an action such as Add folder.
 */
@Composable
internal fun PillChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    accent: Boolean = false,
    description: String? = null,
) {
    val container = if (accent) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (accent) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(100)
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(shape)
                .background(container, shape)
                .padding(start = if (leadingIcon != null) 10.dp else 16.dp, end = if (trailingIcon != null) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingIcon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = content)
            trailingIcon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
        }
    }
}
