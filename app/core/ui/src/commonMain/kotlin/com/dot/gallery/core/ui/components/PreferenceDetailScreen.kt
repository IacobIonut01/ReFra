/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.preference_preview_off
import com.dot.gallery.core.ui.generated.resources.preference_preview_on
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource


/**
 * Data class representing a selectable option in a preference detail screen.
 */
@Stable
data class PreferenceOption<T>(
    val value: T,
    val label: String,
    val isSelected: Boolean,
    val description: String? = null,
    val enabled: Boolean = true,
)

/** Canvas height of a preview tile in the side-by-side layout. */
private val TileCanvasHeight = 196.dp

/** Canvas height of a preview tile when the tiles are stacked. */
private val StackedTileCanvasHeight = 104.dp

/**
 * The container that holds a page's preview tiles: `surfaceContainer`, 28 dp corners and 8 dp
 * around and between the tiles.
 */
@Composable
fun PreferencePreviewStage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .settingsContentWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp)
    ) {
        content()
    }
}

/**
 * One choice in a [PreferencePreviewStage]: a canvas with the preview [content] above a radio
 * label. The selected tile takes `primaryContainer` and a filled check. Tapping a tile picks its
 * value, so the tiles are a control in their own right and the page's switch mirrors them.
 */
@Composable
fun PreferencePreviewTile(
    selected: Boolean,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canvasHeight: Dp = TileCanvasHeight,
    expandCanvas: Boolean = false,
    canvasColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "previewTileContainer",
    )
    val labelColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "previewTileLabel",
    )
    val tileShape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(tileShape)
            .background(container)
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expandCanvas) Modifier.weight(1f) else Modifier)
                .heightIn(min = canvasHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(canvasColor),
            contentAlignment = Alignment.Center,
            content = content,
        )
        Row(
            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The page's main switch: a 72 dp pill, `primaryContainer` while on and `surfaceContainerHigh`
 * while off. The whole pill toggles; a disabled switch drops to 38 % opacity.
 */
@Composable
fun MainSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val container by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "mainSwitchContainer",
    )
    val content by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        label = "mainSwitchContent",
    )
    Row(
        modifier = modifier
            .settingsContentWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(container)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = content,
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A full-page preference detail screen with a switch toggle.
 *
 * Layout, top to bottom: the page bar, the Off/On preview tiles (optional), the main switch, any
 * extra options, then the description as a footnote.
 *
 * @param title The screen title shown in the top app bar.
 * @param isChecked Whether the switch is currently on.
 * @param onCheckedChange Callback when the switch state changes.
 * @param switchLabel Label text shown on the main switch. Defaults to [title].
 * @param description Extended description, shown as the page's footnote.
 * @param preview Optional preview drawn in each tile; receives the tile's value (Off or On).
 * @param useColumnLayout Stacks the two tiles, for wide previews such as bars.
 * @param previewBelowSwitch Places the tiles under the switch instead of above it.
 * @param options Optional list of selectable sub-options shown while the switch is on.
 * @param onOptionSelected Callback when a sub-option is selected.
 * @param enabled Whether the switch, tiles and options are interactive.
 */
@Composable
fun <T> SwitchPreferenceDetailScreen(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchLabel: String = title,
    description: String,
    preview: (@Composable (isChecked: Boolean) -> Unit)? = null,
    useColumnLayout: Boolean = false,
    previewBelowSwitch: Boolean = false,
    options: List<PreferenceOption<T>> = emptyList(),
    onOptionSelected: ((T) -> Unit)? = null,
    customContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val offLabel = stringResource(Res.string.preference_preview_off)
    val onLabel = stringResource(Res.string.preference_preview_on)
    RequestInitialSettingsFocus(
        focusRequester = initialFocusRequester,
        enabled = enabled,
        prepareFocus = {
            listState.scrollToItem(if (preview != null && !previewBelowSwitch) 1 else 0)
        },
    )
    PreferenceDetailScaffold(title = title, listState = listState) {
        @Composable
        fun PreviewStage() {
            PreferencePreviewStage(modifier = Modifier.padding(bottom = 16.dp)) {
                @Composable
                fun Tile(variant: Boolean, modifier: Modifier) {
                    PreferencePreviewTile(
                        selected = isChecked == variant,
                        label = if (variant) onLabel else offLabel,
                        onClick = { if (isChecked != variant) onCheckedChange(variant) },
                        enabled = enabled,
                        canvasHeight = if (useColumnLayout) StackedTileCanvasHeight else TileCanvasHeight,
                        expandCanvas = !useColumnLayout,
                        modifier = modifier,
                    ) {
                        preview?.invoke(variant)
                    }
                }
                if (useColumnLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(variant = false, modifier = Modifier.fillMaxWidth())
                        Tile(variant = true, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Tile(variant = false, modifier = Modifier.weight(1f).fillMaxHeight())
                        Tile(variant = true, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }

        if (preview != null && !previewBelowSwitch) {
            item(key = "preview") { PreviewStage() }
        }

        item(key = "switch") {
            MainSwitchPreference(
                title = switchLabel,
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.focusRequester(initialFocusRequester),
            )
        }

        if (preview != null && previewBelowSwitch) {
            item(key = "preview") {
                Box(Modifier.padding(top = 16.dp)) { PreviewStage() }
            }
        }

        // Extra content and options, shown while the switch is on.
        if (customContent != null) {
            item(key = "custom_content") {
                AnimatedVisibility(
                    visible = isChecked,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 632.dp)
                            .fillMaxWidth()
                    ) {
                        customContent()
                    }
                }
            }
        }

        if (options.isNotEmpty()) {
            item(key = "options_spacer") {
                AnimatedVisibility(visible = isChecked) { Spacer(Modifier.height(16.dp)) }
            }
            items(items = options) { option ->
                val optionPosition = optionPosition(options.indexOf(option), options.size)
                AnimatedVisibility(
                    visible = isChecked,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            title = option.label,
                            summary = option.description,
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

        item(key = "description") {
            Footnote(icon = Icons.Outlined.Info, text = description)
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
    previewBelowSwitch: Boolean = false,
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
        previewBelowSwitch = previewBelowSwitch,
        options = emptyList(),
        onOptionSelected = null,
        customContent = customContent,
        enabled = enabled,
    )
}

/**
 * A full-page preference detail screen for chooser/selection preferences (no switch).
 *
 * With [optionPreview], every option becomes a [PreferencePreviewTile] (one row, or a 2 × 2 grid
 * for four options) and the radio list is dropped: use it for up to four visual choices. Without
 * it, an optional single [preview] sits above a radio list. The [description] closes the page as
 * a footnote.
 *
 * @param title The screen title shown in the top app bar.
 * @param description Extended description, shown as the page's footnote.
 * @param preview Optional composable shown above the radio list.
 * @param optionPreview Optional per-option preview; turns the options into tiles.
 * @param options List of selectable options.
 * @param onOptionSelected Callback when an option is selected.
 * @param customContent Optional completely custom content instead of the standard radio list.
 */
@Composable
fun <T> ChooserPreferenceDetailScreen(
    title: String,
    description: String,
    preview: (@Composable () -> Unit)? = null,
    options: List<PreferenceOption<T>> = emptyList(),
    onOptionSelected: ((T) -> Unit)? = null,
    customContent: (@Composable () -> Unit)? = null,
    optionPreview: (@Composable BoxScope.(T) -> Unit)? = null,
    optionCanvasHeight: Dp = if (options.size > 2) 112.dp else TileCanvasHeight,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val tiles = optionPreview != null && options.isNotEmpty()
    RequestInitialSettingsFocus(
        focusRequester = initialFocusRequester,
        enabled = options.isNotEmpty(),
        prepareFocus = { listState.scrollToItem(if (preview != null && !tiles) 1 else 0) },
    )
    PreferenceDetailScaffold(title = title, listState = listState) {
        if (tiles) {
            item(key = "tiles") {
                PreferencePreviewStage {
                    @Composable
                    fun Tile(index: Int, modifier: Modifier) {
                        val option = options[index]
                        PreferencePreviewTile(
                            selected = option.isSelected,
                            label = option.label,
                            onClick = { onOptionSelected?.invoke(option.value) },
                            enabled = option.enabled,
                            canvasHeight = optionCanvasHeight,
                            modifier = if (index == 0) {
                                modifier.focusRequester(initialFocusRequester)
                            } else modifier,
                        ) {
                            optionPreview!!(option.value)
                        }
                    }
                    val rows = if (options.size == 4) options.indices.chunked(2) else listOf(options.indices.toList())
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { index ->
                                    Tile(index, Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (preview != null) {
                item(key = "preview") {
                    PreferencePreviewStage(modifier = Modifier.padding(bottom = 16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            preview()
                        }
                    }
                }
            }

            if (options.isNotEmpty()) {
                items(items = options) { option ->
                    val optionIndex = options.indexOf(option)
                    SettingsItem(
                        modifier = if (optionIndex == 0) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        },
                        item = SettingsEntity.Preference(
                            title = option.label,
                            summary = option.description,
                            onClick = { onOptionSelected?.invoke(option.value) },
                            screenPosition = optionPosition(optionIndex, options.size),
                            enabled = option.enabled,
                        ),
                        customTrailingContent = {
                            RadioButton(
                                selected = option.isSelected,
                                onClick = { onOptionSelected?.invoke(option.value) },
                                enabled = option.enabled,
                            )
                        }
                    )
                }
            }
        }

        if (customContent != null) {
            item(key = "custom") {
                Box(
                    modifier = Modifier
                        .widthIn(max = 632.dp)
                        .fillMaxWidth()
                ) {
                    customContent()
                }
            }
        }

        if (description.isNotBlank()) {
            item(key = "description") {
                Footnote(icon = Icons.Outlined.Info, text = description)
            }
        }
    }
}

/** Shared page shell: the gallery page bar over a centred list that feeds the bar's blur. */
@Composable
private fun PreferenceDetailScaffold(
    title: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = title,
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current).settingsFocusGroup(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 8.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

private fun optionPosition(index: Int, size: Int): Position = when {
    size == 1 -> Position.Alone
    index == 0 -> Position.Top
    index == size - 1 -> Position.Bottom
    else -> Position.Middle
}
