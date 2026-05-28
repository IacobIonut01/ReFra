/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.NavigationBackButton

/**
 * Data class representing a selectable option in a preference detail screen.
 */
@Stable
data class PreferenceOption<T>(
    val value: T,
    val label: String,
    val isSelected: Boolean,
    val description: String? = null,
)

/**
 * A full-page preference detail screen with a switch toggle.
 *
 * Inspired by Android 14/15 system settings (e.g., "Double-press power button"):
 * - Title (LargeTopAppBar)
 * - Preview area (composable slot, optional)
 * - Switch row with label
 * - Extended description
 * - Extra options (optional, shown below the description)
 *
 * @param title The screen title shown in the top app bar.
 * @param isChecked Whether the switch is currently on.
 * @param onCheckedChange Callback when the switch state changes.
 * @param switchLabel Label text shown next to the switch. Defaults to [title].
 * @param description Extended description explaining what this setting does.
 * @param preview Optional composable slot for a visual preview above the switch.
 *                Receives the current checked state so it can react visually.
 * @param options Optional list of selectable sub-options shown below the description.
 * @param onOptionSelected Callback when a sub-option is selected.
 * @param enabled Whether the switch and options are interactive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SwitchPreferenceDetailScreen(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchLabel: String = title,
    description: String,
    preview: (@Composable (isChecked: Boolean) -> Unit)? = null,
    useColumnLayout: Boolean = false,
    options: List<PreferenceOption<T>> = emptyList(),
    onOptionSelected: ((T) -> Unit)? = null,
    customContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview area — shows both on and off states
            if (preview != null) {
                item(key = "preview") {
                    @Composable
                    fun PreviewVariant(variant: Boolean, modifier: Modifier = Modifier, expandContent: Boolean = false) {
                        val isActive = isChecked == variant
                        val borderColor = if (isActive) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                        Column(
                            modifier = modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    width = 2.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (expandContent) Modifier.weight(1f) else Modifier)
                                    .heightIn(min = 100.dp)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                preview(variant)
                            }
                            Text(
                                text = if (variant) "On" else "Off",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )
                        }
                    }
                    if (useColumnLayout) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PreviewVariant(variant = false, modifier = Modifier.fillMaxWidth())
                            PreviewVariant(variant = true, modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max)
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PreviewVariant(variant = false, modifier = Modifier.weight(1f).fillMaxHeight(), expandContent = true)
                            PreviewVariant(variant = true, modifier = Modifier.weight(1f).fillMaxHeight(), expandContent = true)
                        }
                    }
                }
            }

            // Switch row
            item(key = "switch") {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = switchLabel,
                        isChecked = isChecked,
                        onCheck = { onCheckedChange(it) },
                        enabled = enabled,
                        screenPosition = Position.Alone
                    )
                )
            }

            // Description
            item(key = "description") {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = if (options.isNotEmpty() || customContent != null) 16.dp else 0.dp)
                )
            }

            // Custom content (visible when switch is on)
            if (customContent != null) {
                item(key = "custom_content") {
                    AnimatedVisibility(
                        visible = isChecked,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                        ) {
                            customContent()
                        }
                    }
                }
            }

            // Extra options
            if (options.isNotEmpty()) {
                items(
                    items = options,
                    key = { it.label }
                ) { option ->
                    val optionIndex = options.indexOf(option)
                    val optionPosition = when {
                        options.size == 1 -> Position.Alone
                        optionIndex == 0 -> Position.Top
                        optionIndex == options.lastIndex -> Position.Bottom
                        else -> Position.Middle
                    }
                    AnimatedVisibility(
                        visible = isChecked,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        SettingsItem(
                            item = SettingsEntity.Preference(
                                title = option.label,
                                onClick = { onOptionSelected?.invoke(option.value) },
                                enabled = enabled && isChecked,
                                screenPosition = optionPosition
                            ),
                            customTrailingContent = {
                                RadioButton(
                                    selected = option.isSelected,
                                    onClick = { onOptionSelected?.invoke(option.value) },
                                    enabled = enabled && isChecked
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Convenience overload for a simple switch detail screen without extra options.
 */
@Composable
fun SwitchPreferenceDetailScreen(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchLabel: String = title,
    description: String,
    preview: (@Composable (isChecked: Boolean) -> Unit)? = null,
    useColumnLayout: Boolean = false,
    customContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    SwitchPreferenceDetailScreen<Unit>(
        title = title,
        isChecked = isChecked,
        onCheckedChange = onCheckedChange,
        switchLabel = switchLabel,
        description = description,
        preview = preview,
        useColumnLayout = useColumnLayout,
        options = emptyList(),
        onOptionSelected = null,
        customContent = customContent,
        enabled = enabled,
    )
}

/**
 * A full-page preference detail screen for chooser/selection preferences (no switch).
 *
 * Similar to [SwitchPreferenceDetailScreen] but without the toggle:
 * - Title (LargeTopAppBar)
 * - Preview area (composable slot, optional)
 * - Extended description
 * - Selection options (radio buttons, cards, or custom)
 *
 * @param title The screen title shown in the top app bar.
 * @param description Extended description explaining what this setting does.
 * @param preview Optional composable slot for a visual preview.
 * @param options List of selectable options shown below the description.
 * @param onOptionSelected Callback when an option is selected.
 * @param customContent Optional completely custom content instead of the standard radio list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChooserPreferenceDetailScreen(
    title: String,
    description: String,
    preview: (@Composable () -> Unit)? = null,
    options: List<PreferenceOption<T>> = emptyList(),
    onOptionSelected: ((T) -> Unit)? = null,
    customContent: (@Composable () -> Unit)? = null,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview area
            if (preview != null) {
                item(key = "preview") {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        preview()
                    }
                }
            }

            // Description
            item(key = "description") {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = if (options.isNotEmpty() || customContent != null) 16.dp else 0.dp)
                )
            }

            // Standard radio options
            if (options.isNotEmpty()) {
                items(
                    items = options,
                    key = { it.label }
                ) { option ->
                    val optionIndex = options.indexOf(option)
                    val optionPosition = when {
                        options.size == 1 -> Position.Alone
                        optionIndex == 0 -> Position.Top
                        optionIndex == options.lastIndex -> Position.Bottom
                        else -> Position.Middle
                    }
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            title = option.label,
                            summary = option.description,
                            onClick = { onOptionSelected?.invoke(option.value) },
                            screenPosition = optionPosition
                        ),
                        customTrailingContent = {
                            RadioButton(
                                selected = option.isSelected,
                                onClick = { onOptionSelected?.invoke(option.value) }
                            )
                        }
                    )
                }
            }

            // Custom content (for cards, grids, etc.)
            if (customContent != null) {
                item(key = "custom") {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                    ) {
                        customContent()
                    }
                }
            }
        }
    }
}
