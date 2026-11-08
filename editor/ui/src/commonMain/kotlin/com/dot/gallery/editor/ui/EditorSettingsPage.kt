package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_settings_back
import org.jetbrains.compose.resources.stringResource

internal const val TAG_EDITOR_SETTINGS = "editor.settings"
internal const val TAG_EDITOR_SETTINGS_BACK = "editor.settings.back"

/**
 * One row of an editor settings page — host-agnostic data, so a host with
 * its own settings DSL (Gallery's SettingsEntity / BaseSettingsScreen) can
 * render it through [LocalEditorSettingsRenderer].
 */
sealed class EditorSettingRow(
    val icon: ImageVector,
    val title: String,
    val summary: String?,
    val tag: String
) {
    class Toggle(
        icon: ImageVector, title: String, summary: String?, tag: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : EditorSettingRow(icon, title, summary, tag)

    /** A value picked from [options] in a menu anchored on the row. */
    class Choice(
        icon: ImageVector, title: String, summary: String?, tag: String,
        val options: List<String>,
        val selected: Int,
        val onSelect: (Int) -> Unit
    ) : EditorSettingRow(icon, title, summary, tag)

    /** Opens something else; [value] is an optional trailing readout. */
    class Action(
        icon: ImageVector, title: String, summary: String?, tag: String,
        val value: String? = null,
        val enabled: Boolean = true,
        val onClick: () -> Unit
    ) : EditorSettingRow(icon, title, summary, tag)

    /** A read-only fact. */
    class Info(
        icon: ImageVector, title: String, tag: String,
        val value: String
    ) : EditorSettingRow(icon, title, null, tag)
}

class EditorSettingsSection(val title: String, val rows: List<EditorSettingRow>)

/**
 * Draws an editor settings page with the host's own settings components.
 * The Gallery host renders through its SettingsEntity DSL; without one the
 * editor falls back to [EditorSettingsFallbackPage].
 */
fun interface EditorSettingsRenderer {
    @Composable
    fun Render(
        title: String,
        sections: List<EditorSettingsSection>,
        footer: String?,
        onBack: () -> Unit
    )
}

val LocalEditorSettingsRenderer =
    staticCompositionLocalOf<EditorSettingsRenderer?> { null }

/** An editor settings page — the host's renderer when it provides one. */
@Composable
internal fun EditorSettingsPage(
    title: String,
    sections: List<EditorSettingsSection>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    footer: String? = null
) {
    val renderer = LocalEditorSettingsRenderer.current
    if (renderer != null) {
        Box(modifier.fillMaxSize().testTag(TAG_EDITOR_SETTINGS)) {
            renderer.Render(title, sections, footer, onBack)
        }
    } else {
        EditorSettingsFallbackPage(title, sections, onBack, modifier, footer)
    }
}

/**
 * The page for hosts without a settings DSL (desktop, the standalone
 * Android library). It mirrors the anatomy of Gallery's BaseSettingsScreen —
 * the editor modules build for desktop and cannot depend on app:core:ui —
 * so the look matches when Gallery renders the same rows itself.
 */
@Composable
private fun EditorSettingsFallbackPage(
    title: String,
    sections: List<EditorSettingsSection>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    footer: String? = null
) {
    Surface(
        modifier = modifier.fillMaxSize().testTag(TAG_EDITOR_SETTINGS),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 632.dp).fillMaxWidth()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 4.dp, top = 4.dp)
                        .testTag(TAG_EDITOR_SETTINGS_BACK)
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(Res.string.editor_settings_back)
                    )
                }
                Text(
                    text = title,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 28.dp, bottom = 28.dp)
                        .semantics { heading() }
                )
                sections.forEach { section ->
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 8.dp)
                            .semantics { heading() }
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        section.rows.forEachIndexed { index, row ->
                            SettingsRow(row, settingsRowShape(index, section.rows.size))
                        }
                    }
                }
                if (footer != null) {
                    Text(
                        text = footer,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Top, Middle, Bottom and Alone corners of the app's grouped settings rows. */
private fun settingsRowShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) 24.dp else 8.dp
    val bottom = if (index == count - 1) 24.dp else 8.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun SettingsRow(row: EditorSettingRow, shape: RoundedCornerShape) {
    var menuOpen by remember { mutableStateOf(false) }
    val enabled = (row as? EditorSettingRow.Action)?.enabled ?: true
    val interaction = when (row) {
        is EditorSettingRow.Toggle -> Modifier.toggleable(
            value = row.checked,
            role = Role.Switch,
            onValueChange = row.onCheckedChange
        )
        is EditorSettingRow.Choice -> Modifier.clickable(role = Role.Button) { menuOpen = true }
        is EditorSettingRow.Action -> Modifier.clickable(
            enabled = row.enabled, role = Role.Button, onClick = row.onClick
        )
        is EditorSettingRow.Info -> Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(interaction)
            .testTag(row.tag)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val alpha = if (enabled) 1f else 0.4f
        Icon(
            imageVector = row.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier.padding(end = 12.dp).size(22.dp)
        )
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = row.title,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (row.summary != null) {
                Text(
                    text = row.summary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        when (row) {
            is EditorSettingRow.Toggle -> Switch(checked = row.checked, onCheckedChange = null)
            is EditorSettingRow.Choice -> Box {
                SettingsValue(row.options.getOrElse(row.selected) { "" })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    row.options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            trailingIcon = if (index == row.selected) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else null,
                            onClick = {
                                menuOpen = false
                                row.onSelect(index)
                            },
                            modifier = Modifier.testTag("${row.tag}.$index")
                        )
                    }
                }
            }
            is EditorSettingRow.Action -> row.value?.let { SettingsValue(it) }
            is EditorSettingRow.Info -> SettingsValue(row.value)
        }
    }
}

@Composable
private fun SettingsValue(value: String) {
    Text(
        text = value,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}
