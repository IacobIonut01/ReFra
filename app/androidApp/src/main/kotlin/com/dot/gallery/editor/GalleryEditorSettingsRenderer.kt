package com.dot.gallery.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionSheet
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.editor.ui.EditorSettingRow
import com.dot.gallery.editor.ui.EditorSettingsRenderer
import com.dot.gallery.editor.ui.EditorSettingsSection
import kotlinx.coroutines.launch

/**
 * Renders the editors' settings pages with Gallery's own settings DSL:
 * [BaseSettingsScreen] over [SettingsEntity] rows (headers, switches and
 * right-text preferences, grouped by [Position]), and the app's
 * [OptionSheet] for choice rows.
 */
internal val GalleryEditorSettingsRenderer = EditorSettingsRenderer { title, sections, footer, onBack ->
    var choice by remember { mutableStateOf<EditorSettingRow.Choice?>(null) }
    val entities = remember(sections) {
        sections.flatMap { section -> section.toEntities { choice = it } }.toMutableStateList()
    }
    BaseSettingsScreen(
        title = title,
        settingsList = entities,
        onBack = onBack,
        bottomContent = footer?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }
        }
    )
    choice?.let { row -> ChoiceSheet(row, onDone = { choice = null }) }
}

private fun EditorSettingsSection.toEntities(
    openChoice: (EditorSettingRow.Choice) -> Unit
): List<SettingsEntity> = buildList {
    add(SettingsEntity.Header(title = title))
    rows.forEachIndexed { index, row ->
        val position = when {
            rows.size == 1 -> Position.Alone
            index == 0 -> Position.Top
            index == rows.lastIndex -> Position.Bottom
            else -> Position.Middle
        }
        add(
            when (row) {
                is EditorSettingRow.Toggle -> SettingsEntity.SwitchPreference(
                    icon = row.icon,
                    title = row.title,
                    summary = row.summary,
                    isChecked = row.checked,
                    onCheck = row.onCheckedChange,
                    screenPosition = position
                )
                is EditorSettingRow.Choice -> SettingsEntity.Preference(
                    icon = row.icon,
                    title = row.title,
                    summary = row.summary,
                    rightText = row.options.getOrNull(row.selected),
                    onClick = { openChoice(row) },
                    screenPosition = position,
                    tag = row.tag
                )
                is EditorSettingRow.Action -> SettingsEntity.Preference(
                    icon = row.icon,
                    title = row.title,
                    summary = row.summary,
                    rightText = row.value,
                    enabled = row.enabled,
                    onClick = row.onClick,
                    screenPosition = position,
                    tag = row.tag
                )
                is EditorSettingRow.Info -> SettingsEntity.Preference(
                    icon = row.icon,
                    title = row.title,
                    rightText = row.value,
                    screenPosition = position,
                    tag = row.tag
                )
            }
        )
    }
}

@Composable
private fun ChoiceSheet(row: EditorSettingRow.Choice, onDone: () -> Unit) {
    val sheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(row) { sheetState.show() }
    val options = remember(row) {
        row.options.mapIndexed { index, label ->
            OptionItem(
                icon = if (index == row.selected) Icons.Outlined.Check else null,
                text = label,
                onClick = { _ ->
                    row.onSelect(index)
                    scope.launch { sheetState.hide(); onDone() }
                }
            )
        }.toMutableStateList()
    }
    OptionSheet(
        state = sheetState,
        onDismiss = onDone,
        headerContent = {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        },
        optionList = arrayOf(options)
    )
}
