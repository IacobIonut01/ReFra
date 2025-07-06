package com.dot.gallery.feature_node.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity

@Composable
fun rememberSwitchPreference(
    vararg keys: Any? = emptyArray(),
    title: String,
    icon: ImageVector? = null,
    summary: String? = null,
    isChecked: Boolean,
    onCheck: (Boolean) -> Unit,
    enabled: Boolean = true,
    screenPosition: Position
) = remember(
    *keys,
    title,
    summary,
    isChecked,
    onCheck,
    enabled,
    screenPosition
) {
    SettingsEntity.SwitchPreference(
        title = title,
        summary = summary,
        icon = icon,
        isChecked = isChecked,
        onCheck = onCheck,
        enabled = enabled,
        screenPosition = screenPosition
    )
}

@Composable
fun rememberPreference(
    vararg keys: Any? = emptyArray(),
    title: String,
    icon: ImageVector? = null,
    summary: String? = null,
    onClick: () -> Unit,
    screenPosition: Position
) = remember(
    *keys,
    title,
    summary,
    onClick,
    screenPosition
) {
    SettingsEntity.Preference(
        title = title,
        summary = summary,
        icon = icon,
        onClick = onClick,
        screenPosition = screenPosition
    )
}